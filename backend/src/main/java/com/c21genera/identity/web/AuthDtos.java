package com.c21genera.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {

  private AuthDtos() {}

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}

  public record LogoutRequest(@NotBlank String refreshToken) {}

  public record AuthResponse(String accessToken, Instant accessTokenExpiresAt, String refreshToken) {}

  public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(max = 128) String newPassword) {}

  public record CompleteAccountLinkRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 128) String password) {}

  /** purpose: INVITE (alta) o RESET (restablecimiento). */
  public record AccountLinkResponse(String name, String email, String purpose, Instant expiresAt) {}

  public record MeResponse(UUID id, String name, String email, String role, List<String> permissions) {}
}
