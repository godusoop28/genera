package com.c21genera.leads.application;

import com.c21genera.leads.LeadSource;
import com.c21genera.leads.domain.LeadSourceOption;
import com.c21genera.leads.domain.LeadSourceOption.Category;
import com.c21genera.leads.infrastructure.LeadSourceOptionRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import java.text.Normalizer;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #29: catálogo "¿De dónde viene el lead?". El selector
 * muestra solo las activas (agrupadas por categoría); los leads guardan el
 * código, así que desactivar o renombrar una fuente no cambia el histórico
 * (se muestra con el nombre vigente del catálogo).
 */
@Service
@Transactional
public class LeadSourceCatalog {

  public record SourceOption(String code, Category category, String label, String description, LeadSource channel, boolean active, boolean requiresDetail, boolean system, int sortOrder) {}

  /** Fuente ya validada para guardarla en un lead. */
  public record Resolved(String code, LeadSource channel, String label, String detail) {}

  private final LeadSourceOptionRepository options;
  private final Clock clock;

  LeadSourceCatalog(LeadSourceOptionRepository options, Clock clock) {
    this.options = options;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<SourceOption> all() {
    return options.findAll().stream()
        .sorted(Comparator.comparing((LeadSourceOption o) -> o.getCategory().ordinal()).thenComparingInt(LeadSourceOption::getSortOrder).thenComparing(LeadSourceOption::getLabel))
        .map(LeadSourceCatalog::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<String, SourceOption> byCode() {
    return all().stream().collect(Collectors.toMap(SourceOption::code, Function.identity()));
  }

  /**
   * Valida la fuente elegida en el alta o al corregir la ficha. Una fuente
   * inactiva solo se acepta si el lead ya la tenía (keepCode): corregir otros
   * datos no obliga a cambiarla.
   */
  @Transactional(readOnly = true)
  public Resolved resolve(String code, String detail, String keepCode) {
    if (code == null || code.isBlank()) {
      throw new UnprocessableException("LEAD_SOURCE_REQUIRED", "Indica de dónde viene el lead.");
    }
    LeadSourceOption option = options.findById(code.strip()).orElseThrow(() -> new UnprocessableException("LEAD_SOURCE_UNKNOWN", "Esa fuente no existe en el catálogo."));
    boolean unchanged = option.getCode().equals(keepCode);
    if (!unchanged && (!option.isActive() || option.isSystem())) {
      throw new UnprocessableException("LEAD_SOURCE_INACTIVE", "La fuente \"" + option.getLabel() + "\" no está disponible.");
    }
    String text = detail == null || detail.isBlank() ? null : detail.strip();
    if (option.isRequiresDetail() && text == null) {
      throw new UnprocessableException("LEAD_SOURCE_DETAIL_REQUIRED", "Describe el origen del lead (\"" + option.getLabel() + "\").");
    }
    return new Resolved(option.getCode(), option.getChannel(), option.getLabel(), text);
  }

  public SourceOption update(String code, String label, String description, boolean active, boolean requiresDetail) {
    LeadSourceOption option = options.findById(code).orElseThrow(() -> new NotFoundException("Fuente no encontrada"));
    if (option.isSystem() && !active) {
      throw new UnprocessableException("LEAD_SOURCE_SYSTEM", "Esta fuente la asigna el asistente de WhatsApp: no se puede desactivar.");
    }
    option.update(label, description, active, requiresDetail, clock.instant());
    return view(option);
  }

  /** Fuente nueva (p. ej. una subcategoría "Inmuebles24 — anuncio pagado"); el código se deriva del nombre. */
  public SourceOption create(Category category, String label, String description, LeadSource channel, boolean requiresDetail) {
    if (label == null || label.isBlank()) {
      throw new UnprocessableException("LEAD_SOURCE_LABEL_REQUIRED", "Escribe el nombre de la fuente.");
    }
    if (channel == null || channel == LeadSource.WHATSAPP_BOT) {
      channel = LeadSource.OTHER;
    }
    String base = codeOf(label);
    String code = base;
    for (int i = 2; options.existsById(code); i++) {
      code = base + "_" + i;
    }
    int sort = options.findAll().stream().filter(o -> o.getCategory() == category).mapToInt(LeadSourceOption::getSortOrder).filter(s -> s < 90).max().orElse(0) + 5;
    return view(options.save(new LeadSourceOption(code, category, label.strip(), description == null || description.isBlank() ? null : description.strip(), channel, requiresDetail, sort, clock.instant())));
  }

  static String codeOf(String label) {
    String plain = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    String code = plain.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    if (code.isEmpty()) {
      code = "FUENTE";
    }
    return code.length() > 40 ? code.substring(0, 40) : code;
  }

  private static SourceOption view(LeadSourceOption o) {
    return new SourceOption(o.getCode(), o.getCategory(), o.getLabel(), o.getDescription(), o.getChannel(), o.isActive(), o.isRequiresDetail(), o.isSystem(), o.getSortOrder());
  }
}
