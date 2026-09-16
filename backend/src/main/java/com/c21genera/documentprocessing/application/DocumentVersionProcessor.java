package com.c21genera.documentprocessing.application;

import com.c21genera.documentprocessing.domain.DocumentQualityAnalyzer;
import com.c21genera.documentprocessing.domain.ImageNormalizer;
import com.c21genera.documentprocessing.domain.ImageNormalizer.NormalizedImage;
import com.c21genera.documentprocessing.infrastructure.PdfAssembler;
import com.c21genera.documentprocessing.infrastructure.PdfAssembler.PageContent;
import com.c21genera.documentprocessing.infrastructure.ProcessedStorageKeys;
import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.PageView;
import com.c21genera.shared.events.DocumentEvents.DocumentVersionProcessed;
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
 * -> QUALITY_ANALYSIS -> PDF_GENERATION (ver AGENTS §34-37). Publica
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

  @Transactional
  public void process(ProcessDocumentVersionPayload payload) {
    documentsApi.markProcessing(payload.documentVersionId());

    List<PageView> pages = documentsApi.pagesOf(payload.documentVersionId());
    if (pages.isEmpty()) {
      documentsApi.markFailed(payload.documentVersionId(), "La versión no tiene páginas cargadas");
      return;
    }

    List<String> qualityIssues = new ArrayList<>();
    List<NormalizedPage> normalizedPages = new ArrayList<>();
    List<PageContent> pdfPages = new ArrayList<>();

    for (PageView page : pages) {
      byte[] original = readAll(page.storageKeyOriginal());

      if ("application/pdf".equals(page.mimeType())) {
        pdfPages.add(new PageContent(original, page.mimeType()));
        continue;
      }

      NormalizedImage normalized = normalizer.normalize(original, page.mimeType());
      DocumentQualityAnalyzer.QualityResult quality =
          qualityAnalyzer.analyze(normalized.content(), normalized.mimeType(), normalized.widthPx(), normalized.heightPx());
      if (!quality.acceptable()) {
        qualityIssues.addAll(quality.issues());
      }

      String normalizedKey = ProcessedStorageKeys.normalizedPageKey(payload.documentVersionId(), page.pageNumber());
      fileStorage.store(
          normalizedKey,
          new ByteArrayInputStream(normalized.content()),
          normalized.content().length,
          normalized.mimeType());
      normalizedPages.add(new NormalizedPage(page.pageNumber(), normalized.content(), normalizedKey));
      pdfPages.add(new PageContent(normalized.content(), normalized.mimeType()));
    }

    if (!qualityIssues.isEmpty()) {
      documentsApi.markQualityFailed(payload.documentVersionId(), String.join("; ", qualityIssues));
      return;
    }

    byte[] pdfBytes = pdfAssembler.assemble(pdfPages);
    String pdfKey = ProcessedStorageKeys.pdfKey(payload.documentVersionId());
    storeBytes(pdfKey, pdfBytes, "application/pdf");

    String manifestJson = writeManifest(normalizedPages);
    String manifestKey = ProcessedStorageKeys.manifestKey(payload.documentVersionId());
    storeBytes(manifestKey, manifestJson.getBytes(StandardCharsets.UTF_8), "application/json");

    documentsApi.markProcessed(payload.documentVersionId(), pdfKey, manifestKey);

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
