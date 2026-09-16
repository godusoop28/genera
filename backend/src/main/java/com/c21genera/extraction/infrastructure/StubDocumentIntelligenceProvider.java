package com.c21genera.extraction.infrastructure;

import com.c21genera.extraction.domain.StructuredExtractionProvider;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementación local/test: nunca llama a un proveedor externo (ver AGENTS
 * §39, "nunca IA real en CI/tests"). Devuelve siempre un resultado vacío, de
 * modo que el pipeline de extracción se pueda probar de punta a punta sin
 * credenciales ni red.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "false", matchIfMissing = true)
public class StubDocumentIntelligenceProvider implements StructuredExtractionProvider {

  @Override
  public ExtractionResult extract(DocumentTypeCode type, byte[] pdfBytes, List<String> fieldNames) {
    return ExtractionResult.empty();
  }
}
