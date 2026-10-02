package com.c21genera.shared.web;

import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.storage.FileStorage;
import com.c21genera.shared.storage.SignedFileLinks;
import java.io.InputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrega un archivo con una liga temporal firmada ({@link SignedFileLinks}).
 * No requiere sesión: la liga misma es la autorización, y solo la genera el
 * backend después de validar permisos (igual que una URL prefirmada de S3).
 */
@RestController
public class PublicFileController {

  private final SignedFileLinks links;
  private final FileStorage fileStorage;

  public PublicFileController(SignedFileLinks links, FileStorage fileStorage) {
    this.links = links;
    this.fileStorage = fileStorage;
  }

  @GetMapping("/api/v1/public/files/{token}")
  public ResponseEntity<InputStreamResource> download(@PathVariable String token) {
    String storageKey = links.verify(token).orElseThrow(() -> new NotFoundException("Archivo", token));
    InputStream content = fileStorage.get(storageKey);
    String filename = storageKey.substring(storageKey.lastIndexOf('/') + 1);
    return ResponseEntity.ok()
        .contentType(mediaTypeOf(filename))
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .body(new InputStreamResource(content));
  }

  private static MediaType mediaTypeOf(String filename) {
    String lower = filename.toLowerCase();
    if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
    if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
    if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
    if (lower.endsWith(".docx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    return MediaType.APPLICATION_OCTET_STREAM;
  }
}
