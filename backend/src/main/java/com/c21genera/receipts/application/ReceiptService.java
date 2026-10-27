package com.c21genera.receipts.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.RequirementStatusView;
import com.c21genera.documents.DocumentsApi.VersionView;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ManualClientDataView;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.expedientes.ExpedienteSummary.ParticipantView;
import com.c21genera.identity.CurrentUser;
import com.c21genera.identity.UserDirectory;
import com.c21genera.notifications.NotificationsApi;
import com.c21genera.notifications.NotificationsApi.FileAttachment;
import com.c21genera.notifications.StaffAlerts;
import com.c21genera.privacy.PrivacyApi;
import com.c21genera.privacy.PrivacyApi.NoticeVersion;
import com.c21genera.receipts.domain.DocumentReceipt;
import com.c21genera.receipts.domain.DocumentReceipt.Header;
import com.c21genera.receipts.domain.DocumentReceipt.SignatureMethod;
import com.c21genera.receipts.domain.ReceiptSettings;
import com.c21genera.receipts.domain.ReceiptStatus;
import com.c21genera.receipts.domain.ReceivedItem;
import com.c21genera.receipts.infrastructure.DocumentReceiptRepository;
import com.c21genera.receipts.infrastructure.ReceiptPdfRenderer;
import com.c21genera.receipts.infrastructure.ReceiptPdfRenderer.Signature;
import com.c21genera.receipts.infrastructure.ReceiptSettingsRepository;
import com.c21genera.shared.config.StorageProperties;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.storage.FileStorage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #31: acuse de recepción de documentos. Se arma con los
 * documentos realmente almacenados en el expediente (sin recapturarlos),
 * pasa por Borrador → Pendiente de firma → Firmado → Enviado, y la firma la
 * pone el representante legal: en el sistema (contraseña + firma) o cargando
 * el archivo firmado por otro medio autorizado. Generar el acuse nunca lo firma.
 */
@Service
@Transactional
public class ReceiptService {

  private static final Set<String> REPRESENTATIVE_ROLES = Set.of("ADMINISTRATOR");
  private static final Set<String> NOT_RECEIVED = Set.of("PENDING", "NOT_APPLICABLE");

  public record ReceiptView(
      UUID id,
      int sequenceNumber,
      String number,
      ReceiptStatus status,
      String clientName,
      String clientEmail,
      String responsibleEntity,
      String representativeName,
      boolean textsApproved,
      Integer privacyNoticeVersion,
      List<ReceivedItem> items,
      SignatureMethod signatureMethod,
      String signedByName,
      Instant signedAt,
      String sentTo,
      String sentChannel,
      Instant sentAt,
      String sentByName,
      String sendDetail,
      Instant requestedSignatureAt,
      String requestedByName,
      String generatedByName,
      Instant generatedAt) {}

  /** Lo que cubriría un acuse nuevo, y qué cambió desde el último emitido. */
  public record Preview(List<ReceivedItem> items, int newSinceLast, boolean hasReceipts, boolean textsApproved, String responsibleEntity, String representativeName, String clientEmail) {}

  public record SettingsView(
      String responsibleEntity,
      String representativeName,
      String representativeTitle,
      String responsibilityText,
      String privacyText,
      boolean approved,
      String approvedByName,
      Instant approvedAt,
      String updatedByName) {}

  private final DocumentReceiptRepository receipts;
  private final ReceiptSettingsRepository settingsRepository;
  private final ReceiptPdfRenderer renderer;
  private final DocumentsApi documents;
  private final ExpedienteLifecycleApi expedientes;
  private final PrivacyApi privacy;
  private final NotificationsApi notifications;
  private final StaffAlerts alerts;
  private final UserDirectory users;
  private final FileStorage storage;
  private final StorageProperties storageProperties;
  private final ObjectMapper json;
  private final Clock clock;

  ReceiptService(
      DocumentReceiptRepository receipts,
      ReceiptSettingsRepository settingsRepository,
      ReceiptPdfRenderer renderer,
      DocumentsApi documents,
      ExpedienteLifecycleApi expedientes,
      PrivacyApi privacy,
      NotificationsApi notifications,
      StaffAlerts alerts,
      UserDirectory users,
      FileStorage storage,
      StorageProperties storageProperties,
      ObjectMapper json,
      Clock clock) {
    this.receipts = receipts;
    this.settingsRepository = settingsRepository;
    this.renderer = renderer;
    this.documents = documents;
    this.expedientes = expedientes;
    this.privacy = privacy;
    this.notifications = notifications;
    this.alerts = alerts;
    this.users = users;
    this.storage = storage;
    this.storageProperties = storageProperties;
    this.json = json;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<ReceiptView> list(UUID expedienteId) {
    return receipts.findByExpedienteIdOrderBySequenceNumberDesc(expedienteId).stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public Preview preview(UUID expedienteId) {
    List<ReceivedItem> items = receivedItems(expedienteId);
    List<DocumentReceipt> previous = receipts.findByExpedienteIdOrderBySequenceNumberDesc(expedienteId);
    int newSinceLast = items.size();
    if (!previous.isEmpty()) {
      Set<String> covered = new HashSet<>();
      itemsOf(previous.get(0)).forEach(i -> covered.add(key(i)));
      newSinceLast = (int) items.stream().filter(i -> !covered.contains(key(i))).count();
    }
    ReceiptSettings s = settings();
    return new Preview(items, newSinceLast, !previous.isEmpty(), s.isApproved(), s.getResponsibleEntity(), s.getRepresentativeName(), contactEmail(expedienteId));
  }

  /** Emite un acuse nuevo (borrador) con lo recibido hasta este momento; los anteriores se conservan. */
  public ReceiptView generate(UUID expedienteId, CurrentUser user) {
    List<ReceivedItem> items = receivedItems(expedienteId);
    if (items.isEmpty()) {
      throw new UnprocessableException("RECEIPT_NO_DOCUMENTS", "Todavía no se ha recibido ningún documento en este expediente.");
    }
    ExpedienteSummary summary = expedientes.getSummary(expedienteId);
    ReceiptSettings s = settings();
    Optional<NoticeVersion> notice = privacy.noticeFor(expedienteId);
    String propertyKey = null;
    try {
      propertyKey = expedientes.getPropertyProfile(expedienteId).propertyKey();
    } catch (RuntimeException e) {
      // Sin perfil del inmueble: el acuse sale sin clave.
    }
    Header header =
        new Header(
            expedienteId,
            summary.folio(),
            propertyKey,
            summary.ownerDisplayName(),
            contactEmail(expedienteId),
            s.getResponsibleEntity(),
            s.getRepresentativeName(),
            s.getRepresentativeTitle(),
            s.getResponsibilityText(),
            s.getPrivacyText(),
            s.isApproved(),
            notice.map(NoticeVersion::version).orElse(null),
            notice.map(NoticeVersion::sha256).orElse(null),
            notice.map(NoticeVersion::storageKey).orElse(null),
            notice.map(NoticeVersion::acceptedAt).orElse(null));
    UUID id = UUID.randomUUID();
    int sequence = receipts.maxSequence(expedienteId) + 1;
    Instant now = clock.instant();
    String number = summary.folio() + "-A" + sequence;
    byte[] pdf = renderer.render(header, number, items, now, null);
    FileStorage.StoredObjectMetadata stored = storage.store(prefix(expedienteId, id) + "acuse-borrador.pdf", new ByteArrayInputStream(pdf), pdf.length, "application/pdf");
    DocumentReceipt receipt = receipts.save(new DocumentReceipt(id, sequence, header, toJson(items), items.size(), stored.storageKey(), stored.sha256(), user.name(), now));
    return view(receipt);
  }

  /** Pasa a firma del representante legal y le avisa en el sistema. */
  public ReceiptView requestSignature(UUID receiptId, CurrentUser user) {
    DocumentReceipt r = load(receiptId);
    if (r.getStatus() != ReceiptStatus.DRAFT) {
      throw new UnprocessableException("RECEIPT_NOT_DRAFT", "Este acuse ya se mandó a firma.");
    }
    r.requestSignature(user.name(), clock.instant());
    alerts.notifyRoles(
        REPRESENTATIVE_ROLES,
        "Acuse pendiente de firma",
        "Acuse " + r.number() + " pendiente de firma del representante legal",
        "Cliente: " + r.getClientName() + "\nDocumentos: " + r.getItemCount() + "\nSolicitado por: " + user.name(),
        "/expedientes/" + r.getExpedienteId(),
        "receipt-sign:" + r.getId(),
        false);
    return view(r);
  }

  /**
   * Firma en el sistema: solo el representante (RECEIPT_SIGN), confirmando con
   * su contraseña y su firma autógrafa digitalizada. Requiere textos aprobados.
   */
  public ReceiptView signElectronically(UUID receiptId, String password, String signaturePngBase64, String title, CurrentUser user) {
    DocumentReceipt r = load(receiptId);
    requireSignable(r);
    if (password == null || !users.passwordMatches(user.id(), password)) {
      throw new UnprocessableException("RECEIPT_PASSWORD_INVALID", "La contraseña no es correcta.");
    }
    byte[] png = decode(signaturePngBase64);
    Instant now = clock.instant();
    String signerTitle = title != null && !title.isBlank() ? title.strip() : (r.getRepresentativeTitle() != null ? r.getRepresentativeTitle() : "Representante legal");
    byte[] pdf = renderer.render(r.header(), r.number(), itemsOf(r), r.getGeneratedAt(), new Signature(user.name(), signerTitle, png, now, r.getDraftSha256()));
    FileStorage.StoredObjectMetadata stored = storage.store(prefix(r.getExpedienteId(), r.getId()) + "acuse-firmado.pdf", new ByteArrayInputStream(pdf), pdf.length, "application/pdf");
    r.signed(SignatureMethod.ELECTRONIC_SIMPLE, stored.storageKey(), stored.sha256(), user.id(), user.name(), now);
    return view(r);
  }

  /** Firmado por otro medio autorizado (papel, DocuSign…): se carga el PDF firmado. */
  public ReceiptView uploadSigned(UUID receiptId, byte[] content, CurrentUser user) {
    DocumentReceipt r = load(receiptId);
    requireSignable(r);
    if (content == null || content.length < 5 || !new String(content, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF")) {
      throw new UnprocessableException("RECEIPT_FILE_INVALID", "Carga el acuse firmado en PDF.");
    }
    FileStorage.StoredObjectMetadata stored = storage.store(prefix(r.getExpedienteId(), r.getId()) + "acuse-firmado.pdf", new ByteArrayInputStream(content), content.length, "application/pdf");
    r.signed(SignatureMethod.UPLOADED, stored.storageKey(), stored.sha256(), user.id(), user.name(), clock.instant());
    return view(r);
  }

  /** Envía al cliente el acuse firmado y el aviso de privacidad por correo; queda el registro del envío. */
  public ReceiptView send(UUID receiptId, String email, CurrentUser user) {
    DocumentReceipt r = load(receiptId);
    if (r.getStatus() != ReceiptStatus.SIGNED && r.getStatus() != ReceiptStatus.SENT) {
      throw new UnprocessableException("RECEIPT_NOT_SIGNED", "Solo se envía al cliente el acuse firmado por el representante legal.");
    }
    if (!r.isTextsApproved()) {
      throw new UnprocessableException("RECEIPT_TEXT_NOT_APPROVED", "Este acuse se emitió con textos sin aprobación legal: genera uno nuevo cuando estén aprobados.");
    }
    String to = email != null && !email.isBlank() ? email.strip() : r.getClientEmail();
    if (to == null || to.isBlank()) {
      throw new UnprocessableException("RECEIPT_EMAIL_REQUIRED", "El cliente no tiene correo registrado: escríbelo para enviar el acuse.");
    }
    List<FileAttachment> files = new ArrayList<>();
    files.add(new FileAttachment(r.getSignedPdfKey(), "Acuse-" + r.number() + ".pdf"));
    if (r.getPrivacyNoticeKey() != null) {
      files.add(new FileAttachment(r.getPrivacyNoticeKey(), "Aviso-de-privacidad-v" + r.getPrivacyNoticeVersion() + ".pdf"));
    }
    notifications.notifyWithFiles(
        r.getExpedienteId(),
        "Acuse de recepción de documentos",
        to,
        "Acuse de recepción de tus documentos — expediente " + r.getExpedienteFolio(),
        "Hola " + firstName(r.getClientName()) + ",\n\n"
            + "Te enviamos el acuse de recepción firmado por el representante legal de " + r.getResponsibleEntity()
            + ", con la relación de los documentos que nos entregaste para tu expediente " + r.getExpedienteFolio()
            + ", y el aviso de privacidad bajo el cual los resguardamos.\n\n"
            + "Consérvalo como constancia de lo que entregaste.\n\nCENTURY 21 Genera\nWhatsApp 7778005300 · ventas@c21genera.com",
        files);
    r.sent(to, "EMAIL", "Correo encolado: el resultado de la entrega aparece en el historial de notificaciones del expediente.", user.name(), clock.instant());
    return view(r);
  }

  @Transactional(readOnly = true)
  public URI download(UUID receiptId, boolean signed) {
    DocumentReceipt r = load(receiptId);
    String key = signed ? r.getSignedPdfKey() : r.getDraftPdfKey();
    if (key == null) {
      throw new UnprocessableException("RECEIPT_NOT_SIGNED", "Este acuse todavía no está firmado.");
    }
    return storage.generateTemporaryDownloadUrl(key, storageProperties.presignedUrlTtl());
  }

  @Transactional(readOnly = true)
  public UUID expedienteOf(UUID receiptId) {
    return load(receiptId).getExpedienteId();
  }

  // --- Textos (área legal) ---------------------------------------------------------

  @Transactional(readOnly = true)
  public SettingsView settingsView() {
    ReceiptSettings s = settings();
    return new SettingsView(
        s.getResponsibleEntity(),
        s.getRepresentativeName(),
        s.getRepresentativeTitle(),
        s.getResponsibilityText(),
        s.getPrivacyText(),
        s.isApproved(),
        s.getApprovedByName(),
        s.getApprovedAt(),
        s.getUpdatedByName());
  }

  public SettingsView updateSettings(String entity, String representativeName, String representativeTitle, String responsibilityText, String privacyText, CurrentUser user) {
    if (entity == null || entity.isBlank() || responsibilityText == null || responsibilityText.isBlank() || privacyText == null || privacyText.isBlank()) {
      throw new UnprocessableException("RECEIPT_TEXT_REQUIRED", "La entidad responsable y los textos son obligatorios.");
    }
    settings().update(entity, representativeName, representativeTitle, responsibilityText, privacyText, user.name(), clock.instant());
    return settingsView();
  }

  public SettingsView approve(boolean approved, CurrentUser user) {
    settings().approve(approved, user.name(), clock.instant());
    return settingsView();
  }

  // ---------------------------------------------------------------------------

  /**
   * Cada versión recibida de cada documento con archivo (incluidos los
   * adicionales). Lo pendiente o lo que "no aplica" sin archivo no figura.
   */
  List<ReceivedItem> receivedItems(UUID expedienteId) {
    ExpedienteSummary summary = expedientes.getSummary(expedienteId);
    Map<UUID, String> participants = summary.participants().stream().collect(Collectors.toMap(ParticipantView::id, ParticipantView::fullName, (a, b) -> a));
    List<ReceivedItem> items = new ArrayList<>();
    for (RequirementStatusView doc : documents.requirementStatusOf(expedienteId)) {
      List<VersionView> versions = documents.versionViewsOf(doc.documentId());
      if (versions.isEmpty() || (NOT_RECEIVED.contains(doc.status()) && doc.currentVersionId() == null)) {
        continue;
      }
      String participant = doc.participantId() == null ? null : participants.get(doc.participantId());
      for (VersionView v : versions) {
        items.add(new ReceivedItem(doc.displayName(), participant, v.versionNumber(), v.uploadedAt(), v.id().equals(doc.currentVersionId()), doc.status()));
      }
    }
    items.sort(Comparator.comparing(ReceivedItem::name).thenComparing(ReceivedItem::versionNumber, Comparator.reverseOrder()));
    return items;
  }

  private String contactEmail(UUID expedienteId) {
    ManualClientDataView manual = expedientes.getManualData(expedienteId);
    if (manual != null && manual.email() != null && !manual.email().isBlank()) {
      return manual.email();
    }
    return expedientes.getSummary(expedienteId).participants().stream().filter(ParticipantView::isOwner).map(ParticipantView::email).filter(e -> e != null && !e.isBlank()).findFirst().orElse(null);
  }

  private void requireSignable(DocumentReceipt r) {
    if (r.getStatus() == ReceiptStatus.SIGNED || r.getStatus() == ReceiptStatus.SENT) {
      throw new UnprocessableException("RECEIPT_ALREADY_SIGNED", "Este acuse ya está firmado.");
    }
    if (!r.isTextsApproved()) {
      throw new UnprocessableException("RECEIPT_TEXT_NOT_APPROVED", "Los textos de este acuse no estaban aprobados por el área legal al emitirlo: aprueba los textos y genera un acuse nuevo.");
    }
  }

  private static String key(ReceivedItem i) {
    return i.name() + "|" + i.participant() + "|" + i.versionNumber();
  }

  private ReceiptSettings settings() {
    return settingsRepository.findById((short) 1).orElseThrow(() -> new IllegalStateException("Falta la fila de receipt_settings"));
  }

  private DocumentReceipt load(UUID id) {
    return receipts.findById(id).orElseThrow(() -> new NotFoundException("Acuse no encontrado"));
  }

  private static String prefix(UUID expedienteId, UUID receiptId) {
    return "expedientes/" + expedienteId + "/acuses/" + receiptId + "/";
  }

  private String toJson(List<ReceivedItem> items) {
    try {
      return json.writeValueAsString(items);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("No se pudo guardar la relación de documentos", e);
    }
  }

  private List<ReceivedItem> itemsOf(DocumentReceipt r) {
    try {
      return json.readValue(r.getItemsJson(), new TypeReference<List<ReceivedItem>>() {});
    } catch (JsonProcessingException e) {
      return List.of();
    }
  }

  private static byte[] decode(String base64) {
    if (base64 == null || base64.isBlank()) {
      throw new UnprocessableException("RECEIPT_SIGNATURE_REQUIRED", "Dibuja tu firma para firmar el acuse.");
    }
    String cleaned = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
    try {
      return Base64.getDecoder().decode(cleaned);
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException("RECEIPT_SIGNATURE_INVALID", "La firma no es válida; vuelve a dibujarla.");
    }
  }

  private static String firstName(String name) {
    return name == null || name.isBlank() ? "" : name.strip().split("\\s+")[0];
  }

  private ReceiptView view(DocumentReceipt r) {
    return new ReceiptView(
        r.getId(),
        r.getSequenceNumber(),
        r.number(),
        r.getStatus(),
        r.getClientName(),
        r.getClientEmail(),
        r.getResponsibleEntity(),
        r.getRepresentativeName(),
        r.isTextsApproved(),
        r.getPrivacyNoticeVersion(),
        itemsOf(r),
        r.getSignatureMethod(),
        r.getSignedByName(),
        r.getSignedAt(),
        r.getSentTo(),
        r.getSentChannel(),
        r.getSentAt(),
        r.getSentByName(),
        r.getSendDetail(),
        r.getRequestedSignatureAt(),
        r.getRequestedByName(),
        r.getGeneratedByName(),
        r.getGeneratedAt());
  }
}
