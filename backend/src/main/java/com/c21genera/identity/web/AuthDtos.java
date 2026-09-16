package com.c21genera.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {

  private AuthDtos() {}

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}

  public record LogoutRequest(@NotBlank String refreshToken) {}

  public record AuthResponse(String accessToken, Instant accessTokenExpiresAt, String refreshToken) {}

  public record MeResponse(UUID id, String name, String email, String role, List<String> permissions) {}
}
