package com.c21genera.notifications.application;

import com.c21genera.identity.UserDirectory;
import com.c21genera.notifications.NotificationsApi;
import com.c21genera.notifications.StaffAlerts;
import com.c21genera.notifications.domain.StaffNotification;
import com.c21genera.notifications.infrastructure.StaffNotificationRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Avisos al personal: quedan en su campana y, si se pide, también se mandan a su correo (outbox). */
@Service
@Transactional
public class StaffAlertService implements StaffAlerts {

  private static final String SIGNATURE = "\n\nCENTURY 21 Genera — aviso automático del sistema";

  private final StaffNotificationRepository repository;
  private final UserDirectory users;
  private final NotificationsApi email;
  private final Clock clock;

  StaffAlertService(StaffNotificationRepository repository, UserDirectory users, NotificationsApi email, Clock clock) {
    this.repository = repository;
    this.users = users;
    this.email = email;
    this.clock = clock;
  }

  @Override
  public boolean notifyUser(UUID userId, String kind, String title, String body, String link, String dedupeKey, boolean alsoEmail) {
    if (userId == null || (dedupeKey != null && repository.existsByUserIdAndDedupeKey(userId, dedupeKey))) {
      return false;
    }
    repository.save(new StaffNotification(userId, kind, title, body, link, dedupeKey, clock.instant()));
    if (alsoEmail) {
      users.contactOf(userId)
          .filter(UserDirectory.UserContact::active)
          .filter(c -> c.email() != null && !c.email().isBlank())
          .ifPresent(c -> email.notify(null, kind, c.email(), title, (body == null ? "" : body) + SIGNATURE));
    }
    return true;
  }

  @Override
  public void notifyRoles(Set<String> roleCodes, String kind, String title, String body, String link, String dedupeKey, boolean alsoEmail) {
    for (UserDirectory.StaffMember member : users.activeStaff()) {
      if (roleCodes.contains(member.roleCode())) {
        notifyUser(member.id(), kind, title, body, link, dedupeKey, alsoEmail);
      }
    }
  }

  public record InboxItem(UUID id, String kind, String title, String body, String link, boolean read, java.time.Instant createdAt) {}

  public record Inbox(long unread, List<InboxItem> items) {}

  @Transactional(readOnly = true)
  public Inbox inbox(UUID userId) {
    List<InboxItem> items =
        repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 40)).stream()
            .map(n -> new InboxItem(n.getId(), n.getKind(), n.getTitle(), n.getBody(), n.getLink(), n.getReadAt() != null, n.getCreatedAt()))
            .toList();
    return new Inbox(repository.countByUserIdAndReadAtIsNull(userId), items);
  }

  public Inbox markRead(UUID userId, UUID notificationId) {
    repository.findById(notificationId).filter(n -> n.getUserId().equals(userId)).ifPresent(n -> n.markRead(clock.instant()));
    return inbox(userId);
  }

  public Inbox markAllRead(UUID userId) {
    repository.findByUserIdAndReadAtIsNull(userId).forEach(n -> n.markRead(clock.instant()));
    return inbox(userId);
  }
}
