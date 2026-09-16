package com.c21genera.compliance.application;

import com.c21genera.compliance.domain.ComplianceCheckCode;
import com.c21genera.compliance.domain.ComplianceChecklist;
import com.c21genera.compliance.domain.ComplianceChecklist.ComplianceCheckResult;
import com.c21genera.documents.DocumentsApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.extraction.ExtractionApi;
import com.c21genera.privacy.PrivacyApi;
import com.c21genera.privacy.PrivacyApi.ConsentView;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Calcula el checklist de cumplimiento documental de un expediente a
 * demanda, consultando únicamente las APIs públicas de los demás módulos
 * (ver AGENTS §7/§42-44): nunca decide nada por sí solo, solo reporta.
 */
@Service
public class ComplianceChecklistService {

  private final ExpedienteLifecycleApi expedienteApi;
  private final DocumentsApi documentsApi;
  private final PrivacyApi privacyApi;
  private final ExtractionApi extractionApi;

  public ComplianceChecklistService(
      ExpedienteLifecycleApi expedienteApi, DocumentsApi documentsApi, PrivacyApi privacyApi, ExtractionApi extractionApi) {
    this.expedienteApi = expedienteApi;
    this.documentsApi = documentsApi;
    this.privacyApi = privacyApi;
    this.extractionApi = extractionApi;
  }

  public ComplianceChecklist checklistOf(UUID expedienteId) {
    List<ComplianceCheckResult> items = new ArrayList<>();

    items.add(
        result(
            ComplianceCheckCode.PRIVACY_NOTICE_PRESENTED,
            privacyApi.activePrivacyNoticeTemplateExists(),
            "Debe existir una plantilla de aviso de privacidad activa"));

    items.add(
        result(
            ComplianceCheckCode.CONTRACT_TEMPLATE_CORRECT,
            privacyApi.activeContractTemplateExists(),
            "Debe existir una plantilla de contrato activa"));

    Optional<ConsentView> consent = privacyApi.consentOf(expedienteId);
    items.add(
        result(
            ComplianceCheckCode.MAIN_CONSENT_RECORDED,
            consent.map(ConsentView::mainPurposesAccepted).orElse(false),
            "El cliente debe haber aceptado la finalidad principal del aviso de privacidad"));

    items.add(
        result(
            ComplianceCheckCode.REQUIRED_DOCUMENTS_ACCEPTED,
            documentsApi.allRequiredAccepted(expedienteId),
            "Todos los documentos obligatorios deben estar aceptados"));

    ExpedienteSummary summary = expedienteApi.getSummary(expedienteId);
    boolean receptionSigned = summary.status().ordinal() >= ExpedienteStatus.RECEPTION_SIGNED.ordinal();
    items.add(
        result(ComplianceCheckCode.RECEPTION_SIGNED, receptionSigned, "La recepción de documentos debe estar firmada"));

    items.add(
        result(
            ComplianceCheckCode.NO_UNRESOLVED_DATA_CONFLICTS,
            !extractionApi.hasUnresolvedConflicts(expedienteId),
            "No debe haber conflictos de datos sin resolver entre documentos"));

    boolean allPassed = items.stream().allMatch(ComplianceCheckResult::passed);
    return new ComplianceChecklist(expedienteId, items, allPassed);
  }

  private static ComplianceCheckResult result(ComplianceCheckCode code, boolean passed, String detail) {
    return new ComplianceCheckResult(code, passed, detail);
  }
}
