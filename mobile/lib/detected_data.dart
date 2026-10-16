// Lo que la revisión automática leyó en los documentos, organizado para
// prellenar el contrato y los datos de cada participante (port de
// frontend/src/lib/detected-data.ts).

import 'api/models.dart';
import 'labels.dart';

/// Un valor leído en un documento del expediente.
class Detected {
  const Detected({required this.value, required this.source, required this.confirmed});
  final String value;

  /// Documento del que salió, p. ej. "Escritura".
  final String source;

  /// Ya lo confirmó una persona al revisar el documento.
  final bool confirmed;
}

class DetectedData {
  final Map<String, Detected> legal = {};
  Detected? landAreaM2;
  Detected? builtAreaM2;
  final Map<String, Map<String, Detected>> participants = {};
}

const _months = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

String _strip(String s) => s.replaceAll('á', 'a').replaceAll('é', 'e').replaceAll('í', 'i').replaceAll('ó', 'o').replaceAll('ú', 'u');

/// AAAA-MM-DD, DD/MM/AAAA o "15 de marzo de 2010" → AAAA-MM-DD; null si no se reconoce.
String? normalizeDate(String raw) {
  final text = raw.trim().toLowerCase();
  String pad(int n) => n.toString().padLeft(2, '0');
  bool valid(int y, int m, int d) => y > 1800 && y < 2200 && m >= 1 && m <= 12 && d >= 1 && d <= 31;
  var match = RegExp(r'^(\d{4})-(\d{1,2})-(\d{1,2})').firstMatch(text);
  if (match != null) {
    final y = int.parse(match[1]!), m = int.parse(match[2]!), d = int.parse(match[3]!);
    return valid(y, m, d) ? '$y-${pad(m)}-${pad(d)}' : null;
  }
  match = RegExp(r'^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$').firstMatch(text);
  if (match != null) {
    final d = int.parse(match[1]!), m = int.parse(match[2]!), y = int.parse(match[3]!);
    return valid(y, m, d) ? '$y-${pad(m)}-${pad(d)}' : null;
  }
  match = RegExp(r'(\d{1,2})\s+de\s+([a-záéíóú]+)\s+(?:de|del)\s+(\d{4})').firstMatch(text);
  if (match != null) {
    final m = _months.indexOf(_strip(match[2]!)) + 1;
    final d = int.parse(match[1]!), y = int.parse(match[3]!);
    return valid(y, m, d) ? '$y-${pad(m)}-${pad(d)}' : null;
  }
  return null;
}

/// "1,250.50 m2" → "1250.50"; null si no hay número.
String? normalizeArea(String raw) => RegExp(r'\d+(?:\.\d+)?').firstMatch(raw.replaceAll(RegExp(r',(?=\d{3}\b)'), ''))?.group(0);

typedef _Target = ({String kind, String key, bool date});

_Target _legal(String key, [bool date = false]) => (kind: 'legal', key: key, date: date);
_Target _area(String key) => (kind: 'area', key: key, date: false);
_Target _participant(String key, [bool date = false]) => (kind: 'participant', key: key, date: date);

/// Qué dato del contrato alimenta cada campo de cada tipo de documento.
final Map<String, Map<String, _Target>> _mapping = {
  'DEED': {
    'deedNumber': _legal('deed.number'),
    'deedDate': _legal('deed.date', true),
    'notaryName': _legal('deed.notaryName'),
    'notaryNumber': _legal('deed.notaryNumber'),
    'notaryPlace': _legal('deed.notaryPlace'),
    'publicRegistryFolio': _legal('deed.registryData'),
    'landArea': _area('landAreaM2'),
    'builtArea': _area('builtAreaM2'),
  },
  'PRIVATE_CONTRACT': {
    'sellerFullName': _legal('privateContract.sellerName'),
    'buyerFullName': _legal('privateContract.buyerName'),
    'contractDate': _legal('privateContract.date', true),
    'ratificationDate': _legal('privateContract.ratificationDate', true),
    'notaryName': _legal('privateContract.notaryName'),
    'notaryNumber': _legal('privateContract.notaryNumber'),
    'notaryPlace': _legal('privateContract.notaryPlace'),
    'registryDate': _legal('privateContract.registryDate', true),
    'publicRegistryFolio': _legal('privateContract.realFolio'),
    'landArea': _area('landAreaM2'),
  },
  'CONDOMINIUM_REGIME': {
    'deedNumber': _legal('condominium.deedNumber'),
    'deedDate': _legal('condominium.date', true),
    'notaryName': _legal('condominium.notaryName'),
    'notaryNumber': _legal('condominium.notaryNumber'),
    'notaryPlace': _legal('condominium.notaryPlace'),
    'registryDate': _legal('condominium.registryDate', true),
    'regimeRegistrationFolio': _legal('condominium.realFolio'),
  },
  'INCORPORATION_DEED': {
    'companyType': _legal('company.companyType'),
    'instrumentNumber': _legal('company.instrumentNumber'),
    'instrumentDate': _legal('company.instrumentDate', true),
    'notaryTitle': _legal('company.notaryTitle'),
    'notaryName': _legal('company.notaryName'),
    'notaryNumber': _legal('company.notaryNumber'),
    'notaryPlace': _legal('company.notaryPlace'),
    'commerceRegistryPlace': _legal('company.commerceRegistryPlace'),
    'mercantileFolio': _legal('company.mercantileFolio'),
  },
  'POWER_OF_ATTORNEY': {
    'instrumentNumber': _legal('representation.instrumentNumber'),
    'instrumentDate': _legal('representation.instrumentDate', true),
    'notaryName': _legal('representation.notaryName'),
    'notaryNumber': _legal('representation.notaryNumber'),
    'notaryPlace': _legal('representation.notaryPlace'),
    'publicRegistryFolio': _legal('representation.registryFolio'),
  },
  'PROPERTY_TAX': {'landArea': _area('landAreaM2'), 'builtArea': _area('builtAreaM2')},
  'CADASTRAL_PLAN': {'landArea': _area('landAreaM2'), 'builtArea': _area('builtAreaM2')},
  // El espacio "INE" es el de la identificación oficial: puede traer un pasaporte o una tarjeta de residente.
  'INE': {
    'electorKey': _participant('idDocumentNumber'),
    'documentNumber': _participant('idDocumentNumber'),
    'nationality': _participant('nationality'),
    'curp': _participant('curp'),
    'birthDate': _participant('birthDate', true),
    'address': _participant('address'),
  },
  'PASSPORT': {
    'passportNumber': _participant('idDocumentNumber'),
    'nationality': _participant('nationality'),
    'birthDate': _participant('birthDate', true),
    'curp': _participant('curp'),
  },
  'CURP': {'curp': _participant('curp'), 'birthDate': _participant('birthDate', true)},
  'TAX_STATUS_CERTIFICATE': {'rfc': _participant('rfc'), 'curp': _participant('curp'), 'address': _participant('address')},
  'PROOF_OF_ADDRESS': {'address': _participant('address')},
};

/// La boleta del RPP es la fuente del folio real (Control de Cambios #8). Escritura y contrato
/// privado nunca coexisten, así que alimenta a los dos: el contrato solo usa el que aplica.
final Map<String, Map<String, List<_Target>>> _alsoFeeds = {
  'RPP_REGISTRATION_SLIP': {
    'publicRegistryFolio': [_legal('deed.registryData'), _legal('privateContract.realFolio')],
    'registryDate': [_legal('privateContract.registryDate', true)],
  },
};

/// Si dos documentos traen el mismo dato, gana el de mayor rango.
const _priority = [
  'RPP_REGISTRATION_SLIP',
  'DEED',
  'PRIVATE_CONTRACT',
  'CONDOMINIUM_REGIME',
  'INCORPORATION_DEED',
  'POWER_OF_ATTORNEY',
  'INE',
  'PASSPORT',
  'TAX_STATUS_CERTIFICATE',
  'CURP',
  'PROPERTY_TAX',
  'CADASTRAL_PLAN',
  'PROOF_OF_ADDRESS',
];

const _idDocument = {
  'INE': (type: 'INE', issuer: 'Instituto Nacional Electoral'),
  'PASSPORT': (type: 'PASAPORTE', issuer: 'Secretaría de Relaciones Exteriores'),
};

String _plain(String s) => s.toUpperCase().replaceAll('Á', 'A').replaceAll('É', 'E').replaceAll('Í', 'I').replaceAll('Ó', 'O').replaceAll('Ú', 'U');

/// Qué identificación se subió en el espacio de la INE, según lo que leyó la IA (Control de Cambios #9).
String? identificationKind(String? raw) {
  final text = _plain(raw ?? '');
  if (text.trim().isEmpty) return null;
  if (RegExp(r'(^|[^A-Z])(INE|IFE)([^A-Z]|$)|CREDENCIAL PARA VOTAR|ELECTORAL').hasMatch(text)) return 'INE';
  if (text.contains('PASAPORTE') || text.contains('PASSPORT')) return 'PASAPORTE';
  if (text.contains('RESIDENTE') || text.contains('FM2') || text.contains('FM3')) return 'FM2_RESIDENTE';
  if (text.contains('CEDULA PROFESIONAL')) return 'CEDULA_PROFESIONAL';
  return 'OTRA';
}

bool _isMexico(String country) => RegExp(r'^(MEXICO|ESTADOS UNIDOS MEXICANOS|MEX)$').hasMatch(_plain(country).trim());

DetectedData buildDetectedData(List<ExpedienteObservation> observations, String? ownerParticipantId) {
  final result = DetectedData();
  final ineHolders = <String, Detected>{};
  // Tipo y país de la identificación que se subió en el espacio de la INE, por participante.
  final idKinds = <String, String?>{};
  final idCountries = <String, Detected>{};
  for (final o in observations) {
    final participantId = o.participantId ?? ownerParticipantId;
    if (o.documentType != 'INE' || participantId == null || o.value.trim().isEmpty) continue;
    if (o.fieldName == 'identificationType') idKinds[participantId] = identificationKind(o.value);
    if (o.fieldName == 'issuingCountry') {
      idCountries[participantId] = Detected(value: o.value.trim(), source: documentTypeLabel(o.documentType), confirmed: o.confirmed);
    }
  }
  int rank(String t) => _priority.contains(t) ? _priority.indexOf(t) : _priority.length;
  final sorted = [...observations]..sort((a, b) {
      final byConfirmed = (b.confirmed ? 1 : 0) - (a.confirmed ? 1 : 0);
      return byConfirmed != 0 ? byConfirmed : rank(a.documentType) - rank(b.documentType);
    });

  for (final o in sorted) {
    if (o.value.trim().isEmpty) continue;
    for (final target in [?_mapping[o.documentType]?[o.fieldName], ...?_alsoFeeds[o.documentType]?[o.fieldName]]) {
      _apply(result, ineHolders, idKinds, idCountries, ownerParticipantId, o, target);
    }
  }
  // La credencial del INE solo se expide a ciudadanos mexicanos: si ningún documento dice la
  // nacionalidad (un pasaporte sí la trae y tiene prioridad), se deduce de la INE.
  ineHolders.forEach((participantId, ine) {
    result.participants.putIfAbsent(participantId, () => {}).putIfAbsent('nationality', () => Detected(value: 'Mexicana', source: ine.source, confirmed: ine.confirmed));
  });
  // Una identificación de otro tipo emitida por México (p. ej. pasaporte mexicano) también la indica.
  idCountries.forEach((participantId, country) {
    if (!_isMexico(country.value)) return;
    result.participants.putIfAbsent(participantId, () => {}).putIfAbsent('nationality', () => Detected(value: 'Mexicana', source: country.source, confirmed: country.confirmed));
  });
  return result;
}

void _apply(
  DetectedData result,
  Map<String, Detected> ineHolders,
  Map<String, String?> idKinds,
  Map<String, Detected> idCountries,
  String? ownerParticipantId,
  ExpedienteObservation o,
  _Target target,
) {
  var value = o.value.trim();
  if (target.date) {
    final date = normalizeDate(value);
    if (date == null) return;
    value = date;
  }
  if (target.kind == 'area') {
    final area = normalizeArea(value);
    if (area == null) return;
    value = area;
  }
  final detected = Detected(value: value, source: documentTypeLabel(o.documentType), confirmed: o.confirmed);
  if (target.kind == 'legal') {
    result.legal.putIfAbsent(target.key, () => detected);
  } else if (target.kind == 'area') {
    if (target.key == 'landAreaM2') result.landAreaM2 ??= detected;
    if (target.key == 'builtAreaM2') result.builtAreaM2 ??= detected;
  } else {
    // Documentos sin titular propio (p. ej. la constancia fiscal de la empresa) son del propietario.
    final participantId = o.participantId ?? ownerParticipantId;
    if (participantId == null) return;
    final bucket = result.participants.putIfAbsent(participantId, () => {});
    if (bucket.containsKey(target.key)) return;
    bucket[target.key] = detected;
    final kind = o.documentType == 'INE' ? (idKinds[participantId] ?? 'INE') : null;
    final id = kind != null && kind != 'INE' ? null : _idDocument[o.documentType];
    if (target.key == 'idDocumentNumber' && id != null) {
      bucket.putIfAbsent('idDocumentType', () => Detected(value: id.type, source: detected.source, confirmed: detected.confirmed));
      bucket.putIfAbsent('idDocumentIssuer', () => Detected(value: id.issuer, source: detected.source, confirmed: detected.confirmed));
      if (o.documentType == 'INE') ineHolders[participantId] = detected;
    } else if (target.key == 'idDocumentNumber' && kind != null && kind != 'OTRA') {
      // Otra identificación oficial en el espacio de la INE: no implica nacionalidad mexicana.
      bucket.putIfAbsent('idDocumentType', () => Detected(value: kind, source: detected.source, confirmed: detected.confirmed));
      final country = idCountries[participantId];
      if (country != null) {
        bucket.putIfAbsent('idDocumentIssuer', () => Detected(value: 'Gobierno de ${country.value}', source: country.source, confirmed: country.confirmed));
      }
    }
  }
}

String legalValue(Json legal, String key) {
  final [section, field] = key.split('.');
  final value = (legal[section] as Map?)?[field];
  return value == null ? '' : '$value';
}
