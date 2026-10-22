package com.c21genera.expedientes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.OperationType;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.PropertyLegalStatus;
import com.c21genera.expedientes.SignerCharacter;
import com.c21genera.expedientes.application.ExpedienteService.PortfolioFilter;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.domain.ExpedienteChange;
import com.c21genera.expedientes.infrastructure.ExpedienteChangeRepository;
import com.c21genera.expedientes.infrastructure.ExpedienteParticipantRepository;
import com.c21genera.expedientes.infrastructure.ExpedienteRepository;
import com.c21genera.expedientes.infrastructure.ManualClientDataRepository;
import com.c21genera.identity.UserDirectory;
import com.c21genera.identity.UserDirectory.StaffMember;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteDataCorrected;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Control de Cambios #24: asesor asignado por expediente y filtros de cartera. */
class AdvisorAssignmentTest {

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

  private final UUID ana = UUID.randomUUID();
  private final UUID luis = UUID.randomUUID();
  private final Actor director = Actor.staff(UUID.randomUUID(), "Carmen Directora", "DIRECTOR");

  private Expediente expedienteOf(UUID advisorId, String advisorName) {
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
            advisorId,
            advisorName);
    when(expedienteRepository.findById(e.getId())).thenReturn(Optional.of(e));
    return e;
  }

  @Test
  void reassigningRecordsPreviousAndNewAdvisorInTheHistoryWithoutTouchingTheContract() {
    Expediente e = expedienteOf(ana, "Ana Asesora");

    service.reassignAdvisor(e.getId(), luis, "Luis Asesor", director, "Ana cambió de zona");

    assertThat(e.getAssignedAdvisorId()).isEqualTo(luis);
    assertThat(e.getAssignedAdvisorName()).isEqualTo("Luis Asesor");
    ArgumentCaptor<ExpedienteChange> change = ArgumentCaptor.forClass(ExpedienteChange.class);
    verify(changeRepository).save(change.capture());
    assertThat(change.getValue().getField()).isEqualTo("Asesor asignado");
    assertThat(change.getValue().getOldValue()).isEqualTo("Ana Asesora");
    assertThat(change.getValue().getNewValue()).isEqualTo("Luis Asesor");
    assertThat(change.getValue().getActorName()).isEqualTo("Carmen Directora");
    assertThat(change.getValue().getChangedAt()).isNotNull();
    ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
    verify(events).publishEvent(event.capture());
    assertThat(((ExpedienteDataCorrected) event.getValue()).contractRelevant()).isFalse();
  }

  @Test
  void reassigningToTheSameAdvisorRecordsNothing() {
    Expediente e = expedienteOf(ana, "Ana Asesora");

    service.reassignAdvisor(e.getId(), ana, "Ana Asesora", director, null);

    verify(changeRepository, never()).save(any());
  }

  @Test
  void advisorCanBeChangedEvenAfterTheContractWasSigned() {
    Expediente e = expedienteOf(ana, "Ana Asesora");
    e.markContractSignedExternally();
    assertThat(e.getStatus()).isEqualTo(ExpedienteStatus.CONTRACT_SIGNED);

    service.reassignAdvisor(e.getId(), luis, "Luis Asesor", director, null);

    assertThat(e.getAssignedAdvisorId()).isEqualTo(luis);
  }

  @Test
  void portfolioFilterMatchesCategorySubtypeOperationAndAdvisor() {
    UUID habitacional = UUID.randomUUID();
    UUID casa = UUID.randomUUID();
    UUID terrenos = UUID.randomUUID();
    Object[] houseSaleAna = {PropertyCaseType.HOUSING, OperationType.SALE, ana, "Ana", ExpedienteStatus.DRAFT, 2L, habitacional, casa};
    Object[] unassignedRentPendingLand = {PropertyCaseType.RESIDENTIAL_LAND, OperationType.RENT, null, null, ExpedienteStatus.DRAFT, 1L, terrenos, null};

    assertThat(ExpedienteService.matches(houseSaleAna, new PortfolioFilter(habitacional, null, false, OperationType.SALE, ana, false))).isTrue();
    assertThat(ExpedienteService.matches(houseSaleAna, new PortfolioFilter(habitacional, casa, false, null, null, false))).isTrue();
    assertThat(ExpedienteService.matches(houseSaleAna, new PortfolioFilter(terrenos, null, false, null, null, false))).isFalse();
    assertThat(ExpedienteService.matches(houseSaleAna, new PortfolioFilter(null, null, false, OperationType.RENT, null, false))).isFalse();
    assertThat(ExpedienteService.matches(houseSaleAna, new PortfolioFilter(null, null, false, null, luis, false))).isFalse();
    assertThat(ExpedienteService.matches(unassignedRentPendingLand, new PortfolioFilter(null, null, false, null, null, true))).isTrue();
    assertThat(ExpedienteService.matches(houseSaleAna, new PortfolioFilter(null, null, false, null, null, true))).isFalse();
    // Pendientes de clasificación: sin subcategoría.
    assertThat(ExpedienteService.matches(unassignedRentPendingLand, new PortfolioFilter(terrenos, null, true, null, null, false))).isTrue();
    assertThat(ExpedienteService.matches(houseSaleAna, new PortfolioFilter(null, null, true, null, null, false))).isFalse();
    assertThat(ExpedienteService.matches(unassignedRentPendingLand, PortfolioFilter.NONE)).isTrue();
  }

  @Test
  void onlyActiveCustomerFacingStaffCanBeAssignedAndAdvisorsComeFirst() {
    UserDirectory directory = mock(UserDirectory.class);
    UUID reviewer = UUID.randomUUID();
    UUID admin = UUID.randomUUID();
    when(directory.activeStaff())
        .thenReturn(
            List.of(
                new StaffMember(admin, "Admin", "a@x.mx", "ADMINISTRATOR"),
                new StaffMember(reviewer, "Revisora", "r@x.mx", "DOCUMENT_REVIEWER"),
                new StaffMember(luis, "Luis", "l@x.mx", "ADVISOR"),
                new StaffMember(ana, "Ana", "an@x.mx", "ADVISOR")));
    AssignableAdvisors advisors = new AssignableAdvisors(directory);

    assertThat(advisors.list()).extracting(StaffMember::name).containsExactly("Ana", "Luis", "Admin");
    assertThatThrownBy(() -> advisors.require(reviewer)).isInstanceOf(UnprocessableException.class);
    assertThatThrownBy(() -> advisors.require(UUID.randomUUID())).isInstanceOf(UnprocessableException.class);
    assertThatThrownBy(() -> advisors.require(null)).isInstanceOf(UnprocessableException.class);
    assertThat(advisors.require(ana).name()).isEqualTo("Ana");
  }
}
