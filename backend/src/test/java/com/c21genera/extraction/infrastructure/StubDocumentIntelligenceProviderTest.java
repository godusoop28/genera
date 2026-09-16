package com.c21genera.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.extraction.domain.StructuredExtractionProvider.ExtractionResult;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Nunca debe llamar a una IA real: es el proveedor usado en tests/local (ver AGENTS §39). */
class StubDocumentIntelligenceProviderTest {

  private final StubDocumentIntelligenceProvider provider = new StubDocumentIntelligenceProvider();

  @Test
  void alwaysReturnsAnEmptyResultRegardlessOfInput() {
    ExtractionResult result = provider.extract(DocumentTypeCode.INE, new byte[]{1, 2, 3}, List.of("fullName", "curp"));

    assertThat(result.fields()).isEmpty();
    assertThat(result.warnings()).isEmpty();
  }
}
