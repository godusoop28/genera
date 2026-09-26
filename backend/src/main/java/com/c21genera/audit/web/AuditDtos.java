package com.c21genera.audit.web;

import com.c21genera.audit.domain.Activity;
import com.c21genera.audit.domain.AuditEvent;
import java.time.Instant;
import java.util.UUID;

public final class AuditDtos {

  private AuditDtos() {}

  /** actorType: CLIENT, STAFF, SYSTEM o UNKNOWN (registros anteriores a que se guardara el actor). */
  public record ActivityResponse(
      UUID id,
      Instant occurredAt,
      String category,
      String action,
      String actorType,
      String actorName,
      String actorRole,
      UUID documentId,
      String documentLabel,
      String message) {

    static ActivityResponse from(Activity a) {
      return new ActivityResponse(
          a.getId(),
          a.getOccurredAt(),
          a.getCategory().name(),
          a.getAction(),
          a.getActorType(),
          a.getActorName(),
          a.getActorRole(),
          a.getDocumentId(),
          a.getDocumentLabel(),
          a.getMessage());
    }
  }

  public record AuditEventResponse(
      UUID id,
      Instant occurredAt,
      String eventType,
      String aggregateType,
      UUID aggregateId,
      String actorType,
      UUID actorUserId,
      String actorName,
      String actorRole,
      String summary) {

    static AuditEventResponse from(AuditEvent e) {
      return new AuditEventResponse(
          e.getId(),
          e.getOccurredAt(),
          e.getEventType(),
          e.getAggregateType(),
          e.getAggregateId(),
          e.getActorType(),
          e.getActorUserId(),
          e.getActorName(),
          e.getActorRole(),
          e.getSummary());
    }
  }
}
