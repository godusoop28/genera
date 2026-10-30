package com.c21genera.expedientes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.OperationType;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.PropertyLegalStatus;
import com.c21genera.expedientes.SignerCharacter;
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
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteDataCorrected;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** CC#34 (precio sugerido por el cliente) y CC#39 (referencia del inmueble en "Corregir datos"). */
class SuggestedPriceAndReferenceTest {

  private final ExpedienteRepository expedienteRepository = mock(ExpedienteRepository.class);
  private final ManualClientDataRepository manualRepository = mock(ManualClientDataRepository.class);
  private final ExpedienteChangeRepository changeRepository = mock(ExpedienteChangeRepository.class);
  private final ExpedienteParticipantRepository participantRepository = mock(ExpedienteParticipantRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final ExpedienteService service =
      new ExpedienteService(
          expedienteRepository,
          participantRepository,
          manualRepository,
          changeRepository,
          events,
          new ObjectMapper(),
          mock(com.c21genera.propertycatalog.PropertyCatalog.class),
          mock(PropertyKeyService.class),
          Clock.systemUTC());

  private Expediente expediente(String reference) {
    Expediente e =
        new Expediente(
            "EXP-2026-000002",
            "María Pérez",
            PersonType.FISICA,
            SignerCharacter.PROPIETARIO,
            AccreditationType.ESCRITURA_PUBLICA,
            false,
            PropertyCaseType.HOUSING,
            OperationType.SALE,
            PropertyLegalStatus.EN_REVISION,
            null,
            reference,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Ana Asesora");
    return e;
  }

  private ManualClientData dataOf(Expediente e) {
    UUID id = e.getId();
    ManualClientData data = new ManualClientData(id);
    ExpedienteParticipant owner = new ExpedienteParticipant(id, ParticipantRole.OWNER, "María Pérez", 1);
    when(manualRepository.findById(any())).thenReturn(Optional.of(data));
    when(participantRepository.findByExpedienteIdOrderByOrdinalAsc(any())).thenReturn(List.of(owner));
    return data;
  }

  @Test
  void theClientCanSetAndClearTheSuggestedPriceEvenWhenTheContractIsLocked() {
    Expediente locked = spy(expediente(null));
    doThrow(new ConflictException("EXPEDIENTE_LOCKED", "bloqueado")).when(locked).ensureCorrectable();
    when(expedienteRepository.findById(any())).thenReturn(Optional.of(locked));
    ManualClientData data = dataOf(locked);

    service.updateManualData(locked.getId(), ManualClientDataUpdate.fromClient(null, null, null, null, new BigDecimal("4500000.50"), false), Actor.client(), null);
    assertThat(data.getSuggestedPrice()).isEqualByComparingTo("4500000.50");

    service.updateManualData(locked.getId(), ManualClientDataUpdate.fromClient(null, null, null, null, null, true), Actor.client(), null);
    assertThat(data.getSuggestedPrice()).isNull();
  }

  @Test
  void theSuggestedPriceStaysInTheHistoryButDoesNotInvalidateTheContract() {
    Expediente e = expediente(null);
    when(expedienteRepository.findById(any())).thenReturn(Optional.of(e));
    dataOf(e);

    service.updateManualData(e.getId(), ManualClientDataUpdate.fromClient(null, null, null, null, new BigDecimal("3200000"), false), Actor.client(), null);

    ArgumentCaptor<ExpedienteChange> change = ArgumentCaptor.forClass(ExpedienteChange.class);
    verify(changeRepository).save(change.capture());
    assertThat(change.getValue().getField()).isEqualTo(ManualClientData.SUGGESTED_PRICE_FIELD);
    assertThat(change.getValue().getNewValue()).isEqualTo("3200000");
    ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
    verify(events, atLeastOnce()).publishEvent(event.capture());
    assertThat(event.getAllValues()).filteredOn(ExpedienteDataCorrected.class::isInstance)
        .singleElement()
        .satisfies(ev -> assertThat(((ExpedienteDataCorrected) ev).contractRelevant()).isFalse());
  }

  @Test
  void anEmptySuggestedPriceIsNotStoredAsZero() {
    Expediente e = expediente(null);
    when(expedienteRepository.findById(any())).thenReturn(Optional.of(e));
    ManualClientData data = dataOf(e);

    service.updateManualData(e.getId(), ManualClientDataUpdate.fromClient("cliente@correo.com", "7770000000", null, null, null, false), Actor.client(), null);

    assertThat(data.getSuggestedPrice()).isNull();
    assertThat(data.getEmail()).isEqualTo("cliente@correo.com");
  }

  @Test
  void correctingTheReferenceIsRecordedWithoutInvalidatingTheContract() {
    Expediente e = expediente("Casa Las Flores");
    when(expedienteRepository.findById(any())).thenReturn(Optional.of(e));
    dataOf(e);

    service.correctConfiguration(
        e.getId(),
        new ExpedienteService.ConfigurationCorrection(
            PersonType.FISICA, false, AccreditationType.ESCRITURA_PUBLICA, false, null, null, OperationType.SALE, PropertyLegalStatus.EN_REVISION, null,
            "Casa Jardines"),
        Actor.staff(UUID.randomUUID(), "Carmen Directora", "DIRECTOR"),
        "El cliente la conoce así");

    assertThat(e.getPropertyReference()).isEqualTo("Casa Jardines");
    ArgumentCaptor<ExpedienteChange> change = ArgumentCaptor.forClass(ExpedienteChange.class);
    verify(changeRepository, atLeastOnce()).save(change.capture());
    assertThat(change.getAllValues()).anySatisfy(c -> {
      assertThat(c.getField()).isEqualTo("Referencia del inmueble que ve el cliente");
      assertThat(c.getOldValue()).isEqualTo("Casa Las Flores");
      assertThat(c.getNewValue()).isEqualTo("Casa Jardines");
    });
  }

  @Test
  void aNullReferenceKeepsTheCurrentOne() {
    Expediente e = expediente("Casa Las Flores");
    when(expedienteRepository.findById(any())).thenReturn(Optional.of(e));
    dataOf(e);

    service.correctConfiguration(
        e.getId(),
        new ExpedienteService.ConfigurationCorrection(
            PersonType.FISICA, false, AccreditationType.ESCRITURA_PUBLICA, false, null, null, OperationType.SALE, PropertyLegalStatus.EN_REVISION, null),
        Actor.staff(UUID.randomUUID(), "Carmen Directora", "DIRECTOR"),
        "Sin cambios de referencia");

    assertThat(e.getPropertyReference()).isEqualTo("Casa Las Flores");
  }
}
