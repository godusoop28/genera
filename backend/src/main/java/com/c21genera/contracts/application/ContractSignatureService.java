package com.c21genera.contracts.application;

import com.c21genera.contracts.domain.ContractGeneration;
import com.c21genera.contracts.domain.ContractReferenceData;
import com.c21genera.contracts.domain.ContractSignature;
import com.c21genera.contracts.domain.ContractSignature.Party;
import com.c21genera.contracts.domain.ContractTemplate;
import com.c21genera.contracts.domain.SignatureCertificate;
import com.c21genera.contracts.infrastructure.ContractGenerationRepository;
import com.c21genera.contracts.infrastructure.ContractSignatureRepository;
import com.c21genera.contracts.infrastructure.PdfContractRenderer;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.expedientes.ExpedienteSummary.ParticipantView;
import com.c21genera.notifications.NotificationsApi;
import com.c21genera.shared.config.ContractsProperties;
import com.c21genera.shared.config.PublicLinkProperties;
import com.c21genera.shared.config.StorageProperties;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.ContractEvents.ContractFullySigned;
import com.c21genera.shared.events.ContractEvents.ContractSignatureRecorded;
import com.c21genera.shared.security.OpaqueTokens;
import com.c21genera.shared.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Firma del contrato con trazabilidad (retroalimentación 25/09, hallazgos 3
 * y 17). Cambiar un estado a mano ya no equivale a una firma: el contrato
 * queda firmado solo cuando cada parte firmó, y cada firma guarda firmante,
 * versión, huella SHA-256 del PDF firmado, fecha y hora, y evidencia.
 *
 * <ul>
 *   <li>Cliente: liga personal por firmante (token de un solo propósito, con
 *       vencimiento; solo se guarda su hash). Debe escribir su nombre tal
 *       como aparece en el contrato, trazar su firma y aceptar; se registran
 *       IP y navegador, y se verifica que firma exactamente la versión
 *       vigente (misma huella).</li>
 *   <li>Intermediaria: un usuario con CONTRACT_SIGN firma desde el sistema.</li>
 *   <li>Autógrafa: el staff carga el contrato firmado a mano y escaneado; queda
 *       como evidencia con su huella y el usuario que lo registró.</li>
 * </ul>
 * Al completarse todas las firmas se genera la constancia de firmas, el
 * paquete final (contrato + constancia), el expediente avanza a
 * CONTRACT_SIGNED y se disparan el seguimiento posterior y los avisos.
 */
@Service
@Transactional
public class ContractSignatureService {

  private static final long MAX_SIGNATURE_IMAGE_BYTES = 400_000;
  private static final long MAX_EVIDENCE_BYTES = 20_000_000;

  private final ContractGenerationRepository contractRepository;
  private final ContractSignatureRepository signatureRepository;
  private final ExpedienteLifecycleApi expedienteApi;
  private final NotificationsApi notifications;
  private final FileStorage fileStorage;
  private final StorageProperties storageProperties;
  private final ContractsProperties contractsProperties;
  private final PublicLinkProperties publicLinkProperties;
  private final PdfContractRenderer pdfRenderer;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public ContractSignatureService(
      ContractGenerationRepository contractRepository,
      ContractSignatureRepository signatureRepository,
      ExpedienteLifecycleApi expedienteApi,
      NotificationsApi notifications,
      FileStorage fileStorage,
      StorageProperties storageProperties,
      ContractsProperties contractsProperties,
      PublicLinkProperties publicLinkProperties,
      PdfContractRenderer pdfRenderer,
      ApplicationEventPublisher events,
      Clock clock) {
    this.contractRepository = contractRepository;
    this.signatureRepository = signatureRepository;
    this.expedienteApi = expedienteApi;
    this.notifications = notifications;
    this.fileStorage = fileStorage;
    this.storageProperties = storageProperties;
    this.contractsProperties = contractsProperties;
    this.publicLinkProperties = publicLinkProperties;
    this.pdfRenderer = pdfRenderer;
    this.events = events;
    this.clock = clock;
  }

  /** emailed: si se envió la liga por correo al firmante (si no, el staff debe compartirla). */
  public record IssuedSigningLink(UUID signatureId, String signerName, String signerCapacity, String url, Instant expiresAt, boolean emailed) {}

  /** Crea una firma pendiente por cada firmante del cliente y una para la intermediaria. */
  List<IssuedSigningLink> requestSignatures(ContractGeneration contract, ExpedienteSummary summary) {
    Instant now = clock.instant();
    Instant expiresAt = now.plus(contractsProperties.signingLinkTtl());
    String clientEmail = expedienteApi.getManualData(summary.id()).email();
    List<ParticipantView> signers = ContractTemplate.clientSignersOf(summary);
    List<IssuedSigningLink> links = new ArrayList<>();
    for (ParticipantView signer : signers) {
      String raw = OpaqueTokens.generate();
      String email = notBlank(signer.email()) ? signer.email() : signers.size() == 1 ? clientEmail : null;
      ContractSignature signature =
          signatureRepository.save(
              new ContractSignature(
                  contract.getId(),
                  summary.id(),
                  Party.CLIENT,
                  signer.id(),
                  signer.fullName(),
                  ContractTemplate.signerCapacityOf(summary, signer),
                  email,
                  OpaqueTokens.sha256Hex(raw),
                  expiresAt,
                  now));
      links.add(sendLink(signature, raw, summary, contract));
    }
    signatureRepository.save(
        new ContractSignature(
            contract.getId(),
            summary.id(),
            Party.INTERMEDIARY,
            null,
            ContractReferenceData.LEGAL_REPRESENTATIVE,
            "Representante legal de " + ContractReferenceData.INTERMEDIARY_LEGAL_NAME,
            null,
            null,
            null,
            now));
    return links;
  }

  /** Genera una liga nueva para un firmante pendiente (la anterior deja de funcionar). */
  public IssuedSigningLink reissueLink(UUID signatureId) {
    ContractSignature signature = getSignature(signatureId);
    if (signature.getParty() != Party.CLIENT) {
      throw new UnprocessableException("NOT_A_CLIENT_SIGNATURE", "La intermediaria firma desde el sistema, no con liga.");
    }
    ContractGeneration contract = getContract(signature.getContractGenerationId());
    contract.ensureSignable();
    String raw = OpaqueTokens.generate();
    signature.reissueToken(OpaqueTokens.sha256Hex(raw), clock.instant().plus(contractsProperties.signingLinkTtl()));
    return sendLink(signature, raw, expedienteApi.getSummary(signature.getExpedienteId()), contract);
  }

  private IssuedSigningLink sendLink(ContractSignature signature, String rawToken, ExpedienteSummary summary, ContractGeneration contract) {
    String url = contractsProperties.signingBaseUrlOr(publicLinkProperties.baseUrl()) + "/" + rawToken;
    boolean emailed = notBlank(signature.getSignerEmail());
    notifications.notify(
        summary.id(),
        "Invitación a firmar el contrato",
        signature.getSignerEmail(),
        "Firma tu contrato de intermediación - expediente " + summary.folio(),
        "Hola " + signature.getSignerName() + ",\n\n"
            + "Tu contrato de intermediación con CENTURY 21 Genera (expediente " + summary.folio() + ", versión " + contract.getVersionNumber()
            + ") está listo para firmarse. Léelo completo y fírmalo en esta liga personal:\n\n"
            + url
            + "\n\nLa liga es solo para ti, vence el " + formatDate(signature.getTokenExpiresAt()) + " y deja de funcionar al usarse."
            + "\n\nCENTURY 21 Genera");
    return new IssuedSigningLink(signature.getId(), signature.getSignerName(), signature.getSignerCapacity(), url, signature.getTokenExpiresAt(), emailed);
  }

  void voidPendingSignatures(ContractGeneration contract, String reason) {
    for (ContractSignature signature : signatureRepository.findByContractGenerationIdOrderByRequestedAtAsc(contract.getId())) {
      signature.voidBecause(clock.instant(), reason);
    }
  }

  @Transactional(readOnly = true)
  public ContractSignature signature(UUID signatureId) {
    return getSignature(signatureId);
  }

  @Transactional(readOnly = true)
  public List<ContractSignature> signaturesOf(UUID contractId) {
    return signatureRepository.findByContractGenerationIdOrderByRequestedAtAsc(contractId);
  }

  // ---------------------------------------------------------------------
  // Firma del cliente por liga personal
  // ---------------------------------------------------------------------

  public record SigningView(
      String folio,
      int versionNumber,
      Instant generatedAt,
      String signerName,
      String signerCapacity,
      String documentSha256,
      URI pdfUrl,
      String consentText,
      boolean alreadySigned) {}

  @Transactional(readOnly = true)
  public SigningView publicView(String rawToken) {
    ContractSignature signature = resolveToken(rawToken);
    ContractGeneration contract = getContract(signature.getContractGenerationId());
    contract.ensureSignable();
    ExpedienteSummary summary = expedienteApi.getSummary(signature.getExpedienteId());
    return new SigningView(
        summary.folio(),
        contract.getVersionNumber(),
        contract.getGeneratedAt(),
        signature.getSignerName(),
        signature.getSignerCapacity(),
        contract.getDocumentSha256(),
        fileStorage.generateTemporaryDownloadUrl(contract.getPdfStorageKey(), storageProperties.presignedUrlTtl()),
        consentText(contract, signature),
        false);
  }

  public record SignCommand(String typedName, String signatureImageBase64, String documentSha256, boolean accepted, String ipAddress, String userAgent) {}

  public ContractSignature signByClient(String rawToken, SignCommand command) {
    ContractSignature signature = resolveToken(rawToken);
    ContractGeneration contract = getContract(signature.getContractGenerationId());
    signElectronically(contract, signature, command, null, Actor.client());
    return signature;
  }

  /** La intermediaria firma desde el sistema (usuario con CONTRACT_SIGN). */
  public ContractSignature signAsIntermediary(UUID contractId, SignCommand command, Actor actor) {
    ContractGeneration contract = getContract(contractId);
    ContractSignature signature =
        signatureRepository.findByContractGenerationIdOrderByRequestedAtAsc(contractId).stream()
            .filter(s -> s.getParty() == Party.INTERMEDIARY && s.getStatus() == ContractSignature.Status.PENDING)
            .findFirst()
            .orElseThrow(() -> new ConflictException("NO_PENDING_SIGNATURE", "La intermediaria ya firmó esta versión del contrato."));
    // Firma quien representa a la intermediaria en el sistema; queda registrado su usuario.
    signElectronically(contract, signature, command, actor.userId(), actor);
    return signature;
  }

  private void signElectronically(ContractGeneration contract, ContractSignature signature, SignCommand command, UUID registeredBy, Actor actor) {
    contract.ensureSignable();
    ensureExpedienteAwaitingSignature(contract);
    if (!command.accepted()) {
      throw new UnprocessableException("CONSENT_REQUIRED", "Debes confirmar que leíste el contrato y que aceptas firmarlo electrónicamente.");
    }
    if (command.documentSha256() == null || !command.documentSha256().equalsIgnoreCase(contract.getDocumentSha256())) {
      throw new ConflictException(
          "DOCUMENT_CHANGED", "El contrato que tienes abierto ya no es la versión vigente. Vuelve a cargar la página para ver la versión actual.");
    }
    if (!sameName(command.typedName(), signature.getSignerName())) {
      throw new UnprocessableException(
          "NAME_MISMATCH", "Escribe tu nombre completo tal como aparece en el contrato: " + signature.getSignerName() + ".");
    }
    byte[] image = decodePng(command.signatureImageBase64());
    String imageKey = "expedientes/%s/contracts/v%d/firmas/%s.png".formatted(contract.getExpedienteId(), contract.getVersionNumber(), signature.getId());
    FileStorage.StoredObjectMetadata stored = fileStorage.store(imageKey, new ByteArrayInputStream(image), image.length, "image/png");

    signature.signElectronically(
        clock.instant(),
        contract.getDocumentSha256(),
        command.typedName().strip(),
        truncate(command.ipAddress(), 64),
        truncate(command.userAgent(), 400),
        stored.storageKey(),
        stored.sha256(),
        consentText(contract, signature),
        registeredBy);
    events.publishEvent(
        new ContractSignatureRecorded(
            contract.getExpedienteId(), contract.getId(), contract.getVersionNumber(), signature.getSignerName(), signature.getSignerCapacity(),
            ContractSignature.Method.ELECTRONIC_SIMPLE.name(), actor));
    completeIfAllSigned(contract);
  }

  /**
   * Firma autógrafa: el contrato impreso se firmó a mano y se carga escaneado.
   * Registra como firmadas todas las firmas pendientes de esa versión, con el
   * archivo como evidencia (y su huella) y el usuario que lo cargó.
   */
  public ContractGeneration registerAutographSignatures(UUID contractId, byte[] scannedFile, String contentType, Actor actor) {
    ContractGeneration contract = getContract(contractId);
    contract.ensureSignable();
    ensureExpedienteAwaitingSignature(contract);
    if (scannedFile == null || scannedFile.length == 0) {
      throw new UnprocessableException("NO_FILE", "Carga el contrato firmado y escaneado.");
    }
    if (scannedFile.length > MAX_EVIDENCE_BYTES) {
      throw new UnprocessableException("FILE_TOO_LARGE", "El archivo escaneado no debe pasar de 20 MB.");
    }
    String extension = "application/pdf".equals(contentType) ? "pdf" : "image/png".equals(contentType) ? "png" : "jpg";
    String key =
        "expedientes/%s/contracts/v%d/firma-autografa-%s.%s".formatted(contract.getExpedienteId(), contract.getVersionNumber(), UUID.randomUUID(), extension);
    FileStorage.StoredObjectMetadata stored = fileStorage.store(key, new ByteArrayInputStream(scannedFile), scannedFile.length, contentType);

    for (ContractSignature signature : signatureRepository.findByContractGenerationIdOrderByRequestedAtAsc(contractId)) {
      if (signature.getStatus() == ContractSignature.Status.PENDING) {
        signature.signAutograph(clock.instant(), contract.getDocumentSha256(), stored.storageKey(), stored.sha256(), actor.userId());
        events.publishEvent(
            new ContractSignatureRecorded(
                contract.getExpedienteId(), contractId, contract.getVersionNumber(), signature.getSignerName(), signature.getSignerCapacity(),
                ContractSignature.Method.AUTOGRAPH_SCAN.name(), actor));
      }
    }
    completeIfAllSigned(contract);
    return contract;
  }

  private void completeIfAllSigned(ContractGeneration contract) {
    List<ContractSignature> signatures = signatureRepository.findByContractGenerationIdOrderByRequestedAtAsc(contract.getId());
    List<ContractSignature> active = signatures.stream().filter(s -> s.getStatus() != ContractSignature.Status.VOIDED).toList();
    boolean allSigned = !active.isEmpty() && active.stream().allMatch(s -> s.getStatus() == ContractSignature.Status.SIGNED);
    if (!allSigned) {
      contract.markPartiallySigned();
      return;
    }
    ExpedienteSummary summary = expedienteApi.getSummary(contract.getExpedienteId());
    String packageKey = buildSignedPackage(contract, summary, active);
    contract.markFullySigned(clock.instant(), packageKey);
    expedienteApi.recordContractSigned(contract.getExpedienteId());
    events.publishEvent(new ContractFullySigned(contract.getExpedienteId(), contract.getId(), contract.getVersionNumber(), contract.getDocumentSha256()));
  }

  /** Contrato original + constancia de firmas (con los trazos y la evidencia de cada una). */
  private String buildSignedPackage(ContractGeneration contract, ExpedienteSummary summary, List<ContractSignature> signatures) {
    Map<UUID, byte[]> images = new HashMap<>();
    for (ContractSignature s : signatures) {
      if (s.getSignatureImageKey() != null) {
        images.put(s.getId(), readAll(s.getSignatureImageKey()));
      }
    }
    byte[] certificate = pdfRenderer.render(SignatureCertificate.build(contract, summary, signatures, images));
    byte[] original = readAll(contract.getPdfStorageKey());
    byte[] merged = merge(original, certificate);
    String key = "expedientes/%s/contracts/v%d/contrato-firmado.pdf".formatted(contract.getExpedienteId(), contract.getVersionNumber());
    return fileStorage.store(key, new ByteArrayInputStream(merged), merged.length, "application/pdf").storageKey();
  }

  private static byte[] merge(byte[] first, byte[] second) {
    try (PDDocument a = Loader.loadPDF(first);
        PDDocument b = Loader.loadPDF(new RandomAccessReadBuffer(second))) {
      new PDFMergerUtility().appendDocument(a, b);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      a.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo armar el contrato firmado", e);
    }
  }

  private void ensureExpedienteAwaitingSignature(ContractGeneration contract) {
    ExpedienteStatus status = expedienteApi.getSummary(contract.getExpedienteId()).status();
    if (status != ExpedienteStatus.READY_FOR_SIGNATURE) {
      throw new ConflictException(
          "EXPEDIENTE_NOT_READY_FOR_SIGNATURE",
          "El expediente tiene correcciones pendientes; este contrato no se puede firmar hasta que se resuelvan y se genere una versión vigente.");
    }
  }

  private ContractSignature resolveToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw invalidLink();
    }
    ContractSignature signature = signatureRepository.findByTokenHash(OpaqueTokens.sha256Hex(rawToken)).orElseThrow(ContractSignatureService::invalidLink);
    if (!signature.isLinkUsable(clock.instant())) {
      throw invalidLink();
    }
    return signature;
  }

  private static NotFoundException invalidLink() {
    return new NotFoundException("Esta liga de firma no es válida, ya se usó, venció o el contrato fue reemplazado. Pide una nueva a tu asesor.");
  }

  private ContractSignature getSignature(UUID id) {
    return signatureRepository.findById(id).orElseThrow(() -> new NotFoundException("Firma", id));
  }

  private ContractGeneration getContract(UUID id) {
    return contractRepository.findById(id).orElseThrow(() -> new NotFoundException("Contrato", id));
  }

  static String consentText(ContractGeneration contract, ContractSignature signature) {
    return "Declaro que leí completo el contrato de prestación de servicios de intermediación (versión "
        + contract.getVersionNumber()
        + ", huella SHA-256 "
        + contract.getDocumentSha256()
        + ") y que lo firmo electrónicamente en mi carácter de "
        + signature.getSignerCapacity()
        + ". Acepto que esta firma electrónica me obliga en los mismos términos que mi firma autógrafa.";
  }

  /** Mismo nombre ignorando mayúsculas, acentos y espacios repetidos. */
  static boolean sameName(String typed, String expected) {
    return typed != null && normalize(typed).equals(normalize(expected));
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toUpperCase(Locale.ROOT)
        .replaceAll("[^A-Z0-9Ñ ]", " ")
        .replaceAll("\\s+", " ")
        .strip();
  }

  private static byte[] decodePng(String base64) {
    if (base64 == null || base64.isBlank()) {
      throw new UnprocessableException("SIGNATURE_REQUIRED", "Traza tu firma en el recuadro.");
    }
    String cleaned = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(cleaned);
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException("SIGNATURE_INVALID", "La firma trazada no es válida; vuelve a trazarla.");
    }
    boolean png = bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    if (!png || bytes.length > MAX_SIGNATURE_IMAGE_BYTES) {
      throw new UnprocessableException("SIGNATURE_INVALID", "La firma trazada no es válida; vuelve a trazarla.");
    }
    return bytes;
  }

  private byte[] readAll(String key) {
    try (InputStream in = fileStorage.get(key)) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String formatDate(Instant instant) {
    return instant == null ? "" : java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(java.time.ZoneId.of("America/Mexico_City")).format(instant);
  }

  private static String truncate(String value, int max) {
    return value == null ? null : value.length() <= max ? value : value.substring(0, max);
  }

  private static boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
