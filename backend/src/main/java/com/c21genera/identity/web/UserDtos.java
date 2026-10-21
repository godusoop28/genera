package com.c21genera.identity.web;

import com.c21genera.identity.application.AccountLinkService.SetupLink;
import com.c21genera.identity.domain.PasswordSetupPurpose;
import com.c21genera.identity.domain.RoleCode;
import com.c21genera.identity.domain.User;
import com.c21genera.identity.domain.UserStatus;
import com.c21genera.shared.staff.StaffWorkload;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UserDtos {

  private UserDtos() {}

  /** Control de Cambios #25: sin contraseña; la persona la define con su enlace. */
  public record CreateUserRequest(@NotBlank @Size(max = 160) String name, @NotBlank @Email @Size(max = 255) String email, @NotNull RoleCode roleCode) {}

  public record UpdateUserRequest(@NotBlank @Size(max = 160) String name, @NotBlank @Email @Size(max = 255) String email, @NotNull RoleCode roleCode) {}

  public record OffboardUserRequest(@NotBlank @Size(max = 500) String reason) {}

  /** locked: bloqueada ahora por intentos fallidos (hasta lockedUntil). */
  public record UserResponse(
      UUID id,
      String name,
      String email,
      RoleCode roleCode,
      String roleName,
      UserStatus status,
      Instant lastActivityAt,
      boolean locked,
      Instant lockedUntil,
      Instant passwordChangedAt,
      Instant offboardedAt,
      Instant createdAt) {

    public static UserResponse from(User user) {
      return from(user, Instant.now());
    }

    public static UserResponse from(User user, Instant now) {
      boolean locked = user.isLocked(now);
      return new UserResponse(
          user.getId(),
          user.getName(),
          user.getEmail(),
          user.getRole().getCode(),
          user.getRole().getName(),
          user.getStatus(),
          user.getLastActivityAt(),
          locked,
          locked ? user.getLockedUntil() : null,
          user.getPasswordChangedAt(),
          user.getOffboardedAt(),
          user.getCreatedAt());
    }
  }

  /** El enlace solo se muestra una vez, en esta respuesta; el administrador lo comparte con la persona. */
  public record SetupLinkResponse(String url, Instant expiresAt, PasswordSetupPurpose purpose) {

    public static SetupLinkResponse from(SetupLink link) {
      return new SetupLinkResponse(link.url(), link.expiresAt(), link.purpose());
    }
  }

  public record CreatedUserResponse(UserResponse user, SetupLinkResponse link) {}

  public record WorkloadItem(String label, long count) {

    public static List<WorkloadItem> from(List<StaffWorkload.Item> items) {
      return items.stream().map(i -> new WorkloadItem(i.label(), i.count())).toList();
    }
  }
}
