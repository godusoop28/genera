package com.c21genera.expedientes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.c21genera.expedientes.domain.PropertyKey;
import com.c21genera.expedientes.infrastructure.ExpedienteChangeRepository;
import com.c21genera.expedientes.infrastructure.ExpedienteRepository;
import com.c21genera.propertycatalog.PropertyCatalog;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Control de Cambios #27 y su adenda: clave estructurada, asignación manual y claves en uso. */
class PropertyKeyServiceTest {

  private static final UUID HABITACIONAL = UUID.randomUUID();
  private static final PropertyCatalog.Subtype CASA =
      new PropertyCatalog.Subtype(UUID.randomUUID(), HABITACIONAL, "Habitacional", "Casa", 10, true, false, "HOUSING", "habitacional", "casa", "C");

  private final ExpedienteRepository expedienteRepository = mock(ExpedienteRepository.class);
  private final ExpedienteChangeRepository changeRepository = mock(ExpedienteChangeRepository.class);
  private final PropertyCatalog catalog = mock(PropertyCatalog.class);
  private final PropertyKeyService service =
      new PropertyKeyService(expedienteRepository, changeRepository, catalog, mock(ApplicationEventPublisher.class), Clock.systemUTC());
  private final Actor admin = Actor.staff(UUID.randomUUID(), "Admin", "ADMINISTRATOR");

  PropertyKeyServiceTest() {
    when(catalog.subtype(CASA.id())).thenReturn(Optional.of(CASA));
  }

  private Expediente casaEnVenta(String advisorName) {
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
            "Av. Patria 100",
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            advisorName);
    e.classifyProperty(new Expediente.PropertyClassification(HABITACIONAL, "Habitacional", CASA.id(), "Casa", null));
    when(expedienteRepository.findById(e.getId())).thenReturn(Optional.of(e));
    return e;
  }

  @Test
  void keysAreNormalizedAndSplitIntoCombinationAndConsecutive() {
    PropertyKey key = PropertyKey.parse(" cvea-3 ").orElseThrow();
    assertThat(key.value()).isEqualTo("CVEA03");
    assertThat(key.prefix()).isEqualTo("CVEA");
    assertThat(key.number()).isEqualTo(3);
    assertThat(PropertyKey.parse("CVEA")).isEmpty();
    assertThat(PropertyKey.parse("03")).isEmpty();
    assertThat(PropertyKey.parse("CVEA00")).isEmpty();
  }

  @Test
  void initialsAreFirstNameAndFirstSurname() {
    assertThat(PropertyKey.initialsOf("Emiliano Álvarez")).isEqualTo("EA");
    assertThat(PropertyKey.initialsOf("Juan Pérez López")).isEqualTo("JP");
    assertThat(PropertyKey.initialsOf("Juan Carlos Pérez López")).isEqualTo("JP");
    assertThat(PropertyKey.initialsOf("María de la Luz Rodríguez Gómez")).isEqualTo("MR");
    assertThat(PropertyKey.prefixFor("C", OperationType.RENT, "Emiliano Álvarez")).contains("CREA");
  }

  @Test
  void automaticKeyContinuesFromTheHighestConsecutiveOfTheCombination() {
    Expediente e = casaEnVenta("Emiliano Álvarez");
    when(expedienteRepository.maxPropertyKeyNumber("CVEA")).thenReturn(5);

    service.assignAutomatically(e);

    assertThat(e.getPropertyKey()).isEqualTo("CVEA06");
  }

  @Test
  void firstKeyOfACombinationIs01() {
    Expediente e = casaEnVenta("Emiliano Álvarez");

    service.assignAutomatically(e);

    assertThat(e.getPropertyKey()).isEqualTo("CVEA01");
  }

  @Test
  void checkReportsWhichExpedienteAlreadyHasTheKey() {
    Expediente holder = casaEnVenta("Emiliano Álvarez");
    holder.assignPropertyKey(new PropertyKey("CVEA", 3));
    when(expedienteRepository.findByPropertyKey("CVEA03")).thenReturn(Optional.of(holder));

    PropertyKeyService.KeyCheck check = service.check("cvea03", UUID.randomUUID());

    assertThat(check.valid()).isTrue();
    assertThat(check.available()).isFalse();
    assertThat(check.assignedTo().folio()).isEqualTo("EXP-2026-000001");
    assertThat(check.message()).contains("CVEA03").contains("Juan Pérez").contains("Av. Patria 100");
    // La clave que ya tiene el mismo expediente no es un conflicto.
    assertThat(service.check("CVEA03", holder.getId()).available()).isTrue();
    assertThat(service.check("CVEA04", null).message()).isEqualTo("La clave CVEA04 está disponible.");
    assertThat(service.check("hola", null).valid()).isFalse();
  }

  @Test
  void manualAssignmentRespectsTheCapturedKeyAndRecordsIt() {
    Expediente e = casaEnVenta("Emiliano Álvarez");

    service.assign(e.getId(), "CVEA07", admin, "Migración");

    assertThat(e.getPropertyKey()).isEqualTo("CVEA07");
    assertThat(e.getPropertyKeyNumber()).isEqualTo(7);
    ArgumentCaptor<ExpedienteChange> change = ArgumentCaptor.forClass(ExpedienteChange.class);
    verify(changeRepository).save(change.capture());
    assertThat(change.getValue().getField()).isEqualTo("Clave del inmueble");
    assertThat(change.getValue().getNewValue()).isEqualTo("CVEA07");
  }

  @Test
  void theSameKeyCannotBeAssignedToTwoExpedientes() {
    Expediente holder = casaEnVenta("Emiliano Álvarez");
    holder.assignPropertyKey(new PropertyKey("CVEA", 3));
    when(expedienteRepository.findByPropertyKey("CVEA03")).thenReturn(Optional.of(holder));
    Expediente other = casaEnVenta("Emiliano Álvarez");

    assertThatThrownBy(() -> service.assign(other.getId(), "CVEA03", admin, null)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.assign(other.getId(), "03", admin, null)).isInstanceOf(UnprocessableException.class);
    assertThat(other.getPropertyKey()).isNull();
    verify(changeRepository, never()).save(any());
  }

  @Test
  void combinationListsUsedConsecutivesGapsAndNext() {
    PropertyKeyService.KeyCombination c = PropertyKeyService.combinationOf("CVEA", new TreeSet<>(List.of(1, 3, 5)));

    assertThat(c.used()).containsExactly(1, 3, 5);
    assertThat(c.gaps()).containsExactly(2, 4);
    assertThat(c.nextKey()).isEqualTo("CVEA06");
  }

  @Test
  void overviewSuggestsDistinctKeysForExpedientesWithoutOne() {
    Expediente withKey = casaEnVenta("Emiliano Álvarez");
    withKey.assignPropertyKey(new PropertyKey("CVEA", 2));
    Expediente a = casaEnVenta("Emiliano Álvarez");
    Expediente b = casaEnVenta("Emiliano Álvarez");
    when(expedienteRepository.findByPropertyKeyIsNotNull()).thenReturn(List.of(withKey));
    when(expedienteRepository.findByPropertyKeyIsNull()).thenReturn(List.of(a, b));

    PropertyKeyService.KeyOverview overview = service.overview(new PropertyKeyService.KeyFilter(null, null, false, null, null, null));

    assertThat(overview.assigned()).extracting(PropertyKeyService.AssignedKey::key).containsExactly("CVEA02");
    assertThat(overview.withoutKey()).extracting(PropertyKeyService.PendingExpediente::suggestedKey).containsExactly("CVEA03", "CVEA04");
    assertThat(overview.combinations()).singleElement().satisfies(c -> assertThat(c.gaps()).containsExactly(1));
  }
}
