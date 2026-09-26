package com.c21genera.notifications.infrastructure;

import com.c21genera.notifications.domain.NotificationLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

  List<NotificationLog> findByExpedienteIdOrderByCreatedAtDesc(UUID expedienteId);
}
