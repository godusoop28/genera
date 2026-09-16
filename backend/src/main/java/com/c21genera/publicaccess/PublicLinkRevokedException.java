package com.c21genera.publicaccess;

import com.c21genera.shared.domain.DomainException;
import org.springframework.http.HttpStatus;

/**
 * API pública: cualquier módulo con endpoints públicos (documents, privacy,
 * expedientes) puede lanzar esto cuando el token no resuelve o cuando un
 * recurso no pertenece al expediente resuelto (mismo 404 genérico para
 * evitar enumeración, ver AGENTS §161-162).
 */
public class PublicLinkRevokedException extends DomainException {

  public PublicLinkRevokedException() {
    super("PUBLIC_LINK_INVALID", HttpStatus.NOT_FOUND, "Esta liga no es válida o fue revocada.");
  }
}
