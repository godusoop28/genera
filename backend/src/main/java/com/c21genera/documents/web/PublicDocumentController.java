package com.c21genera.documents.web;

import com.c21genera.documents.application.DocumentService;
import com.c21genera.documents.application.DocumentService.UploadedFileContent;
import com.c21genera.documents.domain.Document;
import com.c21genera.documents.domain.UploadedVia;
import com.c21genera.documents.web.DocumentDtos.DocumentResponse;
import com.c21genera.documents.web.DocumentDtos.DocumentVersionResponse;
import com.c21genera.publicaccess.PublicAccessTokenApi;
import com.c21genera.publicaccess.PublicLinkRevokedException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Portal del cliente sin cuenta (ver AGENTS §90-97). El token en la URL ES
 * la credencial: cada acceso valida pertenencia al expediente para evitar
 * IDOR (AGENTS §161-162), nunca se confía en un documentId por sí solo.
 */
@RestController
@RequestMapping("/api/v1/public/expedientes/{token}/documents")
public class PublicDocumentController {

  private final DocumentService documentService;
  private final PublicAccessTokenApi tokenApi;

  public PublicDocumentController(DocumentService documentService, PublicAccessTokenApi tokenApi) {
    this.documentService = documentService;
    this.tokenApi = tokenApi;
  }

  @GetMapping
  public List<DocumentResponse> list(@PathVariable String token) {
    UUID expedienteId = resolve(token);
    return documentService.documentsOf(expedienteId).stream().map(DocumentResponse::from).toList();
  }

  @GetMapping("/{documentId}")
  public DocumentResponse get(@PathVariable String token, @PathVariable UUID documentId) {
    UUID expedienteId = resolve(token);
    return DocumentResponse.from(requireOwnedByExpediente(documentId, expedienteId));
  }

  @PostMapping("/{documentId}/versions")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public DocumentVersionResponse upload(
      @PathVariable String token, @PathVariable UUID documentId, @RequestParam("files") List<MultipartFile> files) {
    UUID expedienteId = resolve(token);
    requireOwnedByExpediente(documentId, expedienteId);
    List<UploadedFileContent> contents = files.stream().map(PublicDocumentController::readFile).toList();
    return DocumentVersionResponse.from(documentService.uploadVersion(documentId, contents, UploadedVia.PUBLIC_PORTAL));
  }

  private Document requireOwnedByExpediente(UUID documentId, UUID expedienteId) {
    Document document = documentService.get(documentId);
    if (!document.getExpedienteId().equals(expedienteId)) {
      // Mismo mensaje que "no encontrado": nunca se revela que el documentId existe en otro expediente.
      throw new PublicLinkRevokedException();
    }
    return document;
  }

  private UUID resolve(String token) {
    return tokenApi.resolve(token).orElseThrow(PublicLinkRevokedException::new);
  }

  private static UploadedFileContent readFile(MultipartFile file) {
    try {
      return new UploadedFileContent(file.getBytes(), file.getOriginalFilename());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
