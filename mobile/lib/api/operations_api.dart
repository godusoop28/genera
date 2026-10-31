// CC#32 (operaciones de cierre) y CC#33 (fotos del inmueble): mismos endpoints que la web
// (frontend/src/lib/api/operations.ts y photos.ts).

import 'client.dart';

final _c = ApiClient.instance;

typedef J = Map<String, dynamic>;

J _j(dynamic v) => Map<String, dynamic>.from(v as Map);
List<J> _l(dynamic v) => (v as List? ?? const []).whereType<Map>().map((e) => Map<String, dynamic>.from(e)).toList();
String? _s(dynamic v) => v?.toString();
String _sr(dynamic v, [String f = '']) => v?.toString() ?? f;
int _i(dynamic v) => v is num ? v.toInt() : int.tryParse('$v') ?? 0;
double? _d(dynamic v) => v == null ? null : (v is num ? v.toDouble() : double.tryParse('$v'));

String _q(Map<String, Object?> params) {
  final entries = params.entries.where((e) => e.value != null && e.value != '' && e.value != false).map((e) => '${e.key}=${Uri.encodeQueryComponent('${e.value}')}');
  return entries.isEmpty ? '' : '?${entries.join('&')}';
}

// --- Operaciones ---------------------------------------------------------------

class OperationItem {
  OperationItem(J j)
      : id = _sr(j['id']),
        folio = _sr(j['folio']),
        origin = _sr(j['origin']),
        expedienteId = _s(j['expedienteId']),
        operationType = _sr(j['operationType'], 'SALE'),
        propertyDescription = _sr(j['propertyDescription']),
        representedSide = _sr(j['representedSide'], 'SELLER'),
        advisorName = _s(j['advisorName']),
        status = _sr(j['status']),
        docStatus = _sr(j['docStatus']),
        displayStatus = _sr(j['displayStatus']),
        closedOn = _s(j['closedOn']),
        closedByName = _s(j['closedByName']),
        amount = _d(j['amount']),
        currency = _s(j['currency']),
        sellerName = _s(j['sellerName']),
        buyerName = _s(j['buyerName']),
        requirementsTotal = _i(j['requirementsTotal']),
        requirementsSatisfied = _i(j['requirementsSatisfied']),
        docDueDate = _s(j['docDueDate']),
        docFollowUpOwner = _s(j['docFollowUpOwner']);

  final String id, folio, origin, operationType, propertyDescription, representedSide, status, docStatus, displayStatus;
  final String? expedienteId, advisorName, closedOn, closedByName, currency, sellerName, buyerName, docDueDate, docFollowUpOwner;
  final double? amount;
  final int requirementsTotal, requirementsSatisfied;
}

class OperationTotals {
  OperationTotals(J j)
      : total = _i(j['total']),
        closed = _i(j['closed']),
        closedPendingDocs = _i(j['closedPendingDocs']),
        inReview = _i(j['inReview']),
        external = _i(j['external']);
  final int total, closed, closedPendingDocs, inReview, external;
}

class OperationList {
  OperationList(J j)
      : items = _l(j['items']).map(OperationItem.new).toList(),
        totals = OperationTotals(_j(j['totals']));
  final List<OperationItem> items;
  final OperationTotals totals;
}

class EvidenceView {
  EvidenceView(J j)
      : id = _sr(j['id']),
        versionNumber = _i(j['versionNumber']),
        kind = _sr(j['kind']),
        filename = _s(j['filename']),
        documentName = _s(j['documentName']),
        note = _s(j['note']),
        documentDate = _s(j['documentDate']),
        expiresOn = _s(j['expiresOn']),
        attachedByName = _s(j['attachedByName']),
        attachedAt = _sr(j['attachedAt']);
  final String id, kind, attachedAt;
  final int versionNumber;
  final String? filename, documentName, note, documentDate, expiresOn, attachedByName;

  String get title => switch (kind) {
        'FILE' => filename ?? 'Archivo',
        'EXPEDIENTE_DOCUMENT' => 'Documento del expediente: ${documentName ?? ''}',
        _ => note ?? '',
      };
}

class RequirementView {
  RequirementView(J j)
      : id = _sr(j['id']),
        section = _sr(j['section']),
        partySide = _s(j['partySide']),
        label = _sr(j['label']),
        description = _s(j['description']),
        mandatory = j['mandatory'] == true,
        validityDays = j['validityDays'] == null ? null : _i(j['validityDays']),
        status = _sr(j['status']),
        requestedFrom = _s(j['requestedFrom']),
        followUpOwner = _s(j['followUpOwner']),
        dueDate = _s(j['dueDate']),
        observation = _s(j['observation']),
        reviewedByName = _s(j['reviewedByName']),
        naJustification = _s(j['naJustification']),
        naApprovedAt = _s(j['naApprovedAt']),
        satisfied = j['satisfied'] == true,
        expired = j['expired'] == true,
        dateMissing = j['dateMissing'] == true,
        currentEvidence = j['currentEvidence'] == null ? null : EvidenceView(_j(j['currentEvidence'])),
        previousEvidence = _l(j['previousEvidence']).map(EvidenceView.new).toList();
  final String id, section, label, status;
  final String? partySide, description, requestedFrom, followUpOwner, dueDate, observation, reviewedByName, naJustification, naApprovedAt;
  final bool mandatory, satisfied, expired, dateMissing;
  final int? validityDays;
  final EvidenceView? currentEvidence;
  final List<EvidenceView> previousEvidence;

  bool get naPending => status == 'NOT_APPLICABLE' && naApprovedAt == null;
}

class PartyView {
  PartyView(J j)
      : side = _sr(j['side']),
        fullName = _sr(j['fullName']),
        personType = _s(j['personType']),
        representativeName = _s(j['representativeName']),
        representativeRole = _s(j['representativeRole']),
        email = _s(j['email']),
        phone = _s(j['phone']),
        counterpartContact = _s(j['counterpartContact']),
        representedByGenera = j['representedByGenera'] == true;
  final String side, fullName;
  final String? personType, representativeName, representativeRole, email, phone, counterpartContact;
  final bool representedByGenera;
}

class OperationDetail {
  OperationDetail(J j)
      : operation = OperationItem(_j(j['operation'])),
        propertyAddress = _s(j['propertyAddress']),
        externalSource = _s(j['externalSource']),
        externalOffice = _s(j['externalOffice']),
        externalContact = _s(j['externalContact']),
        generaParticipation = _s(j['generaParticipation']),
        cancelReason = _s(j['cancelReason']),
        docAuthorizedByName = _s(j['docAuthorizedByName']),
        expedienteFolio = _s(j['expedienteFolio']),
        parties = _l(j['parties']).map(PartyView.new).toList(),
        requirements = _l(j['requirements']).map(RequirementView.new).toList(),
        missingBySide = Map<String, dynamic>.from(_j(j['summary'])['missingBySide'] as Map).map((k, v) => MapEntry(k, _i(v))),
        blockers = (_j(j['summary'])['blockers'] as List? ?? const []).map((e) => '$e').toList(),
        needsUpdate = _i(_j(j['summary'])['needsUpdate']),
        satisfied = _i(_j(j['summary'])['satisfied']),
        total = _i(_j(j['summary'])['total']),
        history = _l(j['history']),
        expedienteDocuments = _l(j['expedienteDocuments']),
        matrixApproved = j['matrixApproved'] == true;
  final OperationItem operation;
  final String? propertyAddress, externalSource, externalOffice, externalContact, generaParticipation, cancelReason, docAuthorizedByName, expedienteFolio;
  final List<PartyView> parties;
  final List<RequirementView> requirements;
  final Map<String, int> missingBySide;
  final List<String> blockers;
  final int needsUpdate, satisfied, total;
  final List<J> history;
  final List<J> expedienteDocuments;
  final bool matrixApproved;
}

const _base = '/internal/operations';

Future<OperationList> listOperations({String? status, String? origin, bool pendingDocs = false, String? q}) async =>
    OperationList(_j(await _c.get('$_base${_q({'status': status, 'origin': origin, 'pendingDocs': pendingDocs, 'q': q})}')));
Future<OperationDetail> getOperation(String id) async => OperationDetail(_j(await _c.get('$_base/$id')));
Future<List<OperationItem>> operationsOfExpediente(String expedienteId) async => _l(await _c.get('$_base/by-expediente/$expedienteId')).map(OperationItem.new).toList();
Future<OperationDetail> createOperation(J body) async => OperationDetail(_j(await _c.post(_base, body)));
Future<OperationDetail> moveToClosingReview(String id) async => OperationDetail(_j(await _c.post('$_base/$id/closing-review', {})));
Future<OperationDetail> closeOperation(String id, J body) async => OperationDetail(_j(await _c.post('$_base/$id/close', body)));
Future<OperationDetail> cancelOperation(String id, String reason) async => OperationDetail(_j(await _c.post('$_base/$id/cancel', {'reason': reason})));
Future<OperationDetail> reopenOperation(String id, String reason) async => OperationDetail(_j(await _c.post('$_base/$id/reopen', {'reason': reason})));
Future<OperationDetail> authorizeOperation(String id) async => OperationDetail(_j(await _c.post('$_base/$id/authorize', {})));
Future<OperationDetail> addEvidenceNote(String id, String reqId, String note, String? documentDate) async =>
    OperationDetail(_j(await _c.post('$_base/$id/requirements/$reqId/evidence/note', {'note': note, 'documentDate': documentDate})));
Future<OperationDetail> linkEvidenceDocument(String id, String reqId, String documentId, String? documentDate) async =>
    OperationDetail(_j(await _c.post('$_base/$id/requirements/$reqId/evidence/document', {'documentId': documentId, 'documentDate': documentDate})));
Future<OperationDetail> attachEvidenceFile(String id, String reqId, UploadFile file, String? documentDate) async => OperationDetail(_j(await _c.upload(
      '$_base/$id/requirements/$reqId/evidence/file',
      'file',
      [file],
      fields: {'documentDate': ?documentDate},
    )));
Future<OperationDetail> reviewRequirement(String id, String reqId, String status, String? observation) async =>
    OperationDetail(_j(await _c.post('$_base/$id/requirements/$reqId/review', {'status': status, 'observation': observation})));
Future<OperationDetail> requestNotApplicable(String id, String reqId, String justification) async =>
    OperationDetail(_j(await _c.post('$_base/$id/requirements/$reqId/not-applicable', {'justification': justification})));
Future<OperationDetail> decideNotApplicable(String id, String reqId, bool approve, String? observation) async =>
    OperationDetail(_j(await _c.post('$_base/$id/requirements/$reqId/not-applicable/decision', {'approve': approve, 'observation': observation})));
Future<String> evidenceDownload(String id, String evidenceId) async => _sr(_j(await _c.get('$_base/$id/evidence/$evidenceId/download'))['url']);

const operationStatusLabels = {'IN_PROCESS': 'En proceso', 'CLOSING_REVIEW': 'En revisión de cierre', 'CLOSED': 'Cerrada', 'CANCELLED': 'Cancelada'};
const docStatusLabels = {'PENDING': 'Pendiente', 'IN_REVIEW': 'En revisión', 'COMPLETE_VALIDATED': 'Completa y validada'};
const requirementStatusLabels = {
  'PENDING': 'Pendiente',
  'RECEIVED': 'Recibido',
  'IN_REVIEW': 'En revisión',
  'VALIDATED': 'Validado',
  'NEEDS_CORRECTION': 'Requiere corrección',
  'NOT_APPLICABLE': 'No aplica',
};
const sectionLabels = {'OPERATION': 'Operación', 'PLD': 'PLD', 'PROFECO': 'PROFECO'};
const originLabels = {'OWN': 'Cartera propia', 'EXTERNAL': 'Propiedad externa'};
const requestedFromLabels = {
  'CLIENT': 'Cliente de C21 Genera',
  'COUNTERPARTY': 'Contraparte (por su asesor)',
  'OFFICE': 'Oficina participante',
  'THIRD_PARTY': 'Tercero',
  'INTERNAL': 'Equipo C21 Genera',
};

String partyLabel(String type, String side) =>
    type == 'RENT' ? (side == 'SELLER' ? 'Arrendador' : 'Arrendatario') : (side == 'SELLER' ? 'Vendedor' : 'Comprador');
String representedLabel(String type, String side) => side == 'BOTH' ? 'Ambas partes' : partyLabel(type, side);

// --- Fotos del inmueble (CC#33) ---------------------------------------------------

class PropertyPhoto {
  PropertyPhoto(J j)
      : id = _sr(j['id']),
        uploadedBy = _sr(j['uploadedBy']),
        uploadedByName = _s(j['uploadedByName']),
        uploadedAt = _sr(j['uploadedAt']),
        originalFilename = _s(j['originalFilename']),
        selectedForPortal = j['selectedForPortal'] == true,
        mainPhoto = j['mainPhoto'] == true,
        portalOrder = j['portalOrder'] == null ? null : _i(j['portalOrder']),
        url = _s(j['url']);
  final String id, uploadedBy, uploadedAt;
  final String? uploadedByName, originalFilename, url;
  final bool selectedForPortal, mainPhoto;
  final int? portalOrder;
}

class PhotosResponse {
  PhotosResponse(J j)
      : photos = _l(j['photos']).map(PropertyPhoto.new).toList(),
        remaining = _i(_j(j['limits'])['remaining']),
        maxBytes = _i(_j(j['limits'])['maxBytesPerPhoto']),
        maxPerUpload = _i(_j(j['limits'])['maxPerUpload']),
        maxPhotos = _i(_j(j['limits'])['maxPhotos']);
  final List<PropertyPhoto> photos;
  final int remaining, maxBytes, maxPerUpload, maxPhotos;
}

Future<PhotosResponse> listPhotos(String expedienteId) async => PhotosResponse(_j(await _c.get('/internal/expedientes/$expedienteId/photos')));
Future<PhotosResponse> uploadPhotos(String expedienteId, List<UploadFile> files, {void Function(int)? onProgress}) async =>
    PhotosResponse(_j(await _c.upload('/internal/expedientes/$expedienteId/photos', 'files', files, onProgress: onProgress)));
Future<PhotosResponse> savePhotoSelection(String expedienteId, List<({String id, bool main})> items) async => PhotosResponse(
    _j(await _c.put('/internal/expedientes/$expedienteId/photos/selection', {'items': items.map((i) => {'photoId': i.id, 'main': i.main}).toList()})));
Future<PhotosResponse> removePhoto(String expedienteId, String photoId) async => PhotosResponse(_j(await _c.delete('/internal/expedientes/$expedienteId/photos/$photoId')));
