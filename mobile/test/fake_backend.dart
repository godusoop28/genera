// Respuestas simuladas del backend para las pruebas de pantallas (mismo
// formato que la API real).

const _now = '2026-10-14T16:30:00Z';

final _expediente = {
  'id': 'exp-1',
  'folio': 'GEN-2026-0042',
  'ownerDisplayName': 'María Fernanda López Gutiérrez',
  'status': 'UNDER_REVIEW',
  'personType': 'FISICA',
  'signerCharacter': 'COPROPIETARIO',
  'signedByAttorney': false,
  'accreditationType': 'ESCRITURA_PUBLICA',
  'condominiumRegime': true,
  'propertyCaseType': 'HOUSING',
  'propertyCategoryId': 'cat-1',
  'propertyCategoryName': 'Habitacional',
  'propertySubtypeId': 'sub-casa',
  'propertySubtypeName': 'Casa',
  'propertyTypeDetail': null,
  'propertyTypeLabel': 'Habitacional → Casa',
  'operationType': 'SALE',
  'assignedAdvisorId': 'u-2',
  'assignedAdvisorName': 'Ana Sofía Herrera',
  'declaredLegalStatus': 'EN_REVISION',
  'propertyAddress': 'Calle Río Balsas 123, Col. Vista Hermosa, Cuernavaca, Morelos',
  'propertyReference': 'Casa en Vista Hermosa',
  'decisionReason': null,
  'createdByUserId': 'u-1',
  'correctable': true,
  'createdAt': '2026-10-01T15:00:00Z',
  'updatedAt': '2026-10-13T18:20:00Z',
};

final _participants = [
  {'id': 'p-1', 'role': 'OWNER', 'fullName': 'María Fernanda López Gutiérrez', 'ordinal': 1, 'nationality': 'Mexicana', 'idDocumentType': 'INE', 'idDocumentNumber': 'LOGM850101', 'birthDate': '1985-01-01', 'civilStatus': 'CASADO', 'rfc': 'LOGM850101AB1', 'email': 'maria@example.com', 'phone': '777 123 4567'},
  {'id': 'p-2', 'role': 'CO_OWNER', 'fullName': 'Jorge Alberto Ramírez Soto', 'ordinal': 2},
];

Map<String, dynamic> _version(String id, {List<String> issues = const [], bool? matches = true, String? kind}) => {
      'id': 'v-$id',
      'versionNumber': 1,
      'uploadedAt': '2026-10-10T17:00:00Z',
      'uploadedVia': 'PUBLIC_PORTAL',
      'uploadedByName': null,
      'processingStatus': 'PROCESSED',
      'aiTypeMatches': matches,
      'aiLegible': true,
      'aiDetectedKind': kind ?? 'Credencial para votar',
      'aiObservations': null,
      'aiAssessedAt': '2026-10-10T17:01:00Z',
      'blockingIssues': issues,
      'warnings': ['La foto tiene un ligero reflejo en la esquina inferior.'],
      'qualityLevel': 'ACCEPTED_WITH_WARNINGS',
      'aiPagesAnalyzed': 1,
      'aiPagesTotal': 1,
      'aiFieldsExpected': 5,
      'aiFieldsFound': 4,
      'aiCheckFailed': false,
    };

Map<String, dynamic> _doc(String id, String type, String status, {String? participant, bool withFile = true, String? pipeline, bool matches = true}) => {
      'id': id,
      'expedienteId': 'exp-1',
      'requirementCode': type,
      'type': type,
      'participantId': participant,
      'required': true,
      'deferred': false,
      'deferralReason': null,
      'status': status,
      'currentVersionNumber': withFile ? 1 : 0,
      'lastReviewReasonCode': status == 'RETURNED' ? 'BLURRY_IMAGE' : null,
      'lastReviewComment': status == 'RETURNED' ? 'La foto salió borrosa.' : null,
      'lastReviewedAt': status == 'RETURNED' ? '2026-10-11T15:00:00Z' : null,
      'latestVersion': withFile ? _version(id, matches: matches, kind: matches ? null : 'Recibo de luz') : null,
      'pipelineStatus': pipeline,
    };

final _documents = [
  _doc('d-1', 'INE', 'READY_FOR_REVIEW', participant: 'p-1', pipeline: 'EXTRACTION_COMPLETE'),
  _doc('d-2', 'PROOF_OF_ADDRESS', 'RETURNED', participant: 'p-1', pipeline: 'REQUIRES_REVIEW'),
  _doc('d-3', 'TAX_STATUS_CERTIFICATE', 'ACCEPTED', participant: 'p-1', pipeline: 'ACCEPTED'),
  _doc('d-4', 'INE', 'PENDING', participant: 'p-2', withFile: false),
  _doc('d-5', 'DEED', 'READY_FOR_REVIEW', pipeline: 'EXTRACTION_PARTIAL', matches: false),
  _doc('d-6', 'PROPERTY_TAX', 'PENDING', withFile: false),
];

Map<String, dynamic> _lead(String id, String name, String stage, {Map<String, dynamic>? advisor, String status = 'ASSIGNED'}) => {
      'id': id,
      'name': name,
      'phone': '+52 777 555 0101',
      'email': 'cliente@example.com',
      'source': id == 'lead-2' ? 'PORTAL' : 'WHATSAPP_BOT',
      'sourceDetail': id == 'lead-2' ? 'Inmuebles24' : null,
      'interest': 'BUY',
      'stage': stage,
      'property': {'online21Id': 634294, 'key': 'GN 14', 'title': 'Casa en venta en Lomas de Cortés'},
      'advisor': advisor,
      'assignmentMethod': advisor != null ? 'ROULETTE' : null,
      'summary': 'Busca casa de 3 recámaras con jardín, presupuesto cercano a 4.5 millones. Prefiere visitar el sábado.',
      'budget': 4500000,
      'lostReason': null,
      'createdAt': '2026-10-14T13:00:00Z',
      'lastActivityAt': '2026-10-14T15:00:00Z',
      'conversationId': 'conv-1',
      'assignmentStatus': status,
      'assignmentRule': null,
      'pendingReason': status == 'PENDING_SCHEDULE' ? 'Nadie en turno en este momento.' : null,
      'nextShiftAt': status == 'PENDING_SCHEDULE' ? '2026-10-15T15:00:00Z' : null,
      'propertyAdvisor': {'id': 'u-9', 'name': 'Eva Martínez'},
      'coordinationNote': null,
    };

final _ana = {'id': 'u-2', 'name': 'Ana Sofía Herrera'};
final _leads = [
  _lead('lead-1', 'Roberto Castillo Núñez', 'CONTACTED', advisor: _ana),
  _lead('lead-2', 'Lucía Domínguez', 'NEW', status: 'PENDING_SCHEDULE'),
  _lead('lead-3', 'Pedro Aguilar Medina', 'APPOINTMENT', advisor: _ana),
];

final _listing = {
  'id': 634294, 'key': '634294--649', 'internalKey': 'GN 14', 'status': 'enPromocion', 'operation': 'venta', 'propertyType': 'habitacional',
  'subtype': 'casa', 'subtypeLabel': 'Casa', 'contractType': 'exclusiva', 'title': 'Casa con jardín en Lomas de Cortés', 'salePrice': 5980000,
  'rentPrice': null, 'currency': 'MXN', 'street': 'Retorno de Fátima', 'number': '47', 'neighborhood': 'Lomas de Cortés', 'municipality': 'Cuernavaca',
  'state': 'Morelos', 'postalCode': '62240', 'bedrooms': 4, 'bathrooms': 3.5, 'halfBathrooms': 1, 'parking': 2, 'landM2': 430, 'builtM2': 330,
  'description': 'Casa de dos niveles con jardín amplio y cocina integral.', 'publishedOnline': true, 'advisorOnline21Id': 41388, 'photoUrl': null,
  'photoCount': 0, 'createdAt': '2026-09-01T16:00:00Z', 'updatedAt': '2026-10-10T16:00:00Z',
};

final _captured = {
  'id': 'prop-1', 'online21Id': null, 'syncStatus': 'FAILED', 'syncError': '21Online no aceptó la propiedad: estado: Este valor no debería estar vacío.',
  'syncUncertain': false, 'syncedAt': null,
  'data': {
    'operation': 'renta', 'propertyType': 'comercial', 'subtype': 'local', 'contractType': 'exclusiva', 'status': 'enPromocion',
    'headline': 'Local comercial sobre avenida', 'description': null, 'salePrice': null, 'rentPrice': 18000, 'currency': 'MXN', 'hidePrice': false,
    'street': 'Av. Morelos', 'extNumber': '210', 'neighborhood': 'Centro', 'municipality': 'Cuernavaca', 'state': 'Morelos', 'postalCode': null,
    'hideStreet': false, 'hideNumber': true, 'bedrooms': null, 'bathrooms': 1, 'halfBathrooms': null, 'parking': 2, 'landM2': null, 'builtM2': 120,
    'levels': 1, 'age': 10, 'maintenanceFee': null, 'commission': 5, 'publishOnline': true, 'advisorUserId': 'u-2',
  },
  'advisor': _ana, 'advisorLinked': false, 'createdBy': {'id': 'u-1', 'name': 'Administrador Demo'},
  'createdAt': '2026-10-14T15:00:00Z', 'updatedAt': '2026-10-14T15:00:00Z', 'canEdit': true,
};

final _advisors = [
  for (final (i, name, status) in [(2, 'Ana Sofía Herrera', 'AVAILABLE'), (3, 'Carlos Méndez', 'OFF_SHIFT'), (4, 'Eva Martínez', 'ABSENT')])
    {
      'userId': 'u-$i',
      'name': name,
      'email': 'asesor$i@c21genera.com',
      'roleCode': 'ADVISOR',
      'receivesLeads': i != 4,
      'weight': 10,
      'dailyLimit': i == 2 ? 5 : null,
      'online21UserId': i == 3 ? null : 1200 + i,
      'whatsappPhone': i == 2 ? '+52 777 000 0000' : null,
      'status': status,
      'statusDetail': status == 'AVAILABLE' ? 'En turno' : 'Fuera de horario',
      'currentShiftEnd': status == 'AVAILABLE' ? '2026-10-14T18:00:00' : null,
      'currentShiftKind': status == 'AVAILABLE' ? 'REGULAR' : null,
      'nextShift': '2026-10-15T09:00:00',
      'needsCalendar': false,
      'legacyShift': null,
      'assignedToday': 2,
      'openLeads': 7,
      'wonLeads': i == 2 ? 3 : 0,
    },
];

Map<String, dynamic> _occ(String advisorId, String name, String date, String start, String end, {String kind = 'REGULAR'}) => {
      'shiftId': 's-$advisorId-$kind',
      'advisorId': advisorId,
      'advisorName': name,
      'kind': kind,
      'assignable': true,
      'recurrence': 'WEEKLY',
      'date': date,
      'start': '${date}T$start:00',
      'end': '${date}T$end:00',
      'notes': null,
      'absentParts': [],
      'fullyAbsent': false,
      'days': [1, 2, 3, 4, 5],
      'seriesStart': '2026-09-01',
      'seriesEnd': null,
    };

List<Map<String, dynamic>> _occurrences(String from, String to) {
  final out = <Map<String, dynamic>>[];
  var d = DateTime.parse(from);
  final end = DateTime.parse(to);
  while (!d.isAfter(end)) {
    final date = d.toIso8601String().substring(0, 10);
    if (d.weekday <= 5) {
      out.add(_occ('u-2', 'Ana Sofía Herrera', date, '09:00', '14:00'));
      out.add(_occ('u-3', 'Carlos Méndez', date, '14:00', '20:00'));
    } else {
      out.add(_occ('u-4', 'Eva Martínez', date, '10:00', '15:00', kind: 'GUARD'));
    }
    d = d.add(const Duration(days: 1));
  }
  return out;
}

/// Peticiones que no son GET (para verificar que la app manda lo correcto).
final List<({String method, String path, Object? body})> sentRequests = [];

/// Devuelve (status, cuerpo JSON) para cada petición.
(int, Object?) fakeBackend(String method, Uri url, [Object? body]) {
  final p = url.path.replaceFirst('/api/v1', '');
  final q = url.queryParameters;
  if (method != 'GET') {
    sentRequests.add((method: method, path: p, body: body));
    if (p == '/auth/login') {
      final ok = body is Map && body['password'] == 'correcta';
      return ok
          ? (200, {'accessToken': 'nuevo', 'accessTokenExpiresAt': _now, 'refreshToken': 'r'})
          : (401, {'title': 'Unauthorized', 'status': 401, 'detail': 'Credenciales inválidas.', 'code': 'BAD_CREDENTIALS'});
    }
    if (p.startsWith('/internal/documents/') && p.endsWith('/accept')) return (200, {..._documents.first, 'status': 'ACCEPTED'});
    if (p == '/internal/leads') return fakeBackend('GET', Uri.parse('/api/v1/internal/leads/lead-1'));
    if (p == '/internal/crm/calendar/shifts') return (201, {'id': 's-nuevo'});
    if (p == '/internal/expedientes') return (201, _expediente);
    if (p == '/internal/conversations/conv-1/reply') return fakeBackend('GET', Uri.parse('/api/v1/internal/conversations/conv-1'));
    if (p.startsWith('/internal/drafts/')) return (200, {'formKey': 'x', 'payload': '{}', 'updatedAt': _now});
    if (p == '/internal/expedientes/duplicate-check') return (200, []);
    if (p.startsWith('/internal/leads/lead-1/')) return fakeBackend('GET', Uri.parse('/api/v1/internal/leads/lead-1'));
    if (p == '/internal/properties') return (201, _captured);
    // Control de Cambios #25: el alta y el restablecimiento devuelven un enlace de un solo uso.
    final link = {'url': 'https://genera.test/acceso/tok-123', 'expiresAt': '2026-10-17T16:30:00Z'};
    if (p == '/internal/users') {
      final b = body as Map;
      return (
        201,
        {
          'user': {'id': 'u-9', 'name': b['name'], 'email': b['email'], 'roleCode': b['roleCode'], 'roleName': 'Asesor', 'status': 'PENDING', 'lastActivityAt': null, 'locked': false},
          'link': {...link, 'purpose': 'INVITE'},
        }
      );
    }
    if (p.startsWith('/internal/users/') && p.endsWith('/password-link')) return (200, {...link, 'purpose': 'RESET'});
    if (p.startsWith('/internal/properties/prop-1')) return (200, {..._captured, 'syncStatus': 'DONE', 'syncError': null, 'online21Id': 700001, 'syncedAt': _now});
    return (200, {});
  }
  // Control de Cambios #26: catálogo de inmuebles.
  if (p == '/internal/property-catalog') {
    Map<String, Object?> sub(String id, String cat, String catName, String name, String caseType, String type, String subtype, [bool detail = false]) => {
          'id': id,
          'categoryId': cat,
          'categoryName': catName,
          'name': name,
          'sortOrder': 10,
          'active': true,
          'requiresDetail': detail,
          'caseType': caseType,
          'online21Type': type,
          'online21Subtype': subtype,
        };
    return (
      200,
      [
        {
          'id': 'cat-1',
          'name': 'Habitacional',
          'sortOrder': 10,
          'active': true,
          'subtypes': [
            sub('sub-casa', 'cat-1', 'Habitacional', 'Casa', 'HOUSING', 'habitacional', 'casa'),
            sub('sub-depto', 'cat-1', 'Habitacional', 'Departamento', 'DEPARTMENT', 'habitacional', 'departamento'),
          ],
        },
        {
          'id': 'cat-3',
          'name': 'Industrial y logística',
          'sortOrder': 40,
          'active': true,
          'subtypes': [sub('sub-nave', 'cat-3', 'Industrial y logística', 'Nave industrial', 'COMMERCIAL', 'comercial', 'nave')],
        },
        {
          'id': 'cat-8',
          'name': 'Especiales',
          'sortOrder': 80,
          'active': true,
          'subtypes': [sub('sub-otro', 'cat-8', 'Especiales', 'Otro — especificar', 'COMMERCIAL', 'comercial', 'inmueble-productivo', true)],
        },
      ],
    );
  }
  if (p == '/internal/audit-events/accounts') {
    return (
      200,
      {
        'items': [
          {'id': 'a-1', 'occurredAt': _now, 'eventType': 'UserCREATED', 'aggregateType': 'User', 'aggregateId': 'u-2', 'actorType': 'STAFF', 'actorName': 'Administrador Demo', 'actorRole': 'ADMINISTRATOR', 'summary': 'Ana Sofía Herrera: Alta de la cuenta con rol Asesor'},
        ],
        'page': 0,
        'size': 100,
        'totalElements': 1,
        'totalPages': 1,
      }
    );
  }
  if (p == '/internal/me') return (200, {'id': 'u-1', 'name': 'Administrador Demo', 'email': 'admin@demo.local', 'role': 'ADMINISTRATOR', 'permissions': ['*']});
  if (p.startsWith('/internal/drafts/')) return (204, null);
  if (p == '/internal/expedientes/advisors') {
    return (
      200,
      [
        {'id': 'u-2', 'name': 'Ana Sofía Herrera', 'roleCode': 'ADVISOR'},
        {'id': 'u-3', 'name': 'Carlos Méndez', 'roleCode': 'ADVISOR'},
        {'id': 'u-1', 'name': 'Administrador Demo', 'roleCode': 'ADMINISTRATOR'},
      ],
    );
  }
  if (p == '/internal/expedientes/overview/portfolio') {
    Map<String, int> counts(int total) => {'total': total, 'pending': total ~/ 2, 'inReview': total - total ~/ 2, 'complete': 0};
    return (
      200,
      {
        'total': counts(5),
        'byTypeOperation': [
          {'categoryId': 'cat-1', 'categoryName': 'Habitacional', 'subtypeId': 'sub-casa', 'subtypeName': 'Casa', 'operationType': 'SALE', 'counts': counts(3)},
          {'categoryId': 'cat-1', 'categoryName': 'Habitacional', 'subtypeId': 'sub-depto', 'subtypeName': 'Departamento', 'operationType': 'RENT', 'counts': counts(1)},
          {'categoryId': 'cat-2', 'categoryName': 'Terrenos', 'subtypeId': null, 'subtypeName': 'Pendiente de clasificación', 'operationType': 'SALE', 'counts': counts(1)},
        ],
        'byAdvisor': [
          {'advisorId': 'u-2', 'name': 'Ana Sofía Herrera', 'counts': counts(4)},
          {'advisorId': null, 'name': 'Sin asesor asignado', 'counts': counts(1)},
        ],
      },
    );
  }
  if (p == '/internal/expedientes/overview') {
    return (
      200,
      {
        'page': {
          'items': [
            {'expediente': _expediente, 'documents': {'required': 14, 'received': 9, 'accepted': 5}},
            {
              'expediente': {..._expediente, 'id': 'exp-2', 'folio': 'GEN-2026-0041', 'ownerDisplayName': 'Inmobiliaria del Sol, S.A. de C.V.', 'status': 'WAITING_DOCUMENTS', 'propertyAddress': null},
              'documents': {'required': 12, 'received': 0, 'accepted': 0},
            },
          ],
          'page': 0,
          'size': 18,
          'totalElements': 2,
          'totalPages': 1,
        },
        'counts': {'total': 2, 'pending': 1, 'inReview': 1, 'complete': 0},
      }
    );
  }
  if (p == '/internal/expedientes/duplicate-check') return (200, []);
  if (p == '/internal/expedientes/exp-1') return (200, _expediente);
  if (p == '/internal/expedientes/exp-1/participants') return (200, _participants);
  if (p == '/internal/expedientes/exp-1/public-link') {
    return (200, {'createdAt': '2026-10-02T15:00:00Z', 'expiresAt': '2026-11-02T15:00:00Z', 'revokedAt': null, 'lastUsedAt': '2026-10-12T20:00:00Z', 'usable': true});
  }
  if (method == 'POST' && p == '/internal/expedientes/exp-1/documents') {
    return (201, {..._doc('d-extra', 'OTHER', 'PENDING', withFile: false), 'customName': (body as Map?)?['name']});
  }
  if (p == '/internal/expedientes/exp-1/privacy') return (200, {'status': 'PENDING'});
  if (p == '/internal/expedientes/exp-1/autofill') return (200, {'filled': 0});
  if (p == '/internal/expedientes/exp-1/documents') return (200, _documents);
  // Visor sin descarga (Control de Cambios #6).
  if (RegExp(r'^/internal/document-versions/[^/]+/view$').hasMatch(p)) return (200, {'pageCount': 2, 'canDownload': false});
  if (p == '/internal/expedientes/exp-1/requirements') {
    return (200, [for (final d in _documents) {'requirementCode': d['type'], 'type': d['type'], 'required': true, 'conditional': false, 'participantId': d['participantId']}]);
  }
  if (p == '/internal/expedientes/exp-1/extracted-fields') {
    return (
      200,
      [
        {'id': 'o-1', 'documentId': 'd-5', 'documentType': 'DEED', 'participantId': null, 'documentStatus': 'READY_FOR_REVIEW', 'fieldName': 'deedNumber', 'value': '45,210', 'confirmed': false, 'confidence': 0.9},
        {'id': 'o-2', 'documentId': 'd-5', 'documentType': 'DEED', 'participantId': null, 'documentStatus': 'READY_FOR_REVIEW', 'fieldName': 'landArea', 'value': '250.5 m2', 'confirmed': false, 'confidence': 0.8},
        {'id': 'o-3', 'documentId': 'd-1', 'documentType': 'INE', 'participantId': 'p-2', 'documentStatus': 'READY_FOR_REVIEW', 'fieldName': 'curp', 'value': 'RASJ800101HMSMTR09', 'confirmed': false, 'confidence': 0.95},
      ]
    );
  }
  if (p == '/internal/expedientes/exp-1/data-conflicts') {
    return (
      200,
      [
        {'id': 'c-1', 'fieldName': 'ownerName', 'description': 'El nombre en el predial ("María F. López") no coincide exactamente con la escritura.', 'resolved': false, 'resolvedAutomatically': false, 'detectedAt': _now, 'severity': 'WARNING'},
        {'id': 'c-2', 'fieldName': 'landArea', 'description': 'La superficie del plano (250 m²) y de la escritura (250.5 m²) son casi iguales.', 'resolved': false, 'resolvedAutomatically': false, 'detectedAt': _now, 'severity': 'INFO'},
      ]
    );
  }
  // Controles de cambios #14, #15, #20, #21 y #23.
  if (RegExp(r'^/internal/documents/[^/]+/pending$').hasMatch(p) && method == 'PUT') {
    final id = p.split('/')[3];
    final base = _documents.firstWhere((d) => d['id'] == id, orElse: () => _documents.first);
    return (200, {...base, 'markedPending': true, 'pendingNote': (body as Map?)?['note'], 'pendingAt': _now});
  }
  if (p == '/internal/expedientes/exp-1/condominium-areas') {
    return (
      200,
      [
        {'key': 'condo-land-area', 'field': 'landArea', 'label': 'Superficie de terreno (m²)', 'condominiumRegime': '85.40', 'deed': '85.40', 'propertyTax': '85.40', 'consistent': true},
        {'key': 'condo-undivided-area', 'field': 'undividedLandArea', 'label': 'Superficie de terreno de indivisos (m²)', 'condominiumRegime': '12.50', 'deed': '12.50', 'propertyTax': '19.00', 'consistent': false},
      ]
    );
  }
  if (p == '/internal/expedientes/exp-1/notary') {
    return (200, {'closingActive': false, 'documents': [], 'submissions': []});
  }
  if (p == '/internal/expedientes/exp-1/signed-contracts') return (200, []);
  if (p == '/internal/feasibility/fea-1/chat') {
    return (
      200,
      [
        {'id': 'm1', 'role': 'USER', 'status': 'DONE', 'content': '¿Qué escuelas hay cerca?', 'sources': [], 'authorName': 'Ana Asesora', 'createdAt': '2026-10-24T18:20:00Z'},
        {
          'id': 'm2', 'role': 'ASSISTANT', 'status': 'DONE', 'content': 'Hay **tres escuelas** a menos de 1 km:\n- Colegio A\n- Escuela B',
          'sources': [{'title': 'SEP', 'url': 'https://example.com/sep'}], 'createdAt': '2026-10-24T18:21:00Z',
        },
      ]
    );
  }
  if (p == '/internal/expedientes/exp-1/feasibility') {
    return (
      200,
      {
        'available': true,
        'reports': [
          {
            'id': 'fea-1', 'expedienteId': 'exp-1', 'versionNumber': 1, 'status': 'READY', 'requestedAt': '2026-10-24T18:00:00Z', 'requestedByName': 'Ana Asesora',
            'stageStartedAt': '2026-10-24T18:05:00Z', 'completedAt': '2026-10-24T18:14:00Z', 'hasDocx': true, 'hasPptx': true,
            'summary': {
              'executiveSummary': 'La casa está por arriba del rango competitivo; con un precio de salida de \$4,350,000 compite con las alternativas directas.',
              'ownerPrice': 4500000, 'rangeMin': 4200000, 'rangeMax': 4450000, 'recommendedPrice': 4350000, 'classification': 'LIMITE_SUPERIOR',
              'strategy': 'Salir en \$4,350,000 y revisar a los 30 días.',
              'comparables': [
                {'id': 'C1', 'group': 'ARRIBA', 'zone': 'Vista Hermosa, Cuernavaca', 'price': 4900000, 'url': 'https://example.com/c1', 'urlVerified': true, 'locationPrecision': 'EXACTA'},
                {'id': 'C2', 'group': 'SIMILAR', 'zone': 'Delicias, Cuernavaca', 'price': 4400000, 'url': null, 'urlVerified': false, 'locationPrecision': 'APROXIMADA'},
              ],
              'pendingItems': ['Superficie de construcción por confirmar'],
            },
          },
          {'id': 'fea-0', 'expedienteId': 'exp-1', 'versionNumber': 0, 'status': 'FAILED', 'requestedAt': '2026-10-23T18:00:00Z', 'stageStartedAt': '2026-10-23T18:00:00Z', 'error': 'La IA tardó más de 45 minutos.', 'hasDocx': false, 'hasPptx': false},
        ],
      }
    );
  }
  if (p == '/internal/settings/visit-instructions') return (200, {'text': 'Las visitas al inmueble deberán programarse previamente.'});
  if (p == '/internal/expedientes/exp-1/client-data') return (200, {'authorizedPrice': 4500000, 'email': 'maria@example.com', 'phone': '777 123 4567', 'bedrooms': 3, 'bathrooms': 2});
  if (p == '/internal/expedientes/exp-1/legal-details') return (200, {'deed': {'number': null}, 'propertyChecklist': {'LICENSES': true}});
  if (p == '/internal/expedientes/exp-1/contract/readiness') {
    return (200, {'ready': false, 'blockers': ['Faltan 4 documentos obligatorios por aceptar'], 'missingData': ['Número de escritura'], 'reviewItems': ['Instrucciones para visitas'], 'inconsistencies': [], 'variant': 'Persona física · copropietarios · escritura'});
  }
  if (p == '/internal/expedientes/exp-1/contract/data') {
    return (200, {'price': '4500000', 'priceWritten': '', 'commission': '225000', 'vat': '36000', 'totalCommissionWithVat': '261000', 'penaltyBase': '225000', 'penaltyVat': '36000', 'penalty': '261000', 'exclusivityDays': 180, 'exclusivityEndDate': '2027-04-12'});
  }
  if (p == '/internal/expedientes/exp-1/contracts') {
    return (
      200,
      [
        {
          'id': 'k-1',
          'versionNumber': 1,
          'generatedAt': '2026-10-12T16:00:00Z',
          'documentSha256': 'a3f1c9e0b2d4f6a8c0e2f4a6b8d0f2a4c6e8a0b2d4f6a8c0e2f4a6b8d0f2a4c6',
          'status': 'DRAFT_INCOMPLETE',
          'variantSummary': 'Persona física · escritura',
          'missingItems': ['Número de escritura'],
          'hasPdf': true,
          'hasSignedPackage': false,
          'signatures': [],
        },
      ]
    );
  }
  if (p == '/internal/expedientes/exp-1/closing') return (404, {'title': 'No encontrado', 'status': 404});
  if (p == '/internal/expedientes/exp-1/compliance-checklist') {
    return (
      200,
      {
        'expedienteId': 'exp-1',
        'allPassed': false,
        'items': [
          {'code': 'PRIVACY_NOTICE_PRESENTED', 'passed': true, 'detail': 'Versión 3 vigente.'},
          {'code': 'REQUIRED_DOCUMENTS_ACCEPTED', 'passed': false, 'detail': 'Faltan 4 documentos.'},
        ],
      }
    );
  }
  if (p == '/internal/expedientes/exp-1/activity') {
    return (
      200,
      [
        {'id': 'a-1', 'occurredAt': '2026-10-12T16:00:00Z', 'category': 'DOCUMENT', 'actorType': 'CLIENT', 'documentLabel': 'INE', 'message': 'El cliente cargó su identificación.'},
        {'id': 'a-2', 'occurredAt': '2026-10-11T15:00:00Z', 'category': 'REVIEW', 'actorType': 'STAFF', 'actorName': 'Administrador Demo', 'actorRole': 'ADMINISTRATOR', 'message': 'Se devolvió el comprobante de domicilio.'},
      ]
    );
  }
  if (p == '/internal/expedientes/exp-1/changes' || p == '/internal/expedientes/exp-1/notifications') return (200, []);
  if (p == '/internal/crm/dashboard') {
    return (
      200,
      {
        'total': 24,
        'assigned': 20,
        'unassigned': 2,
        'pendingAssignment': 2,
        'open': 15,
        'won': 4,
        'closed': 6,
        'staleNew': 3,
        'byStage': {'NEW': 5, 'CONTACTED': 6, 'APPOINTMENT': 4, 'VISITED': 2, 'NEGOTIATION': 1, 'WON': 4, 'LOST': 2},
        'bySource': {'WHATSAPP_BOT': 12, 'PORTAL': 7, 'PHONE': 5},
        'byAdvisor': [
          {'advisorId': 'u-2', 'name': 'Ana Sofía Herrera', 'leads': 11, 'won': 3},
          {'advisorId': 'u-3', 'name': 'Carlos Méndez', 'leads': 9, 'won': 1},
        ],
        'topProperties': [
          {'online21Id': 634294, 'key': 'GN 14', 'title': 'Casa en venta en Lomas de Cortés', 'leads': 6},
        ],
        'recent': _leads,
      }
    );
  }
  if (p == '/internal/leads') return (200, {'items': _leads, 'total': 3, 'page': 0, 'totalPages': 1, 'counts': {'ALL': 3, 'UNASSIGNED': 0, 'PENDING': 1, 'NEW': 1, 'OPEN': 2, 'WON': 0, 'LOST': 0}});
  if (p == '/internal/leads/lead-1') {
    return (
      200,
      {
        'lead': _leads.first,
        'activities': [
          {'id': 'la-1', 'kind': 'CREATED', 'text': 'El asistente registró el lead desde WhatsApp.', 'actorName': null, 'createdAt': '2026-10-14T13:00:00Z'},
          {'id': 'la-2', 'kind': 'ASSIGNED', 'text': 'Asignado a Ana Sofía Herrera (ronda).', 'actorName': null, 'createdAt': '2026-10-14T13:00:05Z'},
        ],
        'assignments': [
          {'id': 'as-1', 'outcome': 'ASSIGNED', 'advisor': _ana, 'reason': 'ROULETTE', 'rule': 'Ronda equitativa entre asesores en turno', 'shiftId': 's-1', 'roundMode': 'EQUAL', 'detail': null, 'actorName': null, 'createdAt': '2026-10-14T13:00:05Z'},
        ],
        'online21SyncStatus': 'DONE',
        'advisorNoticeStatus': 'DONE',
      }
    );
  }
  if (p == '/internal/properties/inventory') {
    return (200, {'configured': true, 'error': null, 'items': [{'listing': _listing, 'advisor': _ana, 'generaId': null}, {'listing': {..._listing, 'id': 634331, 'key': '634331--649', 'internalKey': 'GA 08', 'status': 'suspendida', 'operation': 'renta', 'rentPrice': 15000, 'salePrice': null, 'title': 'Departamento amueblado en renta', 'advisorOnline21Id': 99}, 'advisor': null, 'generaId': 'prop-1'}], 'page': 1, 'totalItems': 2, 'hasNext': false});
  }
  // Detalle de 21Online (como su pantalla "Ver propiedad").
  if (p == '/internal/properties/inventory/634294/details') {
    return (
      200,
      {
        'details': {
          'listing': _listing,
          'fields': [
            {'section': 'Generales', 'label': 'Clave', 'value': 'GN 14'},
            {'section': 'Generales', 'label': 'Comisión', 'value': '5%'},
            {'section': 'Características', 'label': 'Alberca', 'value': 'Sí'},
          ],
          'photos': [],
          'floorPlans': [],
          'videoUrl': null,
          'latitude': 18.93,
          'longitude': -99.22,
          'publicUrl': 'https://century21mexico.com/propiedad/634294',
          'advisor': {'name': 'Ana Sofía Herrera', 'email': 'ana@c21genera.com', 'mobile': '7771234567', 'officePhone': null},
        },
        'generaAdvisor': _ana,
      }
    );
  }
  if (p == '/internal/properties/inventory/634294/portals') {
    return (
      200,
      [
        {'portal': 'Mercado Libre', 'active': true, 'status': 'publicado', 'url': 'https://casa.mercadolibre.com.mx/MLM-1', 'reference': 'ID: MLM1', 'withError': false},
        {'portal': 'Inmuebles24', 'active': false, 'status': null, 'url': null, 'withError': true, 'error': 'Falta foto de portada'},
      ]
    );
  }
  if (p == '/internal/properties/inventory/634294/clients') {
    return (
      200,
      {
        'contactsVisible': true,
        'clients': [
          {'id': 2839694, 'name': 'Guadalupe Jasso Rocha', 'phone': '8121463220', 'source': 'Mercado Libre', 'status': 'Interesado', 'createdAt': _now},
          {'id': 2839695, 'name': 'Pedro Ruiz', 'phone': null, 'source': 'Inmuebles24', 'status': 'Interesado', 'createdAt': '2026-09-02T10:00:00Z'},
        ],
      }
    );
  }
  if (p == '/internal/properties/inventory/634294/follow-ups') {
    return (200, [
      {'type': 'visita', 'subtype': 'Primera visita', 'advisor': 'Ana Sofía Herrera', 'client': 'Guadalupe Jasso Rocha', 'createdAt': _now},
    ]);
  }
  if (p == '/internal/properties/inventory/634294') return (200, {'listing': _listing, 'advisor': _ana, 'generaId': null});
  if (p == '/internal/properties') return (200, {'configured': true, 'captured': [_captured]});
  if (p == '/internal/properties/prop-1') return (200, _captured);
  if (p == '/internal/crm/advisors') return (200, _advisors);
  if (p == '/internal/crm/assignment-settings') {
    return (200, {'roundMode': 'EQUAL', 'ownerOffShiftPolicy': 'RESPECT_OWNER', 'updatedByName': 'Administrador Demo', 'pendingLeads': 2, 'nextCoverage': '2026-10-15T09:00:00', 'noUpcomingCoverage': false, 'timezone': 'America/Mexico_City'});
  }
  if (p == '/internal/crm/calendar') {
    final from = q['from'] ?? '2026-10-12';
    final to = q['to'] ?? '2026-10-18';
    return (
      200,
      {
        'timezone': 'America/Mexico_City',
        'today': '2026-10-14',
        'now': '2026-10-14T10:30:00',
        'from': from,
        'to': to,
        'canManage': true,
        'canEditOwn': true,
        'advisors': [
          for (final a in _advisors) {'userId': a['userId'], 'name': a['name'], 'roleCode': 'ADVISOR', 'status': a['status'], 'statusDetail': a['statusDetail'], 'nextShift': a['nextShift'], 'needsCalendar': false, 'legacyShift': null},
        ],
        'occurrences': _occurrences(from, to),
        'absences': [
          {'id': 'ab-1', 'advisorId': 'u-4', 'advisorName': 'Eva Martínez', 'kind': 'VACATION', 'start': '2026-10-14T00:00:00', 'end': '2026-10-16T00:00:00', 'notes': null},
        ],
        'gaps': [
          {'start': '2026-10-14T20:00:00', 'end': '2026-10-14T23:00:00'},
        ],
        'onShiftNow': [
          {'advisorId': 'u-2', 'name': 'Ana Sofía Herrera', 'kind': 'REGULAR', 'assignable': true, 'start': '2026-10-14T09:00:00', 'end': '2026-10-14T14:00:00'},
        ],
        'nextUp': [
          {'advisorId': 'u-3', 'name': 'Carlos Méndez', 'kind': 'REGULAR', 'assignable': true, 'start': '2026-10-14T14:00:00', 'end': '2026-10-14T20:00:00'},
        ],
        'nextCoverage': null,
      }
    );
  }
  if (p == '/internal/assistant/status') return (200, {'mode': 'TEST_ONLY', 'aiConfigured': true, 'model': 'x', 'whatsappConfigured': false, 'webhookConfigured': false, 'inventoryAvailable': true});
  final conversation = {
    'id': 'conv-1',
    'contactName': 'Roberto Castillo Núñez',
    'phone': '+52 777 555 0101',
    'test': false,
    'control': 'AI',
    'intent': 'Comprar casa en Cuernavaca',
    'handoffState': 'HANDED_OFF',
    'leadId': 'lead-1',
    'needsAttention': 'Pidió hablar con una persona.',
    'lastMessage': '¿Puedo verla el sábado?',
    'lastMessageRole': 'CLIENT',
    'lastMessageAt': '2026-10-14T15:00:00Z',
    'openEscalations': 1,
  };
  if (p == '/internal/conversations') return (200, [conversation, {...conversation, 'id': 'conv-2', 'contactName': 'Prueba del asistente', 'test': true, 'needsAttention': null, 'openEscalations': 0, 'control': 'PAUSED'}]);
  if (p == '/internal/conversations/conv-1') {
    return (
      200,
      {
        'conversation': conversation,
        'summary': 'Busca casa de 3 recámaras; presupuesto 4.5 MDP.',
        'messages': [
          {'id': 'm-1', 'role': 'CLIENT', 'text': 'Hola, vi la casa GN 14 en Lomas de Cortés. ¿Sigue disponible?', 'status': 'SENT', 'error': null, 'createdAt': '2026-10-14T14:50:00Z'},
          {'id': 'm-2', 'role': 'BOT', 'text': '¡Hola Roberto! Sí, sigue disponible. Tiene 3 recámaras y jardín. ¿Te gustaría agendar una visita?', 'status': 'SENT', 'error': null, 'createdAt': '2026-10-14T14:51:00Z'},
          {'id': 'm-3', 'role': 'CLIENT', 'text': '¿Puedo verla el sábado?', 'status': 'SENT', 'error': null, 'createdAt': '2026-10-14T15:00:00Z'},
          {'id': 'm-4', 'role': 'TEAM', 'text': 'Claro, te esperamos el sábado a las 11.', 'status': 'SENT', 'error': null, 'createdAt': '2026-10-14T15:05:00Z'},
        ],
        'escalations': [
          {'id': 'e-1', 'type': 'HUMAN', 'reason': 'Pidió hablar con una persona.', 'status': 'OPEN', 'createdAt': '2026-10-14T15:00:00Z'},
        ],
      }
    );
  }
  if (p == '/internal/users') {
    return (
      200,
      {
        'items': [
          {'id': 'u-1', 'name': 'Administrador Demo', 'email': 'admin@demo.local', 'roleCode': 'ADMINISTRATOR', 'roleName': 'Administrador', 'status': 'ACTIVE', 'lastActivityAt': _now},
          {'id': 'u-2', 'name': 'Ana Sofía Herrera', 'email': 'ana@c21genera.com', 'roleCode': 'ADVISOR', 'roleName': 'Asesor', 'status': 'ACTIVE', 'lastActivityAt': null},
          {'id': 'u-5', 'name': 'Luis Revisor', 'email': 'luis@c21genera.com', 'roleCode': 'DOCUMENT_REVIEWER', 'roleName': 'Revisor documental', 'status': 'INACTIVE', 'lastActivityAt': null},
          {'id': 'u-6', 'name': 'Pedro Nuevo', 'email': 'pedro@c21genera.com', 'roleCode': 'ADVISOR', 'roleName': 'Asesor', 'status': 'PENDING', 'lastActivityAt': null, 'locked': false},
        ],
        'page': 0,
        'size': 100,
        'totalElements': 4,
        'totalPages': 1,
      }
    );
  }
  if (p == '/internal/roles') {
    return (
      200,
      [
        {'id': 'r-1', 'code': 'ADMINISTRATOR', 'name': 'Administrador', 'description': 'Todo el sistema.', 'permissions': ['USER_MANAGE', 'EXPEDIENT_CREATE']},
        {'id': 'r-2', 'code': 'ADVISOR', 'name': 'Asesor', 'description': 'Sus expedientes y leads.', 'permissions': ['EXPEDIENT_CREATE']},
      ]
    );
  }
  if (p == '/internal/permissions') {
    return (200, [
      {'code': 'USER_MANAGE', 'description': 'Administrar usuarios'},
      {'code': 'EXPEDIENT_CREATE', 'description': 'Crear expedientes'},
    ]);
  }
  return (404, {'title': 'No encontrado', 'status': 404, 'detail': 'Ruta simulada no definida: $method $p'});
}
