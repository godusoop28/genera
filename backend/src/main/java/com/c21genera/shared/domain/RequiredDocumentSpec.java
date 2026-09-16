package com.c21genera.shared.domain;

import java.util.UUID;

/**
 * Salida de {@code expedientes.domain.DocumentRequirementPolicy}: qué
 * documento se requiere, si es condicional y a qué participante corresponde
 * (si aplica). Vive en shared porque viaja dentro de un evento de
 * integración consumido por el módulo documents (ver AGENTS §21/§88).
 */
public record RequiredDocumentSpec(
    String requirementCode, DocumentTypeCode type, boolean required, boolean conditional, UUID participantId) {}
