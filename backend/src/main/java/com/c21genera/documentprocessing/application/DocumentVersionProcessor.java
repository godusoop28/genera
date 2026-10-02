package com.c21genera.documentprocessing.application;

import com.c21genera.documentprocessing.domain.DocumentQualityAnalyzer;
import com.c21genera.documentprocessing.domain.DocumentQualityAnalyzer.QualityLevel;
import com.c21genera.documentprocessing.domain.DocumentQualityAnalyzer.QualityResult;
import com.c21genera.documentprocessing.domain.ImageNormalizer;
import com.c21genera.documentprocessing.domain.ImageNormalizer.NormalizedImage;
import com.c21genera.documentprocessing.infrastructure.PdfAssembler;
import com.c21genera.documentprocessing.infrastructure.PdfAssembler.PageContent;
import com.c21genera.documentprocessing.infrastructure.ProcessedStorageKeys;
import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.PageView;
import com.c21genera.shared.events.DocumentEvents.DocumentQualityFailed;
import com.c21genera.shared.events.DocumentEvents.DocumentVersionProcessed;
import com.c21genera.shared.pdf.PdfFiles;
import com.c21genera.shared.storage.FileStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pipeline síncrono ejecutado por el worker para una versión de documento:
 * FILE_VALIDATION (implícita, ya hecha con Tika al subir) -> IMAGE_NORMALIZATION
 * -> QUALITY_ANALYSIS (advertencias, no rechazos) -> PDF_GENERATION (ver AGENTS §34-37). Publica
 * {@link DocumentVersionProcessed} para que extraction continúe con OCR.
 */
@Service
public class DocumentVersionProcessor {

  private record NormalizedPage(int pageNumber, byte[] jpeg, String storageKey) {}

  private final DocumentsApi documentsApi;
  private final FileStorage fileStorage;
  private final ImageNormalizer normalizer;
  private final DocumentQualityAnalyzer qualityAnalyzer;
  private final PdfAssembler pdfAssembler;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher events;

  public DocumentVersionProcessor(
      DocumentsApi documentsApi,
      FileStorage fileStorage,
      ImageNormalizer normalizer,
      DocumentQualityAnalyzer qualityAnalyzer,
      PdfAssembler pdfAssembler,
      ObjectMapper objectMapper,
      ApplicationEventPublisher events) {
    this.documentsApi = documentsApi;
    this.fileStorage = fileStorage;
    this.normalizer = normalizer;
    this.qualityAnalyzer = qualityAnalyzer;
    this.pdfAssembler = pdfAssembler;
    this.objectMapper = objectMapper;
    this.events = events;
  }

  /**
   * Aceptar y procesar todo lo posible: una página con advertencias de calidad
   * (oscura, algo borrosa, resolución baja) sigue al PDF y a la extracción. La
   * versión solo queda como QUALITY_FAILED si NINGUNA página se puede usar, y
   * como FAILED si el archivo está dañado y no se puede abrir.
   */
  @Transactional
  public void process(ProcessDocumentVersionPayload payload) {
    documentsApi.markProcessing(payload.documentVersionId());

    List<PageView> pages = documentsApi.pagesOf(payload.documentVersionId());
    if (pages.isEmpty()) {
      documentsApi.markFailed(payload.documentVersionId(), "La versión no tiene páginas cargadas");
      return;
    }

    List<String> warnings = new ArrayList<>();
    List<String> unreadableReasons = new ArrayList<>();
    List<String> damagedReasons = new ArrayList<>();
    List<NormalizedPage> normalizedPages = new ArrayList<>();
    List<PageContent> pdfPages = new ArrayList<>();
    byte[] singlePdfOriginal = null;
    String singlePdfKey = null;
    int usablePages = 0;

    for (PageView page : pages) {
      String prefix = pages.size() > 1 ? "Archivo " + page.pageNumber() + ": " : "";
      byte[] original = readAll(page.storageKeyOriginal());

      if ("application/pdf".equals(page.mimeType())) {
        try {
          PdfFiles.pageCount(original);
        } catch (PdfFiles.UnreadablePdfException e) {
          damagedReasons.add(prefix + e.getMessage());
          continue;
        }
        pdfPages.add(new PageContent(original, page.mimeType()));
        singlePdfOriginal = original;
        singlePdfKey = page.storageKeyOriginal();
        usablePages++;
        continue;
      }

      NormalizedImage normalized;
      try {
        normalized = normalizer.normalize(original, page.mimeType());
      } catch (ImageNormalizer.UnreadableImageException e) {
        damagedReasons.add(prefix + "la imagen está dañada o en un formato que no se puede abrir");
        continue;
      }
      QualityResult quality =
          qualityAnalyzer.analyze(normalized.content(), normalized.mimeType(), normalized.widthPx(), normalized.heightPx(), payload.type());
      if (quality.level() == QualityLevel.UNREADABLE) {
        unreadableReasons.addAll(quality.issues().stream().map(i -> prefix + i).toList());
        // Se conserva en el PDF: el revisor la ve y, si alguien la puede leer, decide.
      } else {
        usablePages++;
        warnings.addAll(quality.issues().stream().map(i -> prefix + i).toList());
      }

      String normalizedKey = ProcessedStorageKeys.normalizedPageKey(payload.documentVersionId(), page.pageNumber());
      fileStorage.store(normalizedKey, new ByteArrayInputStream(normalized.content()), normalized.content().length, normalized.mimeType());
      normalizedPages.add(new NormalizedPage(page.pageNumber(), normalized.content(), normalizedKey));
      pdfPages.add(new PageContent(normalized.content(), normalized.mimeType()));
    }

    if (usablePages == 0) {
      if (!unreadableReasons.isEmpty()) {
        String reason = String.join("; ", unreadableReasons.stream().distinct().toList());
        documentsApi.markQualityFailed(payload.documentVersionId(), reason);
        events.publishEvent(
            new DocumentQualityFailed(payload.expedienteId(), payload.documentId(), payload.documentVersionId(), payload.type(), reason));
      } else {
        documentsApi.markFailed(payload.documentVersionId(), String.join("; ", damagedReasons.stream().distinct().toList()));
      }
      return;
    }
    // Alguna página no sirve pero otras sí: se procesa lo que se puede y se avisa.
    warnings.addAll(unreadableReasons.stream().map(r -> r + " (se procesó el resto)").toList());
    warnings.addAll(damagedReasons.stream().map(r -> r + " (se omitió)").toList());

    String pdfKey;
    if (pdfPages.size() == 1 && singlePdfOriginal != null) {
      // Un solo PDF: ya es el documento final; no se duplica (puede pesar decenas de MB).
      pdfKey = singlePdfKey;
    } else {
      byte[] pdfBytes = pdfAssembler.assemble(pdfPages);
      pdfKey = ProcessedStorageKeys.pdfKey(payload.documentVersionId());
      storeBytes(pdfKey, pdfBytes, "application/pdf");
    }

    String manifestJson = writeManifest(normalizedPages);
    String manifestKey = ProcessedStorageKeys.manifestKey(payload.documentVersionId());
    storeBytes(manifestKey, manifestJson.getBytes(StandardCharsets.UTF_8), "application/json");

    List<String> distinctWarnings = warnings.stream().distinct().toList();
    documentsApi.markProcessed(
        payload.documentVersionId(),
        pdfKey,
        manifestKey,
        distinctWarnings.isEmpty() ? QualityLevel.ACCEPTED.name() : QualityLevel.ACCEPTED_WITH_WARNINGS.name(),
        distinctWarnings);

    events.publishEvent(
        new DocumentVersionProcessed(payload.expedienteId(), payload.documentId(), payload.documentVersionId(), payload.type(), pdfKey));
  }

  private byte[] readAll(String storageKey) {
    try (InputStream in = fileStorage.get(storageKey)) {
      return in.readAllBytes();
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo leer la página original " + storageKey, e);
    }
  }

  private void storeBytes(String key, byte[] content, String mimeType) {
    fileStorage.store(key, new ByteArrayInputStream(content), content.length, mimeType);
  }

  private String writeManifest(List<NormalizedPage> pages) {
    record ManifestEntry(int pageNumber, String storageKey, String mimeType) {}
    List<ManifestEntry> entries = pages.stream().map(p -> new ManifestEntry(p.pageNumber(), p.storageKey(), "image/jpeg")).toList();
    try {
      return objectMapper.writeValueAsString(entries);
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo generar el manifiesto de páginas normalizadas", e);
    }
  }
}
