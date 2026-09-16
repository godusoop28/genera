package com.c21genera.expedientes.application;

import com.c21genera.expedientes.ExpedienteEvents.ExpedienteCreated;
import com.c21genera.expedientes.ExpedienteEvents.ExpedienteRequirementsChanged;
import com.c21genera.expedientes.ExpedienteEvents.PropertyAccepted;
import com.c21genera.expedientes.ExpedienteEvents.PropertyRejected;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.expedientes.ExpedienteSummary.ParticipantView;
import com.c21genera.expedientes.RequiredDocumentSpec;
import com.c21genera.expedientes.domain.AccreditationType;
import com.c21genera.expedientes.domain.DocumentRequirementPolicy;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.domain.ExpedienteParticipant;
import com.c21genera.expedientes.domain.ManualClientData;
import com.c21genera.expedientes.domain.ManualClientData.ManualClientDataUpdate;
import com.c21genera.expedientes.domain.ParticipantRole;
import com.c21genera.expedientes.domain.PersonType;
import com.c21genera.expedientes.domain.PropertyCaseType;
import com.c21genera.expedientes.domain.PropertyLegalStatus;
import com.c21genera.expedientes.domain.SignerCharacter;
import com.c21genera.expedientes.infrastructure.ExpedienteParticipantRepository;
import com.c21genera.expedientes.infrastructure.ExpedienteRepository;
import com.c21genera.expedientes.infrastructure.ManualClientDataRepository;
import com.c21genera.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExpedienteService implements ExpedienteLifecycleApi {

  private final ExpedienteRepository expedienteRepository;
  private final ExpedienteParticipantRepository participantRepository;
  private final ManualClientDataRepository manualClientDataRepository;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public ExpedienteService(
      ExpedienteRepository expedienteRepository,
      ExpedienteParticipantRepository participantRepository,
      ManualClientDataRepository manualClientDataRepository,
      ApplicationEventPublisher events,
      Clock clock) {
    this.expedienteRepository = expedienteRepository;
    this.participantRepository = participantRepository;
    this.manualClientDataRepository = manualClientDataRepository;
    this.events = events;
    this.clock = clock;
  }

  public record CreateParticipant(ParticipantRole role, String fullName) {}

  public record CreateExpedienteCommand(
      String ownerDisplayName,
      PersonType personType,
      SignerCharacter signerCharacter,
      AccreditationType accreditationType,
      boolean condominiumRegime,
      PropertyCaseType propertyCaseType,
      PropertyLegalStatus declaredLegalStatus,
      String propertyAddress,
      List<CreateParticipant> participants,
      UUID createdByUserId) {}

  public Expediente create(CreateExpedienteCommand command) {
    String folio = generateFolio();
    Expediente expediente =
        new Expediente(
            folio,
            command.ownerDisplayName(),
            command.personType(),
            command.signerCharacter(),
            command.accreditationType(),
            command.condominiumRegime(),
            command.propertyCaseType(),
            command.declaredLegalStatus(),
            command.propertyAddress(),
            command.createdByUserId());
    expedienteRepository.save(expediente);

    int ordinal = 1;
    List<ExpedienteParticipant> participants = new java.util.ArrayList<>();
    for (CreateParticipant p : command.participants()) {
      ExpedienteParticipant participant = new ExpedienteParticipant(expediente.getId(), p.role(), p.fullName(), ordinal++);
      participantRepository.save(participant);
      participants.add(participant);
    }

    manualClientDataRepository.save(new ManualClientData(expediente.getId()));

    events.publishEvent(new ExpedienteCreated(expediente.getId(), folio, command.createdByUserId()));

    List<RequiredDocumentSpec> requirements =
        DocumentRequirementPolicy.compute(
            participants,
            expediente.getSignerCharacter(),
            expediente.getPersonType(),
            expediente.getAccreditationType(),
            expediente.isCondominiumRegime(),
            expediente.getPropertyCaseType());
    events.publishEvent(new ExpedienteRequirementsChanged(expediente.getId(), requirements));

    return expediente;
  }

  @Transactional(readOnly = true)
  public Expediente get(UUID id) {
    return expedienteRepository.findById(id).orElseThrow(() -> new NotFoundException("Expediente", id));
  }

  @Transactional(readOnly = true)
  public Page<Expediente> listAll(Pageable pageable) {
    return expedienteRepository.findAll(pageable);
  }

  @Transactional(readOnly = true)
  public Page<Expediente> listOwn(UUID userId, Pageable pageable) {
    return expedienteRepository.findByCreatedByUserId(userId, pageable);
  }

  @Transactional(readOnly = true)
  public List<ExpedienteParticipant> participantsOf(UUID expedienteId) {
    return participantRepository.findByExpedienteIdOrderByOrdinalAsc(expedienteId);
  }

  @Transactional(readOnly = true)
  public ManualClientData manualDataOf(UUID expedienteId) {
    return manualClientDataRepository
        .findById(expedienteId)
        .orElseThrow(() -> new NotFoundException("Datos manuales del expediente", expedienteId));
  }

  @Transactional(readOnly = true)
  public List<RequiredDocumentSpec> requirementsOf(UUID expedienteId) {
    Expediente expediente = get(expedienteId);
    List<ExpedienteParticipant> participants = participantsOf(expedienteId);
    return DocumentRequirementPolicy.compute(
        participants,
        expediente.getSignerCharacter(),
        expediente.getPersonType(),
        expediente.getAccreditationType(),
        expediente.isCondominiumRegime(),
        expediente.getPropertyCaseType());
  }

  public ManualClientData updateManualData(UUID expedienteId, ManualClientDataUpdate update) {
    ManualClientData data = manualDataOf(expedienteId);
    data.update(update);
    return data;
  }

  @Override
  @Transactional(readOnly = true)
  public ExpedienteSummary getSummary(UUID expedienteId) {
    Expediente e = get(expedienteId);
    List<ParticipantView> participants =
        participantsOf(expedienteId).stream()
            .map(p -> new ParticipantView(p.getId(), p.getRole().name(), p.getFullName(), p.getOrdinal()))
            .toList();
    return new ExpedienteSummary(
        e.getId(),
        e.getFolio(),
        e.getOwnerDisplayName(),
        e.getStatus(),
        e.getPersonType(),
        e.getSignerCharacter(),
        e.getAccreditationType(),
        e.isCondominiumRegime(),
        e.getPropertyCaseType(),
        e.getDeclaredLegalStatus(),
        e.getPropertyAddress(),
        e.getCreatedByUserId(),
        participants);
  }

  @Override
  public void recordPrivacyAccepted(UUID expedienteId) {
    get(expedienteId).transitionToIfAllowed(ExpedienteStatus.WAITING_DOCUMENTS);
  }

  @Override
  public void recordDocumentsSubmitted(UUID expedienteId) {
    get(expedienteId).transitionToIfAllowed(ExpedienteStatus.DOCUMENTS_RECEIVED);
  }

  @Override
  public void recordUnderReview(UUID expedienteId) {
    get(expedienteId).transitionToIfAllowed(ExpedienteStatus.UNDER_REVIEW);
  }

  /**
   * Requiere que el expediente ya esté en UNDER_REVIEW (llamar primero a
   * {@link #recordUnderReview(UUID)}, que es idempotente).
   */
  @Override
  public void recordCorrectionsRequested(UUID expedienteId) {
    get(expedienteId).transitionToIfAllowed(ExpedienteStatus.CORRECTIONS_REQUESTED);
  }

  @Override
  public void recordDocumentsApproved(UUID expedienteId) {
    get(expedienteId).transitionToIfAllowed(ExpedienteStatus.DOCUMENTS_APPROVED);
  }

  @Override
  public void recordReceptionSigned(UUID expedienteId) {
    get(expedienteId).transitionToIfAllowed(ExpedienteStatus.RECEPTION_SIGNED);
  }

  @Override
  public void recordContractPreparation(UUID expedienteId) {
    get(expedienteId).transitionToIfAllowed(ExpedienteStatus.CONTRACT_PREPARATION);
  }

  @Override
  public void recordReadyForSignature(UUID expedienteId) {
    get(expedienteId).transitionToIfAllowed(ExpedienteStatus.READY_FOR_SIGNATURE);
  }

  /**
   * El estado ya garantiza los requisitos previos (ver AGENTS §87): solo se
   * llega a RECEPTION_SIGNED/CONTRACT_PREPARATION/READY_FOR_SIGNATURE
   * después de que documents confirmó todos los documentos aceptados y de
   * que la recepción fue firmada explícitamente.
   */
  public Expediente acceptProperty(UUID expedienteId, UUID decidedByUserId) {
    Expediente expediente = get(expedienteId);
    expediente.acceptProperty(decidedByUserId, clock.instant());
    events.publishEvent(new PropertyAccepted(expedienteId, decidedByUserId));
    return expediente;
  }

  public Expediente rejectProperty(UUID expedienteId, UUID decidedByUserId, String reason) {
    Expediente expediente = get(expedienteId);
    expediente.rejectProperty(decidedByUserId, reason, clock.instant());
    events.publishEvent(new PropertyRejected(expedienteId, decidedByUserId, reason));
    return expediente;
  }

  public Expediente close(UUID expedienteId) {
    Expediente expediente = get(expedienteId);
    expediente.close();
    return expediente;
  }

  private String generateFolio() {
    long seq = expedienteRepository.nextFolioSequenceValue();
    return "EXP-%d-%06d".formatted(Year.now().getValue(), seq);
  }
}
