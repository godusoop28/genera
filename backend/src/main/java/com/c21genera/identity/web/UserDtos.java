package com.c21genera.identity.web;

import com.c21genera.identity.domain.RoleCode;
import com.c21genera.identity.domain.User;
import com.c21genera.identity.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {

  private UserDtos() {}

  public record CreateUserRequest(
      @NotBlank String name,
      @NotBlank @Email String email,
      @NotBlank @Size(min = 8, max = 128) String password,
      @NotNull RoleCode roleCode) {}

  public record UpdateUserRequest(@NotBlank String name, @NotBlank @Email String email, @NotNull RoleCode roleCode) {}

  public record UserResponse(
      UUID id, String name, String email, RoleCode roleCode, String roleName, UserStatus status, Instant lastActivityAt) {

    public static UserResponse from(User user) {
      return new UserResponse(
          user.getId(),
          user.getName(),
          user.getEmail(),
          user.getRole().getCode(),
          user.getRole().getName(),
          user.getStatus(),
          user.getLastActivityAt());
    }
  }
}
