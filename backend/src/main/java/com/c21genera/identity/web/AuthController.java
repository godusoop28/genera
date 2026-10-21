package com.c21genera.identity.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.identity.application.AccountLinkService;
import com.c21genera.identity.application.AuthService;
import com.c21genera.identity.web.AuthDtos.AccountLinkResponse;
import com.c21genera.identity.web.AuthDtos.ChangePasswordRequest;
import com.c21genera.identity.web.AuthDtos.CompleteAccountLinkRequest;
import com.c21genera.identity.web.AuthDtos.AuthResponse;
import com.c21genera.identity.web.AuthDtos.LoginRequest;
import com.c21genera.identity.web.AuthDtos.LogoutRequest;
import com.c21genera.identity.web.AuthDtos.MeResponse;
import com.c21genera.identity.web.AuthDtos.RefreshRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

  private final AuthService authService;
  private final AccountLinkService accountLinks;

  public AuthController(AuthService authService, AccountLinkService accountLinks) {
    this.authService = authService;
    this.accountLinks = accountLinks;
  }

  @PostMapping("/api/v1/auth/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    AuthService.AuthResult result = authService.login(request.email(), request.password());
    return toResponse(result);
  }

  @PostMapping("/api/v1/auth/refresh")
  public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
    AuthService.AuthResult result = authService.refresh(request.refreshToken());
    return toResponse(result);
  }

  @PostMapping("/api/v1/auth/logout")
  public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
    authService.logout(request.refreshToken());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/v1/internal/me")
  public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
    CurrentUser currentUser = CurrentUser.from(jwt);
    return new MeResponse(
        currentUser.id(), currentUser.name(), currentUser.email(), currentUser.role(), currentUser.permissions());
  }

  /**
   * Control de Cambios #25: "Mi perfil" → cambiar contraseña (pide la actual).
   * Cierra las demás sesiones y devuelve una nueva para este dispositivo.
   */
  @PostMapping("/api/v1/internal/me/password")
  public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal Jwt jwt) {
    return toResponse(authService.changeOwnPassword(CurrentUser.from(jwt).id(), request.currentPassword(), request.newPassword()));
  }

  /** Enlace de un solo uso (alta o restablecimiento): ¿sigue vigente y para quién es? */
  @GetMapping("/api/v1/auth/account-link/{token}")
  public AccountLinkResponse accountLink(@PathVariable String token) {
    AccountLinkService.LinkInfo info = accountLinks.describe(token);
    return new AccountLinkResponse(info.name(), info.email(), info.purpose().name(), info.expiresAt());
  }

  /** La persona define su contraseña con el enlace; después entra con su correo y esa contraseña. */
  @PostMapping("/api/v1/auth/account-link")
  public ResponseEntity<Void> completeAccountLink(@Valid @RequestBody CompleteAccountLinkRequest request) {
    accountLinks.complete(request.token(), request.password());
    return ResponseEntity.noContent().build();
  }

  private AuthResponse toResponse(AuthService.AuthResult result) {
    return new AuthResponse(result.accessToken(), result.accessTokenExpiresAt(), result.refreshToken());
  }
}
