package com.c21genera.audit.infrastructure;

import com.c21genera.audit.domain.Activity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

  List<Activity> findByExpedienteIdOrderByOccurredAtDesc(UUID expedienteId);
}
