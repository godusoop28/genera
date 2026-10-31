package com.c21genera.operations.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.RequirementStatusView;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ExpedienteRef;
import com.c21genera.expedientes.ExpedienteLifecycleApi.PropertyProfile;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.identity.CurrentUser;
import com.c21genera.operations.domain.ClosingOperation;
import com.c21genera.operations.domain.ClosingOperation.DocStatus;
import com.c21genera.operations.domain.ClosingOperation.Origin;
import com.c21genera.operations.domain.ClosingOperation.Side;
import com.c21genera.operations.domain.ClosingOperation.Status;
import com.c21genera.operations.domain.ClosingOperation.Type;
import com.c21genera.operations.domain.ClosingRequirement;
import com.c21genera.operations.domain.ClosingRequirement.RequestedFrom;
import com.c21genera.operations.domain.MatrixSettings;
import com.c21genera.operations.domain.OperationEvent;
import com.c21genera.operations.domain.OperationParty;
import com.c21genera.operations.domain.OperationParty.PartySide;
import com.c21genera.operations.domain.RequirementEvidence;
import com.c21genera.operations.domain.RequirementTemplate;
import com.c21genera.operations.infrastructure.ClosingOperationRepository;
import com.c21genera.operations.infrastructure.ClosingRequirementRepository;
import com.c21genera.operations.infrastructure.MatrixSettingsRepository;
import com.c21genera.operations.infrastructure.OperationEventRepository;
import com.c21genera.operations.infrastructure.OperationPartyRepository;
import com.c21genera.operations.infrastructure.RequirementEvidenceRepository;
import com.c21genera.operations.infrastructure.RequirementTemplateRepository;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.ClosingEvents.ClosingCompleted;
import com.c21genera.shared.events.ClosingEvents.ClosingReopened;
import com.c21genera.shared.storage.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.tika.Tika;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Control de Cambios #32: esquema de cierre de operaciones propias y externas con revisión
 * documental de la operación, PLD y PROFECO.
 *
 * <ul>
 *   <li>El estado de la operación y el de su revisión documental son independientes.
 *   <li>Recibir un archivo deja el requisito "Recibido"; solo OPERATION_REVIEW lo valida.
 *   <li>"No aplica" pide justificación y la aprobación de OPERATION_REVIEW; los requisitos legales
 *       obligatorios de la matriz no lo admiten.
 *   <li>"Completo y validado" solo con la matriz aprobada por el área legal, ambas partes
 *       identificadas, todos los requisitos cumplidos y la autorización de OPERATION_REVIEW.
 * </ul>
 */
@Service
@Transactional
public class OperationService {

  public static final String VIEW_ALL = "OPERATION_VIEW_ALL";
  public static final String MANAGE = "OPERATION_MANAGE";
  public static final String REVIEW = "OPERATION_REVIEW";
  public static final String MATRIX_MANAGE = "OPERATION_MATRIX_MANAGE";

  private static final ZoneId MEXICO_CITY = ZoneId.of("America/Mexico_City");
  private static final long MAX_EVIDENCE_BYTES = 25L * 1024 * 1024;
  private static final List<String> EVIDENCE_TYPES = List.of("application/pdf", "image/jpeg", "image/png", "image/webp");

  private final ClosingOperationRepository operations;
  private final OperationPartyRepository parties;
  private final ClosingRequirementRepository requirements;
  private final RequirementEvidenceRepository evidences;
  private final RequirementTemplateRepository templates;
  private final MatrixSettingsRepository matrixSettings;
  private final OperationEventRepository events;
  private final ExpedienteLifecycleApi expedientes;
  private final DocumentsApi documents;
  private final FileStorage storage;
  private final ApplicationEventPublisher publisher;
  private final Clock clock;
  private final Tika tika = new Tika();

  public OperationService(
      ClosingOperationRepository operations,
      OperationPartyRepository parties,
      ClosingRequirementRepository requirements,
      RequirementEvidenceRepository evidences,
      RequirementTemplateRepository templates,
      MatrixSettingsRepository matrixSettings,
      OperationEventRepository events,
      ExpedienteLifecycleApi expedientes,
      DocumentsApi documents,
      FileStorage storage,
      ApplicationEventPublisher publisher,
      Clock clock) {
    this.operations = operations;
    this.parties = parties;
    this.requirements = requirements;
    this.evidences = evidences;
    this.templates = templates;
    this.matrixSettings = matrixSettings;
    this.events = events;
    this.expedientes = expedientes;
    this.documents = documents;
    this.storage = storage;
    this.publisher = publisher;
    this.clock = clock;
  }

  // --- Comandos y vistas ------------------------------------------------------

  public record PartyInput(
      String fullName, String personType, String representativeName, String representativeRole, String email, String phone, String counterpartContact, String notes) {}

  public record CreateCommand(
      Origin origin,
      UUID expedienteId,
      Type operationType,
      Side representedSide,
      String propertyDescription,
      String propertyAddress,
      String externalSource,
      String externalOffice,
      String externalContact,
      String generaParticipation,
      UUID advisorId,
      String advisorName,
      PartyInput seller,
      PartyInput buyer) {}

  public record UpdateCommand(
      Side representedSide,
      String propertyDescription,
      String propertyAddress,
      String externalSource,
      String externalOffice,
      String externalContact,
      String generaParticipation,
      UUID advisorId,
      String advisorName,
      BigDecimal amount,
      String currency,
      PartyInput seller,
      PartyInput buyer) {}

  public record ListFilter(String status, String docStatus, String origin, UUID advisorId, LocalDate closedFrom, LocalDate closedTo, String q, Boolean pendingDocs) {}

  public record OperationItem(
      UUID id,
      String folio,
      String origin,
      UUID expedienteId,
      String operationType,
      String propertyDescription,
      String representedSide,
      UUID advisorId,
      String advisorName,
      String status,
      String docStatus,
      String displayStatus,
      LocalDate closedOn,
      String closedByName,
      BigDecimal amount,
      String currency,
      String sellerName,
      String buyerName,
      int requirementsTotal,
      int requirementsSatisfied,
      int requirementsMissing,
      LocalDate docDueDate,
      String docFollowUpOwner,
      Instant updatedAt) {}

  public record Totals(int total, int closed, int closedPendingDocs, int inReview, int external) {}

  public record OperationList(List<OperationItem> items, Totals totals, List<AdvisorOption> advisors) {}

  public record AdvisorOption(UUID id, String name) {}

  public record PartyView(String side, String fullName, String personType, String representativeName, String representativeRole, String email, String phone, String counterpartContact, String notes, boolean representedByGenera) {}

  public record EvidenceView(
      UUID id,
      int versionNumber,
      String kind,
      String filename,
      Long sizeBytes,
      UUID documentId,
      UUID documentVersionId,
      String documentName,
      String note,
      LocalDate documentDate,
      LocalDate expiresOn,
      String attachedByName,
      Instant attachedAt) {}

  public record RequirementView(
      UUID id,
      String section,
      String partySide,
      String label,
      String description,
      boolean mandatory,
      Integer validityDays,
      String documentTypeHint,
      String status,
      String requestedFrom,
      Instant requestedAt,
      String followUpOwner,
      LocalDate dueDate,
      String observation,
      String reviewedByName,
      Instant reviewedAt,
      String naJustification,
      String naRequestedByName,
      Instant naRequestedAt,
      String naApprovedByName,
      Instant naApprovedAt,
      boolean satisfied,
      boolean expired,
      boolean dateMissing,
      EvidenceView currentEvidence,
      List<EvidenceView> previousEvidence) {}

  public record EventView(UUID id, UUID requirementId, String type, String detail, String actorName, Instant occurredAt) {}

  public record ExpedienteDocumentOption(UUID documentId, UUID versionId, String type, String name, String status) {}

  /** Resumen de faltantes por parte y por sección, para no tener que revisar renglón por renglón. */
  public record Summary(
      Map<String, Integer> missingBySide,
      Map<String, Integer> missingBySection,
      int needsUpdate,
      int needsCorrection,
      int pendingNaApprovals,
      int satisfied,
      int total,
      List<String> blockers) {}

  public record OperationDetail(
      OperationItem operation,
      String propertyAddress,
      String externalSource,
      String externalOffice,
      String externalContact,
      String generaParticipation,
      String cancelReason,
      String docAuthorizedByName,
      Instant docAuthorizedAt,
      String createdByName,
      Instant createdAt,
      String expedienteFolio,
      List<PartyView> parties,
      List<RequirementView> requirements,
      Summary summary,
      List<EventView> history,
      List<ExpedienteDocumentOption> expedienteDocuments,
      boolean matrixApproved,
      int newMatrixRequirements) {}

  // --- Altas ------------------------------------------------------------------

  public OperationDetail create(CreateCommand c, CurrentUser user) {
    requirePermission(user, MANAGE);
    Instant now = clock.instant();
    if (c.origin() == null || c.operationType() == null || c.representedSide() == null) {
      throw new UnprocessableException("OPERATION_INCOMPLETE", "Indica el origen, el tipo de operación y qué parte representa C21 Genera.");
    }
    String folio = "OP-%d-%05d".formatted(Year.now(clock).getValue(), operations.nextFolioSequenceValue());
    ClosingOperation op;
    String description;
    String address = trim(c.propertyAddress());
    UUID advisorId = c.advisorId();
    String advisorName = trim(c.advisorName());
    PartyInput seller = c.seller();
    if (c.origin() == Origin.OWN) {
      if (c.expedienteId() == null) {
        throw new UnprocessableException("OPERATION_EXPEDIENTE_REQUIRED", "Elige el expediente del inmueble de la cartera propia.");
      }
      ExpedienteRef ref = expedientes.ref(c.expedienteId()).orElseThrow(() -> new NotFoundException("Expediente", c.expedienteId()));
      operations.findFirstByExpedienteIdAndStatusNot(ref.id(), Status.CANCELLED).ifPresent(existing -> {
        throw new ConflictException("OPERATION_ALREADY_EXISTS", "Este expediente ya tiene la operación " + existing.getFolio() + " en curso.");
      });
      PropertyProfile profile = expedientes.getPropertyProfile(ref.id());
      ExpedienteSummary summary = expedientes.getSummary(ref.id());
      description = firstNonBlank(trim(c.propertyDescription()), ref.propertyReference(), joinNonBlank(profile.subtypeName(), ref.propertyKey()), ref.folio());
      address = firstNonBlank(address, summary.propertyAddress());
      if (advisorId == null) {
        advisorId = ref.assignedAdvisorId();
        advisorName = firstNonBlank(advisorName, profile.assignedAdvisorName());
      }
      // El vendedor (o arrendador) de la cartera propia es el titular del expediente: se vincula, no se recaptura.
      if (seller == null || isBlank(seller.fullName())) {
        seller = new PartyInput(ref.ownerDisplayName(), summary.personType() == null ? null : summary.personType().name(), null, null, null, null, null, null);
      }
      op = new ClosingOperation(folio, Origin.OWN, ref.id(), c.operationType(), c.representedSide(), user.id(), user.name(), now);
    } else {
      description = trim(c.propertyDescription());
      if (isBlank(description)) {
        throw new UnprocessableException("OPERATION_PROPERTY_REQUIRED", "Describe el inmueble externo (tipo, nombre o clave).");
      }
      if (isBlank(c.externalSource()) && isBlank(c.externalOffice())) {
        throw new UnprocessableException("OPERATION_EXTERNAL_SOURCE_REQUIRED", "Indica el origen de la propiedad o la oficina o tercero participante.");
      }
      op = new ClosingOperation(folio, Origin.EXTERNAL, null, c.operationType(), c.representedSide(), user.id(), user.name(), now);
    }
    if (advisorId == null) {
      advisorId = user.id();
      advisorName = user.name();
    }
    op.describe(description, address, trim(c.externalSource()), trim(c.externalOffice()), trim(c.externalContact()), trim(c.generaParticipation()), c.representedSide(), advisorId, advisorName, now);
    operations.save(op);
    upsertParty(op, PartySide.SELLER, seller);
    upsertParty(op, PartySide.BUYER, c.buyer());
    log(op, null, "CREATED", (op.getOrigin() == Origin.OWN ? "Operación de cartera propia" : "Operación de propiedad externa") + " registrada (" + folio + ").", user);
    int added = applyMatrix(op, now);
    log(op, null, "DOCS_REQUESTED", "Se activó la solicitud de " + added + " requisito(s) según la matriz documental vigente.", user);
    refreshDocStatus(op, user);
    return detail(op.getId(), user);
  }

  public OperationDetail update(UUID id, UpdateCommand c, CurrentUser user) {
    ClosingOperation op = requireEditable(id, user);
    Instant now = clock.instant();
    Side before = op.getRepresentedSide();
    Side side = c.representedSide() == null ? before : c.representedSide();
    String description = firstNonBlank(trim(c.propertyDescription()), op.getPropertyDescription());
    UUID advisorId = c.advisorId() == null ? op.getAdvisorId() : c.advisorId();
    String advisorName = c.advisorId() == null ? op.getAdvisorName() : trim(c.advisorName());
    op.describe(description, trim(c.propertyAddress()), trim(c.externalSource()), trim(c.externalOffice()), trim(c.externalContact()), trim(c.generaParticipation()), side, advisorId, advisorName, now);
    if (c.amount() != null || c.currency() != null) {
      validateAmount(c.amount(), c.currency());
      op.recordTerms(c.amount(), normalizeCurrency(c.currency()), now);
    }
    if (c.seller() != null) upsertParty(op, PartySide.SELLER, c.seller());
    if (c.buyer() != null) upsertParty(op, PartySide.BUYER, c.buyer());
    if (side != before) {
      // Cambia a quién se le piden los papeles que siguen pendientes.
      for (ClosingRequirement r : requirements.findByOperationIdOrderBySortOrderAsc(op.getId())) {
        if (r.getStatus() == ClosingRequirement.Status.PENDING && r.getPartySide() != null) {
          r.followUp(defaultRequestedFrom(side, r.getPartySide()), r.getFollowUpOwner(), r.getDueDate(), now);
        }
      }
      log(op, null, "REPRESENTED_SIDE", "C21 Genera representa ahora: " + sideLabel(side) + " (antes: " + sideLabel(before) + ").", user);
    }
    log(op, null, "UPDATED", "Datos de la operación actualizados.", user);
    return detail(id, user);
  }

  // --- Estado de la operación (cierre comercial) ---------------------------

  public OperationDetail moveToClosingReview(UUID id, CurrentUser user) {
    ClosingOperation op = requireEditable(id, user);
    if (op.getStatus() != Status.IN_PROCESS) {
      throw new ConflictException("OPERATION_STATUS", "Solo una operación en proceso pasa a revisión de cierre.");
    }
    op.changeStatus(Status.CLOSING_REVIEW, clock.instant());
    log(op, null, "STATUS", "En proceso → En revisión de cierre.", user);
    return detail(id, user);
  }

  /**
   * Registra el cierre comercial. Puede quedar "Cerrada — Documentación pendiente": entonces pide la
   * fecha compromiso y el responsable de subsanar los faltantes, sin presentarla como completa.
   */
  public OperationDetail close(UUID id, LocalDate closedOn, BigDecimal amount, String currency, LocalDate docDueDate, String docFollowUpOwner, CurrentUser user) {
    ClosingOperation op = requireEditable(id, user);
    if (op.getStatus() == Status.CLOSED || op.getStatus() == Status.CANCELLED) {
      throw new ConflictException("OPERATION_STATUS", "La operación ya está " + (op.getStatus() == Status.CLOSED ? "cerrada" : "cancelada") + ".");
    }
    LocalDate date = closedOn == null ? today() : closedOn;
    if (date.isAfter(today())) {
      throw new UnprocessableException("CLOSING_DATE_IN_FUTURE", "La fecha de cierre no puede ser posterior a hoy.");
    }
    BigDecimal finalAmount = amount != null ? amount : op.getAmount();
    String finalCurrency = currency != null ? normalizeCurrency(currency) : op.getCurrency();
    validateAmount(finalAmount, finalCurrency);
    if (finalAmount == null) {
      throw new UnprocessableException("CLOSING_AMOUNT_REQUIRED", "Captura el monto de la operación para registrar el cierre.");
    }
    List<OperationParty> ps = parties.findByOperationId(op.getId());
    if (missingParty(ps, PartySide.SELLER) || missingParty(ps, PartySide.BUYER)) {
      throw new UnprocessableException("CLOSING_PARTIES_REQUIRED", "Identifica al " + partyLabel(op, PartySide.SELLER) + " y al " + partyLabel(op, PartySide.BUYER) + " antes de registrar el cierre.");
    }
    Instant now = clock.instant();
    op.recordTerms(finalAmount, finalCurrency, now);
    boolean pending = op.getDocStatus() != DocStatus.COMPLETE_VALIDATED;
    if (pending) {
      if (docDueDate == null || isBlank(docFollowUpOwner)) {
        throw new UnprocessableException(
            "CLOSING_PENDING_FOLLOW_UP", "La documentación no está completa y validada: indica la fecha compromiso y el responsable de subsanar los faltantes.");
      }
      op.followUp(docDueDate, trim(docFollowUpOwner), now);
    }
    op.close(date, firstNonBlank(op.getAdvisorName(), user.name()), now);
    log(op, null, "CLOSED", "Operación cerrada el " + date + " por " + user.name() + (pending ? ". Documentación pendiente: compromiso " + docDueDate + ", responsable " + trim(docFollowUpOwner) + "." : "."), user);
    if (op.getExpedienteId() != null) {
      publisher.publishEvent(new ClosingCompleted(op.getExpedienteId(), date));
    }
    return detail(id, user);
  }

  public OperationDetail cancel(UUID id, String reason, CurrentUser user) {
    ClosingOperation op = requireEditable(id, user);
    if (isBlank(reason)) {
      throw new UnprocessableException("REASON_REQUIRED", "Indica el motivo de la cancelación.");
    }
    if (op.getStatus() == Status.CANCELLED) {
      throw new ConflictException("OPERATION_STATUS", "La operación ya está cancelada.");
    }
    boolean wasClosed = op.getStatus() == Status.CLOSED;
    if (wasClosed) {
      requirePermission(user, REVIEW);
    }
    op.cancel(reason.strip(), clock.instant());
    log(op, null, "CANCELLED", "Operación cancelada: " + reason.strip(), user);
    if (wasClosed && op.getExpedienteId() != null) {
      publisher.publishEvent(new ClosingReopened(op.getExpedienteId()));
    }
    return detail(id, user);
  }

  /** Solo el rol autorizado reabre una operación cerrada o cancelada; se conserva todo su historial. */
  public OperationDetail reopen(UUID id, String reason, CurrentUser user) {
    ClosingOperation op = requireVisible(id, user);
    requirePermission(user, REVIEW);
    if (isBlank(reason)) {
      throw new UnprocessableException("REASON_REQUIRED", "Indica el motivo de la reapertura.");
    }
    if (op.getStatus() != Status.CLOSED && op.getStatus() != Status.CANCELLED) {
      throw new ConflictException("OPERATION_STATUS", "Solo se reabre una operación cerrada o cancelada.");
    }
    if (op.getStatus() == Status.CANCELLED && op.getExpedienteId() != null) {
      operations.findFirstByExpedienteIdAndStatusNot(op.getExpedienteId(), Status.CANCELLED).ifPresent(other -> {
        throw new ConflictException("OPERATION_ALREADY_EXISTS", "El expediente ya tiene otra operación en curso (" + other.getFolio() + ").");
      });
    }
    boolean wasClosed = op.getStatus() == Status.CLOSED;
    op.reopen(clock.instant());
    log(op, null, "REOPENED", "Operación reabierta (vuelve a revisión de cierre): " + reason.strip(), user);
    if (wasClosed && op.getExpedienteId() != null) {
      publisher.publishEvent(new ClosingReopened(op.getExpedienteId()));
    }
    return detail(id, user);
  }

  public OperationDetail followUp(UUID id, LocalDate dueDate, String owner, CurrentUser user) {
    ClosingOperation op = requireEditable(id, user);
    op.followUp(dueDate, trim(owner), clock.instant());
    log(op, null, "FOLLOW_UP", "Compromiso de documentación: " + (dueDate == null ? "sin fecha" : dueDate) + ", responsable " + (isBlank(owner) ? "sin asignar" : owner.strip()) + ".", user);
    return detail(id, user);
  }

  // --- Revisión documental --------------------------------------------------

  public OperationDetail attachFile(UUID id, UUID requirementId, MultipartFile file, LocalDate documentDate, String note, CurrentUser user) {
    ClosingOperation op = requireEditable(id, user);
    ClosingRequirement r = requireRequirementOpen(op, requirementId);
    if (file == null || file.isEmpty()) {
      throw new UnprocessableException("EVIDENCE_EMPTY", "Elige el archivo de la evidencia.");
    }
    if (file.getSize() > MAX_EVIDENCE_BYTES) {
      throw new UnprocessableException("EVIDENCE_TOO_LARGE", "La evidencia pesa más de 25 MB.");
    }
    String type;
    try (InputStream in = file.getInputStream()) {
      type = tika.detect(in.readNBytes(64 * 1024));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (!EVIDENCE_TYPES.contains(type)) {
      throw new UnprocessableException("EVIDENCE_UNSUPPORTED", "La evidencia debe ser PDF, JPG, PNG o WEBP.");
    }
    int version = (int) evidences.countByRequirementId(r.getId()) + 1;
    String ext = switch (type) {
      case "application/pdf" -> "pdf";
      case "image/png" -> "png";
      case "image/webp" -> "webp";
      default -> "jpg";
    };
    String key = "operaciones/%s/requisitos/%s/v%d.%s".formatted(op.getId(), r.getId(), version, ext);
    FileStorage.StoredObjectMetadata stored;
    try (InputStream in = file.getInputStream()) {
      stored = storage.store(key, in, file.getSize(), type);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Instant now = clock.instant();
    evidences.save(RequirementEvidence.file(r.getId(), version, key, cleanName(file.getOriginalFilename()), type, file.getSize(), stored.sha256(), trim(note), documentDate, user.name(), now));
    r.evidenceReceived();
    log(op, r.getId(), "EVIDENCE", "Evidencia v" + version + " cargada para \"" + r.getLabel() + "\": " + cleanName(file.getOriginalFilename()) + ".", user);
    refreshDocStatus(op, user);
    return detail(id, user);
  }

  /** Reutiliza un documento que ya está en el expediente (no se vuelve a pedir ni se duplica el archivo). */
  public OperationDetail linkDocument(UUID id, UUID requirementId, UUID documentId, LocalDate documentDate, String note, CurrentUser user) {
    ClosingOperation op = requireEditable(id, user);
    ClosingRequirement r = requireRequirementOpen(op, requirementId);
    if (op.getExpedienteId() == null) {
      throw new UnprocessableException("NO_EXPEDIENTE", "Esta operación no está ligada a un expediente.");
    }
    RequirementStatusView doc =
        documents.requirementStatusOf(op.getExpedienteId()).stream()
            .filter(d -> d.documentId().equals(documentId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Documento", documentId));
    if (doc.currentVersionId() == null) {
      throw new UnprocessableException("DOCUMENT_WITHOUT_FILE", "Ese documento del expediente todavía no tiene archivo.");
    }
    int version = (int) evidences.countByRequirementId(r.getId()) + 1;
    String name = firstNonBlank(doc.name(), doc.type() == null ? null : doc.type().name());
    Instant now = clock.instant();
    evidences.save(RequirementEvidence.document(r.getId(), version, documentId, doc.currentVersionId(), name, trim(note), documentDate, user.name(), now));
    r.evidenceReceived();
    log(op, r.getId(), "EVIDENCE_LINKED", "Se vinculó el documento del expediente \"" + name + "\" (estatus " + doc.status() + ") a \"" + r.getLabel() + "\" como v" + version + ".", user);
    refreshDocStatus(op, user);
    return detail(id, user);
  }

  public OperationDetail addNoteEvidence(UUID id, UUID requirementId, String note, LocalDate documentDate, CurrentUser user) {
    ClosingOperation op = requireEditable(id, user);
    ClosingRequirement r = requireRequirementOpen(op, requirementId);
    if (isBlank(note)) {
      throw new UnprocessableException("EVIDENCE_EMPTY", "Escribe el dato o la referencia que sustenta el requisito.");
    }
    int version = (int) evidences.countByRequirementId(r.getId()) + 1;
    evidences.save(RequirementEvidence.note(r.getId(), version, note.strip(), documentDate, user.name(), clock.instant()));
    r.evidenceReceived();
    log(op, r.getId(), "EVIDENCE", "Dato o referencia v" + version + " registrado para \"" + r.getLabel() + "\".", user);
    refreshDocStatus(op, user);
    return detail(id, user);
  }

  public OperationDetail requirementFollowUp(UUID id, UUID requirementId, RequestedFrom from, String owner, LocalDate dueDate, CurrentUser user) {
    ClosingOperation op = requireEditable(id, user);
    ClosingRequirement r = requireRequirement(op, requirementId);
    r.followUp(from == null ? r.getRequestedFrom() : from, trim(owner), dueDate, clock.instant());
    log(op, r.getId(), "REQUEST", "Seguimiento de \"" + r.getLabel() + "\": se solicita a " + requestedFromLabel(r.getRequestedFrom()) + (isBlank(owner) ? "" : ", responsable " + owner.strip()) + (dueDate == null ? "" : ", compromiso " + dueDate) + ".", user);
    return detail(id, user);
  }

  /** Revisión del rol autorizado: En revisión / Validado / Requiere corrección (con observación). */
  public OperationDetail review(UUID id, UUID requirementId, ClosingRequirement.Status status, String observation, CurrentUser user) {
    ClosingOperation op = requireVisible(id, user);
    requirePermission(user, REVIEW);
    ClosingRequirement r = requireRequirementOpen(op, requirementId);
    if (status != ClosingRequirement.Status.IN_REVIEW && status != ClosingRequirement.Status.VALIDATED && status != ClosingRequirement.Status.NEEDS_CORRECTION) {
      throw new UnprocessableException("REVIEW_STATUS", "La revisión solo puede dejar el requisito en revisión, validado o con corrección requerida.");
    }
    List<RequirementEvidence> history = evidences.findByRequirementIdInOrderByVersionNumberDesc(List.of(r.getId()));
    if (status == ClosingRequirement.Status.VALIDATED) {
      if (history.isEmpty()) {
        throw new UnprocessableException("VALIDATE_WITHOUT_EVIDENCE", "No se puede validar sin evidencia: vincula o carga el archivo, versión o dato que lo sustenta.");
      }
      RequirementEvidence current = history.get(0);
      if (r.getValidityDays() != null) {
        if (current.getDocumentDate() == null) {
          throw new UnprocessableException("VALIDATE_WITHOUT_DATE", "Este requisito tiene vigencia: captura la fecha del documento antes de validarlo.");
        }
        if (isExpired(r, current)) {
          throw new UnprocessableException("VALIDATE_EXPIRED", "La evidencia ya no está vigente: pide una versión actualizada.");
        }
      }
    }
    if (status == ClosingRequirement.Status.NEEDS_CORRECTION && isBlank(observation)) {
      throw new UnprocessableException("OBSERVATION_REQUIRED", "Indica qué hay que corregir.");
    }
    ClosingRequirement.Status before = r.getStatus();
    r.review(status, trim(observation), user.name(), clock.instant());
    log(op, r.getId(), "REVIEW", "\"" + r.getLabel() + "\": " + requirementStatusLabel(before) + " → " + requirementStatusLabel(status) + (isBlank(observation) ? "" : ". Observación: " + observation.strip()), user);
    refreshDocStatus(op, user);
    return detail(id, user);
  }

  public OperationDetail requestNotApplicable(UUID id, UUID requirementId, String justification, CurrentUser user) {
    ClosingOperation op = requireEditable(id, user);
    ClosingRequirement r = requireRequirementOpen(op, requirementId);
    if (r.isMandatory()) {
      throw new UnprocessableException("NA_NOT_ALLOWED", "Es un requisito legal obligatorio según la matriz: no se puede marcar como \"No aplica\".");
    }
    if (isBlank(justification)) {
      throw new UnprocessableException("NA_JUSTIFICATION_REQUIRED", "Justifica por qué no aplica.");
    }
    Instant now = clock.instant();
    r.requestNotApplicable(justification.strip(), user.name(), now);
    log(op, r.getId(), "NA_REQUESTED", "Se solicitó \"No aplica\" para \"" + r.getLabel() + "\": " + justification.strip(), user);
    // Si quien lo marca es el rol autorizado, queda validado en el mismo acto (y así consta).
    if (user.hasPermission(REVIEW)) {
      r.approveNotApplicable(user.name(), now);
      log(op, r.getId(), "NA_APPROVED", "\"No aplica\" validado por " + user.name() + ".", user);
    }
    refreshDocStatus(op, user);
    return detail(id, user);
  }

  public OperationDetail decideNotApplicable(UUID id, UUID requirementId, boolean approve, String observation, CurrentUser user) {
    ClosingOperation op = requireVisible(id, user);
    requirePermission(user, REVIEW);
    ClosingRequirement r = requireRequirementOpen(op, requirementId);
    if (r.getStatus() != ClosingRequirement.Status.NOT_APPLICABLE || r.getNaApprovedAt() != null) {
      throw new ConflictException("NA_NOT_PENDING", "Ese requisito no tiene un \"No aplica\" por validar.");
    }
    Instant now = clock.instant();
    if (approve) {
      r.approveNotApplicable(user.name(), now);
      log(op, r.getId(), "NA_APPROVED", "\"No aplica\" de \"" + r.getLabel() + "\" validado.", user);
    } else {
      if (isBlank(observation)) {
        throw new UnprocessableException("OBSERVATION_REQUIRED", "Indica por qué sí aplica.");
      }
      r.rejectNotApplicable(observation.strip(), user.name(), now);
      log(op, r.getId(), "NA_REJECTED", "\"No aplica\" de \"" + r.getLabel() + "\" rechazado: " + observation.strip(), user);
    }
    refreshDocStatus(op, user);
    return detail(id, user);
  }

  /** Agrega a la operación los requisitos nuevos de la matriz vigente (no quita los existentes). */
  public OperationDetail applyCurrentMatrix(UUID id, CurrentUser user) {
    ClosingOperation op = requireEditable(id, user);
    if (op.getDocStatus() == DocStatus.COMPLETE_VALIDATED) {
      throw new ConflictException("DOCS_LOCKED", "El expediente de cierre está completo y validado; reabre la revisión documental para agregar requisitos.");
    }
    int added = applyMatrix(op, clock.instant());
    log(op, null, "MATRIX_APPLIED", added == 0 ? "La operación ya tenía todos los requisitos de la matriz vigente." : "Se agregaron " + added + " requisito(s) nuevos de la matriz vigente.", user);
    refreshDocStatus(op, user);
    return detail(id, user);
  }

  /** Declara el expediente de cierre "Completo y validado". */
  public OperationDetail authorize(UUID id, CurrentUser user) {
    ClosingOperation op = requireVisible(id, user);
    requirePermission(user, REVIEW);
    List<String> blockers = blockers(op, requirements.findByOperationIdOrderBySortOrderAsc(op.getId()), parties.findByOperationId(op.getId()), latestByRequirement(op));
    if (!blockers.isEmpty()) {
      throw new UnprocessableException("AUTHORIZE_BLOCKED", "No se puede declarar completo y validado: " + String.join(" ", blockers));
    }
    op.authorize(user.id(), user.name(), clock.instant());
    log(op, null, "AUTHORIZED", "Expediente de cierre declarado Completo y validado por " + user.name() + ".", user);
    return detail(id, user);
  }

  public OperationDetail reopenDocReview(UUID id, String reason, CurrentUser user) {
    ClosingOperation op = requireVisible(id, user);
    requirePermission(user, REVIEW);
    if (op.getDocStatus() != DocStatus.COMPLETE_VALIDATED) {
      throw new ConflictException("DOCS_NOT_COMPLETE", "La revisión documental no está cerrada.");
    }
    if (isBlank(reason)) {
      throw new UnprocessableException("REASON_REQUIRED", "Indica el motivo de la reapertura.");
    }
    op.docStatus(DocStatus.IN_REVIEW, clock.instant());
    log(op, null, "DOCS_REOPENED", "Revisión documental reabierta: " + reason.strip(), user);
    return detail(id, user);
  }

  public URI evidenceUrl(UUID id, UUID evidenceId, CurrentUser user) {
    ClosingOperation op = requireVisible(id, user);
    RequirementEvidence e = evidences.findById(evidenceId).orElseThrow(() -> new NotFoundException("Evidencia", evidenceId));
    ClosingRequirement r = requireRequirement(op, e.getRequirementId());
    if (e.getStorageKey() == null) {
      throw new UnprocessableException("EVIDENCE_WITHOUT_FILE", "Esta evidencia no es un archivo cargado al cierre" + (e.getDocumentId() != null ? "; ábrela desde los documentos del expediente." : "."));
    }
    Objects.requireNonNull(r);
    return storage.generateTemporaryDownloadUrl(e.getStorageKey(), Duration.ofMinutes(10));
  }

  // --- Consultas --------------------------------------------------------------

  @Transactional(readOnly = true)
  public OperationList list(ListFilter f, CurrentUser user) {
    List<ClosingOperation> visible = operations.findAllByOrderByUpdatedAtDesc().stream().filter(o -> canSee(o, user)).toList();
    Map<UUID, List<OperationParty>> partiesByOp = parties.findByOperationIdIn(ids(visible)).stream().collect(Collectors.groupingBy(OperationParty::getOperationId));
    Map<UUID, List<ClosingRequirement>> reqByOp = requirements.findByOperationIdIn(ids(visible)).stream().collect(Collectors.groupingBy(ClosingRequirement::getOperationId));
    List<OperationItem> all = visible.stream().map(o -> item(o, partiesByOp.getOrDefault(o.getId(), List.of()), reqByOp.getOrDefault(o.getId(), List.of()))).toList();
    String q = f.q() == null ? "" : f.q().strip().toLowerCase(Locale.ROOT);
    List<OperationItem> items =
        all.stream()
            .filter(i -> f.status() == null || f.status().isBlank() || i.status().equals(f.status()))
            .filter(i -> f.docStatus() == null || f.docStatus().isBlank() || i.docStatus().equals(f.docStatus()))
            .filter(i -> f.origin() == null || f.origin().isBlank() || i.origin().equals(f.origin()))
            .filter(i -> f.advisorId() == null || f.advisorId().equals(i.advisorId()))
            .filter(i -> f.closedFrom() == null || (i.closedOn() != null && !i.closedOn().isBefore(f.closedFrom())))
            .filter(i -> f.closedTo() == null || (i.closedOn() != null && !i.closedOn().isAfter(f.closedTo())))
            .filter(i -> !Boolean.TRUE.equals(f.pendingDocs()) || !DocStatus.COMPLETE_VALIDATED.name().equals(i.docStatus()) && !Status.CANCELLED.name().equals(i.status()))
            .filter(i -> q.isEmpty() || (i.folio() + " " + i.propertyDescription() + " " + nz(i.sellerName()) + " " + nz(i.buyerName()) + " " + nz(i.advisorName())).toLowerCase(Locale.ROOT).contains(q))
            .toList();
    Totals totals =
        new Totals(
            all.size(),
            (int) all.stream().filter(i -> Status.CLOSED.name().equals(i.status())).count(),
            (int) all.stream().filter(i -> Status.CLOSED.name().equals(i.status()) && !DocStatus.COMPLETE_VALIDATED.name().equals(i.docStatus())).count(),
            (int) all.stream().filter(i -> Status.CLOSING_REVIEW.name().equals(i.status())).count(),
            (int) all.stream().filter(i -> Origin.EXTERNAL.name().equals(i.origin())).count());
    List<AdvisorOption> advisors =
        all.stream()
            .filter(i -> i.advisorId() != null)
            .collect(Collectors.toMap(OperationItem::advisorId, i -> nz(i.advisorName()), (a, b) -> a))
            .entrySet()
            .stream()
            .map(e -> new AdvisorOption(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(AdvisorOption::name))
            .toList();
    return new OperationList(items, totals, advisors);
  }

  @Transactional(readOnly = true)
  public List<OperationItem> ofExpediente(UUID expedienteId, CurrentUser user) {
    return operations.findByExpedienteIdOrderByCreatedAtDesc(expedienteId).stream()
        .filter(o -> canSee(o, user))
        .map(o -> item(o, parties.findByOperationId(o.getId()), requirements.findByOperationIdOrderBySortOrderAsc(o.getId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public OperationDetail detail(UUID id, CurrentUser user) {
    ClosingOperation op = requireVisible(id, user);
    List<OperationParty> ps = parties.findByOperationId(op.getId());
    List<ClosingRequirement> rs = requirements.findByOperationIdOrderBySortOrderAsc(op.getId());
    Map<UUID, List<RequirementEvidence>> evidenceByReq = evidences.findByRequirementIdInOrderByVersionNumberDesc(ids2(rs)).stream().collect(Collectors.groupingBy(RequirementEvidence::getRequirementId));
    Map<UUID, RequirementEvidence> latest = latestByRequirement(evidenceByReq);
    List<RequirementView> views = rs.stream().map(r -> view(r, evidenceByReq.getOrDefault(r.getId(), List.of()))).toList();
    String folio = null;
    List<ExpedienteDocumentOption> docs = List.of();
    if (op.getExpedienteId() != null) {
      folio = expedientes.ref(op.getExpedienteId()).map(ExpedienteRef::folio).orElse(null);
      docs =
          documents.requirementStatusOf(op.getExpedienteId()).stream()
              .filter(d -> d.currentVersionId() != null)
              .map(d -> new ExpedienteDocumentOption(d.documentId(), d.currentVersionId(), d.type() == null ? null : d.type().name(), d.name(), d.status()))
              .toList();
    }
    Set<UUID> present = rs.stream().map(ClosingRequirement::getTemplateId).filter(Objects::nonNull).collect(Collectors.toSet());
    int newOnes = (int) templates.findAllByOrderBySortOrderAsc().stream().filter(t -> t.appliesTo(op) && !present.contains(t.getId())).count();
    List<EventView> history =
        events.findByOperationIdOrderByOccurredAtDesc(op.getId()).stream()
            .map(e -> new EventView(e.getId(), e.getRequirementId(), e.getType(), e.getDetail(), e.getActorName(), e.getOccurredAt()))
            .toList();
    return new OperationDetail(
        item(op, ps, rs),
        op.getPropertyAddress(),
        op.getExternalSource(),
        op.getExternalOffice(),
        op.getExternalContact(),
        op.getGeneraParticipation(),
        op.getCancelReason(),
        op.getDocAuthorizedByName(),
        op.getDocAuthorizedAt(),
        op.getCreatedByName(),
        op.getCreatedAt(),
        folio,
        ps.stream()
            .sorted(Comparator.comparing(OperationParty::getSide))
            .map(p -> new PartyView(p.getSide().name(), p.getFullName(), p.getPersonType(), p.getRepresentativeName(), p.getRepresentativeRole(), p.getEmail(), p.getPhone(), p.getCounterpartContact(), p.getNotes(), represents(op.getRepresentedSide(), p.getSide())))
            .toList(),
        views,
        summary(op, rs, ps, latest),
        history,
        docs,
        matrix().isApproved(),
        newOnes);
  }

  // --- Reglas internas --------------------------------------------------------

  private int applyMatrix(ClosingOperation op, Instant now) {
    Set<UUID> present = requirements.findByOperationIdOrderBySortOrderAsc(op.getId()).stream().map(ClosingRequirement::getTemplateId).filter(Objects::nonNull).collect(Collectors.toSet());
    int added = 0;
    for (RequirementTemplate t : templates.findAllByOrderBySortOrderAsc()) {
      if (t.appliesTo(op) && !present.contains(t.getId())) {
        RequestedFrom from = t.getPartySide() == null ? RequestedFrom.INTERNAL : defaultRequestedFrom(op.getRepresentedSide(), t.getPartySide());
        requirements.save(new ClosingRequirement(op.getId(), t, from, now));
        added++;
      }
    }
    requirements.flush();
    return added;
  }

  /** Se piden al cliente de C21 Genera los papeles de su lado; los de la contraparte, por su asesor/oficina/tercero. */
  static RequestedFrom defaultRequestedFrom(Side represented, PartySide side) {
    return represents(represented, side) ? RequestedFrom.CLIENT : RequestedFrom.COUNTERPARTY;
  }

  static boolean represents(Side represented, PartySide side) {
    return represented == Side.BOTH || represented.name().equals(side.name());
  }

  private void refreshDocStatus(ClosingOperation op, CurrentUser user) {
    if (op.getDocStatus() == DocStatus.COMPLETE_VALIDATED) {
      return;
    }
    boolean anyActivity = requirements.findByOperationIdOrderBySortOrderAsc(op.getId()).stream().anyMatch(r -> r.getStatus() != ClosingRequirement.Status.PENDING);
    DocStatus next = anyActivity ? DocStatus.IN_REVIEW : DocStatus.PENDING;
    if (next != op.getDocStatus()) {
      op.docStatus(next, clock.instant());
      log(op, null, "DOC_STATUS", "Revisión documental: " + docStatusLabel(next) + ".", user);
    }
  }

  private List<String> blockers(ClosingOperation op, List<ClosingRequirement> rs, List<OperationParty> ps, Map<UUID, RequirementEvidence> latest) {
    List<String> out = new ArrayList<>();
    if (!matrix().isApproved()) {
      out.add("La matriz documental no ha sido aprobada por el área legal o responsable de cumplimiento.");
    }
    if (op.getStatus() == Status.CANCELLED) {
      out.add("La operación está cancelada.");
    }
    if (missingParty(ps, PartySide.SELLER)) out.add("Falta identificar al " + partyLabel(op, PartySide.SELLER) + ".");
    if (missingParty(ps, PartySide.BUYER)) out.add("Falta identificar al " + partyLabel(op, PartySide.BUYER) + ".");
    if (rs.isEmpty()) {
      out.add("La operación no tiene requisitos aplicables.");
    }
    long unsatisfied = rs.stream().filter(r -> !r.isSatisfied()).count();
    if (unsatisfied > 0) {
      out.add(unsatisfied + " requisito(s) sin validar o con \"No aplica\" sin aprobar.");
    }
    long expired = rs.stream().filter(r -> r.getStatus() == ClosingRequirement.Status.VALIDATED && isExpired(r, latest.get(r.getId()))).count();
    if (expired > 0) {
      out.add(expired + " evidencia(s) validada(s) ya no están vigentes.");
    }
    return out;
  }

  private Summary summary(ClosingOperation op, List<ClosingRequirement> rs, List<OperationParty> ps, Map<UUID, RequirementEvidence> latest) {
    Map<String, Integer> bySide = new java.util.LinkedHashMap<>();
    bySide.put("SELLER", 0);
    bySide.put("BUYER", 0);
    bySide.put("OPERATION", 0);
    Map<String, Integer> bySection = new java.util.LinkedHashMap<>();
    for (ClosingRequirement.Section s : ClosingRequirement.Section.values()) bySection.put(s.name(), 0);
    int needsUpdate = 0;
    int corrections = 0;
    int naPending = 0;
    int satisfied = 0;
    for (ClosingRequirement r : rs) {
      RequirementEvidence ev = latest.get(r.getId());
      boolean expired = isExpired(r, ev);
      if (expired) needsUpdate++;
      if (r.getStatus() == ClosingRequirement.Status.NEEDS_CORRECTION) corrections++;
      if (r.getStatus() == ClosingRequirement.Status.NOT_APPLICABLE && r.getNaApprovedAt() == null) naPending++;
      if (r.isSatisfied() && !expired) {
        satisfied++;
      } else {
        bySide.merge(r.getPartySide() == null ? "OPERATION" : r.getPartySide().name(), 1, Integer::sum);
        bySection.merge(r.getSection().name(), 1, Integer::sum);
      }
    }
    return new Summary(bySide, bySection, needsUpdate, corrections, naPending, satisfied, rs.size(), blockers(op, rs, ps, latest));
  }

  private RequirementView view(ClosingRequirement r, List<RequirementEvidence> history) {
    RequirementEvidence current = history.isEmpty() ? null : history.get(0);
    boolean expired = isExpired(r, current);
    boolean dateMissing = r.getValidityDays() != null && current != null && current.getDocumentDate() == null;
    return new RequirementView(
        r.getId(),
        r.getSection().name(),
        r.getPartySide() == null ? null : r.getPartySide().name(),
        r.getLabel(),
        r.getDescription(),
        r.isMandatory(),
        r.getValidityDays(),
        r.getDocumentTypeHint(),
        r.getStatus().name(),
        r.getRequestedFrom() == null ? null : r.getRequestedFrom().name(),
        r.getRequestedAt(),
        r.getFollowUpOwner(),
        r.getDueDate(),
        r.getObservation(),
        r.getReviewedByName(),
        r.getReviewedAt(),
        r.getNaJustification(),
        r.getNaRequestedByName(),
        r.getNaRequestedAt(),
        r.getNaApprovedByName(),
        r.getNaApprovedAt(),
        r.isSatisfied() && !expired,
        expired,
        dateMissing,
        current == null ? null : evidenceView(r, current),
        history.stream().skip(1).map(e -> evidenceView(r, e)).toList());
  }

  private EvidenceView evidenceView(ClosingRequirement r, RequirementEvidence e) {
    LocalDate expires = r.getValidityDays() != null && e.getDocumentDate() != null ? e.getDocumentDate().plusDays(r.getValidityDays()) : null;
    return new EvidenceView(e.getId(), e.getVersionNumber(), e.getKind().name(), e.getFilename(), e.getSizeBytes(), e.getDocumentId(), e.getDocumentVersionId(), e.getDocumentName(), e.getNote(), e.getDocumentDate(), expires, e.getAttachedByName(), e.getAttachedAt());
  }

  private boolean isExpired(ClosingRequirement r, RequirementEvidence e) {
    return r.getValidityDays() != null && e != null && e.getDocumentDate() != null && e.getDocumentDate().plusDays(r.getValidityDays()).isBefore(today());
  }

  private OperationItem item(ClosingOperation o, List<OperationParty> ps, List<ClosingRequirement> rs) {
    int satisfiedCount = (int) rs.stream().filter(ClosingRequirement::isSatisfied).count();
    return new OperationItem(
        o.getId(),
        o.getFolio(),
        o.getOrigin().name(),
        o.getExpedienteId(),
        o.getOperationType().name(),
        o.getPropertyDescription(),
        o.getRepresentedSide().name(),
        o.getAdvisorId(),
        o.getAdvisorName(),
        o.getStatus().name(),
        o.getDocStatus().name(),
        displayStatus(o),
        o.getClosedOn(),
        o.getClosedByName(),
        o.getAmount(),
        o.getCurrency(),
        ps.stream().filter(p -> p.getSide() == PartySide.SELLER).map(OperationParty::getFullName).findFirst().orElse(null),
        ps.stream().filter(p -> p.getSide() == PartySide.BUYER).map(OperationParty::getFullName).findFirst().orElse(null),
        rs.size(),
        satisfiedCount,
        rs.size() - satisfiedCount,
        o.getDocDueDate(),
        o.getDocFollowUpOwner(),
        o.getUpdatedAt());
  }

  static String displayStatus(ClosingOperation o) {
    return switch (o.getStatus()) {
      case IN_PROCESS -> "En proceso";
      case CLOSING_REVIEW -> "En revisión de cierre";
      case CLOSED -> o.isClosedWithPendingDocs() ? "Cerrada — Documentación pendiente" : "Cerrada";
      case CANCELLED -> "Cancelada";
    };
  }

  private void upsertParty(ClosingOperation op, PartySide side, PartyInput in) {
    if (in == null || isBlank(in.fullName())) {
      return;
    }
    OperationParty party = parties.findByOperationId(op.getId()).stream().filter(p -> p.getSide() == side).findFirst().orElseGet(() -> new OperationParty(op.getId(), side));
    party.update(in.fullName().strip(), trim(in.personType()), trim(in.representativeName()), trim(in.representativeRole()), trim(in.email()), trim(in.phone()), trim(in.counterpartContact()), trim(in.notes()));
    parties.save(party);
  }

  private static boolean missingParty(List<OperationParty> ps, PartySide side) {
    return ps.stream().noneMatch(p -> p.getSide() == side && !isBlank(p.getFullName()));
  }

  private ClosingOperation requireVisible(UUID id, CurrentUser user) {
    ClosingOperation op = operations.findById(id).orElseThrow(() -> new NotFoundException("Operación", id));
    if (!canSee(op, user)) {
      throw new NotFoundException("Operación", id);
    }
    return op;
  }

  private ClosingOperation requireEditable(UUID id, CurrentUser user) {
    requirePermission(user, MANAGE);
    ClosingOperation op = requireVisible(id, user);
    if (op.getStatus() == Status.CANCELLED) {
      throw new ConflictException("OPERATION_CANCELLED", "La operación está cancelada; reábrela para modificarla.");
    }
    return op;
  }

  private ClosingRequirement requireRequirement(ClosingOperation op, UUID requirementId) {
    return requirements.findById(requirementId).filter(r -> r.getOperationId().equals(op.getId())).orElseThrow(() -> new NotFoundException("Requisito", requirementId));
  }

  /** Con el expediente completo y validado, los requisitos no cambian hasta reabrir la revisión (queda constancia). */
  private ClosingRequirement requireRequirementOpen(ClosingOperation op, UUID requirementId) {
    if (op.getDocStatus() == DocStatus.COMPLETE_VALIDATED) {
      throw new ConflictException("DOCS_LOCKED", "El expediente de cierre está completo y validado; el rol autorizado debe reabrir la revisión documental para modificarlo.");
    }
    return requireRequirement(op, requirementId);
  }

  private static boolean canSee(ClosingOperation o, CurrentUser user) {
    return user.hasPermission(VIEW_ALL) || user.id().equals(o.getAdvisorId()) || user.id().equals(o.getCreatedByUserId());
  }

  private static void requirePermission(CurrentUser user, String permission) {
    if (!user.hasPermission(permission)) {
      throw new org.springframework.security.access.AccessDeniedException("Tu rol no tiene permiso para esta acción.");
    }
  }

  private MatrixSettings matrix() {
    return matrixSettings.findById((short) 1).orElseThrow(() -> new IllegalStateException("Falta closing_matrix_settings"));
  }

  private Map<UUID, RequirementEvidence> latestByRequirement(ClosingOperation op) {
    List<ClosingRequirement> rs = requirements.findByOperationIdOrderBySortOrderAsc(op.getId());
    return latestByRequirement(evidences.findByRequirementIdInOrderByVersionNumberDesc(ids2(rs)).stream().collect(Collectors.groupingBy(RequirementEvidence::getRequirementId)));
  }

  private static Map<UUID, RequirementEvidence> latestByRequirement(Map<UUID, List<RequirementEvidence>> byReq) {
    return byReq.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
  }

  private void log(ClosingOperation op, UUID requirementId, String type, String detail, CurrentUser user) {
    events.save(new OperationEvent(op.getId(), requirementId, type, detail, user.id(), user.name(), clock.instant()));
    op.touch(clock.instant());
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(MEXICO_CITY));
  }

  private static void validateAmount(BigDecimal amount, String currency) {
    if (amount != null && amount.signum() <= 0) {
      throw new UnprocessableException("AMOUNT_INVALID", "El monto debe ser mayor a cero.");
    }
    if (amount != null && (currency == null || currency.isBlank())) {
      throw new UnprocessableException("CURRENCY_REQUIRED", "Indica la moneda del monto.");
    }
    if (currency != null && !currency.isBlank() && !Set.of("MXN", "USD").contains(currency.strip().toUpperCase(Locale.ROOT))) {
      throw new UnprocessableException("CURRENCY_INVALID", "La moneda debe ser MXN o USD.");
    }
  }

  private static String normalizeCurrency(String currency) {
    return currency == null || currency.isBlank() ? null : currency.strip().toUpperCase(Locale.ROOT);
  }

  static String partyLabel(ClosingOperation op, PartySide side) {
    if (op.getOperationType() == Type.RENT) {
      return side == PartySide.SELLER ? "arrendador" : "arrendatario";
    }
    return side == PartySide.SELLER ? "vendedor" : "comprador";
  }

  private static String sideLabel(Side side) {
    return switch (side) {
      case SELLER -> "vendedor";
      case BUYER -> "comprador";
      case BOTH -> "ambas partes";
    };
  }

  private static String requestedFromLabel(RequestedFrom from) {
    if (from == null) return "sin definir";
    return switch (from) {
      case CLIENT -> "el cliente de C21 Genera";
      case COUNTERPARTY -> "la contraparte (por su asesor)";
      case OFFICE -> "la oficina participante";
      case THIRD_PARTY -> "un tercero";
      case INTERNAL -> "el equipo de C21 Genera";
    };
  }

  static String requirementStatusLabel(ClosingRequirement.Status s) {
    return switch (s) {
      case PENDING -> "Pendiente";
      case RECEIVED -> "Recibido";
      case IN_REVIEW -> "En revisión";
      case VALIDATED -> "Validado";
      case NEEDS_CORRECTION -> "Requiere corrección";
      case NOT_APPLICABLE -> "No aplica";
    };
  }

  private static String docStatusLabel(DocStatus s) {
    return switch (s) {
      case PENDING -> "Pendiente";
      case IN_REVIEW -> "En revisión";
      case COMPLETE_VALIDATED -> "Completa y validada";
    };
  }

  private static Collection<UUID> ids(List<ClosingOperation> ops) {
    return ops.stream().map(ClosingOperation::getId).toList();
  }

  private static Collection<UUID> ids2(List<ClosingRequirement> rs) {
    return rs.stream().map(ClosingRequirement::getId).toList();
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String trim(String s) {
    return isBlank(s) ? null : s.strip();
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (!isBlank(v)) return v.strip();
    }
    return null;
  }

  private static String joinNonBlank(String a, String b) {
    String joined = ((a == null ? "" : a) + " " + (b == null ? "" : b)).strip();
    return joined.isEmpty() ? null : joined;
  }

  private static String cleanName(String name) {
    if (isBlank(name)) return "evidencia";
    String base = name.replace('\\', '/');
    base = base.substring(base.lastIndexOf('/') + 1);
    return base.length() > 200 ? base.substring(0, 200) : base;
  }
}
