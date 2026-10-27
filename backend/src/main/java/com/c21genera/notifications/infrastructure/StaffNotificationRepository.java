package com.c21genera.notifications.infrastructure;

import com.c21genera.notifications.domain.StaffNotification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffNotificationRepository extends JpaRepository<StaffNotification, UUID> {

  List<StaffNotification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable page);

  long countByUserIdAndReadAtIsNull(UUID userId);

  List<StaffNotification> findByUserIdAndReadAtIsNull(UUID userId);

  boolean existsByUserIdAndDedupeKey(UUID userId, String dedupeKey);
}
