package com.c21genera.identity.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.identity.application.UserService;
import com.c21genera.identity.application.UserService.CreatedUser;
import com.c21genera.identity.web.UserDtos.CreateUserRequest;
import com.c21genera.identity.web.UserDtos.CreatedUserResponse;
import com.c21genera.identity.web.UserDtos.OffboardUserRequest;
import com.c21genera.identity.web.UserDtos.SetupLinkResponse;
import com.c21genera.identity.web.UserDtos.UpdateUserRequest;
import com.c21genera.identity.web.UserDtos.UserResponse;
import com.c21genera.identity.web.UserDtos.WorkloadItem;
import com.c21genera.shared.web.PageResponse;
import com.c21genera.shared.web.Pagination;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control de Cambios #25: administración de cuentas. Todo requiere USER_MANAGE
 * (solo el Administrador); asignar o cambiar un rol además pide ROLE_MANAGE
 * (lo verifica UserService). No existe "borrar": se desactiva o se da de baja.
 */
@RestController
@RequestMapping("/api/v1/internal/users")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public PageResponse<UserResponse> list(@PageableDefault(size = 20) Pageable pageable) {
    return PageResponse.of(userService.list(Pagination.cap(pageable)).map(UserResponse::from));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreatedUserResponse create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
    CreatedUser created = userService.create(request.name(), request.email(), request.roleCode(), CurrentUser.from(jwt));
    return new CreatedUserResponse(UserResponse.from(created.user()), SetupLinkResponse.from(created.link()));
  }

  @PatchMapping("/{id}")
  public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
    return UserResponse.from(userService.update(id, request.name(), request.email(), request.roleCode(), CurrentUser.from(jwt)));
  }

  @PostMapping("/{id}/activate")
  public UserResponse activate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return UserResponse.from(userService.activate(id, CurrentUser.from(jwt)));
  }

  @PostMapping("/{id}/deactivate")
  public UserResponse deactivate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return UserResponse.from(userService.deactivate(id, CurrentUser.from(jwt)));
  }

  @PostMapping("/{id}/offboard")
  public UserResponse offboard(@PathVariable UUID id, @Valid @RequestBody OffboardUserRequest request, @AuthenticationPrincipal Jwt jwt) {
    return UserResponse.from(userService.offboard(id, request.reason(), CurrentUser.from(jwt)));
  }

  /** Enlace de restablecimiento (o de alta, si aún no define su contraseña). El administrador nunca ve la contraseña. */
  @PostMapping("/{id}/password-link")
  public SetupLinkResponse passwordLink(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return SetupLinkResponse.from(userService.issuePasswordLink(id, CurrentUser.from(jwt)));
  }

  @PostMapping("/{id}/unlock")
  public UserResponse unlock(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return UserResponse.from(userService.unlock(id, CurrentUser.from(jwt)));
  }

  /** Lo que tiene a su cargo (expedientes en curso, leads abiertos...): hay que reasignarlo antes de la baja. */
  @GetMapping("/{id}/workload")
  public List<WorkloadItem> workload(@PathVariable UUID id) {
    return WorkloadItem.from(userService.workloadOf(id));
  }
}
