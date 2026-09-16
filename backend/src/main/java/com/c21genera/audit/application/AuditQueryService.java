package com.c21genera.audit.application;

import com.c21genera.audit.domain.Activity;
import com.c21genera.audit.domain.AuditEvent;
import com.c21genera.audit.infrastructure.ActivityRepository;
import com.c21genera.audit.infrastructure.AuditEventRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditQueryService {

  private final AuditEventRepository auditEventRepository;
  private final ActivityRepository activityRepository;

  public AuditQueryService(AuditEventRepository auditEventRepository, ActivityRepository activityRepository) {
    this.auditEventRepository = auditEventRepository;
    this.activityRepository = activityRepository;
  }

  public List<Activity> activityOf(UUID expedienteId) {
    return activityRepository.findByExpedienteIdOrderByOccurredAtDesc(expedienteId);
  }

  public List<AuditEvent> auditEventsOf(UUID aggregateId) {
    return auditEventRepository.findByAggregateIdOrderByOccurredAtDesc(aggregateId);
  }
}
