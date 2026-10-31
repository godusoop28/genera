package com.c21genera.operations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.identity.CurrentUser;
import com.c21genera.operations.application.OperationService.CreateCommand;
import com.c21genera.operations.application.OperationService.OperationDetail;
import com.c21genera.operations.application.OperationService.PartyInput;
import com.c21genera.operations.application.OperationService.RequirementView;
import com.c21genera.operations.domain.ClosingOperation;
import com.c21genera.operations.domain.ClosingOperation.Origin;
import com.c21genera.operations.domain.ClosingOperation.Side;
import com.c21genera.operations.domain.ClosingOperation.Type;
import com.c21genera.operations.domain.ClosingRequirement;
import com.c21genera.operations.domain.ClosingRequirement.Section;
import com.c21genera.operations.domain.MatrixSettings;
import com.c21genera.operations.domain.OperationEvent;
import com.c21genera.operations.domain.OperationParty;
import com.c21genera.operations.domain.OperationParty.PartySide;
import com.c21genera.operations.domain.RequirementEvidence;
import com.c21genera.operations.domain.RequirementTemplate;
import com.c21genera.operations.infrastructure.ClosingOperationRepository;
import com.c21genera.operations.infrastructure.ClosingRequirementRepository;
import com.c21genera.operations.infrastructure.MatrixSettingsRepository;
import com.c21genera.operations.infrastructure.OperationEventRepository;
import com.c21genera.operations.infrastructure.OperationPartyRepository;
import com.c21genera.operations.infrastructure.RequirementEvidenceRepository;
import com.c21genera.operations.infrastructure.RequirementTemplateRepository;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.ClosingEvents.ClosingCompleted;
import com.c21genera.shared.storage.FileStorage;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

/** CC#32: cierre comercial vs. cierre documental, "No aplica", vigencias y autorización. */
class OperationServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T18:00:00Z"), ZoneOffset.UTC);

  private final List<ClosingOperation> ops = new ArrayList<>();
  private final List<OperationParty> partyRows = new ArrayList<>();
  private final List<ClosingRequirement> reqRows = new ArrayList<>();
  private final List<RequirementEvidence> evidenceRows = new ArrayList<>();
  private final List<RequirementTemplate> templateRows = new ArrayList<>();
  private final MatrixSettings matrix = newMatrix();

  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final OperationService service;

  private final CurrentUser advisor = new CurrentUser(UUID.randomUUID(), "Asesora Ana", "ana@x.mx", "ADVISOR", List.of("OPERATION_MANAGE"));
  private final CurrentUser legal = new CurrentUser(UUID.randomUUID(), "Lic. Legal", "legal@x.mx", "ADMINISTRATOR", List.of("OPERATION_MANAGE", "OPERATION_REVIEW", "OPERATION_VIEW_ALL"));

  OperationServiceTest() {
    ClosingOperationRepository opRepo = mock(ClosingOperationRepository.class);
    when(opRepo.save(any())).thenAnswer(i -> add(ops, i.getArgument(0)));
    when(opRepo.findById(any())).thenAnswer(i -> ops.stream().filter(o -> o.getId().equals(i.getArgument(0))).findFirst());
    when(opRepo.findAllByOrderByUpdatedAtDesc()).thenAnswer(i -> List.copyOf(ops));
    when(opRepo.nextFolioSequenceValue()).thenReturn(1L);
    when(opRepo.findFirstByExpedienteIdAndStatusNot(any(), any())).thenReturn(Optional.empty());

    OperationPartyRepository partyRepo = mock(OperationPartyRepository.class);
    when(partyRepo.save(any())).thenAnswer(i -> add(partyRows, i.getArgument(0)));
    when(partyRepo.findByOperationId(any())).thenAnswer(i -> partyRows.stream().filter(p -> p.getOperationId().equals(i.getArgument(0))).toList());
    when(partyRepo.findByOperationIdIn(any())).thenAnswer(i -> partyRows.stream().filter(p -> ((Collection<?>) i.getArgument(0)).contains(p.getOperationId())).toList());

    ClosingRequirementRepository reqRepo = mock(ClosingRequirementRepository.class);
    when(reqRepo.save(any())).thenAnswer(i -> add(reqRows, i.getArgument(0)));
    when(reqRepo.findById(any())).thenAnswer(i -> reqRows.stream().filter(r -> r.getId().equals(i.getArgument(0))).findFirst());
    when(reqRepo.findByOperationIdOrderBySortOrderAsc(any()))
        .thenAnswer(i -> reqRows.stream().filter(r -> r.getOperationId().equals(i.getArgument(0))).sorted(Comparator.comparing(ClosingRequirement::getSortOrder)).toList());
    when(reqRepo.findByOperationIdIn(any())).thenAnswer(i -> reqRows.stream().filter(r -> ((Collection<?>) i.getArgument(0)).contains(r.getOperationId())).toList());

    RequirementEvidenceRepository evRepo = mock(RequirementEvidenceRepository.class);
    when(evRepo.save(any())).thenAnswer(i -> add(evidenceRows, i.getArgument(0)));
    when(evRepo.countByRequirementId(any())).thenAnswer(i -> evidenceRows.stream().filter(e -> e.getRequirementId().equals(i.getArgument(0))).count());
    when(evRepo.findByRequirementIdInOrderByVersionNumberDesc(any()))
        .thenAnswer(i -> evidenceRows.stream().filter(e -> ((Collection<?>) i.getArgument(0)).contains(e.getRequirementId())).sorted(Comparator.comparing(RequirementEvidence::getVersionNumber).reversed()).toList());

    RequirementTemplateRepository tplRepo = mock(RequirementTemplateRepository.class);
    when(tplRepo.findAllByOrderBySortOrderAsc()).thenAnswer(i -> List.copyOf(templateRows));

    MatrixSettingsRepository matrixRepo = mock(MatrixSettingsRepository.class);
    when(matrixRepo.findById((short) 1)).thenReturn(Optional.of(matrix));

    OperationEventRepository eventRepo = mock(OperationEventRepository.class);
    when(eventRepo.save(any())).thenAnswer(i -> i.getArgument(0, OperationEvent.class));
    when(eventRepo.findByOperationIdOrderByOccurredAtDesc(any())).thenReturn(List.of());

    ExpedienteLifecycleApi expedientes = mock(ExpedienteLifecycleApi.class);
    DocumentsApi documents = mock(DocumentsApi.class);
    FileStorage storage = mock(FileStorage.class);
    when(storage.store(anyString(), any(), anyLong(), anyString())).thenReturn(new FileStorage.StoredObjectMetadata("k", 1, "sha"));

    template(Section.OPERATION, PartySide.SELLER, "Identificación oficial vigente", true, null, 10);
    template(Section.OPERATION, PartySide.BUYER, "Comprobante de domicilio", false, 90, 20);
    template(Section.PLD, null, "Consulta en listas", false, null, 30);
    template(Section.PROFECO, null, "Aviso de privacidad entregado", false, null, 40);

    service = new OperationService(opRepo, partyRepo, reqRepo, evRepo, tplRepo, matrixRepo, eventRepo, expedientes, documents, storage, publisher, clock);
  }

  @Test
  void anExternalOperationGetsItsRequirementsWhenTheClosingStartsAndAsksEachSideFromTheRightPerson() {
    OperationDetail d = external(Side.BUYER);

    assertThat(d.operation().origin()).isEqualTo("EXTERNAL");
    assertThat(d.operation().displayStatus()).isEqualTo("En proceso");
    assertThat(d.requirements()).hasSize(4);
    // C21 Genera representa al comprador: los papeles del vendedor se piden a la contraparte.
    assertThat(req(d, "Identificación oficial vigente").requestedFrom()).isEqualTo("COUNTERPARTY");
    assertThat(req(d, "Comprobante de domicilio").requestedFrom()).isEqualTo("CLIENT");
    assertThat(d.summary().missingBySide()).containsEntry("SELLER", 1).containsEntry("BUYER", 1).containsEntry("OPERATION", 2);
  }

  @Test
  void anExternalOperationNeedsItsOriginOrParticipatingOffice() {
    assertThatThrownBy(() -> service.create(new CreateCommand(Origin.EXTERNAL, null, Type.SALE, Side.BUYER, "Casa Lomas", null, null, null, null, null, null, null, null, null), advisor))
        .isInstanceOf(UnprocessableException.class);
  }

  @Test
  void receivingAFileIsNotValidatingItAndOnlyTheReviewerValidates() {
    OperationDetail d = external(Side.BOTH);
    UUID id = d.operation().id();
    RequirementView privacy = req(d, "Aviso de privacidad entregado");

    d = service.addNoteEvidence(id, privacy.id(), "Entregado al firmar", null, advisor);
    assertThat(req(d, "Aviso de privacidad entregado").status()).isEqualTo("RECEIVED");
    assertThat(d.operation().docStatus()).isEqualTo("IN_REVIEW");

    assertThatThrownBy(() -> service.review(id, privacy.id(), ClosingRequirement.Status.VALIDATED, null, advisor)).isInstanceOf(AccessDeniedException.class);
    d = service.review(id, privacy.id(), ClosingRequirement.Status.VALIDATED, null, legal);
    assertThat(req(d, "Aviso de privacidad entregado").status()).isEqualTo("VALIDATED");
    assertThat(req(d, "Aviso de privacidad entregado").reviewedByName()).isEqualTo("Lic. Legal");
  }

  @Test
  void validatingWithoutEvidenceOrWithAnExpiredDocumentIsRejected() {
    OperationDetail d = external(Side.BOTH);
    UUID id = d.operation().id();
    RequirementView address = req(d, "Comprobante de domicilio");

    assertThatThrownBy(() -> service.review(id, address.id(), ClosingRequirement.Status.VALIDATED, null, legal)).hasMessageContaining("sin evidencia");

    service.addNoteEvidence(id, address.id(), "Recibo CFE", LocalDate.of(2026, 5, 1), advisor);
    assertThatThrownBy(() -> service.review(id, address.id(), ClosingRequirement.Status.VALIDATED, null, legal)).hasMessageContaining("vigente");
    assertThat(req(service.detail(id, legal), "Comprobante de domicilio").expired()).isTrue();
  }

  @Test
  void aMandatoryRequirementCannotBeNotApplicableAndOthersNeedJustificationAndApproval() {
    OperationDetail d = external(Side.BOTH);
    UUID id = d.operation().id();

    assertThatThrownBy(() -> service.requestNotApplicable(id, req(d, "Identificación oficial vigente").id(), "No la trajo", advisor)).hasMessageContaining("obligatorio");
    UUID lists = req(d, "Consulta en listas").id();
    assertThatThrownBy(() -> service.requestNotApplicable(id, lists, " ", advisor)).hasMessageContaining("Justifica");

    OperationDetail after = service.requestNotApplicable(id, lists, "Operación sin pago en efectivo", advisor);
    assertThat(req(after, "Consulta en listas").status()).isEqualTo("NOT_APPLICABLE");
    assertThat(req(after, "Consulta en listas").satisfied()).isFalse();
    assertThat(after.summary().pendingNaApprovals()).isEqualTo(1);

    after = service.decideNotApplicable(id, lists, true, null, legal);
    assertThat(req(after, "Consulta en listas").satisfied()).isTrue();
    assertThat(req(after, "Consulta en listas").naApprovedByName()).isEqualTo("Lic. Legal");
  }

  @Test
  void aClosedOperationWithMissingDocumentsShowsAsPendingAndNeedsAFollowUpCommitment() {
    OperationDetail d = external(Side.BOTH);
    UUID id = d.operation().id();

    assertThatThrownBy(() -> service.close(id, LocalDate.of(2026, 10, 30), new BigDecimal("4500000"), "MXN", null, null, advisor)).hasMessageContaining("fecha compromiso");

    d = service.close(id, LocalDate.of(2026, 10, 30), new BigDecimal("4500000"), "MXN", LocalDate.of(2026, 11, 15), "Asesora Ana", advisor);
    assertThat(d.operation().status()).isEqualTo("CLOSED");
    assertThat(d.operation().displayStatus()).isEqualTo("Cerrada — Documentación pendiente");
    assertThat(d.operation().closedOn()).isEqualTo(LocalDate.of(2026, 10, 30));
    assertThat(d.operation().closedByName()).isEqualTo("Asesora Ana");
    assertThat(d.operation().docDueDate()).isEqualTo(LocalDate.of(2026, 11, 15));
  }

  @Test
  void closingNeedsBothPartiesAndAnAmount() {
    OperationDetail d = service.create(new CreateCommand(Origin.EXTERNAL, null, Type.RENT, Side.BUYER, "Depto Centro", null, "Oficina aliada", null, null, null, null, null, null, null), advisor);
    UUID id = d.operation().id();
    assertThatThrownBy(() -> service.close(id, null, new BigDecimal("15000"), "MXN", LocalDate.of(2026, 11, 15), "Ana", advisor)).hasMessageContaining("arrendador");
  }

  @Test
  void theFileIsCompleteOnlyWithAnApprovedMatrixEverythingSatisfiedAndTheAuthorizedRole() {
    OperationDetail d = external(Side.BOTH);
    UUID id = d.operation().id();
    for (RequirementView r : d.requirements()) {
      service.addNoteEvidence(id, r.id(), "Evidencia de " + r.label(), LocalDate.of(2026, 10, 20), advisor);
      service.review(id, r.id(), ClosingRequirement.Status.VALIDATED, null, legal);
    }

    assertThatThrownBy(() -> service.authorize(id, advisor)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.authorize(id, legal)).hasMessageContaining("matriz documental no ha sido aprobada");

    matrix.approve(true, "Lic. Legal", clock.instant());
    d = service.authorize(id, legal);
    assertThat(d.operation().docStatus()).isEqualTo("COMPLETE_VALIDATED");
    assertThat(d.docAuthorizedByName()).isEqualTo("Lic. Legal");

    // Con el expediente validado, los requisitos no se tocan sin reabrir la revisión.
    UUID any = d.requirements().get(0).id();
    assertThatThrownBy(() -> service.addNoteEvidence(id, any, "otra", null, advisor)).isInstanceOf(ConflictException.class);

    d = service.close(id, null, new BigDecimal("5000000"), "MXN", null, null, advisor);
    assertThat(d.operation().displayStatus()).isEqualTo("Cerrada");
  }

  @Test
  void closingAnOwnOperationMarksItsExpedienteAsClosed() {
    UUID expedienteId = UUID.randomUUID();
    ClosingOperation op = new ClosingOperation("OP-1", Origin.OWN, expedienteId, Type.SALE, Side.SELLER, advisor.id(), advisor.name(), clock.instant());
    op.describe("Casa Las Flores", null, null, null, null, null, Side.SELLER, advisor.id(), advisor.name(), clock.instant());
    ops.add(op);
    OperationParty seller = new OperationParty(op.getId(), PartySide.SELLER);
    seller.update("María Pérez", null, null, null, null, null, null, null);
    OperationParty buyer = new OperationParty(op.getId(), PartySide.BUYER);
    buyer.update("Juan López", null, null, null, null, null, null, null);
    partyRows.add(seller);
    partyRows.add(buyer);

    service.close(op.getId(), LocalDate.of(2026, 10, 31), new BigDecimal("3000000"), "MXN", LocalDate.of(2026, 11, 10), "Ana", advisor);

    verify(publisher).publishEvent(new ClosingCompleted(expedienteId, LocalDate.of(2026, 10, 31)));
  }

  @Test
  void onlyTheAuthorizedRoleReopensAClosedOperation() {
    OperationDetail d = external(Side.BOTH);
    UUID id = d.operation().id();
    service.close(id, null, new BigDecimal("1000000"), "MXN", LocalDate.of(2026, 11, 20), "Ana", advisor);

    assertThatThrownBy(() -> service.reopen(id, "Error de captura", advisor)).isInstanceOf(AccessDeniedException.class);
    OperationDetail reopened = service.reopen(id, "Error de captura", legal);
    assertThat(reopened.operation().status()).isEqualTo("CLOSING_REVIEW");
    assertThat(reopened.operation().closedOn()).isNull();
  }

  @Test
  void anAdvisorOnlySeesTheirOwnOperations() {
    external(Side.BOTH);
    CurrentUser other = new CurrentUser(UUID.randomUUID(), "Otro", "o@x.mx", "ADVISOR", List.of("OPERATION_MANAGE"));
    assertThat(service.list(new OperationService.ListFilter(null, null, null, null, null, null, null, null), other).items()).isEmpty();
    assertThat(service.list(new OperationService.ListFilter(null, null, "EXTERNAL", null, null, null, null, null), legal).items()).hasSize(1);
  }

  // --- helpers ---

  private OperationDetail external(Side side) {
    return service.create(
        new CreateCommand(
            Origin.EXTERNAL,
            null,
            Type.SALE,
            side,
            "Casa Lomas 12",
            "Cuernavaca",
            "Bolsa inmobiliaria",
            "Inmobiliaria Aliada",
            "Pedro (asesor)",
            "Representamos al comprador",
            null,
            null,
            new PartyInput("Vendedor Uno", "INDIVIDUAL", null, null, null, null, "Pedro", null),
            new PartyInput("Comprador Dos", "INDIVIDUAL", null, null, null, null, null, null)),
        advisor);
  }

  private static RequirementView req(OperationDetail d, String label) {
    return d.requirements().stream().filter(r -> r.label().equals(label)).findFirst().orElseThrow();
  }

  private void template(Section section, PartySide side, String label, boolean mandatory, Integer validity, int order) {
    RequirementTemplate t = new RequirementTemplate(UUID.randomUUID());
    t.update(section, side, null, null, label, null, mandatory, validity, null, order, true);
    templateRows.add(t);
  }

  private static <T> T add(List<T> list, T item) {
    if (!list.contains(item)) list.add(item);
    return item;
  }

  private static MatrixSettings newMatrix() {
    try {
      Constructor<MatrixSettings> c = MatrixSettings.class.getDeclaredConstructor();
      c.setAccessible(true);
      MatrixSettings m = c.newInstance();
      return m;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
