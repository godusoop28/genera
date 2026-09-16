package com.c21genera.identity.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.identity.application.AuthService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
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

  private AuthResponse toResponse(AuthService.AuthResult result) {
    return new AuthResponse(result.accessToken(), result.accessTokenExpiresAt(), result.refreshToken());
  }
}
