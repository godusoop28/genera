package com.c21genera.extraction.application;

import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.UUID;

public record ExtractDocumentFieldsPayload(
    UUID expedienteId, UUID documentId, UUID documentVersionId, DocumentTypeCode type, String pdfStorageKey) {}
