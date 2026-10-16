package com.c21genera.contracts.application;

import com.c21genera.contracts.domain.ContractSetting;
import com.c21genera.contracts.infrastructure.ContractSettingRepository;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #21: texto estándar de "Instrucciones para visitas". Cada expediente
 * empieza con él (el asesor lo puede ajustar) y el administrador lo actualiza sin liberar código.
 */
@Service
@Transactional
public class ContractSettingsService {

  /** Texto definido por C21 Genera; la migración V032 lo guarda como valor inicial. */
  public static final String DEFAULT_VISIT_INSTRUCTIONS =
      "Las visitas al inmueble deberán programarse previamente con el propietario o la persona autorizada, con un mínimo de 24 horas de"
          + " anticipación, sujetas a disponibilidad y confirmación. Todas las visitas serán coordinadas por Century 21 Genera y realizadas"
          + " en compañía de un asesor inmobiliario autorizado. No se permitirá el acceso al inmueble sin previa autorización. Para toda"
          + " visita, se solicitará al interesado que envíe previamente una copia de su identificación oficial.";

  static final int MAX_LENGTH = 3000;

  private final ContractSettingRepository repository;
  private final Clock clock;

  public ContractSettingsService(ContractSettingRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public ContractSetting visitInstructions() {
    return repository
        .findById(ContractSetting.VISIT_INSTRUCTIONS)
        .orElseGet(() -> new ContractSetting(ContractSetting.VISIT_INSTRUCTIONS, DEFAULT_VISIT_INSTRUCTIONS, clock.instant(), null, null));
  }

  @Transactional(readOnly = true)
  public String visitInstructionsText() {
    return visitInstructions().getValue();
  }

  public ContractSetting changeVisitInstructions(String text, Actor actor) {
    String cleaned = text == null ? "" : text.strip();
    if (cleaned.length() < 20) {
      throw new UnprocessableException("VISIT_INSTRUCTIONS_REQUIRED", "Escribe las instrucciones para visitas (al menos 20 caracteres).");
    }
    if (cleaned.length() > MAX_LENGTH) {
      throw new UnprocessableException("VISIT_INSTRUCTIONS_TOO_LONG", "Las instrucciones pueden tener hasta " + MAX_LENGTH + " caracteres.");
    }
    ContractSetting setting =
        repository
            .findById(ContractSetting.VISIT_INSTRUCTIONS)
            .orElseGet(() -> new ContractSetting(ContractSetting.VISIT_INSTRUCTIONS, cleaned, clock.instant(), actor.userId(), actor.name()));
    setting.change(cleaned, clock.instant(), actor.userId(), actor.name());
    return repository.save(setting);
  }
}
