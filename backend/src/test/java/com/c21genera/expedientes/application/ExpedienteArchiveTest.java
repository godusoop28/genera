package com.c21genera.expedientes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.OperationType;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.PropertyLegalStatus;
import com.c21genera.expedientes.SignerCharacter;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.domain.ExpedienteChange;
import com.c21genera.expedientes.infrastructure.ExpedienteChangeRepository;
import com.c21genera.expedientes.infrastructure.ExpedienteParticipantRepository;
import com.c21genera.expedientes.infrastructure.ExpedienteRepository;
import com.c21genera.expedientes.infrastructure.ManualClientDataRepository;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteArchived;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteUnarchived;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Archivar expedientes: sale del listado de trabajo sin tocar su estatus y queda en el historial. */
class ExpedienteArchiveTest {

  private final ExpedienteRepository expedienteRepository = mock(ExpedienteRepository.class);
  private final ExpedienteChangeRepository changeRepository = mock(ExpedienteChangeRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final ExpedienteService service =
      new ExpedienteService(
          expedienteRepository,
          mock(ExpedienteParticipantRepository.class),
          mock(ManualClientDataRepository.class),
          changeRepository,
          events,
          new ObjectMapper(),
          mock(com.c21genera.propertycatalog.PropertyCatalog.class),
          mock(PropertyKeyService.class),
          Clock.systemUTC());

  private final Actor director = Actor.staff(UUID.randomUUID(), "Carmen Directora", "DIRECTOR");

  private Expediente expediente() {
    Expediente e =
        new Expediente(
            "EXP-2026-000001",
            "Juan Pérez",
            PersonType.FISICA,
            SignerCharacter.PROPIETARIO,
            AccreditationType.ESCRITURA_PUBLICA,
            false,
            PropertyCaseType.HOUSING,
            OperationType.SALE,
            PropertyLegalStatus.EN_REVISION,
            null,
            null,
            UUID.randomUUID(),
            null,
            null);
    when(expedienteRepository.findById(e.getId())).thenReturn(Optional.of(e));
    return e;
  }

  @Test
  void archivingKeepsTheStatusAndRecordsWhoAndWhy() {
    Expediente e = expediente();
    ExpedienteStatus before = e.getStatus();

    service.archive(e.getId(), director, "  El dueño desistió  ");

    assertThat(e.isArchived()).isTrue();
    assertThat(e.getStatus()).isEqualTo(before);
    assertThat(e.getArchivedByUserId()).isEqualTo(director.userId());
    assertThat(e.getArchiveReason()).isEqualTo("El dueño desistió");
    ArgumentCaptor<ExpedienteChange> change = ArgumentCaptor.forClass(ExpedienteChange.class);
    verify(changeRepository).save(change.capture());
    assertThat(change.getValue().getField()).isEqualTo("Archivado");
    assertThat(change.getValue().getNewValue()).isEqualTo("Sí");
    ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue()).isInstanceOf(ExpedienteArchived.class);
  }

  @Test
  void archivingTwiceIsRejected() {
    Expediente e = expediente();
    service.archive(e.getId(), director, null);

    assertThatThrownBy(() -> service.archive(e.getId(), director, null)).isInstanceOf(ConflictException.class);
  }

  @Test
  void unarchivingBringsItBackAndClearsTheReason() {
    Expediente e = expediente();
    service.archive(e.getId(), director, "Pausa");

    service.unarchive(e.getId(), director);

    assertThat(e.isArchived()).isFalse();
    assertThat(e.getArchiveReason()).isNull();
    ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
    verify(events, org.mockito.Mockito.times(2)).publishEvent(event.capture());
    assertThat(event.getAllValues().get(1)).isInstanceOf(ExpedienteUnarchived.class);
  }

  @Test
  void unarchivingAnActiveExpedienteIsRejected() {
    Expediente e = expediente();

    assertThatThrownBy(() -> service.unarchive(e.getId(), director)).isInstanceOf(ConflictException.class);
  }
}
