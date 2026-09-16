package com.c21genera.identity.web;

import com.c21genera.identity.domain.Role;
import com.c21genera.identity.infrastructure.RoleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Solo lectura por ahora: los roles base (ADMINISTRATOR, ADVISOR,
 * DOCUMENT_REVIEWER) se siembran en la migración/seed. No se expone CRUD de
 * roles personalizados en el Módulo 1 (ver AGENTS §113).
 */
@RestController
@RequestMapping("/api/v1/internal/roles")
@PreAuthorize("isAuthenticated()")
public class RoleController {

  private final RoleRepository roleRepository;

  public RoleController(RoleRepository roleRepository) {
    this.roleRepository = roleRepository;
  }

  public record RoleResponse(UUID id, String code, String name, String description, List<String> permissions) {}

  @GetMapping
  public List<RoleResponse> list() {
    return roleRepository.findAll().stream()
        .map(
            (Role role) ->
                new RoleResponse(
                    role.getId(),
                    role.getCode().name(),
                    role.getName(),
                    role.getDescription(),
                    role.getPermissions().stream().map(p -> p.getCode()).sorted().toList()))
        .toList();
  }
}
