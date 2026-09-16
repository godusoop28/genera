package com.c21genera.audit.web;

import com.c21genera.audit.domain.Activity;
import com.c21genera.audit.domain.AuditEvent;
import java.time.Instant;
import java.util.UUID;

public final class AuditDtos {

  private AuditDtos() {}

  public record ActivityResponse(UUID id, Instant occurredAt, String category, String message) {

    static ActivityResponse from(Activity a) {
      return new ActivityResponse(a.getId(), a.getOccurredAt(), a.getCategory().name(), a.getMessage());
    }
  }

  public record AuditEventResponse(
      UUID id, Instant occurredAt, String eventType, String aggregateType, UUID aggregateId, UUID actorUserId, String summary) {

    static AuditEventResponse from(AuditEvent e) {
      return new AuditEventResponse(
          e.getId(), e.getOccurredAt(), e.getEventType(), e.getAggregateType(), e.getAggregateId(), e.getActorUserId(), e.getSummary());
    }
  }
}
