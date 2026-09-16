package com.c21genera.shared.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** Envoltorio consistente para listados paginados (ver AGENTS §164). */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }
}
