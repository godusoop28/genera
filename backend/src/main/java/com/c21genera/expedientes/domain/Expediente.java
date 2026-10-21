package com.c21genera.expedientes.domain;

import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.OperationType;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.PropertyLegalStatus;
import com.c21genera.expedientes.SignerCharacter;
import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "expediente")
public class Expediente extends AuditableEntity {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 32)
  private String folio;

  @Column(nullable = false)
  private String ownerDisplayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ExpedienteStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PersonType personType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private SignerCharacter signerCharacter;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AccreditationType accreditationType;

  @Column(nullable = false)
  private boolean condominiumRegime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private PropertyCaseType propertyCaseType;

  // Control de Cambios #26: tipo de inmueble del catálogo, con el nombre tal
  // como estaba al elegirlo. Sin subcategoría = pendiente de clasificación
  // (registros anteriores al catálogo que no decían de qué tipo eran).
  private UUID propertyCategoryId;

  @Column(length = 80)
  private String propertyCategoryName;

  private UUID propertySubtypeId;

  @Column(length = 80)
  private String propertySubtypeName;

  @Column(length = 160)
  private String propertyTypeDetail;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private OperationType operationType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private PropertyLegalStatus declaredLegalStatus;

  private String propertyAddress;

  /** Referencia corta que captura el asesor para que el cliente reconozca su liga; no es el domicilio legal. */
  @Column(length = 120)
  private String propertyReference;

  @Column(nullable = false)
  private boolean allRequiredDocumentsUploaded;

  private String decisionReason;

  private UUID decidedByUserId;

  private Instant decidedAt;

  @Column(nullable = false)
  private UUID createdByUserId;

  /**
   * Control de Cambios #24: asesor responsable del inmueble. Se elige al dar de alta y solo el
   * administrador o directivo lo cambia; el nombre se guarda para listar y reportar sin consultar
   * el módulo identity por cada expediente.
   */
  private UUID assignedAdvisorId;

  @Column(length = 160)
  private String assignedAdvisorName;

  /** Ver {@link com.c21genera.expedientes.LegalDetails}; lo serializa ExpedienteService. */
  private String legalDetailsJson;

  protected Expediente() {}

  public Expediente(
      String folio,
      String ownerDisplayName,
      PersonType personType,
      SignerCharacter signerCharacter,
      AccreditationType accreditationType,
      boolean condominiumRegime,
      PropertyCaseType propertyCaseType,
      OperationType operationType,
      PropertyLegalStatus declaredLegalStatus,
      String propertyAddress,
      String propertyReference,
      UUID createdByUserId,
      UUID assignedAdvisorId,
      String assignedAdvisorName) {
    this.id = UUID.randomUUID();
    this.folio = folio;
    this.ownerDisplayName = ownerDisplayName;
    this.status = ExpedienteStatus.DRAFT;
    this.personType = personType;
    this.signerCharacter = signerCharacter;
    this.accreditationType = accreditationType;
    this.condominiumRegime = condominiumRegime;
    this.propertyCaseType = propertyCaseType;
    this.operationType = operationType;
    this.declaredLegalStatus = declaredLegalStatus;
    this.propertyAddress = propertyAddress;
    this.propertyReference = propertyReference;
    this.createdByUserId = createdByUserId;
    this.assignedAdvisorId = assignedAdvisorId;
    this.assignedAdvisorName = assignedAdvisorName;
  }

  /** Control de Cambios #26: categoría → subcategoría elegidas en el catálogo (detail: solo para "Otro"). */
  public record PropertyClassification(UUID categoryId, String categoryName, UUID subtypeId, String subtypeName, String detail) {}

  public PropertyClassification propertyClassification() {
    return new PropertyClassification(propertyCategoryId, propertyCategoryName, propertySubtypeId, propertySubtypeName, propertyTypeDetail);
  }

  /** Solo la clasificación; el tratamiento de documentos y contrato (propertyCaseType) va en {@link Configuration}. */
  public void classifyProperty(PropertyClassification classification) {
    this.propertyCategoryId = classification.categoryId();
    this.propertyCategoryName = classification.categoryName();
    this.propertySubtypeId = classification.subtypeId();
    this.propertySubtypeName = classification.subtypeName();
    this.propertyTypeDetail = classification.detail();
  }

  public record Configuration(
      String ownerDisplayName,
      PersonType personType,
      SignerCharacter signerCharacter,
      AccreditationType accreditationType,
      boolean condominiumRegime,
      PropertyCaseType propertyCaseType,
      OperationType operationType,
      PropertyLegalStatus declaredLegalStatus,
      String propertyAddress) {}

  public Configuration configuration() {
    return new Configuration(
        ownerDisplayName,
        personType,
        signerCharacter,
        accreditationType,
        condominiumRegime,
        propertyCaseType,
        operationType,
        declaredLegalStatus,
        propertyAddress);
  }

  /**
   * Corrección controlada de los datos principales (ver
   * ExpedienteService#correctConfiguration): el historial y la nueva versión
   * del contrato los gestiona el servicio; aquí solo se protege que no se
   * modifique un expediente cuyo contrato ya se firmó o que ya se decidió.
   */
  public void reconfigure(Configuration configuration) {
    ensureCorrectable();
    this.ownerDisplayName = configuration.ownerDisplayName();
    this.personType = configuration.personType();
    this.signerCharacter = configuration.signerCharacter();
    this.accreditationType = configuration.accreditationType();
    this.condominiumRegime = configuration.condominiumRegime();
    this.propertyCaseType = configuration.propertyCaseType();
    this.operationType = configuration.operationType();
    this.declaredLegalStatus = configuration.declaredLegalStatus();
    this.propertyAddress = configuration.propertyAddress();
  }

  /**
   * Control de Cambios #24: cambiar de asesor no toca datos del contrato, así que se permite en
   * cualquier etapa (también con el contrato firmado o el inmueble decidido).
   */
  public void assignAdvisor(UUID advisorId, String advisorName) {
    this.assignedAdvisorId = advisorId;
    this.assignedAdvisorName = advisorName;
  }

  public void replaceLegalDetailsJson(String json) {
    ensureCorrectable();
    this.legalDetailsJson = json;
  }

  public boolean isCorrectable() {
    return switch (status) {
      case CONTRACT_SIGNED, PROPERTY_ACCEPTED, PROPERTY_REJECTED, CLOSED -> false;
      default -> true;
    };
  }

  public void ensureCorrectable() {
    if (!isCorrectable()) {
      throw new com.c21genera.shared.domain.ConflictException(
          "EXPEDIENTE_LOCKED",
          "Este expediente ya no admite correcciones: el contrato ya se firmó o el inmueble ya fue decidido.");
    }
  }

  public void transitionTo(ExpedienteStatus target) {
    ExpedienteStateMachine.ensureAllowed(this.status, target);
    this.status = target;
  }

  /** Transición idempotente: si ya está en el estado objetivo o más adelante, no hace nada. */
  public boolean transitionToIfAllowed(ExpedienteStatus target) {
    if (this.status == target) {
      return false;
    }
    if (!ExpedienteStateMachine.isAllowed(this.status, target)) {
      return false;
    }
    this.status = target;
    return true;
  }

  /**
   * Control de Cambios #20: el contrato se firmó fuera del sistema (DocuSign o en papel). Puede
   * llegar desde cualquier etapa previa (p. ej. una operación anterior al sistema); un expediente
   * ya decidido o cerrado no cambia.
   */
  public void markContractSignedExternally() {
    if (this.status.ordinal() < ExpedienteStatus.CONTRACT_SIGNED.ordinal()) {
      this.status = ExpedienteStatus.CONTRACT_SIGNED;
    }
  }

  public void acceptProperty(UUID decidedByUserId, Instant when) {
    ExpedienteStateMachine.ensureAllowed(this.status, ExpedienteStatus.PROPERTY_ACCEPTED);
    this.status = ExpedienteStatus.PROPERTY_ACCEPTED;
    this.decidedByUserId = decidedByUserId;
    this.decidedAt = when;
  }

  public void rejectProperty(UUID decidedByUserId, String reason, Instant when) {
    ExpedienteStateMachine.ensureAllowed(this.status, ExpedienteStatus.PROPERTY_REJECTED);
    this.status = ExpedienteStatus.PROPERTY_REJECTED;
    this.decisionReason = reason;
    this.decidedByUserId = decidedByUserId;
    this.decidedAt = when;
  }

  public void close() {
    ExpedienteStateMachine.ensureAllowed(this.status, ExpedienteStatus.CLOSED);
    this.status = ExpedienteStatus.CLOSED;
  }

  public void markAllRequiredDocumentsUploaded() {
    this.allRequiredDocumentsUploaded = true;
  }

  /** Apareció un documento obligatorio sin cargar (p. ej. tras una corrección). */
  public void markRequiredDocumentsPending() {
    this.allRequiredDocumentsUploaded = false;
  }

  public boolean isAllRequiredDocumentsUploaded() {
    return allRequiredDocumentsUploaded;
  }

  public String getDecisionReason() {
    return decisionReason;
  }

  public UUID getDecidedByUserId() {
    return decidedByUserId;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getFolio() {
    return folio;
  }

  public String getOwnerDisplayName() {
    return ownerDisplayName;
  }

  public ExpedienteStatus getStatus() {
    return status;
  }

  public PersonType getPersonType() {
    return personType;
  }

  public SignerCharacter getSignerCharacter() {
    return signerCharacter;
  }

  public AccreditationType getAccreditationType() {
    return accreditationType;
  }

  public boolean isCondominiumRegime() {
    return condominiumRegime;
  }

  public PropertyCaseType getPropertyCaseType() {
    return propertyCaseType;
  }

  public UUID getPropertyCategoryId() {
    return propertyCategoryId;
  }

  public String getPropertyCategoryName() {
    return propertyCategoryName;
  }

  public UUID getPropertySubtypeId() {
    return propertySubtypeId;
  }

  public String getPropertySubtypeName() {
    return propertySubtypeName;
  }

  public String getPropertyTypeDetail() {
    return propertyTypeDetail;
  }

  /** "Habitacional → Casa en condominio", "Especiales → Otro: Capilla" o "Terrenos → Pendiente de clasificación". */
  public String propertyTypeLabel() {
    if (propertyCategoryName == null) {
      return null;
    }
    if (propertySubtypeName == null) {
      return propertyCategoryName + " → Pendiente de clasificación";
    }
    if (propertyTypeDetail != null && !propertyTypeDetail.isBlank()) {
      return propertyCategoryName + " → " + propertySubtypeName.replaceAll("\\s*—.*$", "") + ": " + propertyTypeDetail;
    }
    return propertyCategoryName + " → " + propertySubtypeName;
  }

  public OperationType getOperationType() {
    return operationType;
  }

  public PropertyLegalStatus getDeclaredLegalStatus() {
    return declaredLegalStatus;
  }

  public String getPropertyAddress() {
    return propertyAddress;
  }

  public String getPropertyReference() {
    return propertyReference;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public UUID getAssignedAdvisorId() {
    return assignedAdvisorId;
  }

  public String getAssignedAdvisorName() {
    return assignedAdvisorName;
  }

  public String getLegalDetailsJson() {
    return legalDetailsJson;
  }
}
