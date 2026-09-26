package com.c21genera.expedientes.application;

import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.CivilStatus;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.expedientes.ExpedienteSummary.ParticipantView;
import com.c21genera.expedientes.LegalDetails;
import com.c21genera.expedientes.MaritalRegime;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.PropertyLegalStatus;
import com.c21genera.expedientes.SignerCharacter;
import com.c21genera.expedientes.domain.DocumentRequirementPolicy;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.domain.ExpedienteChange;
import com.c21genera.expedientes.domain.ExpedienteParticipant;
import com.c21genera.expedientes.domain.ManualClientData;
import com.c21genera.expedientes.domain.ManualClientData.ManualClientDataUpdate;
import com.c21genera.expedientes.domain.ParticipantRole;
import com.c21genera.expedientes.infrastructure.ExpedienteChangeRepository;
import com.c21genera.expedientes.infrastructure.ExpedienteParticipantRepository;
import com.c21genera.expedientes.infrastructure.ExpedienteRepository;
import com.c21genera.expedientes.infrastructure.ManualClientDataRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.RequiredDocumentSpec;
import com.c21genera.shared.domain.ReviewDecision;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.DocumentEvents.AllRequiredDocumentsApproved;
import com.c21genera.shared.events.DocumentEvents.AllRequiredDocumentsUploaded;
import com.c21genera.shared.events.DocumentEvents.DocumentReviewed;
import com.c21genera.shared.events.DocumentEvents.ReceptionSigned;
import com.c21genera.shared.events.DocumentEvents.RequiredDocumentsReopened;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteCreated;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteDataCorrected;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteRequirementsChanged;
import com.c21genera.shared.events.ExpedienteEvents.PropertyAccepted;
import com.c21genera.shared.events.ExpedienteEvents.PropertyRejected;
import com.c21genera.shared.events.PrivacyEvents.PrivacyAccepted;
import com.c21genera.shared.events.PublicAccessEvents.PublicLinkGenerated;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExpedienteService implements ExpedienteLifecycleApi {

  private final ExpedienteRepository expedienteRepository;
  private final ExpedienteParticipantRepository participantRepository;
  private final ManualClientDataRepository manualClientDataRepository;
  private final ExpedienteChangeRepository changeRepository;
  private final ApplicationEventPublisher events;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public ExpedienteService(
      ExpedienteRepository expedienteRepository,
      ExpedienteParticipantRepository participantRepository,
      ManualClientDataRepository manualClientDataRepository,
      ExpedienteChangeRepository changeRepository,
      ApplicationEventPublisher events,
      ObjectMapper objectMapper,
      Clock clock) {
    this.expedienteRepository = expedienteRepository;
    this.participantRepository = participantRepository;
    this.manualClientDataRepository = manualClientDataRepository;
    this.changeRepository = changeRepository;
    this.events = events;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public record ParticipantInput(ParticipantRole role, String fullName, ExpedienteParticipant.Details details) {}

  public record CreateExpedienteCommand(
      PersonType personType,
      boolean signedByAttorney,
      AccreditationType accreditationType,
      boolean condominiumRegime,
      PropertyCaseType propertyCaseType,
      PropertyLegalStatus declaredLegalStatus,
      String propertyAddress,
      List<ParticipantInput> participants,
      LegalDetails legalDetails,
      Actor actor) {}

  public Expediente create(CreateExpedienteCommand command) {
    validateParticipants(command.personType(), command.signedByAttorney(), command.participants());

    String folio = generateFolio();
    Expediente expediente =
        new Expediente(
            folio,
            displayNameOf(command.personType(), command.participants()),
            command.personType(),
            signerCharacterOf(command.personType(), command.signedByAttorney(), command.participants()),
            command.accreditationType(),
            command.condominiumRegime(),
            command.propertyCaseType(),
            command.declaredLegalStatus(),
            command.propertyAddress(),
            command.actor().userId());
    if (command.legalDetails() != null) {
      expediente.replaceLegalDetailsJson(writeLegalDetails(command.legalDetails()));
    }
    expedienteRepository.save(expediente);

    int ordinal = 1;
    for (ParticipantInput input : command.participants()) {
      ExpedienteParticipant participant = new ExpedienteParticipant(expediente.getId(), input.role(), input.fullName().strip(), ordinal++);
      participant.update(input.role(), input.fullName().strip(), detailsOrEmpty(input.details()));
      participantRepository.save(participant);
    }

    manualClientDataRepository.save(new ManualClientData(expediente.getId()));

    events.publishEvent(new ExpedienteCreated(expediente.getId(), folio, command.actor().userId(), command.actor()));
    publishRequirements(expediente);
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
  public LegalDetails legalDetailsOf(UUID expedienteId) {
    return readLegalDetails(get(expedienteId).getLegalDetailsJson());
  }

  @Transactional(readOnly = true)
  public List<ExpedienteChange> changesOf(UUID expedienteId) {
    return changeRepository.findByExpedienteIdOrderByChangedAtDesc(expedienteId);
  }

  @Transactional(readOnly = true)
  public List<RequiredDocumentSpec> requirementsOf(UUID expedienteId) {
    return computeRequirements(get(expedienteId));
  }

  // ---------------------------------------------------------------------
  // Correcciones controladas (con historial y nueva versión de contrato)
  // ---------------------------------------------------------------------

  public record ConfigurationCorrection(
      PersonType personType,
      boolean signedByAttorney,
      AccreditationType accreditationType,
      boolean condominiumRegime,
      PropertyCaseType propertyCaseType,
      PropertyLegalStatus declaredLegalStatus,
      String propertyAddress) {}

  public Expediente correctConfiguration(UUID expedienteId, ConfigurationCorrection correction, Actor actor, String reason) {
    Expediente expediente = get(expedienteId);
    expediente.ensureCorrectable();
    List<ExpedienteParticipant> participants = participantsOf(expedienteId);
    List<ParticipantInput> inputs = participants.stream().map(ExpedienteService::toInput).toList();
    validateParticipants(correction.personType(), correction.signedByAttorney(), inputs);

    Map<String, String> before = configurationSnapshot(expediente);
    expediente.reconfigure(
        new Expediente.Configuration(
            displayNameOf(correction.personType(), inputs),
            correction.personType(),
            signerCharacterOf(correction.personType(), correction.signedByAttorney(), inputs),
            correction.accreditationType(),
            correction.condominiumRegime(),
            correction.propertyCaseType(),
            correction.declaredLegalStatus(),
            correction.propertyAddress()));
    recordChanges(expediente, "Datos del expediente", before, configurationSnapshot(expediente), actor, reason);
    publishRequirements(expediente);
    return expediente;
  }

  public LegalDetails updateLegalDetails(UUID expedienteId, LegalDetails details, Actor actor, String reason) {
    Expediente expediente = get(expedienteId);
    expediente.ensureCorrectable();
    Map<String, String> before = flatten(readLegalDetails(expediente.getLegalDetailsJson()));
    expediente.replaceLegalDetailsJson(writeLegalDetails(details));
    recordChanges(expediente, "Datos legales", before, flatten(details), actor, reason);
    return details;
  }

  public ExpedienteParticipant addParticipant(UUID expedienteId, ParticipantInput input, Actor actor, String reason) {
    Expediente expediente = get(expedienteId);
    expediente.ensureCorrectable();
    List<ExpedienteParticipant> current = participantsOf(expedienteId);
    List<ParticipantInput> inputs = new ArrayList<>(current.stream().map(ExpedienteService::toInput).toList());
    inputs.add(input);
    validateParticipants(expediente.getPersonType(), expediente.getSignerCharacter() == SignerCharacter.APODERADO, inputs);

    int ordinal = current.stream().mapToInt(ExpedienteParticipant::getOrdinal).max().orElse(0) + 1;
    ExpedienteParticipant participant = new ExpedienteParticipant(expedienteId, input.role(), input.fullName().strip(), ordinal);
    participant.update(input.role(), input.fullName().strip(), detailsOrEmpty(input.details()));
    participantRepository.save(participant);

    recordChanges(
        expediente,
        "Participantes",
        Map.of(),
        Map.of("Participante agregado", roleLabel(input.role()) + ": " + input.fullName().strip()),
        actor,
        reason);
    refreshDerivedFields(expediente);
    publishRequirements(expediente);
    return participant;
  }

  public ExpedienteParticipant updateParticipant(UUID expedienteId, UUID participantId, ParticipantInput input, Actor actor, String reason) {
    Expediente expediente = get(expedienteId);
    expediente.ensureCorrectable();
    ExpedienteParticipant participant = participantOf(expedienteId, participantId);

    List<ParticipantInput> inputs =
        participantsOf(expedienteId).stream().map(p -> p.getId().equals(participantId) ? input : toInput(p)).toList();
    validateParticipants(expediente.getPersonType(), expediente.getSignerCharacter() == SignerCharacter.APODERADO, inputs);

    Map<String, String> before = participantSnapshot(participant);
    participant.update(input.role(), input.fullName().strip(), detailsOrEmpty(input.details()));
    recordChanges(expediente, "Participante: " + participant.getFullName(), before, participantSnapshot(participant), actor, reason);
    refreshDerivedFields(expediente);
    publishRequirements(expediente);
    return participant;
  }

  public void removeParticipant(UUID expedienteId, UUID participantId, Actor actor, String reason) {
    Expediente expediente = get(expedienteId);
    expediente.ensureCorrectable();
    ExpedienteParticipant participant = participantOf(expedienteId, participantId);
    List<ParticipantInput> remaining =
        participantsOf(expedienteId).stream().filter(p -> !p.getId().equals(participantId)).map(ExpedienteService::toInput).toList();
    validateParticipants(expediente.getPersonType(), expediente.getSignerCharacter() == SignerCharacter.APODERADO, remaining);

    participantRepository.delete(participant);
    participantRepository.flush();
    int ordinal = 1;
    for (ExpedienteParticipant p : participantsOf(expedienteId)) {
      p.moveTo(ordinal++);
    }
    recordChanges(
        expediente,
        "Participantes",
        Map.of("Participante eliminado", roleLabel(participant.getRole()) + ": " + participant.getFullName()),
        Map.of(),
        actor,
        reason);
    refreshDerivedFields(expediente);
    publishRequirements(expediente);
  }

  /**
   * Datos manuales (precio, datos de contacto, Anexo A...). Si el cambio lo
   * hace el staff y ya hay un contrato generado, también invalida ese
   * contrato (ver contracts). Fusiona, no reemplaza: un campo ausente no
   * borra el valor guardado.
   */
  public ManualClientData updateManualData(UUID expedienteId, ManualClientDataUpdate update, Actor actor, String reason) {
    Expediente expediente = get(expedienteId);
    ManualClientData data = manualDataOf(expedienteId);
    Map<String, String> before = data.snapshot();
    data.update(update);
    if (!before.equals(data.snapshot())) {
      // Si no se permite corregir, la excepción revierte la transacción completa.
      expediente.ensureCorrectable();
    }
    recordChanges(expediente, "Datos del cliente e inmueble", before, data.snapshot(), actor, reason);

    // El estado civil del propietario principal alimenta la política (acta de matrimonio).
    if (update.civilStatus() != null) {
      participantsOf(expedienteId).stream()
          .filter(p -> p.getOrdinal() == 1 && p.isOwner() && expediente.getPersonType() == PersonType.FISICA)
          .findFirst()
          .ifPresent(p -> p.declareCivilStatus(update.civilStatus(), p.details().maritalRegime()));
    }
    publishRequirements(expediente);
    return data;
  }

  /** El cliente, desde su liga, declara el estado civil de un titular persona física. */
  public ExpedienteParticipant declareCivilStatus(UUID expedienteId, UUID participantId, CivilStatus civilStatus, MaritalRegime regime) {
    Expediente expediente = get(expedienteId);
    if (expediente.getPersonType() != PersonType.FISICA) {
      throw new UnprocessableException("NOT_APPLICABLE", "El estado civil no aplica a una persona moral.");
    }
    ExpedienteParticipant participant = participantOf(expedienteId, participantId);
    if (!participant.isOwner()) {
      throw new UnprocessableException("NOT_APPLICABLE", "El estado civil solo se captura para propietarios.");
    }
    Map<String, String> before = participantSnapshot(participant);
    if (civilStatus != participant.getCivilStatus() || regime != participant.details().maritalRegime()) {
      expediente.ensureCorrectable();
    }
    participant.declareCivilStatus(civilStatus, regime);
    recordChanges(expediente, "Participante: " + participant.getFullName(), before, participantSnapshot(participant), Actor.client(), null);
    publishRequirements(expediente);
    return participant;
  }

  // ---------------------------------------------------------------------
  // API pública del módulo
  // ---------------------------------------------------------------------

  @Override
  @Transactional(readOnly = true)
  public ExpedienteLifecycleApi.ManualClientDataView getManualData(UUID expedienteId) {
    ManualClientData d = manualDataOf(expedienteId);
    return new ExpedienteLifecycleApi.ManualClientDataView(
        d.getAuthorizedPrice(),
        d.getContractSignatureDate(),
        d.getEmail(),
        d.getPhone(),
        d.getNotificationAddress(),
        d.getVisitInstructions(),
        d.getMarketingDataAuthorized(),
        d.getReceiveAdsAuthorized(),
        d.getAdditionalServicesRequested(),
        d.getBedrooms(),
        d.getBathrooms(),
        d.getParkingSpots(),
        d.getConservationStatus(),
        d.getAvailableServices(),
        d.getRelevantFeatures(),
        d.getLandAreaM2(),
        d.getBuiltAreaM2());
  }

  @Override
  @Transactional(readOnly = true)
  public ExpedienteSummary getSummary(UUID expedienteId) {
    Expediente e = get(expedienteId);
    List<ParticipantView> participants = participantsOf(expedienteId).stream().map(ExpedienteService::toView).toList();
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
        participants,
        readLegalDetails(e.getLegalDetailsJson()));
  }

  public static ParticipantView toView(ExpedienteParticipant p) {
    ExpedienteParticipant.Details d = p.details();
    return new ParticipantView(
        p.getId(),
        p.getRole().name(),
        p.getFullName(),
        p.getOrdinal(),
        d.nationality(),
        d.idDocumentType(),
        d.idDocumentNumber(),
        d.idDocumentIssuer(),
        d.birthDate(),
        d.civilStatus(),
        d.maritalRegime(),
        d.rfc(),
        d.curp(),
        d.email(),
        d.phone(),
        d.address());
  }

  /** publicaccess publica este evento al emitir la liga; expedientes decide su propia transición. */
  @ApplicationModuleListener
  void on(PublicLinkGenerated event) {
    get(event.expedienteId()).transitionToIfAllowed(ExpedienteStatus.WAITING_PRIVACY);
  }

  /** privacy publica este evento; expedientes decide su propia transición (ver AGENTS §7). */
  @ApplicationModuleListener
  void on(PrivacyAccepted event) {
    if (event.mainPurposesAccepted()) {
      recordPrivacyAccepted(event.expedienteId());
    }
  }

  /** documents publica esto en cada decisión de revisión; expedientes decide su propia transición. */
  @ApplicationModuleListener
  void on(DocumentReviewed event) {
    recordUnderReview(event.expedienteId());
    if (event.decision() == ReviewDecision.RETURNED || event.decision() == ReviewDecision.REJECTED) {
      recordCorrectionsRequested(event.expedienteId());
    }
  }

  @ApplicationModuleListener
  void on(AllRequiredDocumentsApproved event) {
    recordDocumentsApproved(event.expedienteId());
  }

  @ApplicationModuleListener
  void on(AllRequiredDocumentsUploaded event) {
    get(event.expedienteId()).markAllRequiredDocumentsUploaded();
  }

  /** Una corrección agregó un documento obligatorio que todavía no está aceptado. */
  @ApplicationModuleListener
  void on(RequiredDocumentsReopened event) {
    Expediente expediente = get(event.expedienteId());
    expediente.markRequiredDocumentsPending();
    switch (expediente.getStatus()) {
      case DOCUMENTS_APPROVED, RECEPTION_SIGNED, CONTRACT_PREPARATION, READY_FOR_SIGNATURE ->
          expediente.transitionToIfAllowed(ExpedienteStatus.CORRECTIONS_REQUESTED);
      default -> {
        /* aún no se había aprobado la documentación: nada que revertir */
      }
    }
  }

  @ApplicationModuleListener
  void on(ReceptionSigned event) {
    recordReceptionSigned(event.expedienteId());
  }

  @Override
  public void recordPrivacyAccepted(UUID expedienteId) {
    // Idempotente y tolerante a que la liga se haya generado antes de que
    // existiera la transición DRAFT -> WAITING_PRIVACY (ver PublicLinkGenerated):
    // si el expediente sigue en DRAFT, lo avanza igual porque el consentimiento
    // ya ocurrió realmente.
    Expediente expediente = get(expedienteId);
    expediente.transitionToIfAllowed(ExpedienteStatus.WAITING_PRIVACY);
    expediente.transitionToIfAllowed(ExpedienteStatus.WAITING_DOCUMENTS);
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
    Expediente expediente = get(expedienteId);
    // Tras una corrección, el expediente puede venir de CORRECTIONS_REQUESTED.
    expediente.transitionToIfAllowed(ExpedienteStatus.UNDER_REVIEW);
    expediente.transitionToIfAllowed(ExpedienteStatus.DOCUMENTS_APPROVED);
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
    Expediente expediente = get(expedienteId);
    expediente.transitionToIfAllowed(ExpedienteStatus.CONTRACT_PREPARATION);
    expediente.transitionToIfAllowed(ExpedienteStatus.READY_FOR_SIGNATURE);
  }

  @Override
  public void recordContractSigned(UUID expedienteId) {
    get(expedienteId).transitionToIfAllowed(ExpedienteStatus.CONTRACT_SIGNED);
  }

  @Override
  public void recordContractInvalidated(UUID expedienteId) {
    Expediente expediente = get(expedienteId);
    if (expediente.getStatus() == ExpedienteStatus.READY_FOR_SIGNATURE) {
      expediente.transitionToIfAllowed(ExpedienteStatus.CONTRACT_PREPARATION);
    }
  }

  /**
   * Solo con el contrato de intermediación firmado por todas las partes
   * (CONTRACT_SIGNED) se puede aceptar el inmueble; la máquina de estados
   * lo garantiza.
   */
  public Expediente acceptProperty(UUID expedienteId, Actor actor) {
    Expediente expediente = get(expedienteId);
    if (expediente.getStatus() != ExpedienteStatus.CONTRACT_SIGNED) {
      throw new UnprocessableException(
          "CONTRACT_NOT_SIGNED",
          "Para aceptar el inmueble primero debe estar firmado el contrato de intermediación por todas las partes.");
    }
    expediente.acceptProperty(actor.userId(), clock.instant());
    events.publishEvent(new PropertyAccepted(expedienteId, actor.userId(), actor));
    return expediente;
  }

  public Expediente rejectProperty(UUID expedienteId, Actor actor, String reason) {
    Expediente expediente = get(expedienteId);
    expediente.rejectProperty(actor.userId(), reason, clock.instant());
    events.publishEvent(new PropertyRejected(expedienteId, actor.userId(), reason, actor));
    return expediente;
  }

  public Expediente close(UUID expedienteId) {
    Expediente expediente = get(expedienteId);
    expediente.close();
    return expediente;
  }

  // ---------------------------------------------------------------------
  // Reglas de participantes según tipo de persona
  // ---------------------------------------------------------------------

  /**
   * Persona física: al menos un titular (el primero es OWNER, el resto
   * CO_OWNER) y, si firma un apoderado, al menos un ATTORNEY. Nunca un
   * representante legal. Persona moral: un único OWNER (la sociedad), al
   * menos un LEGAL_REPRESENTATIVE y ningún copropietario ni apoderado.
   */
  static void validateParticipants(PersonType personType, boolean signedByAttorney, List<ParticipantInput> participants) {
    if (participants == null || participants.isEmpty()) {
      throw invalid("Registra al menos un propietario.");
    }
    for (ParticipantInput p : participants) {
      if (p.role() == null || p.fullName() == null || p.fullName().isBlank()) {
        throw invalid("Cada participante debe tener rol y nombre completo.");
      }
      if (p.fullName().toLowerCase(Locale.ROOT).contains("pendiente de captura")) {
        throw invalid("Captura el nombre real de cada participante (no se aceptan nombres provisionales).");
      }
    }
    if (participants.getFirst().role() != ParticipantRole.OWNER) {
      throw invalid("El primer participante debe ser el propietario principal.");
    }
    long owners = participants.stream().filter(p -> p.role() == ParticipantRole.OWNER).count();
    if (owners != 1) {
      throw invalid("Debe haber exactamente un propietario principal; los demás titulares se registran como copropietarios.");
    }
    long legalRepresentatives = participants.stream().filter(p -> p.role() == ParticipantRole.LEGAL_REPRESENTATIVE).count();
    long attorneys = participants.stream().filter(p -> p.role() == ParticipantRole.ATTORNEY).count();
    long coOwners = participants.stream().filter(p -> p.role() == ParticipantRole.CO_OWNER).count();

    if (personType == PersonType.MORAL) {
      if (coOwners > 0 || attorneys > 0) {
        throw invalid(
            "En persona moral el titular es la sociedad y firma su representante legal: no se registran copropietarios ni apoderados.");
      }
      if (legalRepresentatives == 0) {
        throw invalid("Registra al representante legal de la persona moral.");
      }
      for (ParticipantInput p : participants) {
        if (p.role() == ParticipantRole.OWNER && p.details() != null && p.details().civilStatus() != null) {
          throw invalid("Una persona moral no tiene estado civil.");
        }
      }
    } else {
      if (legalRepresentatives > 0) {
        throw invalid("Una persona física no tiene representante legal; si firma otra persona por ella, regístrala como apoderado.");
      }
      if (signedByAttorney && attorneys == 0) {
        throw invalid("Registra al apoderado que firmará en nombre del propietario.");
      }
      if (!signedByAttorney && attorneys > 0) {
        throw invalid("Hay un apoderado registrado: indica que firma un apoderado o elimínalo.");
      }
    }
  }

  static SignerCharacter signerCharacterOf(PersonType personType, boolean signedByAttorney, List<ParticipantInput> participants) {
    if (personType == PersonType.MORAL) {
      return SignerCharacter.REPRESENTANTE_LEGAL;
    }
    if (signedByAttorney) {
      return SignerCharacter.APODERADO;
    }
    long owners = participants.stream().filter(p -> p.role() == ParticipantRole.OWNER || p.role() == ParticipantRole.CO_OWNER).count();
    return owners > 1 ? SignerCharacter.COPROPIETARIO : SignerCharacter.PROPIETARIO;
  }

  static String displayNameOf(PersonType personType, List<ParticipantInput> participants) {
    List<String> owners =
        participants.stream()
            .filter(p -> p.role() == ParticipantRole.OWNER || p.role() == ParticipantRole.CO_OWNER)
            .map(p -> p.fullName().strip())
            .toList();
    if (owners.size() <= 1) {
      return owners.isEmpty() ? "" : owners.getFirst();
    }
    return String.join(", ", owners.subList(0, owners.size() - 1)) + " y " + owners.getLast();
  }

  private void refreshDerivedFields(Expediente expediente) {
    List<ParticipantInput> inputs = participantsOf(expediente.getId()).stream().map(ExpedienteService::toInput).toList();
    Expediente.Configuration current = expediente.configuration();
    expediente.reconfigure(
        new Expediente.Configuration(
            displayNameOf(current.personType(), inputs),
            current.personType(),
            signerCharacterOf(current.personType(), current.signerCharacter() == SignerCharacter.APODERADO, inputs),
            current.accreditationType(),
            current.condominiumRegime(),
            current.propertyCaseType(),
            current.declaredLegalStatus(),
            current.propertyAddress()));
  }

  private static UnprocessableException invalid(String message) {
    return new UnprocessableException("INVALID_PARTICIPANTS", message);
  }

  private static ParticipantInput toInput(ExpedienteParticipant p) {
    return new ParticipantInput(p.getRole(), p.getFullName(), p.details());
  }

  private static ExpedienteParticipant.Details detailsOrEmpty(ExpedienteParticipant.Details details) {
    return details != null ? details : ExpedienteParticipant.Details.empty();
  }

  private ExpedienteParticipant participantOf(UUID expedienteId, UUID participantId) {
    return participantRepository
        .findById(participantId)
        .filter(p -> p.getExpedienteId().equals(expedienteId))
        .orElseThrow(() -> new NotFoundException("Participante", participantId));
  }

  // ---------------------------------------------------------------------
  // Requisitos
  // ---------------------------------------------------------------------

  private List<RequiredDocumentSpec> computeRequirements(Expediente expediente) {
    ManualClientData data = manualDataOf(expediente.getId());
    return DocumentRequirementPolicy.compute(
        new DocumentRequirementPolicy.Input(
            participantsOf(expediente.getId()),
            expediente.getSignerCharacter(),
            expediente.getPersonType(),
            expediente.getAccreditationType(),
            expediente.isCondominiumRegime(),
            expediente.getPropertyCaseType(),
            data.getCivilStatus()));
  }

  /**
   * documents escucha este evento (en la creación y tras cada corrección) y
   * es idempotente: actualiza la bandera de obligatorio de los documentos que
   * ya existen y deja como no obligatorios los que dejaron de aplicar.
   */
  private void publishRequirements(Expediente expediente) {
    events.publishEvent(new ExpedienteRequirementsChanged(expediente.getId(), computeRequirements(expediente)));
  }

  // ---------------------------------------------------------------------
  // Historial de cambios
  // ---------------------------------------------------------------------

  private void recordChanges(
      Expediente expediente, String section, Map<String, String> before, Map<String, String> after, Actor actor, String reason) {
    Instant now = clock.instant();
    List<String> changed = new ArrayList<>();
    java.util.Set<String> keys = new java.util.LinkedHashSet<>(before.keySet());
    keys.addAll(after.keySet());
    for (String field : keys) {
      String oldValue = emptyToNull(before.get(field));
      String newValue = emptyToNull(after.get(field));
      if (!Objects.equals(oldValue, newValue)) {
        changeRepository.save(
            new ExpedienteChange(expediente.getId(), now, actor, section, field, oldValue, newValue, emptyToNull(reason)));
        changed.add(field);
      }
    }
    if (!changed.isEmpty()) {
      events.publishEvent(
          new ExpedienteDataCorrected(expediente.getId(), actor, section, List.copyOf(changed), emptyToNull(reason), true));
    }
  }

  private static Map<String, String> configurationSnapshot(Expediente e) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("Titular(es)", e.getOwnerDisplayName());
    values.put("Tipo de persona", e.getPersonType() == PersonType.MORAL ? "Persona moral" : "Persona física");
    values.put("Firma", signerLabel(e.getSignerCharacter()));
    values.put(
        "Acreditación de la propiedad",
        e.getAccreditationType() == AccreditationType.ESCRITURA_PUBLICA ? "Escritura pública" : "Contrato privado");
    values.put("Régimen de condominio", e.isCondominiumRegime() ? "Sí" : "No");
    values.put("Tipo de inmueble", propertyLabel(e.getPropertyCaseType()));
    values.put("Situación jurídica declarada", e.getDeclaredLegalStatus().name());
    values.put("Domicilio del inmueble", e.getPropertyAddress());
    return values;
  }

  private static Map<String, String> participantSnapshot(ExpedienteParticipant p) {
    ExpedienteParticipant.Details d = p.details();
    Map<String, String> values = new LinkedHashMap<>();
    values.put("Rol", roleLabel(p.getRole()));
    values.put("Nombre completo", p.getFullName());
    values.put("Nacionalidad", d.nationality());
    values.put("Identificación", d.idDocumentType() == null ? null : d.idDocumentType().name());
    values.put("Folio de identificación", d.idDocumentNumber());
    values.put("Emisor de identificación", d.idDocumentIssuer());
    values.put("Fecha de nacimiento", d.birthDate() == null ? null : d.birthDate().toString());
    values.put("Estado civil", d.civilStatus() == null ? null : d.civilStatus().name());
    values.put("Régimen matrimonial", d.maritalRegime() == null ? null : d.maritalRegime().name());
    values.put("RFC", d.rfc());
    values.put("CURP", d.curp());
    values.put("Correo electrónico", d.email());
    values.put("Teléfono", d.phone());
    values.put("Domicilio", d.address());
    return values;
  }

  /** Aplana los datos legales a "ruta" -> valor, para registrar solo los campos que cambiaron. */
  private Map<String, String> flatten(LegalDetails details) {
    Map<String, String> values = new LinkedHashMap<>();
    flattenInto("", objectMapper.valueToTree(details == null ? LegalDetails.empty() : details), values);
    return values;
  }

  private static void flattenInto(String prefix, JsonNode node, Map<String, String> out) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isObject()) {
      node.fields().forEachRemaining(e -> flattenInto(prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey(), e.getValue(), out));
    } else if (node.isArray()) {
      out.put(prefix, node.toString());
    } else {
      out.put(prefix, node.asText());
    }
  }

  private LegalDetails readLegalDetails(String json) {
    if (json == null || json.isBlank()) {
      return LegalDetails.empty();
    }
    try {
      return objectMapper.readValue(json, LegalDetails.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Datos legales del expediente ilegibles", e);
    }
  }

  private String writeLegalDetails(LegalDetails details) {
    try {
      return objectMapper.writeValueAsString(details);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("No se pudieron serializar los datos legales", e);
    }
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  static String roleLabel(ParticipantRole role) {
    return switch (role) {
      case OWNER -> "Propietario";
      case CO_OWNER -> "Copropietario";
      case ATTORNEY -> "Apoderado";
      case LEGAL_REPRESENTATIVE -> "Representante legal";
    };
  }

  private static String signerLabel(SignerCharacter signer) {
    return switch (signer) {
      case PROPIETARIO -> "Propietario";
      case COPROPIETARIO -> "Copropietarios";
      case APODERADO -> "Apoderado";
      case REPRESENTANTE_LEGAL -> "Representante legal";
    };
  }

  private static String propertyLabel(PropertyCaseType type) {
    return switch (type) {
      case HOUSING -> "Casa";
      case DEPARTMENT -> "Departamento";
      case RESIDENTIAL_LAND -> "Terreno";
      case COMMERCIAL -> "Comercial";
    };
  }

  private String generateFolio() {
    long seq = expedienteRepository.nextFolioSequenceValue();
    return "EXP-%d-%06d".formatted(Year.now(clock).getValue(), seq);
  }
}
