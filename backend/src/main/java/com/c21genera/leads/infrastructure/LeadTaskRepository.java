package com.c21genera.leads.infrastructure;

import com.c21genera.leads.domain.LeadTask;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadTaskRepository extends JpaRepository<LeadTask, UUID> {

  List<LeadTask> findByLeadIdOrderByScheduledAtDesc(UUID leadId);

  List<LeadTask> findByLeadIdIn(Collection<UUID> leadIds);

  /** Agenda: lo programado o hecho dentro del rango, más lo vencido sin resultado (sigue pendiente). */
  @Query(
      "select t from LeadTask t where t.status <> com.c21genera.leads.domain.LeadTask.Status.CANCELLED and ((t.scheduledAt >= :from and t.scheduledAt < :to)"
          + " or (t.status = com.c21genera.leads.domain.LeadTask.Status.SCHEDULED and t.scheduledAt < :now)) order by t.scheduledAt")
  List<LeadTask> agenda(@Param("from") Instant from, @Param("to") Instant to, @Param("now") Instant now);

  /** Programadas con recordatorio cuya hora de aviso ya llegó (horizon = ahora + el máximo de anticipación). */
  @Query(
      "select t from LeadTask t where t.status = com.c21genera.leads.domain.LeadTask.Status.SCHEDULED and t.reminderMinutes is not null"
          + " and t.reminderSentAt is null and t.scheduledAt > :now and t.scheduledAt <= :horizon")
  List<LeadTask> remindersDue(@Param("now") Instant now, @Param("horizon") Instant horizon);

  @Query("select t from LeadTask t where t.status = com.c21genera.leads.domain.LeadTask.Status.SCHEDULED and t.overdueNotifiedAt is null and t.scheduledAt < :before")
  List<LeadTask> overdueCandidates(@Param("before") Instant before);

  List<LeadTask> findByCompletedAtGreaterThanEqual(Instant from);

  List<LeadTask> findByStatus(LeadTask.Status status);
}
