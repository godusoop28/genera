package com.c21genera.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.extraction.domain.CrossDocumentValidator.DetectedConflict;
import com.c21genera.extraction.domain.CrossDocumentValidator.FieldValue;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrossDocumentValidatorTest {

  @Test
  void noConflictWhenAllValuesMatchIgnoringCaseAndSpacing() {
    List<FieldValue> values =
        List.of(
            new FieldValue("fullName", "Juan Pérez López", "INE"),
            new FieldValue("fullName", "juan   pérez lópez", "TAX_STATUS_CERTIFICATE"));

    List<DetectedConflict> conflicts = CrossDocumentValidator.validate(values);

    assertThat(conflicts).isEmpty();
  }

  @Test
  void detectsConflictWhenValuesDiffer() {
    List<FieldValue> values =
        List.of(
            new FieldValue("propertyAddress", "Calle Falsa 123", "DEED"),
            new FieldValue("propertyAddress", "Avenida Siempre Viva 742", "PROPERTY_TAX"));

    List<DetectedConflict> conflicts = CrossDocumentValidator.validate(values);

    assertThat(conflicts).hasSize(1);
    assertThat(conflicts.get(0).fieldName()).isEqualTo("propertyAddress");
  }

  @Test
  void ignoresBlankValues() {
    List<FieldValue> values =
        List.of(new FieldValue("fullName", "Juan Pérez", "INE"), new FieldValue("fullName", "  ", "PROOF_OF_ADDRESS"));

    assertThat(CrossDocumentValidator.validate(values)).isEmpty();
  }

  @Test
  void differentFieldNamesAreNeverCompared() {
    List<FieldValue> values =
        List.of(new FieldValue("fullName", "Juan Pérez", "INE"), new FieldValue("ownerFullName", "Otro Nombre", "DEED"));

    assertThat(CrossDocumentValidator.validate(values)).isEmpty();
  }
}
