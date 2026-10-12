package com.c21genera.leads.application;

import com.c21genera.leads.AssignmentMethod;
import com.c21genera.leads.AssignmentStatus;
import com.c21genera.leads.application.AvailabilityService.AdvisorState;
import com.c21genera.leads.application.AvailabilityService.Snapshot;
import com.c21genera.leads.application.AvailabilityService.Status;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.domain.AssignmentEngine;
import com.c21genera.leads.domain.AssignmentEngine.Candidate;
import com.c21genera.leads.domain.AssignmentEngine.Mode;
import com.c21genera.leads.domain.AssignmentEngine.Pick;
import com.c21genera.leads.domain.AssignmentSettings;
import com.c21genera.leads.domain.AssignmentSettings.OwnerOffShiftPolicy;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.domain.Schedule;
import com.c21genera.leads.domain.Schedule.Kind;
import com.c21genera.leads.domain.Schedule.Occurrence;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.leads.infrastructure.AssignmentSettingsRepository;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Servicio ÚNICO de asignación (bot, alta manual con asignación automática y
 * procesamiento de pendientes). Reglas, en orden:
 *
 * <ol>
 *   <li>B. Lead nuevo de una propiedad cuyo asesor de 21Online está vinculado a
 *       un usuario activo que recibe leads: si está en turno, es suyo. Si está
 *       fuera de horario o ausente se aplica la política de la oficina:
 *       RESPECT_OWNER (predeterminado) lo deja pendiente de su próximo turno;
 *       ROUTE_TO_GUARD lo canaliza a un asesor disponible (prefiere guardias)
 *       conservando la referencia del dueño. Dueño no vinculado, desactivado,
 *       en pausa o sin turnos próximos: se registra el motivo y se usa la ronda
 *       (nunca se espera indefinidamente).
 *   <li>C. Sin propiedad o sin dueño vinculado: ronda (equitativa o ponderada)
 *       entre quienes están en un turno habilitado, sin ausencia, sin pausa y
 *       bajo su límite diario.
 *   <li>Nadie disponible: "Pendiente por horario" con el próximo turno elegible.
 * </ol>
 *
 * <p>La regla A (continuidad de un lead activo) y la D (cliente existente con
 * propiedad de otro asesor) se aplican antes, en LeadIntakeService.
 *
 * <p>Debe llamarse dentro de una transacción: bloquea los perfiles (estado de
 * la ronda y conteos) hasta que se guarda el lead. En simulación (dryRun) no
 * bloquea ni mueve la ronda.
 */
@Service
class AssignmentService {

  private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.forLanguageTag("es-MX")).withZone(Schedule.ZONE);

  record Decision(
      AssignmentStatus status,
      UUID advisorId,
      AssignmentMethod reason,
      String rule,
      UUID shiftId,
      String pendingReason,
      Instant nextShiftAt,
      UUID propertyAdvisorUserId,
      Mode roundMode,
      String detail) {}

  private final AdvisorProfileRepository profiles;
  private final AssignmentSettingsRepository settingsRepository;
  private final AvailabilityService availability;

  AssignmentService(AdvisorProfileRepository profiles, AssignmentSettingsRepository settingsRepository, AvailabilityService availability) {
    this.profiles = profiles;
    this.settingsRepository = settingsRepository;
    this.availability = availability;
  }

  AssignmentSettings settings() {
    return settingsRepository.findById((short) 1).orElseThrow(() -> new IllegalStateException("Falta la fila de crm_assignment_settings"));
  }

  /** Lead nuevo. */
  Decision decideNew(Long propertyAdvisorOnline21Id, boolean dryRun) {
    Snapshot s = availability.snapshot(dryRun ? null : profiles.lockAll());
    AssignmentSettings settings = settings();
    Owner owner = resolveOwner(s, propertyAdvisorOnline21Id);
    return decide(s, settings, owner, dryRun, false);
  }

  /** Lead pendiente: se vuelve a evaluar al empezar un turno. */
  Decision decidePending(Lead lead, Snapshot lockedSnapshot) {
    AssignmentSettings settings = settings();
    Owner owner;
    if (lead.getAssignmentStatus() == AssignmentStatus.PENDING_OWNER && lead.getAdvisorUserId() != null) {
      owner = ownerFromUser(lockedSnapshot, lead.getAdvisorUserId(), lead.getPropertyAdvisorOnline21Id());
    } else if (lead.getPropertyAdvisorUserId() != null) {
      owner = ownerFromUser(lockedSnapshot, lead.getPropertyAdvisorUserId(), lead.getPropertyAdvisorOnline21Id());
    } else {
      owner = Owner.none();
    }
    return decide(lockedSnapshot, settings, owner, false, true);
  }

  Snapshot lockedSnapshot() {
    return availability.snapshot(profiles.lockAll());
  }

  // ---------------------------------------------------------------------

  private Decision decide(Snapshot s, AssignmentSettings settings, Owner owner, boolean dryRun, boolean fromPending) {
    String ownerNote = owner.problem();
    if (owner.userId() != null && owner.problem() == null) {
      Optional<Occurrence> onShift = Schedule.activeAt(owner.userId(), s.rules(), s.absences(), s.now(), false);
      if (onShift.isPresent()) {
        return new Decision(
            AssignmentStatus.ASSIGNED,
            owner.userId(),
            AssignmentMethod.PROPERTY_OWNER,
            fromPending ? "Asesor de la propiedad, al iniciar su turno" : "Prioridad: asesor responsable de la propiedad (en turno)",
            onShift.get().shiftId(),
            null,
            null,
            owner.userId(),
            null,
            null);
      }
      boolean absent = Schedule.absentAt(owner.userId(), s.absences(), s.now());
      String why = absent ? "El asesor de la propiedad está ausente" : "El asesor de la propiedad está fuera de horario";
      if (settings.getOwnerOffShiftPolicy() == OwnerOffShiftPolicy.RESPECT_OWNER) {
        Optional<Instant> next = Schedule.nextAvailable(owner.userId(), s.rules(), s.absences(), s.now(), AvailabilityService.HORIZON_DAYS, false);
        if (next.isPresent()) {
          return new Decision(
              AssignmentStatus.PENDING_OWNER,
              owner.userId(),
              AssignmentMethod.PROPERTY_OWNER,
              "Respetar al asesor de la propiedad: queda pendiente de su próximo turno",
              null,
              why + "; se atenderá en su próximo turno (" + WHEN.format(next.get()) + ")",
              next.get(),
              owner.userId(),
              null,
              null);
        }
        ownerNote = why + " y no tiene turnos en los próximos " + AvailabilityService.HORIZON_DAYS + " días: se aplicó la ronda de respaldo";
      } else {
        ownerNote = why + ": se canalizó a un asesor de guardia";
        return round(s, settings, owner.linkedUserId(), ownerNote, true, dryRun);
      }
    }
    return round(s, settings, owner.linkedUserId(), ownerNote, false, dryRun);
  }

  private Decision round(Snapshot s, AssignmentSettings settings, UUID propertyAdvisor, String ownerNote, boolean guard, boolean dryRun) {
    List<AdvisorState> states = new ArrayList<>();
    for (AdvisorProfile p : s.profiles().values()) {
      if (s.active(p.getUserId())) {
        states.add(availability.state(s, p.getUserId()));
      }
    }
    List<AdvisorState> eligible = states.stream().filter(st -> st.status() == Status.AVAILABLE).toList();
    boolean usedGuards = false;
    if (guard) {
      List<AdvisorState> guards = eligible.stream().filter(st -> st.currentShift() != null && st.currentShift().kind() == Kind.GUARD).toList();
      if (!guards.isEmpty()) {
        eligible = guards;
        usedGuards = true;
      }
    }
    Mode mode = settings.getRoundMode();
    List<Candidate> candidates =
        eligible.stream()
            .map(st -> {
              AdvisorProfile p = s.profiles().get(st.userId());
              return new Candidate(st.userId(), p.getWeight(), p.getRoundLastAt(), p.getRoundCurrentWeight());
            })
            .toList();
    Optional<Pick> pick = AssignmentEngine.pick(mode, candidates);
    if (pick.isEmpty()) {
      Instant next = availability.nextCoverage(s).orElse(null);
      String reason = "No hay asesores disponibles en turno" + summary(states) + (ownerNote != null ? ". " + ownerNote : "");
      return new Decision(AssignmentStatus.PENDING_SCHEDULE, null, null, "Pendiente por horario", null, reason, next, propertyAdvisor, mode, ownerNote);
    }
    UUID chosen = pick.get().userId();
    if (!dryRun) {
      AdvisorProfile p = s.profiles().get(chosen);
      p.roundAssigned(s.now());
      pick.get().newCurrentWeights().forEach((id, w) -> s.profiles().get(id).roundWeight(w));
    }
    AdvisorState st = eligible.stream().filter(e -> e.userId().equals(chosen)).findFirst().orElseThrow();
    String modeText = mode == Mode.EQUAL ? "equitativa" : "ponderada";
    String rule =
        (guard ? (usedGuards ? "Guardia: el asesor de la propiedad no estaba disponible" : "Guardia (sin guardias en turno): ronda entre los disponibles") : "Ronda " + modeText)
            + " entre " + eligible.size() + " asesor" + (eligible.size() == 1 ? "" : "es") + " en turno";
    return new Decision(
        AssignmentStatus.ASSIGNED, chosen, guard ? AssignmentMethod.GUARD : AssignmentMethod.ROULETTE, rule, st.currentShift().shiftId(), null, null, propertyAdvisor, mode, ownerNote);
  }

  private static String summary(List<AdvisorState> states) {
    if (states.isEmpty()) {
      return " (no hay asesores configurados)";
    }
    Map<Status, Long> counts = new EnumMap<>(Status.class);
    states.forEach(st -> counts.merge(st.status(), 1L, Long::sum));
    String text =
        counts.entrySet().stream()
            .filter(e -> e.getKey() != Status.AVAILABLE)
            .map(e -> e.getValue() + " " + label(e.getKey()))
            .collect(Collectors.joining(", "));
    return text.isEmpty() ? "" : " (" + text + ")";
  }

  private static String label(Status status) {
    return switch (status) {
      case OFF_SHIFT -> "fuera de horario";
      case ABSENT -> "ausente(s)";
      case PAUSED -> "en pausa";
      case LIMIT_REACHED -> "con límite alcanzado";
      case NO_CALENDAR -> "sin calendario";
      case AVAILABLE -> "disponible(s)";
    };
  }

  /** userId: dueño válido para priorizar; linkedUserId: usuario vinculado aunque no pueda recibir (referencia). */
  record Owner(UUID userId, UUID linkedUserId, String problem) {
    static Owner none() {
      return new Owner(null, null, null);
    }
  }

  private Owner resolveOwner(Snapshot s, Long online21Id) {
    if (online21Id == null) {
      return Owner.none();
    }
    Optional<AdvisorProfile> linked = s.profiles().values().stream().filter(p -> online21Id.equals(p.getOnline21UserId())).findFirst();
    if (linked.isEmpty()) {
      return new Owner(null, null, "El asesor de la propiedad (21Online #" + online21Id + ") no está vinculado a un usuario del CRM: se aplicó la ronda");
    }
    return ownerFromUser(s, linked.get().getUserId(), online21Id);
  }

  private Owner ownerFromUser(Snapshot s, UUID userId, Long online21Id) {
    if (!s.active(userId)) {
      return new Owner(null, userId, "El asesor de la propiedad está desactivado: se aplicó la ronda");
    }
    AdvisorProfile p = s.profiles().get(userId);
    if (p == null || p.pausedAt(s.now())) {
      return new Owner(null, userId, "El asesor de la propiedad está en pausa (no recibe leads): se aplicó la ronda");
    }
    return new Owner(userId, userId, null);
  }
}
