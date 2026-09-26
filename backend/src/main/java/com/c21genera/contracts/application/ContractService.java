package com.c21genera.contracts.application;

import com.c21genera.contracts.domain.ContractCalculator;
import com.c21genera.contracts.domain.ContractGeneration;
import com.c21genera.contracts.domain.ContractTemplate;
import com.c21genera.contracts.domain.ContractTemplate.ContractInput;
import com.c21genera.contracts.domain.document.ContractDocument;
import com.c21genera.contracts.infrastructure.ContractGenerationRepository;
import com.c21genera.contracts.infrastructure.DocxContractRenderer;
import com.c21genera.contracts.infrastructure.PdfContractRenderer;
import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.RequirementStatusView;
import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ManualClientDataView;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.expedientes.ExpedienteSummary.ParticipantView;
import com.c21genera.expedientes.PersonType;
import com.c21genera.extraction.ExtractionApi;
import com.c21genera.privacy.PrivacyApi;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.DocumentTypeLabels;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.ContractEvents.ContractGenerated;
import com.c21genera.shared.events.ContractEvents.ContractSuperseded;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteDataCorrected;
import com.c21genera.shared.storage.FileStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generación del contrato (retroalimentación 25/09, hallazgos 1 y 13).
 *
 * <p>Antes de generar un contrato para firma se valida TODO: documentos
 * obligatorios aceptados (o "No aplica" justificado), recepción firmada,
 * aviso de privacidad aceptado, sin diferencias sin resolver entre
 * documentos, precio mayor a cero y todos los datos que el modelo registrado
 * necesita para la variante del expediente. Si falta algo, solo se puede
 * generar un BORRADOR INCOMPLETO: marcado así en cada página y bloqueado para
 * firma.
 *
 * <p>Cada generación es un snapshot inmutable con versión; cualquier
 * corrección de datos deja sin efecto las versiones no firmadas.
 */
@Service
@Transactional
public class ContractService {

  private final ExpedienteLifecycleApi expedienteApi;
  private final DocumentsApi documentsApi;
  private final PrivacyApi privacyApi;
  private final ExtractionApi extractionApi;
  private final DocxContractRenderer docxRenderer;
  private final PdfContractRenderer pdfRenderer;
  private final FileStorage fileStorage;
  private final ContractGenerationRepository repository;
  private final ContractSignatureService signatureService;
  private final ApplicationEventPublisher events;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public ContractService(
      ExpedienteLifecycleApi expedienteApi,
      DocumentsApi documentsApi,
      PrivacyApi privacyApi,
      ExtractionApi extractionApi,
      DocxContractRenderer docxRenderer,
      PdfContractRenderer pdfRenderer,
      FileStorage fileStorage,
      ContractGenerationRepository repository,
      ContractSignatureService signatureService,
      ApplicationEventPublisher events,
      ObjectMapper objectMapper,
      Clock clock) {
    this.expedienteApi = expedienteApi;
    this.documentsApi = documentsApi;
    this.privacyApi = privacyApi;
    this.extractionApi = extractionApi;
    this.docxRenderer = docxRenderer;
    this.pdfRenderer = pdfRenderer;
    this.fileStorage = fileStorage;
    this.repository = repository;
    this.signatureService = signatureService;
    this.events = events;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /**
   * blockers: requisitos del proceso (documentos, recepción, privacidad,
   * diferencias entre documentos). missingData: datos que el contrato
   * necesita y no se han capturado. Solo con ambas listas vacías se puede
   * generar un contrato para firma.
   */
  public record Readiness(boolean ready, List<String> blockers, List<String> missingData, String variant) {}

  @Transactional(readOnly = true)
  public ContractCalculator.Result calculationsOf(UUID expedienteId) {
    ManualClientDataView data = expedienteApi.getManualData(expedienteId);
    LocalDate signatureDate = data.contractSignatureDate() != null ? data.contractSignatureDate() : today();
    return ContractCalculator.calculate(data.authorizedPrice(), signatureDate);
  }

  @Transactional(readOnly = true)
  public Readiness readinessOf(UUID expedienteId) {
    ExpedienteSummary summary = expedienteApi.getSummary(expedienteId);
    ContractDocument document = ContractTemplate.build(inputFor(summary, 0), false);
    List<String> blockers = processBlockers(summary);
    return new Readiness(blockers.isEmpty() && document.missingItems().isEmpty(), blockers, document.missingItems(), variantOf(summary));
  }

  public record Generated(ContractGeneration contract, List<ContractSignatureService.IssuedSigningLink> signingLinks) {}

  public Generated generate(UUID expedienteId, boolean draft, Actor actor) {
    ExpedienteSummary summary = expedienteApi.getSummary(expedienteId);
    if (!isCorrectable(summary.status())) {
      throw new ConflictException(
          "EXPEDIENTE_LOCKED", "El contrato de este expediente ya está firmado o el inmueble ya se decidió; no se puede generar otra versión.");
    }

    int nextVersion = repository.findFirstByExpedienteIdOrderByVersionNumberDesc(expedienteId).map(c -> c.getVersionNumber() + 1).orElse(1);
    ContractInput input = inputFor(summary, nextVersion);
    List<String> blockers = processBlockers(summary);
    ContractDocument preview = ContractTemplate.build(input, draft);
    boolean complete = blockers.isEmpty() && preview.missingItems().isEmpty();

    if (!draft && !complete) {
      List<String> all = new ArrayList<>(blockers);
      all.addAll(preview.missingItems());
      throw new UnprocessableException(
          "CONTRACT_NOT_READY",
          "No se puede generar el contrato para firma. Falta: " + String.join("; ", all) + ". Puedes generar un borrador INCOMPLETO para revisarlo.");
    }

    boolean isDraft = draft || !complete;
    List<String> missingForRecord = new ArrayList<>(blockers);
    missingForRecord.addAll(preview.missingItems());
    ContractDocument document =
        new ContractDocument(preview.title(), isDraft, isDraft ? missingForRecord : List.of(), preview.blocks());

    byte[] docx = docxRenderer.render(document);
    byte[] pdf = pdfRenderer.render(document);
    String prefix = "expedientes/%s/contracts/v%d/".formatted(expedienteId, nextVersion);
    String docxKey = fileStorage.store(prefix + "contrato.docx", new ByteArrayInputStream(docx), docx.length, DOCX).storageKey();
    String pdfKey = fileStorage.store(prefix + "contrato.pdf", new ByteArrayInputStream(pdf), pdf.length, "application/pdf").storageKey();

    // Una sola versión vigente: las anteriores sin firmar quedan sin efecto.
    supersedeOpenContracts(expedienteId, "Se generó la versión " + nextVersion + " del contrato", actor);

    String snapshotJson = writeSnapshot(input);
    ContractGeneration generation =
        repository.save(
            new ContractGeneration(
                expedienteId,
                nextVersion,
                snapshotJson,
                docxKey,
                pdfKey,
                clock.instant(),
                actor.userId(),
                sha256(snapshotJson.getBytes(StandardCharsets.UTF_8)),
                sha256(pdf),
                isDraft,
                document.missingItems(),
                variantOf(summary)));

    List<ContractSignatureService.IssuedSigningLink> links = List.of();
    if (isDraft) {
      expedienteApi.recordContractPreparation(expedienteId);
    } else {
      links = signatureService.requestSignatures(generation, summary);
      expedienteApi.recordReadyForSignature(expedienteId);
    }
    events.publishEvent(new ContractGenerated(expedienteId, generation.getId(), nextVersion, isDraft, actor));
    return new Generated(generation, links);
  }

  @Transactional(readOnly = true)
  public List<ContractGeneration> listOf(UUID expedienteId) {
    return repository.findByExpedienteIdOrderByVersionNumberDesc(expedienteId);
  }

  @Transactional(readOnly = true)
  public ContractGeneration get(UUID contractId) {
    return repository.findById(contractId).orElseThrow(() -> new NotFoundException("Contrato", contractId));
  }

  public ContractGeneration markDelivered(UUID contractId, String method, Actor actor) {
    ContractGeneration contract = get(contractId);
    contract.markDelivered(clock.instant(), method);
    events.publishEvent(
        new com.c21genera.shared.events.ContractEvents.ContractDelivered(
            contract.getExpedienteId(), contractId, contract.getVersionNumber(), method, actor));
    return contract;
  }

  /**
   * Cualquier corrección de datos del expediente deja sin efecto los
   * contratos que todavía no estén firmados por todas las partes (y anula sus
   * firmas pendientes): hay que generar una versión nueva con los datos
   * corregidos.
   */
  @ApplicationModuleListener
  void on(ExpedienteDataCorrected event) {
    if (!event.contractRelevant()) {
      return;
    }
    int superseded =
        supersedeOpenContracts(
            event.expedienteId(),
            "Se corrigieron datos del expediente (" + event.section() + ": " + String.join(", ", event.changedFields()) + ")",
            event.actor());
    if (superseded > 0) {
      expedienteApi.recordContractInvalidated(event.expedienteId());
    }
  }

  private int supersedeOpenContracts(UUID expedienteId, String reason, Actor actor) {
    int count = 0;
    for (ContractGeneration contract : repository.findByExpedienteIdOrderByVersionNumberDesc(expedienteId)) {
      if (contract.isSupersedable()) {
        contract.supersede(clock.instant(), reason);
        signatureService.voidPendingSignatures(contract, reason);
        events.publishEvent(new ContractSuperseded(expedienteId, contract.getId(), contract.getVersionNumber(), reason, actor));
        count++;
      }
    }
    return count;
  }

  // ---------------------------------------------------------------------

  private ContractInput inputFor(ExpedienteSummary summary, int versionNumber) {
    ManualClientDataView data = expedienteApi.getManualData(summary.id());
    ContractCalculator.Result calculations =
        data.authorizedPrice() == null || data.authorizedPrice().signum() <= 0
            ? null
            : ContractCalculator.calculate(data.authorizedPrice(), data.contractSignatureDate() != null ? data.contractSignatureDate() : today());
    return new ContractInput(summary, data, calculations, deliveredDocuments(summary), versionNumber, today());
  }

  /** Documentos aceptados, con el nombre del participante cuando aplica (cláusula tercera). */
  private List<String> deliveredDocuments(ExpedienteSummary summary) {
    Map<UUID, String> names = summary.participants().stream().collect(Collectors.toMap(ParticipantView::id, ParticipantView::fullName));
    return documentsApi.acceptedDocumentsOf(summary.id()).stream()
        .map(
            d -> {
              String label = labelOf(d.type());
              String who = d.participantId() != null ? names.get(d.participantId()) : null;
              return who != null ? label + " de " + who : label;
            })
        .distinct()
        .toList();
  }

  private List<String> processBlockers(ExpedienteSummary summary) {
    List<String> blockers = new ArrayList<>();
    Map<UUID, String> names = summary.participants().stream().collect(Collectors.toMap(ParticipantView::id, ParticipantView::fullName));

    for (RequirementStatusView doc : documentsApi.requirementStatusOf(summary.id())) {
      if (!doc.required() || "ACCEPTED".equals(doc.status()) || "NOT_APPLICABLE".equals(doc.status())) {
        continue;
      }
      String who = doc.participantId() != null && names.containsKey(doc.participantId()) ? " de " + names.get(doc.participantId()) : "";
      blockers.add("Documento obligatorio sin aceptar: " + DocumentTypeLabels.of(doc.type()) + who + " (" + statusLabel(doc.status()) + ")");
    }

    if (summary.status().ordinal() < ExpedienteStatus.RECEPTION_SIGNED.ordinal()
        || summary.status() == ExpedienteStatus.CORRECTIONS_REQUESTED) {
      blockers.add("Firmar la recepción de documentos (requiere todos los documentos obligatorios aceptados)");
    }

    boolean consent = privacyApi.consentOf(summary.id()).map(c -> c.mainPurposesAccepted()).orElse(false);
    if (!consent) {
      blockers.add("El cliente no ha aceptado y firmado el aviso de privacidad");
    }

    for (String conflict : extractionApi.unresolvedConflictDescriptions(summary.id())) {
      blockers.add("Diferencia entre documentos sin resolver: " + conflict);
    }

    if (ContractTemplate.clientSignersOf(summary).isEmpty()) {
      blockers.add(
          summary.personType() == PersonType.MORAL
              ? "Registrar al representante legal que firmará por la persona moral"
              : "Registrar al apoderado o a los propietarios que firmarán");
    }
    return blockers;
  }

  private static boolean isCorrectable(ExpedienteStatus status) {
    return switch (status) {
      case CONTRACT_SIGNED, PROPERTY_ACCEPTED, PROPERTY_REJECTED, CLOSED -> false;
      default -> true;
    };
  }

  static String variantOf(ExpedienteSummary s) {
    List<String> parts = new ArrayList<>();
    parts.add(s.personType() == PersonType.MORAL ? "Persona moral" : "Persona física");
    long owners = s.participants().stream().filter(ParticipantView::isOwner).count();
    if (s.personType() == PersonType.FISICA && owners > 1) {
      parts.add(owners + " copropietarios");
    }
    parts.add(
        switch (s.signerCharacter()) {
          case APODERADO -> "firma apoderado";
          case REPRESENTANTE_LEGAL -> "firma representante legal";
          default -> "firma el titular";
        });
    parts.add(s.accreditationType() == AccreditationType.ESCRITURA_PUBLICA ? "escritura pública" : "contrato privado");
    parts.add(
        switch (s.propertyCaseType()) {
          case HOUSING -> "casa";
          case DEPARTMENT -> "departamento";
          case RESIDENTIAL_LAND -> "terreno";
          case COMMERCIAL -> "comercial";
        });
    if (s.condominiumRegime()) {
      parts.add("condominio");
    }
    return String.join(" · ", parts);
  }

  private static String statusLabel(String status) {
    return switch (status) {
      case "PENDING" -> "sin cargar";
      case "UPLOADED", "READY_FOR_REVIEW" -> "pendiente de revisión";
      case "RETURNED" -> "devuelto al cliente";
      case "REJECTED" -> "rechazado";
      default -> status.toLowerCase();
    };
  }

  private static String labelOf(String type) {
    try {
      return DocumentTypeLabels.of(com.c21genera.shared.domain.DocumentTypeCode.valueOf(type));
    } catch (IllegalArgumentException e) {
      return type;
    }
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneId.of("America/Mexico_City")));
  }

  private String writeSnapshot(ContractInput input) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("expediente", input.expediente());
    snapshot.put("clientData", input.clientData());
    snapshot.put("calculations", input.calculations());
    snapshot.put("deliveredDocuments", input.deliveredDocuments());
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
}
