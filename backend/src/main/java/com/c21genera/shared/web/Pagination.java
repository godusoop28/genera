package com.c21genera.shared.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Aplica un límite máximo de tamaño de página (ver AGENTS §164). */
public final class Pagination {

  public static final int DEFAULT_MAX_PAGE_SIZE = 100;

  private Pagination() {}

  public static Pageable cap(Pageable pageable) {
    return cap(pageable, DEFAULT_MAX_PAGE_SIZE);
  }

  public static Pageable cap(Pageable pageable, int maxSize) {
    if (pageable.getPageSize() <= maxSize) {
      return pageable;
    }
    return PageRequest.of(pageable.getPageNumber(), maxSize, pageable.getSort());
  }
}
