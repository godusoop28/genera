package com.c21genera.expedientes.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.domain.ExpedienteParticipant;
import com.c21genera.expedientes.domain.ParticipantRole;
import com.c21genera.expedientes.infrastructure.ExpedienteChangeRepository;
import com.c21genera.expedientes.infrastructure.ExpedienteParticipantRepository;
import com.c21genera.expedientes.infrastructure.ExpedienteRepository;
import com.c21genera.expedientes.infrastructure.ManualClientDataRepository;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.DocumentEvents.RequiredDocumentsReopened;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

/**
 * El cliente corrige desde su liga a los titulares (p. ej. faltó un
 * copropietario) para que se le pidan los documentos que faltan.
 */
class ClientOwnerCorrectionTest {

  private final ExpedienteRepository expedienteRepository = mock(ExpedienteRepository.class);
  private final ExpedienteParticipantRepository participantRepository = mock(ExpedienteParticipantRepository.class);
  private final ExpedienteService service =
      new ExpedienteService(
          expedienteRepository,
          participantRepository,
          mock(ManualClientDataRepository.class),
          mock(ExpedienteChangeRepository.class),
          mock(ApplicationEventPublisher.class),
          new ObjectMapper(),
          Clock.systemUTC());
  private final UUID expedienteId = UUID.randomUUID();
  private final Expediente expediente = mock(Expediente.class);

  @BeforeEach
  void setUp() {
    when(expedienteRepository.findById(expedienteId)).thenReturn(Optional.of(expediente));
    when(expediente.getPersonType()).thenReturn(PersonType.FISICA);
    when(expediente.getStatus()).thenReturn(ExpedienteStatus.UNDER_REVIEW);
  }

  @Test
  void clientCannotChangeOwnersOnceDocumentsWereApproved() {
    when(expediente.getStatus()).thenReturn(ExpedienteStatus.DOCUMENTS_APPROVED);

    assertThatThrownBy(() -> service.addCoOwnerByClient(expedienteId, "Laura Martínez Soto"))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void clientCannotChangeOwnersOfACompany() {
    when(expediente.getPersonType()).thenReturn(PersonType.MORAL);

    assertThatThrownBy(() -> service.addCoOwnerByClient(expedienteId, "Laura Martínez Soto"))
        .isInstanceOf(UnprocessableException.class);
  }

  @Test
  void clientCannotRemoveTheMainOwner() {
    UUID ownerId = UUID.randomUUID();
    ExpedienteParticipant owner = mock(ExpedienteParticipant.class);
    when(owner.getExpedienteId()).thenReturn(expedienteId);
    when(owner.getRole()).thenReturn(ParticipantRole.OWNER);
    when(participantRepository.findById(ownerId)).thenReturn(Optional.of(owner));

    assertThatThrownBy(() -> service.removeCoOwnerByClient(expedienteId, ownerId))
        .isInstanceOf(UnprocessableException.class);
  }

  /** Antes el expediente se quedaba "En revisión" y el cliente seguía viendo "Listo". */
  @Test
  void newRequiredDocumentsAfterSubmittingAskTheClientForCorrections() {
    when(expediente.getStatus()).thenReturn(ExpedienteStatus.DOCUMENTS_RECEIVED);

    service.on(new RequiredDocumentsReopened(expedienteId));

    InOrder order = inOrder(expediente);
    order.verify(expediente).transitionToIfAllowed(ExpedienteStatus.UNDER_REVIEW);
    order.verify(expediente).transitionToIfAllowed(ExpedienteStatus.CORRECTIONS_REQUESTED);
    verify(expediente).markRequiredDocumentsPending();
  }
}
