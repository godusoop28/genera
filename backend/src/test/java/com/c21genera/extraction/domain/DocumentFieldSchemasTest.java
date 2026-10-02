package com.c21genera.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.shared.domain.DocumentTypeCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DocumentFieldSchemasTest {

  @ParameterizedTest
  @EnumSource(DocumentTypeCode.class)
  void everyDocumentTypeHasAnExtractionSchemaWithDescribedFields(DocumentTypeCode type) {
    assertThat(DocumentFieldSchemas.fieldsFor(type)).as("campos de %s", type).isNotEmpty();
    // Cada campo lleva su descripción para la IA ("campo (qué es)").
    assertThat(DocumentFieldSchemas.describe(DocumentFieldSchemas.fieldsFor(type))).allMatch(d -> d.contains("("));
  }

  @Test
  void otherDocumentsGetAGenericExtraction() {
    assertThat(DocumentFieldSchemas.fieldsFor(DocumentTypeCode.OTHER)).contains("personNames", "addresses", "dates", "referenceNumbers");
  }

  @Test
  void ineHasAllExpectedFieldsAndReadingHintsForEveryDesign() {
    assertThat(DocumentFieldSchemas.fieldsFor(DocumentTypeCode.INE))
        .containsExactly("fullName", "curp", "electorKey", "birthDate", "address", "expirationYear");
    assertThat(DocumentFieldSchemas.readingHints(DocumentTypeCode.INE)).contains("IFE").contains("reverso").contains("horizontal");
  }
}
