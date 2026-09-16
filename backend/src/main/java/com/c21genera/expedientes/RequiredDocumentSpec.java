package com.c21genera.expedientes;

import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.UUID;

/**
 * Salida de {@code DocumentRequirementPolicy}: qué documento se requiere,
 * si es condicional y a qué participante corresponde (si aplica). Es la API
 * pública que el módulo documents consume para materializar sus propios
 * registros de {@code Document} (ver AGENTS §21/§88 sobre comunicación
 * entre módulos vía API pública/eventos).
 */
public record RequiredDocumentSpec(
    String requirementCode, DocumentTypeCode type, boolean required, boolean conditional, UUID participantId) {}
