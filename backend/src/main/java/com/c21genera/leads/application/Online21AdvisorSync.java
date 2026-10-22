package com.c21genera.leads.application;

import com.c21genera.identity.UserDirectory.StaffMember;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.online21.Online21Users;
import com.c21genera.online21.Online21Users.RemoteUser;
import com.c21genera.shared.domain.UnprocessableException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Personal de Genera ↔ usuarios de 21Online. Sin el vínculo, las propiedades
 * de 21Online no saben qué asesor de Genera las atiende (ruteo de leads,
 * edición desde el CRM). Vincula solo por correo (sin distinguir mayúsculas)
 * a quien aún no está vinculado; nunca cambia un vínculo puesto a mano ni el
 * WhatsApp del aviso de leads (el de 21Online se muestra como sugerencia).
 *
 * <p>De cada asesor vinculado se guarda una copia de su usuario de 21Online
 * (nombre, correo, teléfonos, WhatsApp, foto, puesto y ficha pública):
 * 21Online manda y Genera solo la muestra.
 */
@Service
public class Online21AdvisorSync {

  private static final Logger log = LoggerFactory.getLogger(Online21AdvisorSync.class);

  /** Los que atienden leads y propiedades (igual que en Asesores). */
  private static final List<String> ROUTABLE_ROLES = List.of("ADVISOR", "DIRECTOR", "ADMINISTRATOR");

  private final Online21Users users;
  private final AdvisorProfileRepository profiles;
  private final StaffNames staff;
  private final TransactionTemplate tx;
  private final Clock clock;

  Online21AdvisorSync(Online21Users users, AdvisorProfileRepository profiles, StaffNames staff, TransactionTemplate tx, Clock clock) {
    this.users = users;
    this.profiles = profiles;
    this.staff = staff;
    this.tx = tx;
    this.clock = clock;
  }

  /** Usuario de 21Online y con quién de Genera está vinculado (null = nadie). */
  public record Online21UserView(
      long id, String name, String email, String position, String mobilePhone, String whatsapp, String photoUrl, UUID linkedUserId, String linkedUserName) {}

  /**
   * linked: vínculos nuevos (nombre de Genera → usuario de 21Online). brokenLinks: personal vinculado a
   * un usuario que ya no aparece en 21Online. unmatched: personal activo sin usuario de 21Online.
   */
  public record SyncResult(boolean configured, List<String> linked, List<String> brokenLinks, List<String> unmatched, List<Online21UserView> users) {}

  public List<Online21UserView> list() {
    return run(false).users();
  }

  public SyncResult sync() {
    return run(true);
  }

  /** Cada hora: vincula por correo a quien se dio de alta en ambos sistemas y actualiza la copia de 21Online. */
  @Scheduled(fixedDelayString = "${app.online21.advisor-sync-interval:PT1H}", initialDelayString = "PT4M")
  void scheduledSync() {
    if (!users.available()) {
      return;
    }
    try {
      SyncResult r = run(true);
      if (!r.linked().isEmpty()) {
        log.info("Asesores vinculados con 21Online por correo: {}", r.linked().size());
      }
    } catch (RuntimeException e) {
      log.warn("No se pudieron sincronizar los asesores con 21Online ({})", e.getClass().getSimpleName());
    }
  }

  private SyncResult run(boolean link) {
    if (!users.available()) {
      return new SyncResult(false, List.of(), List.of(), List.of(), List.of());
    }
    List<RemoteUser> remote;
    try {
      remote = users.list();
    } catch (RuntimeException e) {
      throw new UnprocessableException("ONLINE21_UNAVAILABLE", "21Online no respondió. Intenta de nuevo en un momento.");
    }
    List<StaffMember> active = staff.activeStaff();
    List<String> linked = new ArrayList<>();
    if (link) {
      tx.executeWithoutResult(
          s -> {
            linked.addAll(linkByEmail(remote, active));
            refreshSnapshots(remote);
          });
    }
    Map<UUID, AdvisorProfile> byUser = profiles.findAll().stream().collect(Collectors.toMap(AdvisorProfile::getUserId, Function.identity()));
    Map<Long, UUID> linkOf = new HashMap<>();
    byUser.values().stream().filter(p -> p.getOnline21UserId() != null).forEach(p -> linkOf.put(p.getOnline21UserId(), p.getUserId()));
    Map<UUID, String> names = staff.names();
    Set<Long> remoteIds = remote.stream().map(RemoteUser::id).collect(Collectors.toSet());

    List<String> broken = new ArrayList<>();
    List<String> unmatched = new ArrayList<>();
    for (StaffMember m : active) {
      if (!ROUTABLE_ROLES.contains(m.roleCode())) {
        continue;
      }
      AdvisorProfile p = byUser.get(m.id());
      Long id = p == null ? null : p.getOnline21UserId();
      if (id == null) {
        unmatched.add(m.name());
      } else if (!remoteIds.contains(id)) {
        broken.add(m.name() + " (usuario " + id + ")");
      }
    }
    List<Online21UserView> views =
        remote.stream()
            .map(
                u -> {
                  UUID who = linkOf.get(u.id());
                  return new Online21UserView(
                      u.id(), u.name(), u.email(), u.position(), u.mobilePhone(), u.whatsapp(), u.photoUrl(), who, who == null ? null : staff.nameOf(who, names));
                })
            .toList();
    return new SyncResult(true, linked, broken, unmatched, views);
  }

  /** Copia de 21Online en cada perfil vinculado; un usuario que ya no aparece conserva su última copia. */
  private void refreshSnapshots(List<RemoteUser> remote) {
    Map<Long, RemoteUser> byId = remote.stream().collect(Collectors.toMap(RemoteUser::id, Function.identity(), (a, b) -> a));
    for (AdvisorProfile p : profiles.findAll()) {
      RemoteUser u = p.getOnline21UserId() == null ? null : byId.get(p.getOnline21UserId());
      if (u != null) {
        p.online21Snapshot(snapshotOf(u, clock.instant()));
      }
    }
  }

  static AdvisorProfile.Online21Snapshot snapshotOf(RemoteUser u, java.time.Instant at) {
    return new AdvisorProfile.Online21Snapshot(u.name(), u.email(), u.mobilePhone(), u.officePhone(), u.whatsapp(), u.photoUrl(), u.position(), u.profileUrl(), at);
  }

  /** Vincula por correo a quien no tenga vínculo, sin repetir un usuario de 21Online ya vinculado. */
  private List<String> linkByEmail(List<RemoteUser> remote, List<StaffMember> active) {
    Map<String, RemoteUser> byEmail = new HashMap<>();
    remote.stream().filter(u -> u.email() != null).forEach(u -> byEmail.putIfAbsent(u.email().trim().toLowerCase(Locale.ROOT), u));
    Set<Long> taken = profiles.findAll().stream().map(AdvisorProfile::getOnline21UserId).filter(id -> id != null).collect(Collectors.toSet());
    List<String> linked = new ArrayList<>();
    for (StaffMember m : active) {
      if (m.email() == null) {
        continue;
      }
      RemoteUser u = byEmail.get(m.email().trim().toLowerCase(Locale.ROOT));
      if (u == null || taken.contains(u.id())) {
        continue;
      }
      AdvisorProfile p = profiles.findById(m.id()).orElseGet(() -> new AdvisorProfile(m.id()));
      if (p.getOnline21UserId() != null) {
        continue;
      }
      p.linkOnline21(u.id());
      profiles.save(p);
      taken.add(u.id());
      linked.add(m.name() + " → " + u.name());
    }
    return linked;
  }
}
