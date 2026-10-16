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

// --- Usuarios y roles -------------------------------------------------------

Future<PageResponse<UserResponse>> listUsers([int page = 0, int size = 100]) async =>
    PageResponse.fromJson(_j(await _c.get('/internal/users?page=$page&size=$size')), UserResponse.fromJson);
Future<void> createUser(String name, String email, String password, String roleCode) =>
    _c.post('/internal/users', {'name': name, 'email': email, 'password': password, 'roleCode': roleCode});
Future<void> updateUser(String id, String name, String email, String roleCode) => _c.patch('/internal/users/$id', {'name': name, 'email': email, 'roleCode': roleCode});
Future<void> activateUser(String id) => _c.post('/internal/users/$id/activate');
Future<void> deactivateUser(String id) => _c.post('/internal/users/$id/deactivate');
Future<List<RoleResponse>> listRoles() async => _l(await _c.get('/internal/roles'), RoleResponse.fromJson);
Future<List<PermissionResponse>> listPermissions() async => _l(await _c.get('/internal/permissions'), PermissionResponse.fromJson);

// --- Expedientes ------------------------------------------------------------

Future<ExpedienteOverview> getExpedienteOverview({String? q, String? group, int page = 0, int size = 18}) async => ExpedienteOverview.fromJson(
    _j(await _c.get('/internal/expedientes/overview${_q({'page': page, 'size': size, 'sort': 'updatedAt,desc', 'q': q?.trim(), 'group': group})}')));
Future<List<DuplicateMatch>> checkClientDuplicates(List<String> holderNames) async =>
    _l(await _c.post('/internal/expedientes/duplicate-check', {'holderNames': holderNames}), DuplicateMatch.fromJson);
Future<ExpedienteResponse> createExpediente(Json request) async => ExpedienteResponse.fromJson(_j(await _c.post('/internal/expedientes', request)));
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

Future<List<ExtractedField>> getExtractedFields(String documentId) async => _l(await _c.get('/internal/documents/$documentId/extracted-fields'), ExtractedField.fromJson);
Future<List<ExpedienteObservation>> getExpedienteExtractedFields(String expedienteId) async =>
    _l(await _c.get('/internal/expedientes/$expedienteId/extracted-fields'), ExpedienteObservation.fromJson);
Future<ExtractedField> confirmField(String observationId, String confirmedValue) async =>
    ExtractedField.fromJson(_j(await _c.post('/internal/extracted-fields/$observationId/confirm', {'confirmedValue': confirmedValue})));
Future<List<DataConflict>> getDataConflicts(String expedienteId) async => _l(await _c.get('/internal/expedientes/$expedienteId/data-conflicts'), DataConflict.fromJson);
Future<List<DataConflict>> runConsistencyCheck(String expedienteId) async => _l(await _c.post('/internal/expedientes/$expedienteId/consistency-check'), DataConflict.fromJson);
Future<void> resolveConflict(String conflictId, String note) => _c.post('/internal/data-conflicts/$conflictId/resolve', {'note': note});

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

// --- CRM: leads y dashboard -------------------------------------------------

Future<LeadPage> listLeads({String? q, String? group, String? advisorId, String? source, int size = 100}) async =>
    LeadPage.fromJson(_j(await _c.get('/internal/leads${_q({'q': q, 'group': group, 'advisorId': advisorId, 'source': source, 'size': size})}')));
Future<LeadDetail> getLead(String id) async => LeadDetail.fromJson(_j(await _c.get('/internal/leads/$id')));
Future<LeadDetail> changeLeadStage(String id, String stage, [String? lostReason]) async =>
    LeadDetail.fromJson(_j(await _c.post('/internal/leads/$id/stage', {'stage': stage, 'lostReason': lostReason})));
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
