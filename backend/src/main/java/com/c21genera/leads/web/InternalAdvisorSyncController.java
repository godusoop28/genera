package com.c21genera.leads.web;

import com.c21genera.leads.application.Online21AdvisorSync;
import com.c21genera.leads.application.Online21AdvisorSync.Online21UserView;
import com.c21genera.leads.application.Online21AdvisorSync.SyncResult;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Asesores ↔ usuarios de 21Online (pantalla de Asesores). */
@RestController
@PreAuthorize("hasAuthority('ADVISOR_ROUTING_MANAGE')")
public class InternalAdvisorSyncController {

  private final Online21AdvisorSync sync;

  public InternalAdvisorSyncController(Online21AdvisorSync sync) {
    this.sync = sync;
  }

  /** Usuarios de 21Online para elegir el vínculo (con quién de Genera está vinculado cada uno). */
  @GetMapping("/api/v1/internal/crm/advisors/online21-users")
  public List<Online21UserView> users() {
    return sync.list();
  }

  /** Vincula por correo a quien aún no lo esté y reporta vínculos rotos y personal sin usuario de 21Online. */
  @PostMapping("/api/v1/internal/crm/advisors/online21-sync")
  public SyncResult syncNow() {
    return sync.sync();
  }
}
