package com.c21genera.expedientes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import com.c21genera.expedientes.infrastructure.ExpedienteChangeRepository;
import com.c21genera.expedientes.infrastructure.ExpedienteParticipantRepository;
import com.c21genera.expedientes.infrastructure.ExpedienteRepository;
import com.c21genera.expedientes.infrastructure.ManualClientDataRepository;
import com.c21genera.propertycatalog.PropertyCatalog;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Control de Cambios #26: clasificación del inmueble con el catálogo de categorías y subcategorías. */
class PropertyClassificationTest {

  private static final UUID TERRENOS = UUID.randomUUID();
  private static final PropertyCatalog.Subtype TERRENO_HABITACIONAL =
      new PropertyCatalog.Subtype(UUID.randomUUID(), TERRENOS, "Terrenos", "Terreno habitacional", 10, true, false, "RESIDENTIAL_LAND", "habitacional", "terreno");
  private static final PropertyCatalog.Subtype TERRENO_COMERCIAL =
      new PropertyCatalog.Subtype(UUID.randomUUID(), TERRENOS, "Terrenos", "Terreno comercial", 20, true, false, "COMMERCIAL", "comercial", "terreno");
  private static final PropertyCatalog.Subtype OTRO =
      new PropertyCatalog.Subtype(UUID.randomUUID(), UUID.randomUUID(), "Especiales", "Otro — especificar", 30, true, true, "RESIDENTIAL_LAND", "comercial", "inmueble-productivo");

  private final ExpedienteRepository expedienteRepository = mock(ExpedienteRepository.class);
  private final ExpedienteChangeRepository changeRepository = mock(ExpedienteChangeRepository.class);
  private final PropertyCatalog catalog = mock(PropertyCatalog.class);
  private final ExpedienteService service =
      new ExpedienteService(
          expedienteRepository,
          mock(ExpedienteParticipantRepository.class),
          mock(ManualClientDataRepository.class),
          changeRepository,
          mock(ApplicationEventPublisher.class),
          new ObjectMapper(),
          catalog,
          Clock.systemUTC());
  private final Actor director = Actor.staff(UUID.randomUUID(), "Carmen Directora", "DIRECTOR");

  /** Terreno capturado antes del catálogo: conserva su categoría y queda pendiente de clasificación. */
  private Expediente pendingLand() {
    Expediente e =
        new Expediente(
            "EXP-2026-000002", "Juan Pérez", PersonType.FISICA, SignerCharacter.PROPIETARIO, AccreditationType.ESCRITURA_PUBLICA, false,
            PropertyCaseType.RESIDENTIAL_LAND, OperationType.SALE, PropertyLegalStatus.EN_REVISION, null, null, UUID.randomUUID(), null, null);
    e.classifyProperty(new Expediente.PropertyClassification(TERRENOS, "Terrenos", null, null, null));
    when(expedienteRepository.findById(e.getId())).thenReturn(Optional.of(e));
    for (PropertyCatalog.Subtype s : new PropertyCatalog.Subtype[] {TERRENO_HABITACIONAL, TERRENO_COMERCIAL, OTRO}) {
      when(catalog.subtype(s.id())).thenReturn(Optional.of(s));
    }
    return e;
  }

  @Test
  void pendingRecordShowsItsCategoryWithoutGuessingTheSubtype() {
    Expediente e = pendingLand();

    assertThat(e.propertyTypeLabel()).isEqualTo("Terrenos → Pendiente de clasificación");
  }

  @Test
  void classifyingAPendingRecordKeepsHistory() {
    Expediente e = pendingLand();

    service.classifyProperty(e.getId(), TERRENO_HABITACIONAL.id(), null, director, "Uso habitacional según escritura");

    assertThat(e.propertyTypeLabel()).isEqualTo("Terrenos → Terreno habitacional");
    assertThat(e.getPropertySubtypeId()).isEqualTo(TERRENO_HABITACIONAL.id());
    ArgumentCaptor<ExpedienteChange> change = ArgumentCaptor.forClass(ExpedienteChange.class);
    verify(changeRepository).save(change.capture());
    assertThat(change.getValue().getOldValue()).isEqualTo("Terrenos → Pendiente de clasificación");
    assertThat(change.getValue().getNewValue()).isEqualTo("Terrenos → Terreno habitacional");
  }

  @Test
  void quickClassificationCannotChangeDocumentsOrContract() {
    Expediente e = pendingLand();

    assertThatThrownBy(() -> service.classifyProperty(e.getId(), TERRENO_COMERCIAL.id(), null, director, null)).isInstanceOf(ConflictException.class);
    assertThat(e.getPropertySubtypeId()).isNull();
  }

  @Test
  void otherRequiresADescription() {
    Expediente e = pendingLand();

    assertThatThrownBy(() -> service.classifyProperty(e.getId(), OTRO.id(), "  ", director, null))
        .isInstanceOf(UnprocessableException.class)
        .hasMessageContaining("Describe");

    service.classifyProperty(e.getId(), OTRO.id(), "Capilla", director, null);
    assertThat(e.propertyTypeLabel()).isEqualTo("Especiales → Otro: Capilla");
  }

  /**
   * Caso real (EXP-2026-000030): el personal aceptó o marcó "No aplica" todo sin que el cliente oprimiera
   * "Enviar" en su liga. Antes se quedaba en "Esperando documentos" y no se podía firmar la recepción.
   */
  @Test
  void approvingEverythingWhileStillWaitingForTheClientReachesDocumentsApproved() {
    Expediente e = pendingLand();
    e.transitionToIfAllowed(com.c21genera.expedientes.ExpedienteStatus.WAITING_PRIVACY);
    e.transitionToIfAllowed(com.c21genera.expedientes.ExpedienteStatus.WAITING_DOCUMENTS);

    service.recordDocumentsApproved(e.getId());

    assertThat(e.getStatus()).isEqualTo(com.c21genera.expedientes.ExpedienteStatus.DOCUMENTS_APPROVED);
  }

  @Test
  void aSubtypeIsRequired() {
    Expediente e = pendingLand();
    when(catalog.subtype(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.classifyProperty(e.getId(), UUID.randomUUID(), null, director, null)).isInstanceOf(UnprocessableException.class);
  }
}
