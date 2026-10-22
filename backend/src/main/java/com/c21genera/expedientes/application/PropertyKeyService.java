package com.c21genera.expedientes.application;

import com.c21genera.expedientes.OperationType;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.domain.ExpedienteChange;
import com.c21genera.expedientes.domain.PropertyKey;
import com.c21genera.expedientes.infrastructure.ExpedienteChangeRepository;
import com.c21genera.expedientes.infrastructure.ExpedienteRepository;
import com.c21genera.propertycatalog.PropertyCatalog;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteDataCorrected;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #27 y su adenda: clave estructurada del inmueble (tipo + operación +
 * iniciales del asesor + consecutivo, ver {@link PropertyKey}).
 *
 * <ul>
 *   <li>Al dar de alta se genera sola con el consecutivo más alto de la combinación + 1.
 *   <li>Las propiedades que ya existían la reciben a mano (administrador o directivo) respetando
 *       la clave capturada; antes de guardar se valida que nadie más la tenga.
 *   <li>El listado de claves muestra, por combinación, los consecutivos en uso y los disponibles.
 * </ul>
 *
 * Una vez asignada la clave no cambia sola (aunque después se reasigne el asesor o se corrija el
 * tipo): es el identificador del inmueble. Solo el administrador o directivo la corrige a mano.
 */
@Service
@Transactional
public class PropertyKeyService {

  private final ExpedienteRepository expedienteRepository;
  private final ExpedienteChangeRepository changeRepository;
  private final PropertyCatalog propertyCatalog;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public PropertyKeyService(
      ExpedienteRepository expedienteRepository,
      ExpedienteChangeRepository changeRepository,
      PropertyCatalog propertyCatalog,
      ApplicationEventPublisher events,
      Clock clock) {
    this.expedienteRepository = expedienteRepository;
    this.changeRepository = changeRepository;
    this.propertyCatalog = propertyCatalog;
    this.events = events;
    this.clock = clock;
  }

  /** Combinación que le corresponde al expediente (vacío si su tipo no tiene letras o el asesor no tiene nombre). */
  @Transactional(readOnly = true)
  public Optional<String> prefixOf(Expediente expediente) {
    String typeCode =
        expediente.getPropertySubtypeId() == null
            ? null
            : propertyCatalog.subtype(expediente.getPropertySubtypeId()).map(PropertyCatalog.Subtype::keyCode).orElse(null);
    return PropertyKey.prefixFor(typeCode, expediente.getOperationType(), expediente.getAssignedAdvisorName());
  }

  /** Siguiente consecutivo automático: el más alto registrado para la combinación + 1. */
  @Transactional(readOnly = true)
  public PropertyKey nextFor(String prefix) {
    Integer max = expedienteRepository.maxPropertyKeyNumber(prefix);
    return new PropertyKey(prefix, (max == null ? 0 : max) + 1);
  }

  /** Alta: genera la clave si el tipo de inmueble y el asesor lo permiten; si no, queda para asignarla a mano. */
  public void assignAutomatically(Expediente expediente) {
    if (expediente.getPropertyKey() != null) {
      return;
    }
    prefixOf(expediente).map(this::nextFor).ifPresent(expediente::assignPropertyKey);
  }

  /** Resultado de validar una clave capturada a mano. assignedTo: el otro expediente que ya la tiene. */
  public record KeyCheck(String key, boolean valid, boolean available, String message, Holder assignedTo) {}

  public record Holder(UUID expedienteId, String folio, String ownerDisplayName, String property) {}

  /** Validación en tiempo real (antes de guardar). expedienteId: el expediente al que se le quiere asignar. */
  @Transactional(readOnly = true)
  public KeyCheck check(String raw, UUID expedienteId) {
    Optional<PropertyKey> parsed = PropertyKey.parse(raw);
    if (parsed.isEmpty()) {
      return new KeyCheck(
          raw == null ? "" : raw.strip(),
          false,
          false,
          "La clave debe ser letras (tipo + operación + iniciales del asesor) seguidas del consecutivo, p. ej. CVEA03.",
          null);
    }
    String key = parsed.get().value();
    Optional<Expediente> holder = expedienteRepository.findByPropertyKey(key).filter(e -> !e.getId().equals(expedienteId));
    if (holder.isPresent()) {
      Expediente e = holder.get();
      return new KeyCheck(
          key,
          true,
          false,
          "La clave " + key + " ya está asignada al expediente " + describe(e) + ". Por favor elige otro consecutivo.",
          new Holder(e.getId(), e.getFolio(), e.getOwnerDisplayName(), propertyOf(e)));
    }
    return new KeyCheck(key, true, true, "La clave " + key + " está disponible.", null);
  }

  /** Asignación manual (migración o corrección): se respeta la clave capturada; queda en el historial. */
  public Expediente assign(UUID expedienteId, String raw, Actor actor, String reason) {
    Expediente expediente = expedienteRepository.findById(expedienteId).orElseThrow(() -> new NotFoundException("Expediente", expedienteId));
    KeyCheck check = check(raw, expedienteId);
    if (!check.valid()) {
      throw new UnprocessableException("INVALID_PROPERTY_KEY", check.message());
    }
    if (!check.available()) {
      throw new ConflictException("PROPERTY_KEY_IN_USE", check.message());
    }
    PropertyKey key = PropertyKey.parse(raw).orElseThrow();
    String previous = expediente.getPropertyKey();
    if (key.value().equals(previous)) {
      return expediente;
    }
    expediente.assignPropertyKey(key);
    // Que la restricción única responda aquí (dos administradores con la misma clave a la vez) y no al final.
    expedienteRepository.saveAndFlush(expediente);
    String cleanReason = reason == null || reason.isBlank() ? null : reason.strip();
    changeRepository.save(
        new ExpedienteChange(expediente.getId(), clock.instant(), actor, "Datos del expediente", "Clave del inmueble", previous, key.value(), cleanReason));
    events.publishEvent(new ExpedienteDataCorrected(expediente.getId(), actor, "Datos del expediente", List.of("Clave del inmueble"), cleanReason, false));
    return expediente;
  }

  // ----- Consulta de claves en uso -----

  /** unclassified = solo los pendientes de clasificación (sin subcategoría). */
  public record KeyFilter(UUID categoryId, UUID subtypeId, boolean unclassified, OperationType operationType, UUID advisorId, String prefix) {}

  public record AssignedKey(
      UUID expedienteId,
      String folio,
      String key,
      String prefix,
      int number,
      String ownerDisplayName,
      String property,
      String propertyTypeLabel,
      OperationType operationType,
      UUID advisorId,
      String advisorName,
      Instant archivedAt) {}

  /** Por combinación: consecutivos en uso, los huecos disponibles por debajo del más alto y el siguiente automático. */
  public record KeyCombination(String prefix, List<Integer> used, List<Integer> gaps, String nextKey) {}

  /** Expediente sin clave, con la que le tocaría (null si su tipo no tiene letras o falta el asesor). */
  public record PendingExpediente(
      UUID expedienteId,
      String folio,
      String ownerDisplayName,
      String property,
      String propertyTypeLabel,
      OperationType operationType,
      String advisorName,
      String suggestedPrefix,
      String suggestedKey,
      Instant archivedAt) {}

  public record KeyOverview(List<AssignedKey> assigned, List<KeyCombination> combinations, List<PendingExpediente> withoutKey) {}

  @Transactional(readOnly = true)
  public KeyOverview overview(KeyFilter filter) {
    List<Expediente> withKey = expedienteRepository.findByPropertyKeyIsNotNull();
    Map<String, TreeSet<Integer>> usedByPrefix = new HashMap<>();
    for (Expediente e : withKey) {
      usedByPrefix.computeIfAbsent(e.getPropertyKeyPrefix(), p -> new TreeSet<>()).add(e.getPropertyKeyNumber());
    }
    String wantedPrefix = filter.prefix() == null || filter.prefix().isBlank() ? null : filter.prefix().strip().toUpperCase(java.util.Locale.ROOT);

    List<AssignedKey> assigned =
        withKey.stream()
            .filter(e -> matches(e, filter))
            .filter(e -> wantedPrefix == null || e.getPropertyKeyPrefix().startsWith(wantedPrefix))
            .sorted(Comparator.comparing(Expediente::getPropertyKeyPrefix).thenComparing(Expediente::getPropertyKeyNumber))
            .map(
                e ->
                    new AssignedKey(
                        e.getId(),
                        e.getFolio(),
                        e.getPropertyKey(),
                        e.getPropertyKeyPrefix(),
                        e.getPropertyKeyNumber(),
                        e.getOwnerDisplayName(),
                        propertyOf(e),
                        e.propertyTypeLabel(),
                        e.getOperationType(),
                        e.getAssignedAdvisorId(),
                        e.getAssignedAdvisorName(),
                        e.getArchivedAt()))
            .toList();

    // Sugerencias para los que no tienen clave: consecutivos seguidos y sin repetirse entre sí.
    Map<String, Integer> nextByPrefix = new HashMap<>();
    List<PendingExpediente> withoutKey = new ArrayList<>();
    expedienteRepository.findByPropertyKeyIsNull().stream()
        .filter(e -> matches(e, filter))
        .sorted(Comparator.comparing(Expediente::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .forEach(
            e -> {
              String prefix = prefixOf(e).orElse(null);
              String suggested = null;
              if (prefix != null) {
                int next =
                    nextByPrefix.merge(
                        prefix, usedByPrefix.containsKey(prefix) ? usedByPrefix.get(prefix).last() + 1 : 1, (current, ignored) -> current + 1);
                suggested = next <= PropertyKey.MAX_NUMBER ? new PropertyKey(prefix, next).value() : null;
              }
              if (wantedPrefix == null || (prefix != null && prefix.startsWith(wantedPrefix))) {
                withoutKey.add(
                    new PendingExpediente(
                        e.getId(),
                        e.getFolio(),
                        e.getOwnerDisplayName(),
                        propertyOf(e),
                        e.propertyTypeLabel(),
                        e.getOperationType(),
                        e.getAssignedAdvisorName(),
                        prefix,
                        suggested,
                        e.getArchivedAt()));
              }
            });

    // Combinaciones: las de las claves listadas, las sugeridas y la que se consultó (aunque no tenga ninguna).
    TreeMap<String, KeyCombination> combinations = new TreeMap<>();
    List<String> prefixes = new ArrayList<>();
    assigned.forEach(a -> prefixes.add(a.prefix()));
    withoutKey.stream().map(PendingExpediente::suggestedPrefix).filter(java.util.Objects::nonNull).forEach(prefixes::add);
    if (wantedPrefix != null && wantedPrefix.matches("[A-Z]{2,18}")) {
      prefixes.add(wantedPrefix);
    }
    for (String prefix : prefixes) {
      combinations.computeIfAbsent(prefix, p -> combinationOf(p, usedByPrefix.getOrDefault(p, new TreeSet<>())));
    }
    return new KeyOverview(assigned, List.copyOf(combinations.values()), withoutKey);
  }

  static KeyCombination combinationOf(String prefix, TreeSet<Integer> used) {
    List<Integer> gaps = new ArrayList<>();
    int max = used.isEmpty() ? 0 : used.last();
    for (int n = 1; n < max; n++) {
      if (!used.contains(n)) {
        gaps.add(n);
      }
    }
    return new KeyCombination(prefix, List.copyOf(used), gaps, max < PropertyKey.MAX_NUMBER ? new PropertyKey(prefix, max + 1).value() : null);
  }

  private static boolean matches(Expediente e, KeyFilter filter) {
    if (filter.categoryId() != null && !filter.categoryId().equals(e.getPropertyCategoryId())) {
      return false;
    }
    if (filter.subtypeId() != null && !filter.subtypeId().equals(e.getPropertySubtypeId())) {
      return false;
    }
    if (filter.unclassified() && e.getPropertySubtypeId() != null) {
      return false;
    }
    if (filter.operationType() != null && filter.operationType() != e.getOperationType()) {
      return false;
    }
    return filter.advisorId() == null || filter.advisorId().equals(e.getAssignedAdvisorId());
  }

  private static String propertyOf(Expediente e) {
    return e.getPropertyAddress() != null ? e.getPropertyAddress() : e.getPropertyReference();
  }

  private static String describe(Expediente e) {
    String property = propertyOf(e);
    return e.getFolio() + " (" + e.getOwnerDisplayName() + (property != null ? " — " + property : "") + ")";
  }
}
