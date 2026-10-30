import 'client.dart';
import 'models.dart';

// Todos los endpoints que usa la web (frontend/src/lib/api/*.ts), con el mismo
// contrato. Ver backend/README.md para la referencia de cada uno.

final _c = ApiClient.instance;

String _q(Map<String, Object?> params) {
  final entries = params.entries.where((e) => e.value != null && e.value != '').map((e) => '${e.key}=${Uri.encodeQueryComponent('${e.value}')}');
  return entries.isEmpty ? '' : '?${entries.join('&')}';
}

Json _j(dynamic v) => Json.from(v as Map);
List<T> _l<T>(dynamic v, T Function(Json) f) => (v as List? ?? const []).whereType<Map>().map((e) => f(Json.from(e))).toList();

// --- Autenticación -----------------------------------------------------------

Future<Json> login(String email, String password) async => _j(await _c.post('/auth/login', {'email': email, 'password': password}));
Future<void> logout(String refreshToken) => _c.post('/auth/logout', {'refreshToken': refreshToken});
Future<MeResponse> me() async => MeResponse.fromJson(_j(await _c.get('/internal/me')));

/// Control de Cambios #25: "Mi perfil" → cambiar contraseña (pide la actual). Cierra las demás
/// sesiones y devuelve una nueva (accessToken/refreshToken) para este dispositivo.
Future<Json> changeOwnPassword(String currentPassword, String newPassword) async =>
    _j(await _c.post('/internal/me/password', {'currentPassword': currentPassword, 'newPassword': newPassword}));

// --- Usuarios y roles -------------------------------------------------------

Future<PageResponse<UserResponse>> listUsers([int page = 0, int size = 100]) async =>
    PageResponse.fromJson(_j(await _c.get('/internal/users?page=$page&size=$size')), UserResponse.fromJson);
/// Control de Cambios #25: sin contraseña; devuelve el enlace para que la persona defina la suya.
Future<({UserResponse user, SetupLink link})> createUser(String name, String email, String roleCode) async {
  final j = _j(await _c.post('/internal/users', {'name': name, 'email': email, 'roleCode': roleCode}));
  return (user: UserResponse.fromJson(Json.from(j['user'] as Map)), link: SetupLink.fromJson(Json.from(j['link'] as Map)));
}
Future<void> updateUser(String id, String name, String email, String roleCode) => _c.patch('/internal/users/$id', {'name': name, 'email': email, 'roleCode': roleCode});
Future<void> activateUser(String id) => _c.post('/internal/users/$id/activate');
Future<void> deactivateUser(String id) => _c.post('/internal/users/$id/deactivate');

/// Baja definitiva (conserva el historial). El servidor exige reasignar antes lo que tenga a su cargo.
Future<void> offboardUser(String id, String reason) => _c.post('/internal/users/$id/offboard', {'reason': reason});

/// Enlace de restablecimiento (o de alta, si aún no define su contraseña). El administrador nunca ve la contraseña.
Future<SetupLink> issuePasswordLink(String id) async => SetupLink.fromJson(_j(await _c.post('/internal/users/$id/password-link')));
Future<void> unlockUser(String id) => _c.post('/internal/users/$id/unlock');
Future<List<WorkloadItem>> getUserWorkload(String id) async => _l(await _c.get('/internal/users/$id/workload'), WorkloadItem.fromJson);
Future<PageResponse<AuditEvent>> listAccountEvents([int page = 0, int size = 50]) async =>
    PageResponse.fromJson(_j(await _c.get('/internal/audit-events/accounts?page=$page&size=$size')), AuditEvent.fromJson);

// --- Catálogo de inmuebles (Control de Cambios #26) ---------------------------

List<CatalogCategory>? _catalog;
List<CatalogCategory>? _catalogAll;

/// Catálogo vigente (o, con includeInactive, también lo desactivado para filtros). Una vez cargado
/// se reutiliza durante la sesión (cambia poco).
Future<List<CatalogCategory>> listPropertyCatalog({bool includeInactive = false}) async {
  final cached = includeInactive ? _catalogAll : _catalog;
  if (cached != null) return cached;
  final loaded = _l(await _c.get('/internal/property-catalog${includeInactive ? '?includeInactive=true' : ''}'), CatalogCategory.fromJson);
  if (includeInactive) {
    _catalogAll = loaded;
  } else {
    _catalog = loaded;
  }
  return loaded;
}

/// Clasificar el tipo de inmueble sin la corrección completa (no puede cambiar documentos ni contrato).
Future<void> classifyExpedienteProperty(String id, String subtypeId, String? detail, String reason) =>
    _c.put('/internal/expedientes/$id/property-type', {'propertySubtypeId': subtypeId, 'propertyTypeDetail': detail, 'reason': reason});
Future<List<RoleResponse>> listRoles() async => _l(await _c.get('/internal/roles'), RoleResponse.fromJson);
Future<List<PermissionResponse>> listPermissions() async => _l(await _c.get('/internal/permissions'), PermissionResponse.fromJson);

// --- Expedientes ------------------------------------------------------------

/// Control de Cambios #24: advisorId [unassignedAdvisor] = expedientes sin asesor asignado.
const unassignedAdvisor = 'none';

/// Control de Cambios #26: subtypeId [unclassifiedSubtype] = pendientes de clasificación.
const unclassifiedSubtype = 'none';

Map<String, Object?> _portfolio(String? categoryId, String? subtypeId, String? operationType, String? advisorId) => {
  'categoryId': categoryId,
  'subtypeId': subtypeId == unclassifiedSubtype ? null : subtypeId,
  'unclassified': subtypeId == unclassifiedSubtype ? 'true' : null,
  'operationType': operationType,
  'advisorId': advisorId == unassignedAdvisor ? null : advisorId,
  'unassigned': advisorId == unassignedAdvisor ? 'true' : null,
};

Future<ExpedienteOverview> getExpedienteOverview({
  String? q,
  String? group,
  String? categoryId,
  String? subtypeId,
  String? operationType,
  String? advisorId,
  // CC#32: solo los que tienen el cierre completado.
  bool closed = false,
  int page = 0,
  int size = 18,
}) async => ExpedienteOverview.fromJson(
  _j(
    await _c.get(
      '/internal/expedientes/overview${_q({'page': page, 'size': size, 'sort': 'updatedAt,desc', 'q': q?.trim(), 'group': group, 'closed': closed ? 'true' : null, ..._portfolio(categoryId, subtypeId, operationType, advisorId)})}',
    ),
  ),
);
Future<PortfolioReport> getPortfolioReport({String? categoryId, String? subtypeId, String? operationType, String? advisorId}) async =>
    PortfolioReport.fromJson(_j(await _c.get('/internal/expedientes/overview/portfolio${_q(_portfolio(categoryId, subtypeId, operationType, advisorId))}')));
Future<List<AdvisorOption>> listAssignableAdvisors() async => _l(await _c.get('/internal/expedientes/advisors'), AdvisorOption.fromJson);
Future<void> assignAdvisor(String id, String advisorId, String reason) => _c.put('/internal/expedientes/$id/advisor', {'advisorId': advisorId, 'reason': reason});
Future<List<DuplicateMatch>> checkClientDuplicates(List<String> holderNames) async =>
    _l(await _c.post('/internal/expedientes/duplicate-check', {'holderNames': holderNames}), DuplicateMatch.fromJson);
Future<ExpedienteResponse> createExpediente(Json request) async => ExpedienteResponse.fromJson(_j(await _c.post('/internal/expedientes', request)));
// Control de Cambios #27 (adenda): clave estructurada del inmueble.
Future<PropertyKeyCheck> checkPropertyKey(String key, String? expedienteId) async => PropertyKeyCheck.fromJson(_j(await _c.get(
    '/internal/expedientes/property-keys/check?${Uri(queryParameters: {'key': key, 'expedienteId': ?expedienteId}).query}')));
Future<ExpedienteResponse> assignPropertyKey(String id, String key, String? reason) async =>
    ExpedienteResponse.fromJson(_j(await _c.put('/internal/expedientes/$id/property-key', {'key': key, 'reason': reason})));
Future<PropertyKeyOverview> getPropertyKeys({String? operationType, String? advisorId, String? prefix}) async => PropertyKeyOverview.fromJson(_j(await _c.get(
    '/internal/expedientes/property-keys?${Uri(queryParameters: {'operationType': ?operationType, 'advisorId': ?advisorId, if (prefix != null && prefix.isNotEmpty) 'prefix': prefix}).query}')));
Future<ExpedienteResponse> getExpediente(String id) async => ExpedienteResponse.fromJson(_j(await _c.get('/internal/expedientes/$id')));
Future<void> correctExpediente(String id, Json configuration, String reason) => _c.put('/internal/expedientes/$id', {...configuration, 'reason': reason});
Future<List<Participant>> getParticipants(String id) async => _l(await _c.get('/internal/expedientes/$id/participants'), Participant.fromJson);
Future<void> addParticipant(String id, Json participant, String reason) => _c.post('/internal/expedientes/$id/participants', {'participant': participant, 'reason': reason});
Future<void> updateParticipant(String id, String participantId, Json participant, String reason) =>
    _c.put('/internal/expedientes/$id/participants/$participantId', {'participant': participant, 'reason': reason});
Future<void> removeParticipant(String id, String participantId, String reason) =>
    _c.delete('/internal/expedientes/$id/participants/$participantId?reason=${Uri.encodeQueryComponent(reason)}');
Future<Json> getLegalDetails(String id) async => Json.from((await _c.get('/internal/expedientes/$id/legal-details') as Map?) ?? {});
Future<void> updateLegalDetails(String id, Json legalDetails, String reason) => _c.put('/internal/expedientes/$id/legal-details', {'legalDetails': legalDetails, 'reason': reason});
Future<Json> getClientData(String id) async => Json.from((await _c.get('/internal/expedientes/$id/client-data') as Map?) ?? {});
Future<void> updateClientData(String id, Json data) => _c.put('/internal/expedientes/$id/client-data', data);
Future<List<RequirementResponse>> getRequirements(String id) async => _l(await _c.get('/internal/expedientes/$id/requirements'), RequirementResponse.fromJson);
Future<List<ChangeResponse>> getChanges(String id) async => _l(await _c.get('/internal/expedientes/$id/changes'), ChangeResponse.fromJson);
Future<void> signReception(String id) => _c.post('/internal/expedientes/$id/reception/sign');
Future<void> acceptProperty(String id) => _c.post('/internal/expedientes/$id/accept-property');
Future<void> rejectProperty(String id, String reason) => _c.post('/internal/expedientes/$id/reject-property', {'reason': reason});

Future<({String url, String expiresAt})> generatePublicLink(String id) async {
  final j = _j(await _c.post('/internal/expedientes/$id/public-link'));
  return (url: '${j['url']}', expiresAt: '${j['expiresAt']}');
}

Future<PublicLinkStatus?> getPublicLinkStatus(String id) async {
  final v = await _c.get('/internal/expedientes/$id/public-link');
  return v is Map ? PublicLinkStatus.fromJson(Json.from(v)) : null;
}

Future<void> revokePublicLink(String id) => _c.delete('/internal/expedientes/$id/public-link');

Future<List<ActivityResponse>> getActivity(String id) async => _l(await _c.get('/internal/expedientes/$id/activity'), ActivityResponse.fromJson);
Future<List<NotificationResponse>> getNotifications(String id) async => _l(await _c.get('/internal/expedientes/$id/notifications'), NotificationResponse.fromJson);
Future<ComplianceChecklist> getComplianceChecklist(String id) async => ComplianceChecklist.fromJson(_j(await _c.get('/internal/expedientes/$id/compliance-checklist')));
Future<ClosingCase> getClosingCase(String id) async => ClosingCase.fromJson(_j(await _c.get('/internal/expedientes/$id/closing')));
Future<List<ClosingTask>> getClosingTasks(String id) async => _l(await _c.get('/internal/expedientes/$id/closing/tasks'), ClosingTask.fromJson);
Future<void> completeClosingTask(String id, String taskId) => _c.post('/internal/expedientes/$id/closing/tasks/$taskId/complete', {'note': null});
Future<List<ClosingNote>> getClosingNotes(String id) async => _l(await _c.get('/internal/expedientes/$id/closing/notes'), ClosingNote.fromJson);
Future<void> addClosingNote(String id, String note) => _c.post('/internal/expedientes/$id/closing/notes', {'note': note});
Future<EmailPreview> getDocumentsEmailPreview(String id) async =>
    EmailPreview.fromJson(_j(await _c.get('/internal/expedientes/$id/notifications/documents-email-preview')));

// --- Borradores en el servidor ------------------------------------------------

Future<String?> loadDraft(String formKey) async {
  final v = await _c.get('/internal/drafts/${Uri.encodeComponent(formKey)}');
  return v is Map ? v['payload'] as String? : null;
}

Future<void> saveDraft(String formKey, String payload) => _c.put('/internal/drafts/${Uri.encodeComponent(formKey)}', {'payload': payload});
Future<void> deleteDraft(String formKey) => _c.delete('/internal/drafts/${Uri.encodeComponent(formKey)}');

// --- Documentos -------------------------------------------------------------

Future<List<DocumentResponse>> listDocuments(String expedienteId) async => _l(await _c.get('/internal/expedientes/$expedienteId/documents'), DocumentResponse.fromJson);
Future<List<ReviewHistory>> listReviews(String documentId) async => _l(await _c.get('/internal/documents/$documentId/reviews'), ReviewHistory.fromJson);
Future<void> uploadVersion(String documentId, List<UploadFile> files, {void Function(int)? onProgress}) =>
    _c.upload('/internal/documents/$documentId/versions', 'files', files, onProgress: onProgress);
/// "Marcar como pendiente" con nota y fecha estimada opcionales (Control de Cambios #14).
Future<DocumentResponse> markDocumentPending(String documentId, String note, String? dueDate) async =>
    DocumentResponse.fromJson(_j(await _c.put('/internal/documents/$documentId/pending', {'note': note, 'dueDate': dueDate})));
Future<DocumentResponse> clearDocumentPending(String documentId) async => DocumentResponse.fromJson(_j(await _c.delete('/internal/documents/$documentId/pending')));
/// Corrige el nombre de un documento agregado a mano; el archivo no cambia (Control de Cambios #18).
Future<DocumentResponse> renameDocument(String documentId, String name) async =>
    DocumentResponse.fromJson(_j(await _c.put('/internal/documents/$documentId/name', {'name': name})));
/// Qué comprobante de domicilio se entregó (Control de Cambios #13).
Future<DocumentResponse> setAddressProofKind(String documentId, String? kind) async =>
    DocumentResponse.fromJson(_j(await _c.put('/internal/documents/$documentId/address-proof-kind', {'kind': kind})));
Future<DocumentResponse> reprocessDocument(String documentId) async => DocumentResponse.fromJson(_j(await _c.post('/internal/documents/$documentId/reprocess', {'full': false})));
Future<int> reprocessAllDocuments(String expedienteId) async => (_j(await _c.post('/internal/expedientes/$expedienteId/documents/reprocess'))['reprocessed'] as num?)?.toInt() ?? 0;
Future<DocumentResponse> moveDocumentFile(String documentId, String targetDocumentId) async =>
    DocumentResponse.fromJson(_j(await _c.post('/internal/documents/$documentId/move', {'targetDocumentId': targetDocumentId})));
Future<DocumentResponse> acceptDocument(String documentId, [String? overrideJustification]) async =>
    DocumentResponse.fromJson(_j(await _c.post('/internal/documents/$documentId/accept', {'overrideJustification': overrideJustification})));
Future<DocumentResponse> returnDocument(String documentId, String reasonCode, String comment) async =>
    DocumentResponse.fromJson(_j(await _c.post('/internal/documents/$documentId/return', {'reasonCode': reasonCode, 'comment': comment})));
Future<DocumentResponse> rejectDocument(String documentId, String reasonCode, String comment) async =>
    DocumentResponse.fromJson(_j(await _c.post('/internal/documents/$documentId/reject', {'reasonCode': reasonCode, 'comment': comment})));
Future<DocumentResponse> setDocumentDeferred(String documentId, bool deferred, [String? reason]) async =>
    DocumentResponse.fromJson(_j(await _c.put('/internal/documents/$documentId/deferral', {'deferred': deferred, 'reason': reason})));
Future<DocumentResponse> markNotApplicable(String documentId, String justification) async =>
    DocumentResponse.fromJson(_j(await _c.post('/internal/documents/$documentId/not-applicable', {'justification': justification})));
Future<DocumentResponse> requestAgain(String documentId) async => DocumentResponse.fromJson(_j(await _c.post('/internal/documents/$documentId/request-again')));
Future<String> getDownloadUrl(String versionId) async => '${_j(await _c.get('/internal/document-versions/$versionId/download'))['url']}';
Future<List<DocumentVersionItem>> listVersions(String documentId) async => _l(await _c.get('/internal/documents/$documentId/versions'), DocumentVersionItem.fromJson);
/// Documento que no está en el listado, con el nombre capturado (Control de Cambios #1).
Future<DocumentResponse> addCustomDocument(String expedienteId, String name, String? participantId) async =>
    DocumentResponse.fromJson(_j(await _c.post('/internal/expedientes/$expedienteId/documents', {'name': name, 'participantId': participantId})));
/// Falta, pero se aprueba seguir sin él por ahora (Control de Cambios #5).
Future<DocumentResponse> approveException(String documentId, String note) async =>
    DocumentResponse.fromJson(_j(await _c.put('/internal/documents/$documentId/exception', {'note': note})));
Future<DocumentResponse> revokeException(String documentId) async => DocumentResponse.fromJson(_j(await _c.delete('/internal/documents/$documentId/exception')));
/// Pide al cliente una versión actualizada; la vigente se conserva (Control de Cambios #10).
Future<DocumentResponse> requestDocumentUpdate(String documentId, String note) async =>
    DocumentResponse.fromJson(_j(await _c.put('/internal/documents/$documentId/update-request', {'note': note})));
Future<DocumentResponse> cancelDocumentUpdate(String documentId) async =>
    DocumentResponse.fromJson(_j(await _c.delete('/internal/documents/$documentId/update-request')));
/// Visor sin descarga (Control de Cambios #6): páginas y si quien consulta puede descargar.
Future<({int pageCount, bool canDownload})> getViewer(String versionId) async {
  final j = _j(await _c.get('/internal/document-versions/$versionId/view'));
  return (pageCount: (j['pageCount'] as num?)?.toInt() ?? 0, canDownload: j['canDownload'] == true);
}
/// JPEG de la página (1 = la primera) con la marca de agua de quien consulta.
Future<List<int>> getViewerPage(String versionId, int pageNumber) => _c.getBytes('/internal/document-versions/$versionId/view/pages/$pageNumber');

// --- Datos leídos y consistencia ---------------------------------------------

/// Lleva al expediente lo ya validado en los documentos, solo donde está vacío (Control de Cambios #7).
Future<int> autofillExpediente(String expedienteId) async => (_j(await _c.post('/internal/expedientes/$expedienteId/autofill'))['filled'] as num?)?.toInt() ?? 0;
/// Control de Cambios #2: estado del aviso de privacidad (SIGNED / PENDING) y cómo se firmó.
Future<Json> getPrivacyStatus(String expedienteId) async => _j(await _c.get('/internal/expedientes/$expedienteId/privacy'));
Future<void> deferPrivacySignature(String expedienteId) =>
    _c.post('/internal/expedientes/$expedienteId/privacy/defer', {'reason': 'Alta presencial: el aviso se firma después'});
Future<void> uploadPhysicalPrivacySignature(String expedienteId, List<UploadFile> files, bool secondaryPurposesAccepted) =>
    _c.upload('/internal/expedientes/$expedienteId/privacy/physical-signature?secondaryPurposesAccepted=$secondaryPurposesAccepted', 'files', files);
Future<List<ExtractedField>> getExtractedFields(String documentId) async => _l(await _c.get('/internal/documents/$documentId/extracted-fields'), ExtractedField.fromJson);
Future<List<ExpedienteObservation>> getExpedienteExtractedFields(String expedienteId) async =>
    _l(await _c.get('/internal/expedientes/$expedienteId/extracted-fields'), ExpedienteObservation.fromJson);
Future<ExtractedField> confirmField(String observationId, String confirmedValue) async =>
    ExtractedField.fromJson(_j(await _c.post('/internal/extracted-fields/$observationId/confirm', {'confirmedValue': confirmedValue})));
Future<List<DataConflict>> getDataConflicts(String expedienteId) async => _l(await _c.get('/internal/expedientes/$expedienteId/data-conflicts'), DataConflict.fromJson);
Future<List<DataConflict>> runConsistencyCheck(String expedienteId) async => _l(await _c.post('/internal/expedientes/$expedienteId/consistency-check'), DataConflict.fromJson);
/// Solo un directivo o administrador autoriza, confirmando con su contraseña (Control de Cambios #17).
Future<void> resolveConflict(String conflictId, String note, String password) =>
    _c.post('/internal/data-conflicts/$conflictId/resolve', {'note': note, 'password': password});
/// m² de terreno e indivisos: Régimen de condominio vs. Escritura y Predial (Control de Cambios #15).
Future<List<CondominiumArea>> getCondominiumAreas(String expedienteId) async =>
    _l(await _c.get('/internal/expedientes/$expedienteId/condominium-areas'), CondominiumArea.fromJson);

// --- Notaría por etapa (Control de Cambios #23) --------------------------------

Future<NotaryOverview> getNotaryOverview(String expedienteId) async => NotaryOverview.fromJson(_j(await _c.get('/internal/expedientes/$expedienteId/notary')));
Future<NotaryOverview> registerOfferAccepted(String expedienteId, String note) async =>
    NotaryOverview.fromJson(_j(await _c.post('/internal/expedientes/$expedienteId/notary/offer-accepted', {'note': note})));
Future<NotaryOverview> clearOfferAccepted(String expedienteId) async =>
    NotaryOverview.fromJson(_j(await _c.delete('/internal/expedientes/$expedienteId/notary/offer-accepted')));
Future<void> setClosingCheck(String documentId, bool valid, String note) => _c.put('/internal/documents/$documentId/closing-check', {'valid': valid, 'note': note});
Future<void> sendToNotary(String expedienteId, String kind, Map<String, String> versionByDocument, {String? recipientEmail, String? note, bool confirmWarnings = false}) =>
    _c.post('/internal/expedientes/$expedienteId/notary/submissions', {
      'kind': kind,
      'documents': versionByDocument.entries.map((e) => {'documentId': e.key, 'versionId': e.value}).toList(),
      'recipientEmail': recipientEmail,
      'note': note,
      'confirmWarnings': confirmWarnings,
    });

// --- Contratos ----------------------------------------------------------------

Future<ContractCalculations> getContractData(String expedienteId) async => ContractCalculations.fromJson(_j(await _c.get('/internal/expedientes/$expedienteId/contract/data')));
Future<ContractReadiness> getContractReadiness(String expedienteId) async =>
    ContractReadiness.fromJson(_j(await _c.get('/internal/expedientes/$expedienteId/contract/readiness')));
Future<List<SigningLink>> generateContract(String expedienteId, String mode) async =>
    _l(_j(await _c.post('/internal/expedientes/$expedienteId/contracts/generate?mode=$mode'))['signingLinks'], SigningLink.fromJson);
Future<List<ContractGeneration>> listContracts(String expedienteId) async => _l(await _c.get('/internal/expedientes/$expedienteId/contracts'), ContractGeneration.fromJson);
Future<String> getContractDownload(String contractId, String file) async => '${_j(await _c.get('/internal/contracts/$contractId/download?file=$file'))['url']}';
Future<void> signAsIntermediary(String contractId, {required String typedName, required String signatureImageBase64, required String documentSha256, required bool accepted}) =>
    _c.post('/internal/contracts/$contractId/signatures/intermediary',
        {'typedName': typedName, 'signatureImageBase64': signatureImageBase64, 'documentSha256': documentSha256, 'accepted': accepted});
Future<void> registerAutographSignature(String contractId, UploadFile file) => _c.upload('/internal/contracts/$contractId/signatures/autograph', 'file', [file]);
Future<SigningLink> reissueSigningLink(String signatureId) async => SigningLink.fromJson(_j(await _c.post('/internal/contract-signatures/$signatureId/reissue-link')));
Future<void> markContractDelivered(String contractId, String method) => _c.post('/internal/contracts/$contractId/mark-delivered', {'method': method});
/// Contrato firmado fuera del sistema: DOCUSIGN o PHYSICAL, también históricos (Control de Cambios #20).
Future<List<SignedContractUpload>> listSignedContracts(String expedienteId) async =>
    _l(await _c.get('/internal/expedientes/$expedienteId/signed-contracts'), SignedContractUpload.fromJson);
Future<void> uploadSignedContract(String expedienteId, List<UploadFile> files, String signatureMethod, bool historical, String note) =>
    _c.upload('/internal/expedientes/$expedienteId/signed-contracts${_q({'signatureMethod': signatureMethod, 'historical': historical, 'note': note.trim()})}', 'files', files);
/// Texto estándar de "Instrucciones para visitas" (Control de Cambios #21).
Future<String> getVisitInstructions() async => _sOf(_j(await _c.get('/internal/settings/visit-instructions'))['text']);
String _sOf(dynamic v) => v?.toString() ?? '';

// --- CRM: leads y dashboard -------------------------------------------------

Future<LeadPage> listLeads({String? q, String? group, String? advisorId, String? source, int size = 100}) async =>
    LeadPage.fromJson(_j(await _c.get('/internal/leads${_q({'q': q, 'group': group, 'advisorId': advisorId, 'source': source, 'size': size})}')));
Future<LeadDetail> getLead(String id) async => LeadDetail.fromJson(_j(await _c.get('/internal/leads/$id')));
Future<LeadDetail> changeLeadStage(String id, String stage, [String? lostReason, String? lostReasonCode]) async =>
    LeadDetail.fromJson(_j(await _c.post('/internal/leads/$id/stage', {'stage': stage, 'lostReason': lostReason, 'lostReasonCode': lostReasonCode})));

// Control de Cambios #30: actividades del lead y agenda.
Future<LeadTaskItem> createLeadTask(String leadId, Json input) async => LeadTaskItem.fromJson(_j(await _c.post('/internal/leads/$leadId/tasks', input)));
Future<LeadTaskItem> completeLeadTask(String taskId, Json input) async => LeadTaskItem.fromJson(_j(await _c.post('/internal/lead-tasks/$taskId/complete', input)));
Future<LeadTaskItem> cancelLeadTask(String taskId) async => LeadTaskItem.fromJson(_j(await _c.post('/internal/lead-tasks/$taskId/cancel', <String, Object?>{})));
Future<List<LeadTaskItem>> getAgenda(String fromIso, String toIso) async {
  final data = await _c.get('/internal/crm/agenda${_q({'from': fromIso, 'to': toIso})}');
  return data is List ? data.whereType<Map>().map((e) => LeadTaskItem.fromJson(Json.from(e))).toList() : <LeadTaskItem>[];
}
Future<LeadDetail> assignLead(String id, String advisorId, [String? exceptionReason]) async =>
    LeadDetail.fromJson(_j(await _c.post('/internal/leads/$id/assign', {'advisorId': advisorId, 'exceptionReason': exceptionReason})));
Future<LeadDetail> resendLeadNotice(String id) async => LeadDetail.fromJson(_j(await _c.post('/internal/leads/$id/resend-notice')));
Future<LeadDetail> addLeadNote(String id, String text) async => LeadDetail.fromJson(_j(await _c.post('/internal/leads/$id/notes', {'text': text})));
Future<LeadDetail> createLead(Json input) async => LeadDetail.fromJson(_j(await _c.post('/internal/leads', input)));
Future<CrmDashboard> getCrmDashboard([String? from]) async => CrmDashboard.fromJson(_j(await _c.get('/internal/crm/dashboard${_q({'from': from})}')));

// --- CRM: asesores y asignación ----------------------------------------------

Future<List<AdvisorRouting>> listAdvisors() async => _l(await _c.get('/internal/crm/advisors'), AdvisorRouting.fromJson);
Future<AdvisorRouting> updateAdvisor(String userId, Json input) async => AdvisorRouting.fromJson(_j(await _c.put('/internal/crm/advisors/$userId', input)));
Future<AssignmentSettings> getAssignmentSettings() async => AssignmentSettings.fromJson(_j(await _c.get('/internal/crm/assignment-settings')));
Future<AssignmentSettings> updateAssignmentSettings(String roundMode, String ownerOffShiftPolicy) async => AssignmentSettings.fromJson(
    _j(await _c.put('/internal/crm/assignment-settings', {'roundMode': roundMode, 'ownerOffShiftPolicy': ownerOffShiftPolicy})));

// --- CRM: calendario (fechas de Ciudad de México como texto) ------------------

Future<CalendarView> getCalendar(String from, String to) async => CalendarView.fromJson(_j(await _c.get('/internal/crm/calendar${_q({'from': from, 'to': to})}')));
Future<List<CalendarAuditEntry>> getCalendarAudit() async => _l(await _c.get('/internal/crm/calendar/audit'), CalendarAuditEntry.fromJson);
Future<void> createShift(Json input) => _c.post('/internal/crm/calendar/shifts', input);
Future<void> updateShift(String id, Json input, String scope, String date) => _c.put('/internal/crm/calendar/shifts/$id${_q({'scope': scope, 'date': date})}', input);
Future<void> deleteShift(String id, String scope, String date) => _c.delete('/internal/crm/calendar/shifts/$id${_q({'scope': scope, 'date': date})}');
Future<({int created, int skipped})> copyShifts(String sourceFrom, int days, String targetFrom, [String? advisorId]) async {
  final j = _j(await _c.post('/internal/crm/calendar/copy', {'sourceFrom': sourceFrom, 'days': days, 'targetFrom': targetFrom, 'advisorId': advisorId}));
  return (created: (j['created'] as num?)?.toInt() ?? 0, skipped: (j['skipped'] as num?)?.toInt() ?? 0);
}

Future<void> createAbsence(Json input) => _c.post('/internal/crm/calendar/absences', input);
Future<void> updateAbsence(String id, Json input) => _c.put('/internal/crm/calendar/absences/$id', input);
Future<void> deleteAbsence(String id) => _c.delete('/internal/crm/calendar/absences/$id');

// --- CRM: asistente de WhatsApp ---------------------------------------------

Future<AssistantStatus> getAssistantStatus() async => AssistantStatus.fromJson(_j(await _c.get('/internal/assistant/status')));
Future<List<ConversationItem>> listConversations() async => _l(await _c.get('/internal/conversations'), ConversationItem.fromJson);
Future<ConversationDetail> getConversation(String id) async => ConversationDetail.fromJson(_j(await _c.get('/internal/conversations/$id')));
Future<ConversationDetail> setConversationControl(String id, String control) async =>
    ConversationDetail.fromJson(_j(await _c.post('/internal/conversations/$id/control', {'control': control})));
Future<ConversationDetail> replyToConversation(String id, String text) async => ConversationDetail.fromJson(_j(await _c.post('/internal/conversations/$id/reply', {'text': text})));
Future<void> resolveEscalation(String escalationId) => _c.post('/internal/conversations/escalations/$escalationId/resolve');
Future<String?> simulateMessage(String text) async => (await _c.post('/internal/assistant/simulator', {'text': text}) as Map?)?['conversationId'] as String?;
Future<void> resetSimulation() => _c.delete('/internal/assistant/simulator');

// --- CRM: propiedades vinculadas con 21Online ----------------------------------

Future<InventoryPage> listInventory({String? q, String? status, String? operation, int page = 1}) async =>
    InventoryPage.fromJson(_j(await _c.get('/internal/properties/inventory${_q({'q': q?.trim(), 'status': status, 'operation': operation, 'page': page})}')));
Future<InventoryItem> getInventoryItem(String online21Id) async => InventoryItem.fromJson(_j(await _c.get('/internal/properties/inventory/$online21Id')));
Future<CapturedOverview> listCaptured() async => CapturedOverview.fromJson(_j(await _c.get('/internal/properties')));
/// CC#36: listado "En Genera" filtrado, ordenado (NEWEST, OLDEST, PRICE_ASC, PRICE_DESC) y paginado en el
/// servidor sobre todas las propiedades. Importes crudos ("5000000"); límites inclusivos; página desde 1.
Future<CapturedPage> listCapturedPage({
  String? q,
  String? tag,
  String? status,
  String? operation,
  String? minPrice,
  String? maxPrice,
  String? currency,
  String sort = 'NEWEST',
  int page = 1,
  int size = 20,
}) async =>
    CapturedPage.fromJson(_j(await _c.get('/internal/properties${_q({
      'q': q?.trim(),
      'tag': tag,
      'status': status,
      'operation': operation,
      'minPrice': minPrice,
      'maxPrice': maxPrice,
      'currency': currency,
      'sort': sort,
      'page': page,
      'size': size,
    })}')));
/// Trae ya todo el inventario de 21Online (el backend también lo hace solo cada pocos minutos).
Future<Json> importInventory() async => _j(await _c.post('/internal/properties/import'));
Future<CapturedProperty> getCaptured(String id) async => CapturedProperty.fromJson(_j(await _c.get('/internal/properties/$id')));
Future<CapturedProperty> createProperty(Json data) async => CapturedProperty.fromJson(_j(await _c.post('/internal/properties', data)));
Future<CapturedProperty> updateProperty(String id, Json data) async => CapturedProperty.fromJson(_j(await _c.put('/internal/properties/$id', data)));
Future<CapturedProperty> retryPropertySync(String id) async => CapturedProperty.fromJson(_j(await _c.post('/internal/properties/$id/sync')));

// --- Detalle de 21Online (como su pantalla "Ver propiedad") -----------------

Future<PropertyDetails> getPropertyDetails(String online21Id) async =>
    PropertyDetails.fromJson(_j(await _c.get('/internal/properties/inventory/$online21Id/details')));
Future<List<PortalListing>> getPropertyPortals(String online21Id) async =>
    _l(await _c.get('/internal/properties/inventory/$online21Id/portals'), PortalListing.fromJson);

/// contactsVisible: si vienen nombre, teléfono, correo y mensaje (solo el asesor o quien ve todos los leads).
Future<({bool contactsVisible, List<RelatedClient> clients})> getPropertyClients(String online21Id) async {
  final j = _j(await _c.get('/internal/properties/inventory/$online21Id/clients'));
  return (contactsVisible: j['contactsVisible'] == true, clients: _l(j['clients'], RelatedClient.fromJson));
}

Future<List<FollowUp>> getPropertyFollowUps(String online21Id) async =>
    _l(await _c.get('/internal/properties/inventory/$online21Id/follow-ups'), FollowUp.fromJson);

/// Ficha técnica en PDF. variant: ADVISOR, NO_CONTACT o NO_LOGO.
Future<List<int>> getPropertySheet(String online21Id, String variant) => _c.getBytes('/internal/properties/inventory/$online21Id/sheet?variant=$variant');

// --- Factibilidad con IA -----------------------------------------------------------

/// available=false: la IA no está configurada en el servidor.
Future<({bool available, List<FeasibilityReport> reports})> listFeasibility(String expedienteId) async {
  final j = _j(await _c.get('/internal/expedientes/$expedienteId/feasibility'));
  return (available: j['available'] == true, reports: _l(j['reports'], FeasibilityReport.fromJson));
}

Future<void> requestFeasibility(String expedienteId,
        {required String operation, String? askingPrice, String? notes, required List<UploadFile> photos, void Function(int percent)? onProgress}) =>
    _c.upload('/internal/expedientes/$expedienteId/feasibility', 'photos', photos,
        onProgress: onProgress,
        fields: {
          'operation': operation,
          if (askingPrice != null && askingPrice.isNotEmpty) 'askingPrice': askingPrice,
          if (notes != null && notes.trim().isNotEmpty) 'notes': notes.trim(),
        });

Future<String> getFeasibilityDownload(String reportId, String file) async => '${_j(await _c.get('/internal/feasibility/$reportId/download?file=$file'))['url']}';

Future<List<FeasibilityChatMessage>> getFeasibilityChat(String reportId) async =>
    _l(await _c.get('/internal/feasibility/$reportId/chat'), FeasibilityChatMessage.fromJson);

Future<List<FeasibilityChatMessage>> askFeasibilityChat(String reportId, String message) async =>
    _l(await _c.post('/internal/feasibility/$reportId/chat', {'message': message}), FeasibilityChatMessage.fromJson);
