package com.c21genera.documentprocessing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.documentprocessing.infrastructure.HeuristicDocumentQualityAnalyzer;
import com.c21genera.documentprocessing.infrastructure.ImageIoImageNormalizer;
import com.c21genera.documentprocessing.infrastructure.PdfAssembler;
import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.PageView;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.events.DocumentEvents.DocumentQualityFailed;
import com.c21genera.shared.events.DocumentEvents.DocumentVersionProcessed;
import com.c21genera.shared.storage.FileStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Pipeline de procesamiento con los componentes reales de calidad, normalización y PDF; solo el almacenamiento es falso. */
class DocumentVersionProcessorTest {

  private final DocumentsApi documentsApi = mock(DocumentsApi.class);
  private final FileStorage storage = mock(FileStorage.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final Map<String, byte[]> files = new HashMap<>();
  private DocumentVersionProcessor processor;

  private final UUID expedienteId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();
  private final UUID versionId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    when(storage.get(anyString())).thenAnswer(inv -> new ByteArrayInputStream(files.get(inv.<String>getArgument(0))));
    when(storage.store(anyString(), any(), anyLong(), anyString()))
        .thenAnswer(
            inv -> {
              files.put(inv.getArgument(0), ((ByteArrayInputStream) inv.getArgument(1)).readAllBytes());
              return new FileStorage.StoredObjectMetadata(inv.getArgument(0), 0, "x");
            });
    processor =
        new DocumentVersionProcessor(
            documentsApi, storage, new ImageIoImageNormalizer(), new HeuristicDocumentQualityAnalyzer(), new PdfAssembler(), new ObjectMapper(), events);
  }

  private ProcessDocumentVersionPayload payload(DocumentTypeCode type) {
    return new ProcessDocumentVersionPayload(expedienteId, documentId, versionId, type);
  }

  private void pages(Object... keyAndMime) {
    List<PageView> views = new java.util.ArrayList<>();
    for (int i = 0; i < keyAndMime.length; i += 2) {
      views.add(new PageView(i / 2 + 1, (String) keyAndMime[i], (String) keyAndMime[i + 1]));
    }
    when(documentsApi.pagesOf(versionId)).thenReturn(views);
  }

  @Test
  void aMultiPagePdfIsAcceptedAndUsedAsTheFinalDocumentWithoutDuplicatingIt() throws Exception {
    files.put("orig/escritura.pdf", pdfWithPages(30));
    pages("orig/escritura.pdf", "application/pdf");

    processor.process(payload(DocumentTypeCode.DEED));

    verify(documentsApi).markProcessed(eq(versionId), eq("orig/escritura.pdf"), anyString(), eq("ACCEPTED"), eq(List.of()));
    verify(events).publishEvent(any(DocumentVersionProcessed.class));
  }

  @Test
  void aHorizontalPhotoGoesStraightToExtraction() throws Exception {
    files.put("orig/acta.jpg", jpeg(document(1600, 1200)));
    pages("orig/acta.jpg", "image/jpeg");

    processor.process(payload(DocumentTypeCode.MARRIAGE_CERTIFICATE));

    verify(documentsApi).markProcessed(eq(versionId), anyString(), anyString(), eq("ACCEPTED"), eq(List.of()));
    verify(documentsApi, never()).markQualityFailed(any(), anyString());
  }

  @Test
  void oneUnreadablePageAmongGoodOnesIsAWarningAndTheRestIsProcessed() throws Exception {
    files.put("orig/p1.jpg", jpeg(document(1200, 1600)));
    files.put("orig/p2.jpg", jpeg(gray(1200, 1600)));
    pages("orig/p1.jpg", "image/jpeg", "orig/p2.jpg", "image/jpeg");

    processor.process(payload(DocumentTypeCode.DEED));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> warnings = ArgumentCaptor.forClass(List.class);
    verify(documentsApi).markProcessed(eq(versionId), anyString(), anyString(), eq("ACCEPTED_WITH_WARNINGS"), warnings.capture());
    assertThat(warnings.getValue()).anyMatch(w -> w.startsWith("Archivo 2:") && w.contains("se procesó el resto"));
    verify(events).publishEvent(any(DocumentVersionProcessed.class));
  }

  @Test
  void onlyWhenNoPageIsUsableTheVersionIsMarkedUnreadable() throws Exception {
    files.put("orig/blank.jpg", jpeg(gray(1200, 1600)));
    pages("orig/blank.jpg", "image/jpeg");

    processor.process(payload(DocumentTypeCode.INE));

    verify(documentsApi).markQualityFailed(eq(versionId), anyString());
    verify(events).publishEvent(any(DocumentQualityFailed.class));
    verify(documentsApi, never()).markProcessed(any(), any(), any(), any(), any());
  }

  @Test
  void aCorruptPdfFailsWithAClearMessage() {
    files.put("orig/roto.pdf", "%PDF-1.4 esto no es un pdf completo".getBytes());
    pages("orig/roto.pdf", "application/pdf");

    processor.process(payload(DocumentTypeCode.DEED));

    ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
    verify(documentsApi).markFailed(eq(versionId), reason.capture());
    assertThat(reason.getValue()).contains("dañado");
  }

  // ---------------------------------------------------------------------

  static byte[] pdfWithPages(int pages) throws Exception {
    try (PDDocument document = new PDDocument()) {
      for (int i = 0; i < pages; i++) {
        document.addPage(new PDPage());
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    }
  }

  private static BufferedImage document(int w, int h) {
    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    var g = image.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, w, h);
    g.setColor(Color.BLACK);
    g.setFont(new Font("SansSerif", Font.PLAIN, 28));
    for (int y = 80; y < h - 40; y += 50) {
      g.drawString("ACTA DE MATRIMONIO - REGISTRO CIVIL - TEXTO DE PRUEBA", 40, y);
    }
    g.dispose();
    return image;
  }

  private static BufferedImage gray(int w, int h) {
    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    var g = image.createGraphics();
    g.setColor(new Color(128, 128, 128));
    g.fillRect(0, 0, w, h);
    g.dispose();
    return image;
  }

  private static byte[] jpeg(BufferedImage image) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", out);
    return out.toByteArray();
  }
}
