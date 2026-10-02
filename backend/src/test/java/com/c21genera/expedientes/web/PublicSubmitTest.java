package com.c21genera.expedientes.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.expedientes.CivilStatus;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.application.ExpedienteService;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.domain.ExpedienteParticipant;
import com.c21genera.expedientes.domain.ManualClientData;
import com.c21genera.expedientes.domain.RequiredDocumentsPendingException;
import com.c21genera.publicaccess.PublicAccessTokenApi;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * E2E 02/10: el cliente subió sus 12 documentos a la vez desde su liga y al
 * enviar recibió "Todavía faltan documentos obligatorios". El envío ahora se
 * valida contra los documentos mismos, no contra la bandera del evento.
 */
class PublicSubmitTest {

  private final PublicAccessTokenApi tokenApi = mock(PublicAccessTokenApi.class);
  private final ExpedienteService expedienteService = mock(ExpedienteService.class);
  private final DocumentsApi documentsApi = mock(DocumentsApi.class);
  private final PublicExpedienteController controller = new PublicExpedienteController(tokenApi, expedienteService, documentsApi);
  private final UUID expedienteId = UUID.randomUUID();
  private final Expediente expediente = mock(Expediente.class);

  @BeforeEach
  void setUp() {
    when(tokenApi.resolve("tok")).thenReturn(Optional.of(expedienteId));
    when(expedienteService.get(expedienteId)).thenReturn(expediente);
    when(expediente.getStatus()).thenReturn(ExpedienteStatus.WAITING_DOCUMENTS);
    when(expediente.getPersonType()).thenReturn(PersonType.FISICA);
    ManualClientData data = mock(ManualClientData.class);
    when(data.getEmail()).thenReturn("qa@example.com");
    when(data.getPhone()).thenReturn("7771234567");
    when(expedienteService.manualDataOf(expedienteId)).thenReturn(data);
    ExpedienteParticipant owner = mock(ExpedienteParticipant.class);
    when(owner.isOwner()).thenReturn(true);
    when(owner.getCivilStatus()).thenReturn(CivilStatus.CASADO);
    when(expedienteService.participantsOf(expedienteId)).thenReturn(List.of(owner));
  }

  @Test
  void submitSucceedsWhenAllDocumentsAreUploadedEvenIfTheFlagWasNeverSet() {
    when(expediente.isAllRequiredDocumentsUploaded()).thenReturn(false);
    when(documentsApi.allRequiredUploaded(expedienteId)).thenReturn(true);

    controller.submit("tok");

    verify(expedienteService).markAllRequiredDocumentsUploaded(expedienteId);
    verify(expedienteService).recordDocumentsSubmitted(expedienteId);
  }

  @Test
  void submitStillRefusesWhenADocumentIsReallyMissing() {
    when(expediente.isAllRequiredDocumentsUploaded()).thenReturn(true);
    when(documentsApi.allRequiredUploaded(expedienteId)).thenReturn(false);

    assertThatThrownBy(() -> controller.submit("tok")).isInstanceOf(RequiredDocumentsPendingException.class);
  }
}
