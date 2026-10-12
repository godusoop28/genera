package com.c21genera.leads.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.leads.AssignmentMethod;
import com.c21genera.leads.LeadIntake.HandoffRequest;
import com.c21genera.leads.LeadIntake.HandoffResult;
import com.c21genera.leads.LeadIntake.HandoffStatus;
import com.c21genera.leads.LeadIntake.PropertyRef;
import com.c21genera.leads.LeadInterest;
import com.c21genera.leads.LeadSource;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.domain.AssignmentLog;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.leads.infrastructure.LeadRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reglas A (continuidad) y D (propiedad de otro asesor) e idempotencia del bot. */
class LeadIntakeContinuityTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T17:00:00Z"), ZoneOffset.UTC);
  private final UUID ana = UUID.randomUUID();
  private final UUID luis = UUID.randomUUID();
  private LeadRepository leads;
  private AdvisorProfileRepository profiles;
  private AssignmentService assignment;
  private LeadRecorder recorder;
  private LeadIntakeService service;
  private Lead existing;

  @BeforeEach
  void setUp() {
    leads = mock(LeadRepository.class);
    profiles = mock(AdvisorProfileRepository.class);
    assignment = mock(AssignmentService.class);
    recorder = mock(LeadRecorder.class);
    StaffNames staff = mock(StaffNames.class);
    when(staff.nameOf(eq(ana), any())).thenReturn("Ana");
    when(staff.nameOf(eq(luis), any())).thenReturn("Luis");
    service = new LeadIntakeService(leads, profiles, assignment, recorder, staff, clock);

    existing = new Lead("Cliente", "+52 777 555 0142", LeadSource.WHATSAPP_BOT, null, LeadInterest.BUY, clock.instant());
    existing.assignTo(ana, AssignmentMethod.ROULETTE, "Ronda equitativa", null, clock.instant());
    when(leads.findFirstByPhoneDigitsAndCreatedAtAfterOrderByCreatedAtDesc(eq("7775550142"), any())).thenReturn(Optional.of(existing));
    AdvisorProfile luisProfile = new AdvisorProfile(luis);
    luisProfile.update(true, 5, null, 2002L, null);
    when(profiles.findByOnline21UserId(2002L)).thenReturn(Optional.of(luisProfile));
  }

  private HandoffRequest request(PropertyRef property, String key) {
    return new HandoffRequest(key, "Cliente", "+52 1 777 555 0142", LeadSource.WHATSAPP_BOT, null, LeadInterest.BUY, property, "Busca casa", null, UUID.randomUUID(), false);
  }

  @Test
  void activeLeadKeepsItsAdvisorWhenTheClientWritesAgain() {
    HandoffResult r = service.handoff(request(null, "wa:x:s2"));
    assertThat(r.status()).isEqualTo(HandoffStatus.EXISTING);
    assertThat(r.advisorName()).isEqualTo("Ana");
    assertThat(existing.getAdvisorUserId()).isEqualTo(ana);
    verify(assignment, never()).decideNew(any(), anyBoolean());
    verify(recorder).log(eq(existing), eq(AssignmentLog.Outcome.KEPT), eq(ana), eq(AssignmentMethod.CONTINUITY), anyString(), any(), any(), any(), any(), any());
  }

  @Test
  void propertyOfAnotherAdvisorIsFlaggedNotReassigned() {
    HandoffResult r = service.handoff(request(new PropertyRef(634294L, "634294--649", "GN 14 · Casa en Burgos", 2002L), "wa:x:s3"));
    assertThat(r.conflict()).isTrue();
    assertThat(r.advisorName()).isEqualTo("Ana");
    assertThat(existing.getAdvisorUserId()).isEqualTo(ana);
    assertThat(existing.getCoordinationNote()).contains("Luis").contains("Ana");
    // El nuevo interés no se pierde: queda en el lead (no tenía propiedad).
    assertThat(existing.getPropertyOnline21Id()).isEqualTo(634294L);
    assertThat(existing.getPropertyAdvisorUserId()).isEqualTo(luis);
    verify(recorder).log(eq(existing), eq(AssignmentLog.Outcome.CONFLICT), eq(ana), eq(AssignmentMethod.CONTINUITY), anyString(), any(), any(), anyString(), any(), any());
  }

  @Test
  void retriedEventReturnsTheSameLeadWithoutConsumingTheRound() {
    when(leads.findByRequestKey("wa:conv:s1")).thenReturn(Optional.of(existing));
    HandoffResult r = service.handoff(request(null, "wa:conv:s1"));
    assertThat(r.leadId()).isEqualTo(existing.getId());
    verify(assignment, never()).decideNew(any(), anyBoolean());
    verify(leads, never()).save(any());
  }
}
