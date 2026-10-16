// Modelos que reflejan las respuestas REALES del backend (mismo contrato que
// frontend/src/lib/api/types.ts y crm.ts). Los formularios editables
// (datos del contrato, datos legales) se manejan como mapas JSON, igual que la web.

typedef Json = Map<String, dynamic>;

String? _s(dynamic v) => v?.toString();
String _sr(dynamic v, [String fallback = '']) => v?.toString() ?? fallback;
int _i(dynamic v, [int fallback = 0]) => v is num ? v.toInt() : int.tryParse('${v ?? ''}') ?? fallback;
int? _in(dynamic v) => v == null ? null : (v is num ? v.toInt() : int.tryParse('$v'));
double? _dn(dynamic v) => v == null ? null : (v is num ? v.toDouble() : double.tryParse('$v'));
bool _b(dynamic v) => v == true;
bool? _bn(dynamic v) => v is bool ? v : null;
List<String> _ls(dynamic v) => v is List ? v.map((e) => '$e').toList() : const [];
List<T> _list<T>(dynamic v, T Function(Json) f) => v is List ? v.whereType<Map>().map((e) => f(Json.from(e))).toList() : <T>[];

// ---------------------------------------------------------------------------
// Sesión, usuarios y roles
// ---------------------------------------------------------------------------

class MeResponse {
  MeResponse({required this.id, required this.name, required this.email, required this.role, required this.permissions});
  factory MeResponse.fromJson(Json j) =>
      MeResponse(id: _sr(j['id']), name: _sr(j['name']), email: _sr(j['email']), role: _sr(j['role']), permissions: _ls(j['permissions']));
  final String id, name, email, role;
  final List<String> permissions;
}

class UserResponse {
  UserResponse.fromJson(Json j)
      : id = _sr(j['id']),
        name = _sr(j['name']),
        email = _sr(j['email']),
        roleCode = _sr(j['roleCode']),
        roleName = _sr(j['roleName']),
        status = _sr(j['status']),
        lastActivityAt = _s(j['lastActivityAt']);
  final String id, name, email, roleCode, roleName, status;
  final String? lastActivityAt;
}

class RoleResponse {
  RoleResponse.fromJson(Json j)
      : id = _sr(j['id']),
        code = _sr(j['code']),
        name = _sr(j['name']),
        description = _sr(j['description']),
        permissions = _ls(j['permissions']);
  final String id, code, name, description;
  final List<String> permissions;
}

class PermissionResponse {
  PermissionResponse.fromJson(Json j)
      : code = _sr(j['code']),
        description = _sr(j['description']);
  final String code, description;
}

class PageResponse<T> {
  PageResponse.fromJson(Json j, T Function(Json) f)
      : items = _list(j['items'], f),
        page = _i(j['page']),
        size = _i(j['size']),
        totalElements = _i(j['totalElements']),
        totalPages = _i(j['totalPages']);
  final List<T> items;
  final int page, size, totalElements, totalPages;
}

// ---------------------------------------------------------------------------
// Expedientes
// ---------------------------------------------------------------------------

class ExpedienteResponse {
  ExpedienteResponse.fromJson(Json j)
      : id = _sr(j['id']),
        folio = _sr(j['folio']),
        ownerDisplayName = _sr(j['ownerDisplayName']),
        status = _sr(j['status']),
        personType = _sr(j['personType']),
        signerCharacter = _sr(j['signerCharacter']),
        signedByAttorney = _b(j['signedByAttorney']),
        accreditationType = _sr(j['accreditationType']),
        condominiumRegime = _b(j['condominiumRegime']),
        propertyCaseType = _sr(j['propertyCaseType']),
        declaredLegalStatus = _sr(j['declaredLegalStatus']),
        propertyAddress = _s(j['propertyAddress']),
        propertyReference = _s(j['propertyReference']),
        decisionReason = _s(j['decisionReason']),
        correctable = _b(j['correctable']),
        createdAt = _sr(j['createdAt']),
        updatedAt = _sr(j['updatedAt']);
  final String id, folio, ownerDisplayName, status, personType, signerCharacter, accreditationType, propertyCaseType, declaredLegalStatus;
  final bool signedByAttorney, condominiumRegime, correctable;
  final String? propertyAddress, propertyReference, decisionReason;
  final String createdAt, updatedAt;
}

class DocumentProgress {
  DocumentProgress.fromJson(Json j)
      : required = _i(j['required']),
        received = _i(j['received']),
        accepted = _i(j['accepted']);
  final int required, received, accepted;
}

class ExpedienteOverviewItem {
  ExpedienteOverviewItem.fromJson(Json j)
      : expediente = ExpedienteResponse.fromJson(Json.from(j['expediente'] as Map)),
        documents = DocumentProgress.fromJson(Json.from((j['documents'] as Map?) ?? {}));
  final ExpedienteResponse expediente;
  final DocumentProgress documents;
}

class ExpedienteOverview {
  ExpedienteOverview.fromJson(Json j)
      : page = PageResponse.fromJson(Json.from(j['page'] as Map), ExpedienteOverviewItem.fromJson),
        total = _i((j['counts'] as Map?)?['total']),
        pending = _i((j['counts'] as Map?)?['pending']),
        inReview = _i((j['counts'] as Map?)?['inReview']),
        complete = _i((j['counts'] as Map?)?['complete']);
  final PageResponse<ExpedienteOverviewItem> page;
  final int total, pending, inReview, complete;
}

/// Participante (respuesta) — también se usa como borrador editable con [toRequest].
class Participant {
  Participant({
    this.id,
    required this.role,
    this.fullName = '',
    this.ordinal = 0,
    this.nationality,
    this.idDocumentType,
    this.idDocumentNumber,
    this.idDocumentIssuer,
    this.birthDate,
    this.civilStatus,
    this.maritalRegime,
    this.rfc,
    this.curp,
    this.email,
    this.phone,
    this.address,
  });

  factory Participant.fromJson(Json j) => Participant(
        id: _s(j['id']),
        role: _sr(j['role']),
        fullName: _sr(j['fullName']),
        ordinal: _i(j['ordinal']),
        nationality: _s(j['nationality']),
        idDocumentType: _s(j['idDocumentType']),
        idDocumentNumber: _s(j['idDocumentNumber']),
        idDocumentIssuer: _s(j['idDocumentIssuer']),
        birthDate: _s(j['birthDate']),
        civilStatus: _s(j['civilStatus']),
        maritalRegime: _s(j['maritalRegime']),
        rfc: _s(j['rfc']),
        curp: _s(j['curp']),
        email: _s(j['email']),
        phone: _s(j['phone']),
        address: _s(j['address']),
      );

  String? id;
  String role;
  String fullName;
  int ordinal;
  String? nationality, idDocumentType, idDocumentNumber, idDocumentIssuer, birthDate, civilStatus, maritalRegime, rfc, curp, email, phone, address;

  Participant copy() => Participant.fromJson({...toRequest(), 'id': id, 'ordinal': ordinal});

  /// Lee un dato por su nombre del backend (para "Datos detectados").
  String? field(String key) => toRequest()[key] as String?;

  void setField(String key, String? value) {
    final v = (value == null || value.isEmpty) ? null : value;
    switch (key) {
      case 'nationality':
        nationality = v;
      case 'idDocumentType':
        idDocumentType = v;
      case 'idDocumentNumber':
        idDocumentNumber = v;
      case 'idDocumentIssuer':
        idDocumentIssuer = v;
      case 'birthDate':
        birthDate = v;
      case 'civilStatus':
        civilStatus = v;
      case 'maritalRegime':
        maritalRegime = v;
      case 'rfc':
        rfc = v;
      case 'curp':
        curp = v;
      case 'email':
        email = v;
      case 'phone':
        phone = v;
      case 'address':
        address = v;
    }
  }

  Json toRequest() => {
        'role': role,
        'fullName': fullName,
        'nationality': nationality,
        'idDocumentType': idDocumentType,
        'idDocumentNumber': idDocumentNumber,
        'idDocumentIssuer': idDocumentIssuer,
        'birthDate': birthDate,
        'civilStatus': civilStatus,
        'maritalRegime': maritalRegime,
        'rfc': rfc,
        'curp': curp,
        'email': email,
        'phone': phone,
        'address': address,
      };
}

class RequirementResponse {
  RequirementResponse.fromJson(Json j)
      : requirementCode = _sr(j['requirementCode']),
        type = _sr(j['type']),
        required = _b(j['required']),
        conditional = _b(j['conditional']),
        participantId = _s(j['participantId']);
  final String requirementCode, type;
  final bool required, conditional;
  final String? participantId;
}

class ChangeResponse {
  ChangeResponse.fromJson(Json j)
      : id = _sr(j['id']),
        changedAt = _sr(j['changedAt']),
        actorType = _s(j['actorType']),
        actorName = _s(j['actorName']),
        actorRole = _s(j['actorRole']),
        section = _sr(j['section']),
        field = _sr(j['field']),
        oldValue = _s(j['oldValue']),
        newValue = _s(j['newValue']),
        reason = _s(j['reason']);
  final String id, changedAt, section, field;
  final String? actorType, actorName, actorRole, oldValue, newValue, reason;
}

class ActivityResponse {
  ActivityResponse.fromJson(Json j)
      : id = _sr(j['id']),
        occurredAt = _sr(j['occurredAt']),
        actorType = _s(j['actorType']),
        actorName = _s(j['actorName']),
        actorRole = _s(j['actorRole']),
        documentLabel = _s(j['documentLabel']),
        message = _sr(j['message']);
  final String id, occurredAt, message;
  final String? actorType, actorName, actorRole, documentLabel;
}

class NotificationResponse {
  NotificationResponse.fromJson(Json j)
      : id = _sr(j['id']),
        kind = _sr(j['kind']),
        recipient = _sr(j['recipient']),
        subject = _sr(j['subject']),
        status = _sr(j['status']),
        attempts = _i(j['attempts']),
        lastError = _s(j['lastError']),
        createdAt = _sr(j['createdAt']);
  final String id, kind, recipient, subject, status, createdAt;
  final int attempts;
  final String? lastError;
}

class ComplianceItem {
  ComplianceItem.fromJson(Json j)
      : code = _sr(j['code']),
        passed = _b(j['passed']),
        detail = _sr(j['detail']);
  final String code, detail;
  final bool passed;
}

class ComplianceChecklist {
  ComplianceChecklist.fromJson(Json j)
      : allPassed = _b(j['allPassed']),
        items = _list(j['items'], ComplianceItem.fromJson);
  final bool allPassed;
  final List<ComplianceItem> items;
}

class ClosingCase {
  ClosingCase.fromJson(Json j) : status = _sr(j['status']);
  final String status;
}

class ClosingTask {
  ClosingTask.fromJson(Json j)
      : id = _sr(j['id']),
        title = _sr(j['title']),
        dueDate = _s(j['dueDate']),
        done = _b(j['done']),
        doneAt = _s(j['doneAt']),
        doneNote = _s(j['doneNote']);
  final String id, title;
  final String? dueDate, doneAt, doneNote;
  final bool done;
}

class ClosingNote {
  ClosingNote.fromJson(Json j)
      : id = _sr(j['id']),
        note = _sr(j['note']),
        createdAt = _sr(j['createdAt']);
  final String id, note, createdAt;
}

class PublicLinkStatus {
  PublicLinkStatus.fromJson(Json j)
      : createdAt = _sr(j['createdAt']),
        expiresAt = _s(j['expiresAt']),
        revokedAt = _s(j['revokedAt']),
        lastUsedAt = _s(j['lastUsedAt']),
        usable = _b(j['usable']);
  final String createdAt;
  final String? expiresAt, revokedAt, lastUsedAt;
  final bool usable;
}

class DuplicateMatch {
  DuplicateMatch.fromJson(Json j)
      : id = _sr(j['id']),
        folio = _sr(j['folio']),
        matchedName = _sr(j['matchedName']),
        status = _sr(j['status']),
        property = _s(j['property']),
        advisorName = _s(j['advisorName']),
        createdAt = _sr(j['createdAt']);
  final String id, folio, matchedName, status, createdAt;
  final String? property, advisorName;
}

// ---------------------------------------------------------------------------
// Documentos
// ---------------------------------------------------------------------------

class LatestVersion {
  LatestVersion.fromJson(Json j)
      : id = _sr(j['id']),
        versionNumber = _i(j['versionNumber']),
        uploadedAt = _sr(j['uploadedAt']),
        uploadedVia = _sr(j['uploadedVia']),
        uploadedByName = _s(j['uploadedByName']),
        processingStatus = _sr(j['processingStatus']),
        aiTypeMatches = _bn(j['aiTypeMatches']),
        aiLegible = _bn(j['aiLegible']),
        aiDetectedKind = _s(j['aiDetectedKind']),
        aiObservations = _s(j['aiObservations']),
        aiAssessedAt = _s(j['aiAssessedAt']),
        blockingIssues = _ls(j['blockingIssues']),
        warnings = _ls(j['warnings']),
        qualityLevel = _s(j['qualityLevel']),
        aiPagesAnalyzed = _in(j['aiPagesAnalyzed']),
        aiPagesTotal = _in(j['aiPagesTotal']),
        aiFieldsExpected = _in(j['aiFieldsExpected']),
        aiFieldsFound = _in(j['aiFieldsFound']),
        aiCheckFailed = _b(j['aiCheckFailed']);
  final String id, uploadedAt, uploadedVia, processingStatus;
  final int versionNumber;
  final String? uploadedByName, aiDetectedKind, aiObservations, aiAssessedAt, qualityLevel;
  final bool? aiTypeMatches, aiLegible;
  final List<String> blockingIssues, warnings;
  final int? aiPagesAnalyzed, aiPagesTotal, aiFieldsExpected, aiFieldsFound;
  final bool aiCheckFailed;
}

class DocumentResponse {
  DocumentResponse.fromJson(Json j)
      : id = _sr(j['id']),
        type = _sr(j['type']),
        participantId = _s(j['participantId']),
        required = _b(j['required']),
        deferred = _b(j['deferred']),
        deferralReason = _s(j['deferralReason']),
        status = _sr(j['status']),
        currentVersionNumber = _i(j['currentVersionNumber']),
        notApplicableJustification = _s(j['notApplicableJustification']),
        notApplicableAt = _s(j['notApplicableAt']),
        lastReviewReasonCode = _s(j['lastReviewReasonCode']),
        lastReviewComment = _s(j['lastReviewComment']),
        lastReviewedAt = _s(j['lastReviewedAt']),
        latestVersion = j['latestVersion'] is Map ? LatestVersion.fromJson(Json.from(j['latestVersion'] as Map)) : null,
        pipelineStatus = _s(j['pipelineStatus']);
  final String id, type, status;
  final String? participantId, deferralReason, notApplicableJustification, notApplicableAt, lastReviewReasonCode, lastReviewComment, lastReviewedAt, pipelineStatus;
  final bool required, deferred;
  final int currentVersionNumber;
  final LatestVersion? latestVersion;
}

class ReviewHistory {
  ReviewHistory.fromJson(Json j)
      : id = _sr(j['id']),
        decision = _sr(j['decision']),
        reasonCode = _s(j['reasonCode']),
        comment = _s(j['comment']),
        reviewedAt = _sr(j['reviewedAt']),
        overrideJustification = _s(j['overrideJustification']),
        overriddenIssues = _s(j['overriddenIssues']);
  final String id, decision, reviewedAt;
  final String? reasonCode, comment, overrideJustification, overriddenIssues;
}

class ExtractedField {
  ExtractedField.fromJson(Json j)
      : id = _sr(j['id']),
        fieldName = _sr(j['fieldName']),
        detectedValue = _s(j['detectedValue']),
        confirmedValue = _s(j['confirmedValue']),
        confidence = _dn(j['confidence']),
        sourcePage = _in(j['sourcePage']),
        extra = _b(j['extra']);
  final String id, fieldName;
  final String? detectedValue, confirmedValue;
  final double? confidence;
  final int? sourcePage;
  final bool extra;
}

class ExpedienteObservation {
  ExpedienteObservation.fromJson(Json j)
      : documentType = _sr(j['documentType']),
        participantId = _s(j['participantId']),
        fieldName = _sr(j['fieldName']),
        value = _sr(j['value']),
        confirmed = _b(j['confirmed']);
  final String documentType, fieldName, value;
  final String? participantId;
  final bool confirmed;
}

class DataConflict {
  DataConflict.fromJson(Json j)
      : id = _sr(j['id']),
        description = _sr(j['description']),
        resolved = _b(j['resolved']),
        resolvedAutomatically = _b(j['resolvedAutomatically']),
        resolutionNote = _s(j['resolutionNote']),
        detectedAt = _sr(j['detectedAt']),
        resolvedAt = _s(j['resolvedAt']),
        severity = _s(j['severity']) ?? 'WARNING';
  final String id, description, detectedAt, severity;
  final bool resolved, resolvedAutomatically;
  final String? resolutionNote, resolvedAt;
}

class EmailPreview {
  EmailPreview.fromJson(Json j)
      : subject = _sr(j['subject']),
        body = _sr(j['body']),
        attachments = _list(j['attachments'], (a) => (fileName: _sr(a['fileName']), downloadUrl: _sr(a['downloadUrl']))),
        documentTypesWithoutFile = _ls(j['documentTypesWithoutFile']);
  final String subject, body;
  final List<({String fileName, String downloadUrl})> attachments;
  final List<String> documentTypesWithoutFile;
}

// ---------------------------------------------------------------------------
// Contratos
// ---------------------------------------------------------------------------

class ContractCalculations {
  ContractCalculations.fromJson(Json j)
      : price = _sr(j['price'], '0'),
        commission = _sr(j['commission']),
        totalCommissionWithVat = _sr(j['totalCommissionWithVat']),
        penaltyBase = _sr(j['penaltyBase']),
        penaltyVat = _sr(j['penaltyVat']),
        penalty = _sr(j['penalty']),
        exclusivityDays = _i(j['exclusivityDays']),
        exclusivityEndDate = _sr(j['exclusivityEndDate']);
  final String price, commission, totalCommissionWithVat, penaltyBase, penaltyVat, penalty, exclusivityEndDate;
  final int exclusivityDays;
}

class ContractReadiness {
  ContractReadiness.fromJson(Json j)
      : ready = _b(j['ready']),
        blockers = _ls(j['blockers']),
        missingData = _ls(j['missingData']),
        reviewItems = _ls(j['reviewItems']),
        inconsistencies = _ls(j['inconsistencies']),
        variant = _sr(j['variant']);
  final bool ready;
  final List<String> blockers, missingData, reviewItems, inconsistencies;
  final String variant;
}

class ContractSignature {
  ContractSignature.fromJson(Json j)
      : id = _sr(j['id']),
        party = _sr(j['party']),
        signerName = _sr(j['signerName']),
        signerCapacity = _sr(j['signerCapacity']),
        hasEmail = _b(j['hasEmail']),
        status = _sr(j['status']),
        method = _s(j['method']),
        signedAt = _s(j['signedAt']),
        linkExpiresAt = _s(j['linkExpiresAt']),
        ipAddress = _s(j['ipAddress']),
        voidedReason = _s(j['voidedReason']);
  final String id, party, signerName, signerCapacity, status;
  final bool hasEmail;
  final String? method, signedAt, linkExpiresAt, ipAddress, voidedReason;
}

class ContractGeneration {
  ContractGeneration.fromJson(Json j)
      : id = _sr(j['id']),
        versionNumber = _i(j['versionNumber']),
        generatedAt = _sr(j['generatedAt']),
        documentSha256 = _s(j['documentSha256']),
        status = _sr(j['status']),
        variantSummary = _s(j['variantSummary']),
        missingItems = _ls(j['missingItems']),
        hasPdf = _b(j['hasPdf']),
        hasSignedPackage = _b(j['hasSignedPackage']),
        deliveredAt = _s(j['deliveredAt']),
        deliveryMethod = _s(j['deliveryMethod']),
        supersededReason = _s(j['supersededReason']),
        signatures = _list(j['signatures'], ContractSignature.fromJson);
  final String id, generatedAt, status;
  final int versionNumber;
  final String? documentSha256, variantSummary, deliveredAt, deliveryMethod, supersededReason;
  final List<String> missingItems;
  final bool hasPdf, hasSignedPackage;
  final List<ContractSignature> signatures;
}

class SigningLink {
  SigningLink({required this.signatureId, required this.signerName, required this.signerCapacity, required this.url, required this.expiresAt, required this.emailed});
  factory SigningLink.fromJson(Json j) => SigningLink(
        signatureId: _sr(j['signatureId']),
        signerName: _sr(j['signerName']),
        signerCapacity: _sr(j['signerCapacity']),
        url: _sr(j['url']),
        expiresAt: _sr(j['expiresAt']),
        emailed: _b(j['emailed']),
      );
  final String signatureId, signerName, signerCapacity, url, expiresAt;
  final bool emailed;
  SigningLink withUrl(String newUrl) =>
      SigningLink(signatureId: signatureId, signerName: signerName, signerCapacity: signerCapacity, url: newUrl, expiresAt: expiresAt, emailed: emailed);
}

// ---------------------------------------------------------------------------
// CRM (Módulo 3)
// ---------------------------------------------------------------------------

class NamedRef {
  NamedRef.fromJson(Json j)
      : id = _sr(j['id']),
        name = _sr(j['name']);
  final String id, name;
  static NamedRef? maybe(dynamic v) => v is Map ? NamedRef.fromJson(Json.from(v)) : null;
}

class LeadProperty {
  LeadProperty({this.online21Id, this.key, this.title});
  LeadProperty.fromJson(Json j)
      : online21Id = _in(j['online21Id']),
        key = _s(j['key']),
        title = _s(j['title']);
  final int? online21Id;
  final String? key, title;
  static LeadProperty? maybe(dynamic v) => v is Map ? LeadProperty.fromJson(Json.from(v)) : null;
}

class LeadItem {
  LeadItem.fromJson(Json j)
      : id = _sr(j['id']),
        name = _sr(j['name']),
        phone = _s(j['phone']),
        email = _s(j['email']),
        source = _sr(j['source']),
        sourceDetail = _s(j['sourceDetail']),
        interest = _sr(j['interest']),
        stage = _sr(j['stage']),
        property = LeadProperty.maybe(j['property']),
        advisor = NamedRef.maybe(j['advisor']),
        assignmentMethod = _s(j['assignmentMethod']),
        summary = _s(j['summary']),
        budget = _dn(j['budget']),
        lostReason = _s(j['lostReason']),
        createdAt = _sr(j['createdAt']),
        lastActivityAt = _sr(j['lastActivityAt']),
        conversationId = _s(j['conversationId']),
        assignmentStatus = _sr(j['assignmentStatus'], 'UNASSIGNED'),
        assignmentRule = _s(j['assignmentRule']),
        pendingReason = _s(j['pendingReason']),
        nextShiftAt = _s(j['nextShiftAt']),
        propertyAdvisor = NamedRef.maybe(j['propertyAdvisor']),
        coordinationNote = _s(j['coordinationNote']);
  final String id, name, source, interest, stage, createdAt, lastActivityAt, assignmentStatus;
  final String? phone, email, sourceDetail, assignmentMethod, summary, lostReason, conversationId, assignmentRule, pendingReason, nextShiftAt, coordinationNote;
  final LeadProperty? property;
  final NamedRef? advisor, propertyAdvisor;
  final double? budget;
}

class LeadAssignmentEntry {
  LeadAssignmentEntry.fromJson(Json j)
      : id = _sr(j['id']),
        outcome = _sr(j['outcome']),
        advisor = NamedRef.maybe(j['advisor']),
        reason = _s(j['reason']),
        rule = _sr(j['rule']),
        shiftId = _s(j['shiftId']),
        roundMode = _s(j['roundMode']),
        detail = _s(j['detail']),
        actorName = _s(j['actorName']),
        createdAt = _sr(j['createdAt']);
  final String id, outcome, rule, createdAt;
  final NamedRef? advisor;
  final String? reason, shiftId, roundMode, detail, actorName;
}

class LeadActivity {
  LeadActivity.fromJson(Json j)
      : id = _sr(j['id']),
        kind = _sr(j['kind']),
        text = _sr(j['text']),
        actorName = _s(j['actorName']),
        createdAt = _sr(j['createdAt']);
  final String id, kind, text, createdAt;
  final String? actorName;
}

class LeadDetail {
  LeadDetail.fromJson(Json j)
      : lead = LeadItem.fromJson(Json.from(j['lead'] as Map)),
        activities = _list(j['activities'], LeadActivity.fromJson),
        assignments = _list(j['assignments'], LeadAssignmentEntry.fromJson),
        advisorNoticeStatus = _sr(j['advisorNoticeStatus'], 'NOT_REQUIRED');
  final LeadItem lead;
  final List<LeadActivity> activities;
  final List<LeadAssignmentEntry> assignments;
  final String advisorNoticeStatus;
}

class LeadPage {
  LeadPage.fromJson(Json j)
      : items = _list(j['items'], LeadItem.fromJson),
        total = _i(j['total']),
        counts = ((j['counts'] as Map?) ?? {}).map((k, v) => MapEntry('$k', _i(v)));
  final List<LeadItem> items;
  final int total;
  final Map<String, int> counts;
}

class CrmDashboard {
  CrmDashboard.fromJson(Json j)
      : total = _i(j['total']),
        assigned = _i(j['assigned']),
        unassigned = _i(j['unassigned']),
        pendingAssignment = _i(j['pendingAssignment']),
        open = _i(j['open']),
        won = _i(j['won']),
        closed = _i(j['closed']),
        staleNew = _i(j['staleNew']),
        byStage = ((j['byStage'] as Map?) ?? {}).map((k, v) => MapEntry('$k', _i(v))),
        bySource = ((j['bySource'] as Map?) ?? {}).map((k, v) => MapEntry('$k', _i(v))),
        byAdvisor = _list(j['byAdvisor'], (a) => (advisorId: _sr(a['advisorId']), name: _sr(a['name']), leads: _i(a['leads']), won: _i(a['won']))),
        topProperties = _list(j['topProperties'], (p) => (property: LeadProperty.fromJson(p), leads: _i(p['leads']))),
        recent = _list(j['recent'], LeadItem.fromJson);
  final int total, assigned, unassigned, pendingAssignment, open, won, closed, staleNew;
  final Map<String, int> byStage, bySource;
  final List<({String advisorId, String name, int leads, int won})> byAdvisor;
  final List<({LeadProperty property, int leads})> topProperties;
  final List<LeadItem> recent;
}

class AdvisorRouting {
  AdvisorRouting.fromJson(Json j)
      : userId = _sr(j['userId']),
        name = _sr(j['name']),
        email = _sr(j['email']),
        roleCode = _sr(j['roleCode']),
        receivesLeads = _b(j['receivesLeads']),
        weight = _i(j['weight']),
        dailyLimit = _in(j['dailyLimit']),
        online21UserId = _in(j['online21UserId']),
        whatsappPhone = _s(j['whatsappPhone']),
        status = _sr(j['status']),
        statusDetail = _sr(j['statusDetail']),
        currentShiftEnd = _s(j['currentShiftEnd']),
        currentShiftKind = _s(j['currentShiftKind']),
        nextShift = _s(j['nextShift']),
        needsCalendar = _b(j['needsCalendar']),
        legacyShift = _s(j['legacyShift']),
        assignedToday = _i(j['assignedToday']),
        openLeads = _i(j['openLeads']),
        wonLeads = _i(j['wonLeads']);
  final String userId, name, email, roleCode, status, statusDetail;
  final bool receivesLeads, needsCalendar;
  final int weight, assignedToday, openLeads, wonLeads;
  final int? dailyLimit, online21UserId;
  final String? whatsappPhone, currentShiftEnd, currentShiftKind, nextShift, legacyShift;
}

class AssignmentSettings {
  AssignmentSettings.fromJson(Json j)
      : roundMode = _sr(j['roundMode'], 'EQUAL'),
        ownerOffShiftPolicy = _sr(j['ownerOffShiftPolicy'], 'RESPECT_OWNER'),
        updatedByName = _s(j['updatedByName']),
        pendingLeads = _i(j['pendingLeads']),
        nextCoverage = _s(j['nextCoverage']),
        noUpcomingCoverage = _b(j['noUpcomingCoverage']);
  final String roundMode, ownerOffShiftPolicy;
  final String? updatedByName, nextCoverage;
  final int pendingLeads;
  final bool noUpcomingCoverage;
}

class LocalRange {
  LocalRange.fromJson(Json j)
      : start = _sr(j['start']),
        end = _sr(j['end']);
  final String start, end;
}

class ShiftOccurrence {
  ShiftOccurrence.fromJson(Json j)
      : shiftId = _sr(j['shiftId']),
        advisorId = _sr(j['advisorId']),
        advisorName = _sr(j['advisorName']),
        kind = _sr(j['kind']),
        assignable = _b(j['assignable']),
        recurrence = _sr(j['recurrence']),
        date = _sr(j['date']),
        start = _sr(j['start']),
        end = _sr(j['end']),
        notes = _s(j['notes']),
        absentParts = _list(j['absentParts'], LocalRange.fromJson),
        fullyAbsent = _b(j['fullyAbsent']),
        days = j['days'] is List ? (j['days'] as List).map((e) => _i(e)).toList() : <int>[],
        seriesEnd = _s(j['seriesEnd']);
  final String shiftId, advisorId, advisorName, kind, recurrence, date, start, end;
  final bool assignable, fullyAbsent;
  final String? notes, seriesEnd;
  final List<LocalRange> absentParts;
  final List<int> days;
}

class CalendarAbsence {
  CalendarAbsence.fromJson(Json j)
      : id = _sr(j['id']),
        advisorId = _sr(j['advisorId']),
        advisorName = _sr(j['advisorName']),
        kind = _sr(j['kind']),
        start = _sr(j['start']),
        end = _sr(j['end']),
        notes = _s(j['notes']);
  final String id, advisorId, advisorName, kind, start, end;
  final String? notes;
}

class PersonNow {
  PersonNow.fromJson(Json j)
      : advisorId = _sr(j['advisorId']),
        name = _sr(j['name']),
        kind = _sr(j['kind']),
        assignable = _b(j['assignable']),
        start = _sr(j['start']),
        end = _sr(j['end']);
  final String advisorId, name, kind, start, end;
  final bool assignable;
}

class CalendarAdvisor {
  CalendarAdvisor.fromJson(Json j)
      : userId = _sr(j['userId']),
        name = _sr(j['name']),
        status = _sr(j['status']),
        statusDetail = _sr(j['statusDetail']),
        nextShift = _s(j['nextShift']),
        needsCalendar = _b(j['needsCalendar']),
        legacyShift = _s(j['legacyShift']);
  final String userId, name, status, statusDetail;
  final String? nextShift, legacyShift;
  final bool needsCalendar;
}

class CalendarView {
  CalendarView.fromJson(Json j)
      : today = _sr(j['today']),
        now = _sr(j['now']),
        canManage = _b(j['canManage']),
        canEditOwn = _b(j['canEditOwn']),
        advisors = _list(j['advisors'], CalendarAdvisor.fromJson),
        occurrences = _list(j['occurrences'], ShiftOccurrence.fromJson),
        absences = _list(j['absences'], CalendarAbsence.fromJson),
        gaps = _list(j['gaps'], LocalRange.fromJson),
        onShiftNow = _list(j['onShiftNow'], PersonNow.fromJson),
        nextUp = _list(j['nextUp'], PersonNow.fromJson),
        nextCoverage = _s(j['nextCoverage']);
  final String today, now;
  final bool canManage, canEditOwn;
  final List<CalendarAdvisor> advisors;
  final List<ShiftOccurrence> occurrences;
  final List<CalendarAbsence> absences;
  final List<LocalRange> gaps;
  final List<PersonNow> onShiftNow, nextUp;
  final String? nextCoverage;
}

class CalendarAuditEntry {
  CalendarAuditEntry.fromJson(Json j)
      : id = _sr(j['id']),
        action = _sr(j['action']),
        detail = _sr(j['detail']),
        actorName = _s(j['actorName']),
        createdAt = _sr(j['createdAt']);
  final String id, action, detail, createdAt;
  final String? actorName;
}

class ConversationItem {
  ConversationItem.fromJson(Json j)
      : id = _sr(j['id']),
        contactName = _s(j['contactName']),
        phone = _s(j['phone']),
        test = _b(j['test']),
        control = _sr(j['control'], 'AI'),
        intent = _sr(j['intent']),
        leadId = _s(j['leadId']),
        needsAttention = _s(j['needsAttention']),
        lastMessage = _s(j['lastMessage']),
        lastMessageRole = _s(j['lastMessageRole']),
        lastMessageAt = _s(j['lastMessageAt']),
        openEscalations = _i(j['openEscalations']);
  final String id, control, intent;
  final String? contactName, phone, leadId, needsAttention, lastMessage, lastMessageRole, lastMessageAt;
  final bool test;
  final int openEscalations;
  bool get requiresAttention => needsAttention != null || openEscalations > 0;
}

class ConversationMessage {
  ConversationMessage.fromJson(Json j)
      : id = _sr(j['id']),
        role = _sr(j['role']),
        text = _sr(j['text']),
        status = _sr(j['status']),
        error = _s(j['error']),
        createdAt = _sr(j['createdAt']);
  final String id, role, text, status, createdAt;
  final String? error;
}

class ConversationDetail {
  ConversationDetail.fromJson(Json j)
      : conversation = ConversationItem.fromJson(Json.from(j['conversation'] as Map)),
        summary = _s(j['summary']),
        messages = _list(j['messages'], ConversationMessage.fromJson),
        escalations = _list(j['escalations'], (e) => (id: _sr(e['id']), reason: _sr(e['reason']), status: _sr(e['status'])));
  final ConversationItem conversation;
  final String? summary;
  final List<ConversationMessage> messages;
  final List<({String id, String reason, String status})> escalations;
}

class AssistantStatus {
  AssistantStatus.fromJson(Json j)
      : mode = _sr(j['mode'], 'OFF'),
        aiConfigured = _b(j['aiConfigured']),
        whatsappConfigured = _b(j['whatsappConfigured']),
        webhookConfigured = _b(j['webhookConfigured']),
        inventoryAvailable = _b(j['inventoryAvailable']);
  final String mode;
  final bool aiConfigured, whatsappConfigured, webhookConfigured, inventoryAvailable;
}
