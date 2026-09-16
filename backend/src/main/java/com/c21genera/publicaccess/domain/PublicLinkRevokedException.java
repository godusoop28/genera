package com.c21genera.publicaccess.domain;

import com.c21genera.shared.domain.DomainException;
import org.springframework.http.HttpStatus;

public class PublicLinkRevokedException extends DomainException {

  public PublicLinkRevokedException() {
    super("PUBLIC_LINK_INVALID", HttpStatus.NOT_FOUND, "Esta liga no es válida o fue revocada.");
  }
}
