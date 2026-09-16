package com.c21genera.identity.web;

import com.c21genera.identity.domain.Permission;
import com.c21genera.identity.infrastructure.PermissionRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/permissions")
@PreAuthorize("isAuthenticated()")
public class PermissionController {

  private final PermissionRepository permissionRepository;

  public PermissionController(PermissionRepository permissionRepository) {
    this.permissionRepository = permissionRepository;
  }

  public record PermissionResponse(String code, String description) {}

  @GetMapping
  public List<PermissionResponse> list() {
    return permissionRepository.findAll().stream()
        .map((Permission p) -> new PermissionResponse(p.getCode(), p.getDescription()))
        .sorted((a, b) -> a.code().compareTo(b.code()))
        .toList();
  }
}
