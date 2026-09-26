package com.c21genera.expedientes.domain;

import com.c21genera.expedientes.ExpedienteStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Único lugar que decide qué transiciones de {@link ExpedienteStatus} son
 * válidas (ver AGENTS §10). No debe existir lógica de transición en
 * controllers ni servicios de aplicación: todos pasan por aquí.
 */
public final class ExpedienteStateMachine {

  private static final Map<ExpedienteStatus, Set<ExpedienteStatus>> TRANSITIONS = buildTransitions();

  private ExpedienteStateMachine() {}

  public static boolean isAllowed(ExpedienteStatus from, ExpedienteStatus to) {
    return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
  }

  public static void ensureAllowed(ExpedienteStatus from, ExpedienteStatus to) {
    if (!isAllowed(from, to)) {
      throw new InvalidExpedienteTransitionException(from, to);
    }
  }

  private static Map<ExpedienteStatus, Set<ExpedienteStatus>> buildTransitions() {
    Map<ExpedienteStatus, Set<ExpedienteStatus>> map = new EnumMap<>(ExpedienteStatus.class);

    map.put(ExpedienteStatus.DRAFT, EnumSet.of(ExpedienteStatus.WAITING_PRIVACY));
    map.put(ExpedienteStatus.WAITING_PRIVACY, EnumSet.of(ExpedienteStatus.WAITING_DOCUMENTS));
    map.put(ExpedienteStatus.WAITING_DOCUMENTS, EnumSet.of(ExpedienteStatus.DOCUMENTS_RECEIVED));
    map.put(ExpedienteStatus.DOCUMENTS_RECEIVED, EnumSet.of(ExpedienteStatus.UNDER_REVIEW));
    map.put(
        ExpedienteStatus.UNDER_REVIEW,
        EnumSet.of(
            ExpedienteStatus.CORRECTIONS_REQUESTED,
            ExpedienteStatus.DOCUMENTS_APPROVED,
            ExpedienteStatus.PROPERTY_REJECTED));
    map.put(
        ExpedienteStatus.CORRECTIONS_REQUESTED,
        EnumSet.of(ExpedienteStatus.UNDER_REVIEW, ExpedienteStatus.PROPERTY_REJECTED));
    // Si una corrección agrega un documento obligatorio (p. ej. un nuevo
    // copropietario), el expediente vuelve a correcciones aunque ya se
    // hubiera aprobado la documentación o firmado la recepción.
    map.put(
        ExpedienteStatus.DOCUMENTS_APPROVED,
        EnumSet.of(
            ExpedienteStatus.RECEPTION_SIGNED, ExpedienteStatus.CORRECTIONS_REQUESTED, ExpedienteStatus.PROPERTY_REJECTED));
    map.put(
        ExpedienteStatus.RECEPTION_SIGNED,
        EnumSet.of(
            ExpedienteStatus.CONTRACT_PREPARATION, ExpedienteStatus.CORRECTIONS_REQUESTED, ExpedienteStatus.PROPERTY_REJECTED));
    map.put(
        ExpedienteStatus.CONTRACT_PREPARATION,
        EnumSet.of(
            ExpedienteStatus.READY_FOR_SIGNATURE, ExpedienteStatus.CORRECTIONS_REQUESTED, ExpedienteStatus.PROPERTY_REJECTED));
    // Una corrección de datos invalida el contrato pendiente de firma y
    // regresa a preparación para generar una nueva versión.
    map.put(
        ExpedienteStatus.READY_FOR_SIGNATURE,
        EnumSet.of(
            ExpedienteStatus.CONTRACT_SIGNED,
            ExpedienteStatus.CONTRACT_PREPARATION,
            ExpedienteStatus.CORRECTIONS_REQUESTED,
            ExpedienteStatus.PROPERTY_REJECTED));
    // El inmueble solo se acepta con el contrato de intermediación ya firmado.
    map.put(
        ExpedienteStatus.CONTRACT_SIGNED,
        EnumSet.of(ExpedienteStatus.PROPERTY_ACCEPTED, ExpedienteStatus.PROPERTY_REJECTED));
    map.put(ExpedienteStatus.PROPERTY_ACCEPTED, EnumSet.of(ExpedienteStatus.CLOSED));
    map.put(ExpedienteStatus.PROPERTY_REJECTED, EnumSet.of(ExpedienteStatus.CLOSED));
    map.put(ExpedienteStatus.CLOSED, Set.of());

    return Map.copyOf(map);
  }
}
