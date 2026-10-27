package com.c21genera.notifications.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.notifications.application.StaffAlertService;
import com.c21genera.notifications.application.StaffAlertService.Inbox;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Campana del encabezado: avisos del usuario que inició sesión (cada quien solo ve los suyos). */
@RestController
public class StaffInboxController {

  private final StaffAlertService alerts;

  public StaffInboxController(StaffAlertService alerts) {
    this.alerts = alerts;
  }

  @GetMapping("/api/v1/internal/me/notifications")
  public Inbox inbox(@AuthenticationPrincipal Jwt jwt) {
    return alerts.inbox(CurrentUser.from(jwt).id());
  }

  @PostMapping("/api/v1/internal/me/notifications/{id}/read")
  public Inbox read(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return alerts.markRead(CurrentUser.from(jwt).id(), id);
  }

  @PostMapping("/api/v1/internal/me/notifications/read-all")
  public Inbox readAll(@AuthenticationPrincipal Jwt jwt) {
    return alerts.markAllRead(CurrentUser.from(jwt).id());
  }
}
