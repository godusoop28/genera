package com.c21genera.leads.domain;

import com.c21genera.leads.AssignmentMethod;
import com.c21genera.leads.AssignmentStatus;
import com.c21genera.leads.LeadInterest;
import com.c21genera.leads.LeadSource;
import com.c21genera.leads.LeadStage;
import com.c21genera.leads.LeadTemperature;
import com.c21genera.leads.LostReason;
import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "lead")
public class Lead extends AuditableEntity {

  @Id
  private UUID id;

  @Column(nullable = false)
  private String name;

  private String phone;

  private String phoneDigits;

  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LeadSource source;

  private String sourceDetail;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LeadInterest interest;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LeadStage stage;

  private Long propertyOnline21Id;

  private String propertyKey;

  private String propertyTitle;

  private UUID advisorUserId;

  @Enumerated(EnumType.STRING)
  private AssignmentMethod assignmentMethod;

  private Instant assignedAt;

  private String summary;

  private BigDecimal budget;

  private String lostReason;

  private UUID conversationId;

  private String requestKey;

  private Long online21ProspectId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SyncStatus online21SyncStatus = SyncStatus.NOT_REQUIRED;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SyncStatus advisorNoticeStatus = SyncStatus.NOT_REQUIRED;

  @Column(nullable = false)
  private Instant lastActivityAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AssignmentStatus assignmentStatus = AssignmentStatus.UNASSIGNED;

  private String assignmentRule;

  private UUID assignmentShiftId;

  private String pendingReason;

  private Instant pendingSince;

  private Instant nextShiftAt;

  private UUID propertyAdvisorUserId;

  private Long propertyAdvisorOnline21Id;

  private String coordinationNote;

  // Control de Cambios #29: opción precisa del catálogo de fuentes.
  private String sourceCode;

  // Control de Cambios #30: ficha completa del prospecto.
  private LocalDate firstContactOn;

  private UUID propertyCategoryId;

  private UUID propertySubtypeId;

  private String propertyTypeLabel;

  private String zone;

  private BigDecimal budgetMin;

  private String desiredFeatures;

  @Enumerated(EnumType.STRING)
  private LeadTemperature temperature;

  private Integer closeProbability;

  private LocalDate expectedCloseOn;

  @Enumerated(EnumType.STRING)
  private LostReason lostReasonCode;

  private Instant stageEnteredAt;

  private UUID expedienteId;

  private String expedienteFolio;

  protected Lead() {}

  public Lead(String name, String phone, LeadSource source, String sourceDetail, LeadInterest interest, Instant now) {
    this.id = UUID.randomUUID();
    this.name = name == null || name.isBlank() ? "Sin nombre" : name.trim();
    setPhone(phone);
    this.source = source;
    this.sourceDetail = sourceDetail;
    this.interest = interest == null ? LeadInterest.OTHER : interest;
    this.stage = LeadStage.NEW;
    this.lastActivityAt = now;
    this.sourceCode = source == null ? null : source.defaultCode();
    this.firstContactOn = LocalDate.ofInstant(now, BUSINESS_ZONE);
    this.stageEnteredAt = now;
  }

  public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Mexico_City");

  /** Solo dígitos y sin lada de país (52/521): así se reconoce al mismo contacto aunque cambie el formato. */
  public static String digitsOf(String phone) {
    if (phone == null) {
      return null;
    }
    String digits = phone.replaceAll("\\D", "");
    if (digits.length() > 10 && digits.startsWith("521")) {
      digits = digits.substring(3);
    } else if (digits.length() > 10 && digits.startsWith("52")) {
      digits = digits.substring(2);
    }
    return digits.isEmpty() ? null : digits;
  }

  public void setPhone(String phone) {
    this.phone = phone == null || phone.isBlank() ? null : phone.trim();
    this.phoneDigits = digitsOf(phone);
  }

  public void setEmail(String email) {
    this.email = email == null || email.isBlank() ? null : email.trim();
  }

  public void attachProperty(Long online21Id, String key, String title) {
    this.propertyOnline21Id = online21Id;
    this.propertyKey = key;
    this.propertyTitle = title;
  }

  /** Queda atendido por el asesor (una vez asignado no se cambia al terminar su jornada). */
  public void assignTo(UUID advisorUserId, AssignmentMethod method, String rule, UUID shiftId, Instant now) {
    this.advisorUserId = advisorUserId;
    this.assignmentMethod = method;
    this.assignmentRule = rule;
    this.assignmentShiftId = shiftId;
    this.assignmentStatus = AssignmentStatus.ASSIGNED;
    this.assignedAt = now;
    this.pendingReason = null;
    this.pendingSince = null;
    this.nextShiftAt = null;
    this.advisorNoticeStatus = SyncStatus.PENDING;
    touch(now);
  }

  /**
   * En espera: PENDING_OWNER conserva al asesor de la propiedad como
   * responsable (sin contar todavía en su límite ni avisarle); PENDING_SCHEDULE
   * queda sin asesor hasta que empiece un turno elegible.
   */
  public void pending(AssignmentStatus status, UUID reservedFor, String rule, String reason, Instant nextShiftAt, Instant now) {
    this.assignmentStatus = status;
    this.advisorUserId = reservedFor;
    this.assignmentMethod = reservedFor != null ? AssignmentMethod.PROPERTY_OWNER : null;
    this.assignmentRule = rule;
    this.assignmentShiftId = null;
    this.assignedAt = null;
    this.pendingReason = reason;
    if (this.pendingSince == null) {
      this.pendingSince = now;
    }
    this.nextShiftAt = nextShiftAt;
    this.advisorNoticeStatus = SyncStatus.NOT_REQUIRED;
  }

  public void markUnassigned(String reason) {
    this.assignmentStatus = AssignmentStatus.UNASSIGNED;
    this.pendingReason = reason;
  }

  public void propertyOwner(UUID userId, Long online21Id) {
    this.propertyAdvisorUserId = userId;
    this.propertyAdvisorOnline21Id = online21Id;
  }

  public void coordination(String note) {
    this.coordinationNote = note == null || note.isBlank() ? null : (note.length() > 1000 ? note.substring(0, 1000) : note);
  }

  public void changeStage(LeadStage stage, String lostReason, Instant now) {
    changeStage(stage, null, lostReason, now);
  }

  public void changeStage(LeadStage stage, LostReason reasonCode, String lostReason, Instant now) {
    this.stage = stage;
    this.lostReason = stage == LeadStage.LOST ? lostReason : null;
    this.lostReasonCode = stage == LeadStage.LOST ? (reasonCode == null ? LostReason.OTHER : reasonCode) : null;
    this.stageEnteredAt = now;
    touch(now);
  }

  /** Control de Cambios #29: fuente precisa (el canal general se deriva de ella). */
  public void source(LeadSource channel, String code, String detail) {
    this.source = channel;
    this.sourceCode = code;
    this.sourceDetail = detail == null || detail.isBlank() ? null : detail.strip();
  }

  public void rename(String name) {
    if (name != null && !name.isBlank()) {
      this.name = name.strip();
    }
  }

  public void interest(LeadInterest interest) {
    if (interest != null) {
      this.interest = interest;
    }
  }

  /** Control de Cambios #30: lo que busca el prospecto. */
  public void wishes(UUID categoryId, UUID subtypeId, String typeLabel, String zone, BigDecimal budgetMin, BigDecimal budgetMax, String features) {
    this.propertyCategoryId = categoryId;
    this.propertySubtypeId = subtypeId;
    this.propertyTypeLabel = blankToNull(typeLabel);
    this.zone = blankToNull(zone);
    this.budgetMin = budgetMin;
    this.budget = budgetMax;
    this.desiredFeatures = blankToNull(features);
  }

  /** Control de Cambios #30: estimación del asesor. */
  public void outlook(LeadTemperature temperature, Integer probability, LocalDate expectedCloseOn, LocalDate firstContactOn) {
    this.temperature = temperature;
    this.closeProbability = probability == null ? null : Math.max(0, Math.min(100, probability));
    this.expectedCloseOn = expectedCloseOn;
    if (firstContactOn != null) {
      this.firstContactOn = firstContactOn;
    }
  }

  public void replaceSummary(String summary) {
    this.summary = summary == null || summary.isBlank() ? null : (summary.length() > 2000 ? summary.substring(0, 2000) : summary);
  }

  /** Control de Cambios #30 parte 7: expediente del inmueble (null = desvincular). */
  public void linkExpediente(UUID expedienteId, String folio) {
    this.expedienteId = expedienteId;
    this.expedienteFolio = expedienteId == null ? null : folio;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  public void touch(Instant now) {
    this.lastActivityAt = now;
  }

  public void describe(String summary, BigDecimal budget) {
    if (summary != null && !summary.isBlank()) {
      this.summary = summary.length() > 2000 ? summary.substring(0, 2000) : summary;
    }
    if (budget != null) {
      this.budget = budget;
    }
  }

  public void linkConversation(UUID conversationId, String requestKey) {
    this.conversationId = conversationId;
    this.requestKey = requestKey;
  }

  public void markOnline21(SyncStatus status, Long prospectId) {
    this.online21SyncStatus = status;
    if (prospectId != null) {
      this.online21ProspectId = prospectId;
    }
  }

  public void markAdvisorNotice(SyncStatus status) {
    this.advisorNoticeStatus = status;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getPhone() {
    return phone;
  }

  public String getPhoneDigits() {
    return phoneDigits;
  }

  public String getEmail() {
    return email;
  }

  public LeadSource getSource() {
    return source;
  }

  public String getSourceDetail() {
    return sourceDetail;
  }

  public LeadInterest getInterest() {
    return interest;
  }

  public LeadStage getStage() {
    return stage;
  }

  public Long getPropertyOnline21Id() {
    return propertyOnline21Id;
  }

  public String getPropertyKey() {
    return propertyKey;
  }

  public String getPropertyTitle() {
    return propertyTitle;
  }

  public UUID getAdvisorUserId() {
    return advisorUserId;
  }

  public AssignmentMethod getAssignmentMethod() {
    return assignmentMethod;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }

  public String getSummary() {
    return summary;
  }

  public BigDecimal getBudget() {
    return budget;
  }

  public String getLostReason() {
    return lostReason;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public String getRequestKey() {
    return requestKey;
  }

  public Long getOnline21ProspectId() {
    return online21ProspectId;
  }

  public SyncStatus getOnline21SyncStatus() {
    return online21SyncStatus;
  }

  public SyncStatus getAdvisorNoticeStatus() {
    return advisorNoticeStatus;
  }

  public Instant getLastActivityAt() {
    return lastActivityAt;
  }

  public AssignmentStatus getAssignmentStatus() {
    return assignmentStatus;
  }

  public String getAssignmentRule() {
    return assignmentRule;
  }

  public UUID getAssignmentShiftId() {
    return assignmentShiftId;
  }

  public String getPendingReason() {
    return pendingReason;
  }

  public Instant getPendingSince() {
    return pendingSince;
  }

  public Instant getNextShiftAt() {
    return nextShiftAt;
  }

  public UUID getPropertyAdvisorUserId() {
    return propertyAdvisorUserId;
  }

  public Long getPropertyAdvisorOnline21Id() {
    return propertyAdvisorOnline21Id;
  }

  public String getCoordinationNote() {
    return coordinationNote;
  }

  public String getSourceCode() {
    return sourceCode;
  }

  public LocalDate getFirstContactOn() {
    return firstContactOn;
  }

  public UUID getPropertyCategoryId() {
    return propertyCategoryId;
  }

  public UUID getPropertySubtypeId() {
    return propertySubtypeId;
  }

  public String getPropertyTypeLabel() {
    return propertyTypeLabel;
  }

  public String getZone() {
    return zone;
  }

  public BigDecimal getBudgetMin() {
    return budgetMin;
  }

  public String getDesiredFeatures() {
    return desiredFeatures;
  }

  public LeadTemperature getTemperature() {
    return temperature;
  }

  public Integer getCloseProbability() {
    return closeProbability;
  }

  public LocalDate getExpectedCloseOn() {
    return expectedCloseOn;
  }

  public LostReason getLostReasonCode() {
    return lostReasonCode;
  }

  public Instant getStageEnteredAt() {
    return stageEnteredAt;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public String getExpedienteFolio() {
    return expedienteFolio;
  }
}
