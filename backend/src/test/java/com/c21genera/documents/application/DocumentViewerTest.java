package com.c21genera.documents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.documents.domain.DocumentVersion;
import com.c21genera.documents.domain.UploadedVia;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.storage.FileStorage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

/** Control de Cambios #6: el visor entrega imágenes con marca de agua, nunca el PDF. */
class DocumentViewerTest {

  private final DocumentService documentService = mock(DocumentService.class);
  private final FileStorage fileStorage = mock(FileStorage.class);
  private final DocumentViewer viewer =
      new DocumentViewer(documentService, fileStorage, Clock.fixed(Instant.parse("2026-10-15T18:00:00Z"), ZoneOffset.UTC));

  private static byte[] pdfWithPages(int pages) throws Exception {
    try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      for (int i = 0; i < pages; i++) {
        document.addPage(new PDPage(PDRectangle.LETTER));
      }
      document.save(out);
      return out.toByteArray();
    }
  }

  @Test
  void eachPageIsAJpegImageAndThePdfIsDownloadedOnlyOncePerVersion() throws Exception {
    UUID versionId = UUID.randomUUID();
    DocumentVersion version = new DocumentVersion(UUID.randomUUID(), 1, Instant.now(), UploadedVia.PUBLIC_PORTAL, null, null);
    version.completeProcessing("processed/escritura.pdf", "manifest.json");
    when(documentService.getVersion(versionId)).thenReturn(version);
    byte[] pdf = pdfWithPages(3);
    when(fileStorage.get("processed/escritura.pdf")).thenAnswer(i -> new ByteArrayInputStream(pdf));

    assertThat(viewer.info(versionId).pageCount()).isEqualTo(3);
    byte[] page = viewer.page(versionId, 2, "Ana Asesora");
    viewer.page(versionId, 3, "Ana Asesora");

    BufferedImage image = ImageIO.read(new ByteArrayInputStream(page));
    assertThat(image).isNotNull();
    // Carta a 110 dpi: 8.5 in × 110 ≈ 935 px.
    assertThat(image.getWidth()).isBetween(900, 960);
    // Una hoja en blanco deja de serlo: lleva la marca de agua.
    boolean marked = false;
    for (int y = 0; y < image.getHeight() && !marked; y += 4) {
      for (int x = 0; x < image.getWidth() && !marked; x += 4) {
        marked = (image.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF;
      }
    }
    assertThat(marked).isTrue();
    verify(fileStorage, times(1)).get(any());
  }

  @Test
  void aPageOutsideTheDocumentIsNotFound() throws Exception {
    UUID versionId = UUID.randomUUID();
    DocumentVersion version = new DocumentVersion(UUID.randomUUID(), 1, Instant.now(), UploadedVia.PUBLIC_PORTAL, null, null);
    version.completeProcessing("processed/otro.pdf", "manifest.json");
    when(documentService.getVersion(versionId)).thenReturn(version);
    byte[] pdf = pdfWithPages(1);
    when(fileStorage.get("processed/otro.pdf")).thenAnswer(i -> new ByteArrayInputStream(pdf));

    assertThatThrownBy(() -> viewer.page(versionId, 5, "Ana")).isInstanceOf(NotFoundException.class);
  }
}
