package com.c21genera.notary.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.RequirementStatusView;
import com.c21genera.documents.DocumentsApi.VersionView;
import com.c21genera.notary.domain.ClosingCatalog;
import com.c21genera.notary.domain.ClosingDocumentCheck;
import com.c21genera.notary.domain.NotaryStage;
import com.c21genera.notary.domain.NotarySubmission;
import com.c21genera.notary.infrastructure.ClosingDocumentCheckRepository;
import com.c21genera.notary.infrastructure.NotaryStageRepository;
import com.c21genera.notary.infrastructure.NotarySubmissionRepository;
import com.c21genera.notifications.NotificationsApi;
import com.c21genera.notifications.NotificationsApi.FileAttachment;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.NotaryEvents.ClosingDocumentChecked;
import com.c21genera.shared.events.NotaryEvents.NotaryPackageSent;
import com.c21genera.shared.events.NotaryEvents.OfferAcceptanceChanged;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #23: documentos por etapa (comercialización, envío inicial a notaría y cierre).
 * El asesor arma cada paquete eligiendo con casillas qué documentos y qué versión enviar; cada envío
 * queda en el historial. La etapa de cierre se activa al registrar la oferta aceptada y alerta si un
 * documento de cierre no está confirmado como vigente y al corriente.
 */
@Service
@Transactional
public class NotaryService {

  private final NotaryStageRepository stageRepository;
  private final ClosingDocumentCheckRepository checkRepository;
  private final NotarySubmissionRepository submissionRepository;
  private final DocumentsApi documentsApi;
  private final NotificationsApi notificationsApi;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public NotaryService(
      NotaryStageRepository stageRepository,
      ClosingDocumentCheckRepository checkRepository,
      NotarySubmissionRepository submissionRepository,
      DocumentsApi documentsApi,
      NotificationsApi notificationsApi,
      ApplicationEventPublisher events,
      Clock clock) {
    this.stageRepository = stageRepository;
    this.checkRepository = checkRepository;
    this.submissionRepository = submissionRepository;
    this.documentsApi = documentsApi;
    this.notificationsApi = notificationsApi;
    this.events = events;
    this.clock = clock;
  }

  // ---------------------------------------------------------------------
  // Consulta
  // ---------------------------------------------------------------------

  public record DocumentStageView(
      RequirementStatusView document,
      List<VersionView> versions,
      boolean closingRequired,
      Optional<ClosingDocumentCheck> closingCheck) {

    /** La versión vigente está confirmada como vigente y al corriente. */
    public boolean closingValid() {
      return closingCheck.map(c -> c.validFor(document.currentVersionId())).orElse(false);
    }
  }

  public record Overview(NotaryStage stage, List<DocumentStageView> documents, List<NotarySubmission> submissions) {}

  @Transactional(readOnly = true)
  public Overview overview(UUID expedienteId) {
    NotaryStage stage = stageRepository.findById(expedienteId).orElseGet(() -> new NotaryStage(expedienteId));
    Map<UUID, ClosingDocumentCheck> checks =
        checkRepository.findByExpedienteId(expedienteId).stream().collect(Collectors.toMap(ClosingDocumentCheck::getDocumentId, Function.identity()));
    List<DocumentStageView> documents =
        documentsApi.requirementStatusOf(expedienteId).stream()
            .filter(d -> d.required() || d.currentVersionId() != null)
            .map(
                d ->
                    new DocumentStageView(
                        d,
                        documentsApi.versionViewsOf(d.documentId()),
                        ClosingCatalog.requiredForClosing(d.type()),
                        Optional.ofNullable(checks.get(d.documentId()))))
            .toList();
    return new Overview(stage, documents, submissionRepository.findByExpedienteIdOrderBySentAtDesc(expedienteId));
  }

  // ---------------------------------------------------------------------
  // Oferta aceptada (activa la etapa de cierre)
  // ---------------------------------------------------------------------

  public NotaryStage registerOfferAccepted(UUID expedienteId, String note, Actor actor) {
    NotaryStage stage = stageRepository.findById(expedienteId).orElseGet(() -> new NotaryStage(expedienteId));
    String cleaned = blankToNull(note);
    stage.registerOfferAccepted(clock.instant(), actor.name(), cleaned);
    stageRepository.save(stage);
    events.publishEvent(new OfferAcceptanceChanged(expedienteId, true, cleaned, actor));
    return stage;
  }

  public NotaryStage clearOfferAccepted(UUID expedienteId, Actor actor) {
    NotaryStage stage = stageRepository.findById(expedienteId).orElseGet(() -> new NotaryStage(expedienteId));
    if (stage.isClosingActive()) {
      stage.clearOffer();
      stageRepository.save(stage);
      events.publishEvent(new OfferAcceptanceChanged(expedienteId, false, null, actor));
    }
    return stage;
  }

  // ---------------------------------------------------------------------
  // Documentos de cierre: vigente y al corriente
  // ---------------------------------------------------------------------

  public ClosingDocumentCheck checkClosingDocument(UUID documentId, boolean valid, String note, Actor actor) {
    UUID expedienteId = documentsApi.expedienteIdOfDocument(documentId).orElseThrow(() -> new NotFoundException("Documento", documentId));
    RequirementStatusView document = requirement(expedienteId, documentId);
    if (document.currentVersionId() == null) {
      throw new UnprocessableException("DOCUMENT_WITHOUT_FILE", "Este documento todavía no tiene un archivo que confirmar.");
    }
    ClosingDocumentCheck check = checkRepository.findById(documentId).orElseGet(() -> new ClosingDocumentCheck(documentId, expedienteId));
    String cleaned = blankToNull(note);
    check.record(document.currentVersionId(), valid, cleaned, actor.name(), clock.instant());
    checkRepository.save(check);
    events.publishEvent(new ClosingDocumentChecked(expedienteId, documentId, document.displayName(), valid, cleaned, actor));
    return check;
  }

  // ---------------------------------------------------------------------
  // Envío a notaría
  // ---------------------------------------------------------------------

  public record SelectedDocument(UUID documentId, UUID versionId) {}

  public record SendCommand(
      NotarySubmission.Kind kind, List<SelectedDocument> documents, String recipientEmail, String note, boolean confirmWarnings) {}

  private static final String SUBJECT_INITIAL = "CENTURY 21 Genera - Documentos para iniciar el expediente";
  private static final String SUBJECT_CLOSING = "CENTURY 21 Genera - Documentos actualizados para el cierre";

  public NotarySubmission send(UUID expedienteId, SendCommand command, Actor actor) {
    if (command.kind() == null) {
      throw new UnprocessableException("SUBMISSION_KIND_REQUIRED", "Elige el tipo de envío: inicial o de cierre.");
    }
    if (command.documents() == null || command.documents().isEmpty()) {
      throw new UnprocessableException("NO_DOCUMENTS_SELECTED", "Marca al menos un documento para enviar a la notaría.");
    }
    NotaryStage stage = stageRepository.findById(expedienteId).orElseGet(() -> new NotaryStage(expedienteId));
    if (command.kind() == NotarySubmission.Kind.CLOSING && !stage.isClosingActive()) {
      throw new UnprocessableException(
          "CLOSING_NOT_ACTIVE", "El envío de cierre se habilita al registrar la oferta aceptada en el expediente.");
    }
    Map<UUID, RequirementStatusView> documents =
        documentsApi.requirementStatusOf(expedienteId).stream().collect(Collectors.toMap(RequirementStatusView::documentId, Function.identity()));
    Map<UUID, ClosingDocumentCheck> checks =
        checkRepository.findByExpedienteId(expedienteId).stream().collect(Collectors.toMap(ClosingDocumentCheck::getDocumentId, Function.identity()));

    record Chosen(RequirementStatusView document, VersionView version) {}
    List<Chosen> chosen = new ArrayList<>();
    LinkedHashSet<UUID> seen = new LinkedHashSet<>();
    for (SelectedDocument selected : command.documents()) {
      if (selected == null || selected.documentId() == null || !seen.add(selected.documentId())) {
        continue;
      }
      RequirementStatusView document = documents.get(selected.documentId());
      if (document == null) {
        throw new UnprocessableException("DOCUMENT_NOT_IN_EXPEDIENTE", "Uno de los documentos elegidos no pertenece a este expediente.");
      }
      List<VersionView> versions = documentsApi.versionViewsOf(document.documentId());
      if (versions.isEmpty()) {
        throw new UnprocessableException("DOCUMENT_WITHOUT_FILE", document.displayName() + " todavía no tiene archivo: quítalo del envío.");
      }
      VersionView version =
          selected.versionId() == null
              ? versions.getFirst()
              : versions.stream()
                  .filter(v -> v.id().equals(selected.versionId()))
                  .findFirst()
                  .orElseThrow(() -> new UnprocessableException("VERSION_NOT_FOUND", "La versión elegida de " + document.displayName() + " no existe."));
      chosen.add(new Chosen(document, version));
    }

    // Cierre: los documentos de cierre deben ir vigentes y al corriente (confirmados sobre la versión enviada).
    List<String> warnings = new ArrayList<>();
    if (command.kind() == NotarySubmission.Kind.CLOSING) {
      for (Chosen c : chosen) {
        if (!ClosingCatalog.requiredForClosing(c.document().type())) {
          continue;
        }
        ClosingDocumentCheck check = checks.get(c.document().documentId());
        if (check == null || !check.validFor(c.version().id())) {
          warnings.add(c.document().displayName() + " V" + c.version().versionNumber() + " no está confirmado como vigente y al corriente de pago");
        }
      }
    }
    if (!warnings.isEmpty() && !command.confirmWarnings()) {
      throw new UnprocessableException(
          "CLOSING_DOCUMENTS_NOT_VALID", "Revisa antes de enviar: " + String.join("; ", warnings) + ". Confirma para enviar de todos modos.");
    }

    String recipient = blankToNull(command.recipientEmail());
    Instant now = clock.instant();
    NotarySubmission submission =
        new NotarySubmission(
            expedienteId,
            command.kind(),
            recipient,
            blankToNull(command.note()),
            warnings.isEmpty() ? null : String.join("; ", warnings),
            actor.userId(),
            actor.name(),
            now);
    List<FileAttachment> attachments = new ArrayList<>();
    List<String> withoutPdf = new ArrayList<>();
    for (Chosen c : chosen) {
      submission.addItem(c.document().documentId(), c.version().id(), c.version().versionNumber(), truncate(c.document().displayName(), 200));
      if (c.version().pdfStorageKey() != null) {
        attachments.add(new FileAttachment(c.version().pdfStorageKey(), fileName(c.document().displayName(), c.version().versionNumber())));
      } else {
        withoutPdf.add(c.document().displayName());
      }
    }
    submissionRepository.save(submission);

    if (recipient != null) {
      boolean closing = command.kind() == NotarySubmission.Kind.CLOSING;
      StringBuilder body =
          new StringBuilder(
              closing
                  ? "Hola,\n\nAdjuntamos los documentos actualizados para el cierre de la operación.\n\n"
                  : "Hola,\n\nAdjuntamos los documentos para iniciar el expediente en la notaría.\n\n");
      if (submission.getNote() != null) {
        body.append(submission.getNote()).append("\n\n");
      }
      if (!withoutPdf.isEmpty()) {
        body.append("Sin archivo adjunto (se enviarán por separado): ").append(String.join(", ", withoutPdf)).append(".\n\n");
      }
      body.append("CENTURY 21 Genera");
      notificationsApi.notifyWithFiles(
          expedienteId,
          closing ? "Envío de cierre a notaría" : "Envío inicial a notaría",
          recipient,
          closing ? SUBJECT_CLOSING : SUBJECT_INITIAL,
          body.toString(),
          attachments);
    }

    String summary = chosen.stream().map(c -> c.document().displayName() + " V" + c.version().versionNumber()).collect(Collectors.joining(", "));
    events.publishEvent(new NotaryPackageSent(expedienteId, submission.getId(), command.kind().name(), summary, recipient, actor));
    return submission;
  }

  private RequirementStatusView requirement(UUID expedienteId, UUID documentId) {
    return documentsApi.requirementStatusOf(expedienteId).stream()
        .filter(d -> d.documentId().equals(documentId))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Documento", documentId));
  }

  private static String fileName(String name, int version) {
    String base =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^A-Za-z0-9]+", "-")
            .replaceAll("(^-|-$)", "")
            .toLowerCase(Locale.ROOT);
    return base + "-v" + version + ".pdf";
  }

  private static String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
