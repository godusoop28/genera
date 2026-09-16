package com.c21genera.documentprocessing.application;

import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.UUID;

public record ProcessDocumentVersionPayload(
    UUID expedienteId, UUID documentId, UUID documentVersionId, DocumentTypeCode type) {}
