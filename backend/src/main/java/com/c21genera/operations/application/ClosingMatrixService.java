package com.c21genera.operations.application;

import com.c21genera.identity.CurrentUser;
import com.c21genera.operations.domain.ClosingOperation.Origin;
import com.c21genera.operations.domain.ClosingOperation.Type;
import com.c21genera.operations.domain.ClosingRequirement.Section;
import com.c21genera.operations.domain.MatrixSettings;
import com.c21genera.operations.domain.OperationParty.PartySide;
import com.c21genera.operations.domain.RequirementTemplate;
import com.c21genera.operations.infrastructure.MatrixSettingsRepository;
import com.c21genera.operations.infrastructure.RequirementTemplateRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CC#32: matriz documental de cierre, configurable por el área legal o responsable de cumplimiento
 * (OPERATION_MATRIX_MANAGE). Cualquier cambio la deja sin aprobar hasta que se vuelva a aprobar.
 * Los cambios aplican a operaciones nuevas; las existentes los toman con "Aplicar matriz vigente".
 */
@Service
@Transactional
public class ClosingMatrixService {

  private final RequirementTemplateRepository templates;
  private final MatrixSettingsRepository settings;
  private final Clock clock;

  public ClosingMatrixService(RequirementTemplateRepository templates, MatrixSettingsRepository settings, Clock clock) {
    this.templates = templates;
    this.settings = settings;
    this.clock = clock;
  }

  public record TemplateView(
      UUID id,
      String section,
      String partySide,
      String operationType,
      String origin,
      String label,
      String description,
      boolean mandatory,
      Integer validityDays,
      String documentTypeHint,
      int sortOrder,
      boolean active) {}

  public record MatrixView(boolean approved, String approvedByName, Instant approvedAt, int revision, String updatedByName, Instant updatedAt, List<TemplateView> items) {}

  public record TemplateInput(
      Section section,
      PartySide partySide,
      Type operationType,
      Origin origin,
      String label,
      String description,
      boolean mandatory,
      Integer validityDays,
      String documentTypeHint,
      Integer sortOrder,
      Boolean active) {}

  @Transactional(readOnly = true)
  public MatrixView view() {
    MatrixSettings s = matrix();
    List<TemplateView> items =
        templates.findAllByOrderBySortOrderAsc().stream()
            .map(t -> new TemplateView(t.getId(), t.getSection().name(), name(t.getPartySide()), name(t.getOperationType()), name(t.getOrigin()), t.getLabel(), t.getDescription(), t.isMandatory(), t.getValidityDays(), t.getDocumentTypeHint(), t.getSortOrder(), t.isActive()))
            .toList();
    return new MatrixView(s.isApproved(), s.getApprovedByName(), s.getApprovedAt(), s.getRevision(), s.getUpdatedByName(), s.getUpdatedAt(), items);
  }

  public MatrixView save(UUID id, TemplateInput in, CurrentUser user) {
    require(user);
    if (in.section() == null || in.label() == null || in.label().isBlank()) {
      throw new UnprocessableException("TEMPLATE_INCOMPLETE", "Indica la sección y el nombre del requisito.");
    }
    if (in.validityDays() != null && in.validityDays() <= 0) {
      throw new UnprocessableException("TEMPLATE_VALIDITY", "La vigencia debe ser de al menos un día (o vacía si no vence).");
    }
    RequirementTemplate t = id == null ? new RequirementTemplate(UUID.randomUUID()) : templates.findById(id).orElseThrow(() -> new NotFoundException("Requisito de la matriz", id));
    t.update(
        in.section(),
        in.partySide(),
        in.operationType(),
        in.origin(),
        in.label().strip(),
        in.description() == null || in.description().isBlank() ? null : in.description().strip(),
        in.mandatory(),
        in.validityDays(),
        in.documentTypeHint() == null || in.documentTypeHint().isBlank() ? null : in.documentTypeHint().strip(),
        in.sortOrder() == null ? t.getSortOrder() : in.sortOrder(),
        in.active() == null || in.active());
    templates.save(t);
    matrix().changed(user.name(), clock.instant());
    return view();
  }

  public MatrixView approve(boolean approve, CurrentUser user) {
    require(user);
    matrix().approve(approve, user.name(), clock.instant());
    return view();
  }

  private MatrixSettings matrix() {
    return settings.findById((short) 1).orElseThrow(() -> new IllegalStateException("Falta closing_matrix_settings"));
  }

  private static void require(CurrentUser user) {
    if (!user.hasPermission(OperationService.MATRIX_MANAGE)) {
      throw new AccessDeniedException("Solo el área legal o responsable de cumplimiento configura la matriz documental.");
    }
  }

  private static String name(Enum<?> e) {
    return e == null ? null : e.name();
  }
}
