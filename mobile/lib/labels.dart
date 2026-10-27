// Etiquetas en español para todo valor que llega del backend como código
// (mismo contenido que frontend/src/lib/labels.ts, status-labels.ts,
// crm/labels.ts y document-type-labels.ts). Ninguna pantalla muestra el código crudo.

import 'package:intl/intl.dart';

import 'ui/tone.dart';

String label(Map<String, String> map, String? value, [String fallback = '—']) {
  if (value == null || value.isEmpty) return fallback;
  return map[value] ?? value;
}

const personTypeLabels = {'FISICA': 'Persona física', 'MORAL': 'Persona moral (empresa)'};

const signerCharacterLabels = {
  'PROPIETARIO': 'Firma el propietario',
  'COPROPIETARIO': 'Firman los copropietarios',
  'APODERADO': 'Firma un apoderado',
  'REPRESENTANTE_LEGAL': 'Firma el representante legal',
};

const accreditationLabels = {'ESCRITURA_PUBLICA': 'Escritura pública', 'CONTRATO_PRIVADO': 'Contrato privado ratificado'};

const propertyTypeLabels = {
  'HOUSING': 'Casa',
  'DEPARTMENT': 'Departamento',
  'RESIDENTIAL_LAND': 'Terreno para casa habitación',
  'COMMERCIAL': 'Comercial',
};

/// Control de Cambios #24.
const operationLabels = {'SALE': 'Venta', 'RENT': 'Renta'};

const legalStatusLabels = {
  'LIBRE_GRAVAMEN': 'Libre de gravamen',
  'CON_GRAVAMEN': 'Con gravamen',
  'EN_REVISION': 'Por confirmar con el certificado de libertad de gravamen',
};

const participantRoleLabels = {
  'OWNER': 'Propietario principal',
  'CO_OWNER': 'Copropietario',
  'ATTORNEY': 'Apoderado',
  'LEGAL_REPRESENTATIVE': 'Representante legal',
};

const companyOwnerLabel = 'Empresa titular';

const civilStatusLabels = {
  'SOLTERO': 'Soltero(a)',
  'CASADO': 'Casado(a)',
  'UNION_LIBRE': 'Unión libre',
  'DIVORCIADO': 'Divorciado(a)',
  'VIUDO': 'Viudo(a)',
};

const maritalRegimeLabels = {'SOCIEDAD_CONYUGAL': 'Sociedad conyugal (bienes mancomunados)', 'SEPARACION_DE_BIENES': 'Separación de bienes'};

const idDocumentLabels = {
  'INE': 'Credencial para votar (INE)',
  'PASAPORTE': 'Pasaporte',
  'CEDULA_PROFESIONAL': 'Cédula profesional',
  'FM2_RESIDENTE': 'Tarjeta de residente',
};

const documentStatusLabels = {
  'PENDING': 'Sin cargar',
  'UPLOADED': 'Cargado, procesando',
  'READY_FOR_REVIEW': 'Por revisar',
  'ACCEPTED': 'Aceptado',
  'RETURNED': 'Devuelto al cliente',
  'REJECTED': 'Rechazado',
  'REPLACED': 'Reemplazado',
  'NOT_APPLICABLE': 'No aplica',
};

const documentStatusTone = {
  'PENDING': Tone.neutral,
  'UPLOADED': Tone.info,
  'READY_FOR_REVIEW': Tone.info,
  'ACCEPTED': Tone.success,
  'RETURNED': Tone.warning,
  'REJECTED': Tone.danger,
  'REPLACED': Tone.neutral,
  'NOT_APPLICABLE': Tone.neutral,
};

const returnReasonLabels = {
  'BLURRY_IMAGE': 'Imagen borrosa',
  'INCOMPLETE_DOCUMENT': 'Documento incompleto',
  'EXPIRED_DOCUMENT': 'Documento vencido',
  'ILLEGIBLE_INFORMATION': 'Información ilegible',
  'WRONG_DOCUMENT': 'No es el documento solicitado',
  'MISSING_PAGE': 'Falta una página',
  'OTHER': 'Otro (explicar en el comentario)',
};

const contractStatusLabels = {
  'DRAFT_INCOMPLETE': 'Borrador incompleto (no se puede firmar)',
  'GENERATED': 'Enviado a firma',
  'PARTIALLY_SIGNED': 'Firmado parcialmente',
  'SIGNED': 'Firmado por todas las partes',
  'DELIVERED': 'Firmado y entregado al cliente',
  'SUPERSEDED': 'Sin efecto (reemplazado)',
};

const contractStatusTone = {
  'DRAFT_INCOMPLETE': Tone.warning,
  'GENERATED': Tone.gold,
  'PARTIALLY_SIGNED': Tone.info,
  'SIGNED': Tone.success,
  'DELIVERED': Tone.success,
  'SUPERSEDED': Tone.neutral,
};

const roleLabels = {
  'ADMINISTRATOR': 'Administrador',
  'DIRECTOR': 'Director',
  'ADVISOR': 'Asesor',
  'DOCUMENT_REVIEWER': 'Revisor documental',
};

const complianceLabels = {
  'PRIVACY_NOTICE_PRESENTED': 'Aviso de privacidad vigente',
  'CONTRACT_TEMPLATE_CORRECT': 'Modelo de contrato vigente',
  'MAIN_CONSENT_RECORDED': 'El cliente aceptó el aviso de privacidad',
  'REQUIRED_DOCUMENTS_ACCEPTED': 'Documentos obligatorios aceptados',
  'RECEPTION_SIGNED': 'Recepción de documentos firmada',
  'NO_UNRESOLVED_DATA_CONFLICTS': 'Sin diferencias sin resolver entre documentos',
};

const closingStatusLabels = {'OPEN': 'Abierto', 'IN_PROGRESS': 'En curso', 'COMPLETED': 'Completado'};

const notificationStatusLabels = {
  'QUEUED': 'En cola',
  'SENT': 'Enviado',
  'RETRYING': 'Falló, se reintentará',
  'FAILED': 'No se pudo entregar',
  'SKIPPED': 'No enviado (sin correo)',
};

const extractedFieldLabels = {
  'fullName': 'Nombre completo',
  'curp': 'CURP',
  'electorKey': 'Clave de elector',
  'address': 'Domicilio',
  'expirationYear': 'Vigencia',
  'passportNumber': 'Número de pasaporte',
  'birthDate': 'Fecha de nacimiento',
  'rfc': 'RFC',
  'taxRegime': 'Régimen fiscal',
  'ownerFullName': 'Propietario',
  'propertyAddress': 'Domicilio del inmueble',
  'publicRegistryFolio': 'Folio real / datos registrales',
  'deedNumber': 'Número de escritura',
  'notaryNumber': 'Número de notaría',
  'landArea': 'Superficie de terreno',
  'builtArea': 'Superficie de construcción',
  'issueDate': 'Fecha de emisión',
  'hasLiens': 'Gravámenes',
  'cadastralKey': 'Clave catastral',
  'grantorFullName': 'Poderdante',
  'attorneyFullName': 'Apoderado',
  'regimeRegistrationFolio': 'Folio del régimen',
  'spouseFullName': 'Cónyuge',
  'marriageDate': 'Fecha de matrimonio',
  'maritalRegime': 'Régimen matrimonial',
  'buyerFullName': 'Comprador',
  'sellerFullName': 'Vendedor',
  'contractDate': 'Fecha del contrato',
  'companyName': 'Razón social',
  'instrumentNumber': 'Número de instrumento',
  'mercantileFolio': 'Folio mercantil',
  'nationality': 'Nacionalidad',
  'identificationType': 'Tipo de identificación',
  'documentNumber': 'Número de la identificación',
  'issuingCountry': 'País que la emite',
  'deedDate': 'Fecha de la escritura',
  'notaryName': 'Notario',
  'notaryPlace': 'Lugar de la notaría',
  'notaryTitle': 'Notario o corredor',
  'registryDate': 'Fecha de inscripción',
  'ratificationDate': 'Fecha de ratificación',
  'instrumentDate': 'Fecha del instrumento',
  'companyType': 'Tipo de sociedad',
  'commerceRegistryPlace': 'Registro Público de Comercio',
  'expirationDate': 'Fecha de vencimiento',
  'paidPeriod': 'Periodo pagado',
  'serviceNumber': 'Número de servicio',
  'registryPlace': 'Lugar del Registro Civil',
  'actNumber': 'Número de acta',
  'legalRepresentativeFullName': 'Representante legal',
  'appraisalDate': 'Fecha del avalúo',
  'appraisedValue': 'Valor del avalúo',
  'appraiserName': 'Valuador',
  'permittedUse': 'Uso de suelo',
  'issuingAuthority': 'Autoridad que emite',
  'deceasedFullName': 'Autor de la sucesión',
  'heirFullNames': 'Herederos',
  'executorFullName': 'Albacea',
  'courtOrNotary': 'Juzgado o notaría',
  'caseNumber': 'Número de expediente',
  'resolutionDate': 'Fecha de resolución',
  'testatorFullName': 'Testador',
  'debtorFullName': 'Deudor',
  'creditorName': 'Acreedor',
  'amount': 'Monto',
  'isCancelled': '¿Cancelada?',
  'landlordFullName': 'Arrendador',
  'tenantFullName': 'Arrendatario',
  'rentAmount': 'Renta',
  'startDate': 'Fecha de inicio',
  'endDate': 'Fecha de terminación',
  'personNames': 'Personas',
  'addresses': 'Domicilios',
  'dates': 'Fechas',
  'referenceNumbers': 'Números de referencia',
  'summary': 'De qué trata',
  'unitNumber': 'Unidad privativa',
  'undividedPercentage': 'Porcentaje de indiviso',
  'undividedLandArea': 'Superficie de terreno de indivisos',
  'addressProofKind': 'Tipo de comprobante',
};

String extractedFieldLabel(String fieldName) =>
    fieldName.startsWith('extra.') ? fieldName.substring('extra.'.length) : (extractedFieldLabels[fieldName] ?? fieldName);

const pipelineStatusLabels = {
  'UPLOADED': 'Subido',
  'PROCESSING': 'Procesando',
  'EXTRACTION_COMPLETE': 'Extracción completa',
  'EXTRACTION_PARTIAL': 'Extracción parcial',
  'REQUIRES_REVIEW': 'Requiere revisión',
  'ACCEPTED': 'Aceptado',
  'ERROR': 'Error',
};

const pipelineStatusTone = {
  'UPLOADED': Tone.info,
  'PROCESSING': Tone.info,
  'EXTRACTION_COMPLETE': Tone.success,
  'EXTRACTION_PARTIAL': Tone.info,
  'REQUIRES_REVIEW': Tone.warning,
  'ACCEPTED': Tone.success,
  'ERROR': Tone.danger,
};

const documentTypeLabels = {
  'INE': 'Identificación oficial (INE)',
  'PASSPORT': 'Pasaporte',
  'CURP': 'CURP',
  'TAX_STATUS_CERTIFICATE': 'Constancia de Situación Fiscal',
  'DEED': 'Escritura completa (testimonios)',
  'PROOF_OF_ADDRESS': 'Comprobante de domicilio',
  'LIEN_CERTIFICATE': 'Certificado de libertad de gravamen',
  'PROPERTY_TAX': 'Predial',
  'POWER_OF_ATTORNEY': 'Poder notarial',
  'CONDOMINIUM_REGIME': 'Régimen de condominio',
  'WATER_RECEIPT': 'Recibo de agua',
  'ELECTRICITY_RECEIPT': 'Recibo de CFE',
  'CADASTRAL_PLAN': 'Plano catastral',
  'APPRAISAL': 'Avalúo',
  'LAND_USE': 'Uso de suelo',
  'SUCCESSION': 'Sucesión',
  'ADJUDICATION': 'Adjudicación',
  'WILL': 'Testamento',
  'MORTGAGE': 'Hipoteca',
  'LEASE_AGREEMENT': 'Contrato de arrendamiento',
  'RPP_REGISTRATION_SLIP': 'Boleta de inscripción al RPP',
  'MARRIAGE_CERTIFICATE': 'Acta de matrimonio',
  'PRIVATE_CONTRACT': 'Contrato privado de compraventa ratificado',
  'INCORPORATION_DEED': 'Acta constitutiva',
  'PRIVACY_NOTICE': 'Aviso de privacidad firmado',
  'SIGNED_CONTRACT': 'Contrato firmado',
  'OTHER': 'Otro',
};

String documentTypeLabel(String type) => documentTypeLabels[type] ?? type;

// --- Estatus del expediente ---------------------------------------------------

const backendStatusLabels = {
  'DRAFT': 'Nuevo (sin liga para el cliente)',
  'WAITING_PRIVACY': 'Esperando aviso de privacidad',
  'WAITING_DOCUMENTS': 'Esperando documentos del cliente',
  'DOCUMENTS_RECEIVED': 'Documentos recibidos',
  'UNDER_REVIEW': 'En revisión',
  'CORRECTIONS_REQUESTED': 'Correcciones pendientes',
  'DOCUMENTS_APPROVED': 'Documentación aprobada',
  'RECEPTION_SIGNED': 'Recepción firmada',
  'CONTRACT_PREPARATION': 'Preparando contrato',
  'READY_FOR_SIGNATURE': 'Contrato en firma',
  'CONTRACT_SIGNED': 'Contrato firmado',
  'PROPERTY_ACCEPTED': 'Inmueble aceptado',
  'PROPERTY_REJECTED': 'Inmueble rechazado',
  'CLOSED': 'Cerrado',
};

// Ámbar = falta algo del cliente; azul = en revisión o en proceso interno;
// verde = completado; dorado = esperando firmas.
const backendStatusTone = {
  'DRAFT': Tone.neutral,
  'WAITING_PRIVACY': Tone.warning,
  'WAITING_DOCUMENTS': Tone.warning,
  'DOCUMENTS_RECEIVED': Tone.info,
  'UNDER_REVIEW': Tone.info,
  'CORRECTIONS_REQUESTED': Tone.warning,
  'DOCUMENTS_APPROVED': Tone.success,
  'RECEPTION_SIGNED': Tone.success,
  'CONTRACT_PREPARATION': Tone.info,
  'READY_FOR_SIGNATURE': Tone.gold,
  'CONTRACT_SIGNED': Tone.success,
  'PROPERTY_ACCEPTED': Tone.success,
  'PROPERTY_REJECTED': Tone.danger,
  'CLOSED': Tone.obsessed,
};

/// Qué significa cada estatus, qué sigue y quién debe hacerlo.
const backendStatusNextStep = {
  'DRAFT': (meaning: 'El expediente se creó pero el cliente todavía no tiene acceso.', next: 'Generar la liga del cliente y compartírsela.', who: 'Asesor'),
  'WAITING_PRIVACY': (
    meaning: 'El cliente tiene su liga pero no ha aceptado el aviso de privacidad.',
    next: 'Que el cliente abra su liga, acepte y firme el aviso de privacidad.',
    who: 'Cliente'
  ),
  'WAITING_DOCUMENTS': (
    meaning: 'El cliente aceptó el aviso de privacidad y está cargando sus documentos.',
    next: 'Que el cliente cargue todos los documentos obligatorios y los envíe.',
    who: 'Cliente'
  ),
  'DOCUMENTS_RECEIVED': (
    meaning: 'El cliente envió su documentación.',
    next: 'Revisar cada documento: aceptarlo, devolverlo con el motivo o marcarlo como "No aplica".',
    who: 'Revisor documental'
  ),
  'UNDER_REVIEW': (meaning: 'La documentación se está revisando.', next: 'Terminar de revisar los documentos pendientes.', who: 'Revisor documental'),
  'CORRECTIONS_REQUESTED': (
    meaning: 'Hay documentos devueltos, rechazados o nuevos documentos obligatorios sin cargar.',
    next: 'Que el cliente cargue las correcciones desde su liga (ve el motivo de cada una); después, revisarlas.',
    who: 'Cliente, luego revisor'
  ),
  'DOCUMENTS_APPROVED': (meaning: 'Todos los documentos obligatorios están aceptados.', next: 'Firmar la recepción de documentos.', who: 'Administrador o director'),
  'RECEPTION_SIGNED': (
    meaning: 'La recepción de documentos está firmada.',
    next: 'Capturar los datos del contrato y generarlo para firma.',
    who: 'Asesor (datos) y administrador o director (generar)'
  ),
  'CONTRACT_PREPARATION': (
    meaning: 'Se está preparando el contrato (hay un borrador o se corrigieron datos).',
    next: 'Completar los datos faltantes y generar el contrato para firma.',
    who: 'Asesor y administrador o director'
  ),
  'READY_FOR_SIGNATURE': (
    meaning: 'El contrato se envió a firma.',
    next: 'Que cada firmante del cliente firme con su liga personal y que la intermediaria firme desde el sistema.',
    who: 'Cliente e intermediaria'
  ),
  'CONTRACT_SIGNED': (
    meaning: 'El contrato quedó firmado por todas las partes, con su constancia de firmas.',
    next: 'Entregar un tanto firmado al cliente y decidir sobre el inmueble.',
    who: 'Asesor (entrega) y administrador o director (decisión)'
  ),
  'PROPERTY_ACCEPTED': (meaning: 'El inmueble fue aceptado.', next: 'Completar las tareas pendientes del seguimiento posterior a la firma.', who: 'Asesor'),
  'PROPERTY_REJECTED': (meaning: 'El inmueble fue rechazado.', next: 'Nada más en este expediente.', who: '—'),
  'CLOSED': (meaning: 'El expediente está cerrado.', next: 'Nada más en este expediente.', who: '—'),
};

// --- CRM ----------------------------------------------------------------------

// Control de Cambios #30: pipeline de 10 etapas.
const stageOrder = ['NEW', 'CONTACTED', 'QUALIFIED', 'APPOINTMENT', 'VISITED', 'PROPOSAL', 'NEGOTIATION', 'OFFER_ACCEPTED', 'WON', 'LOST'];

const stageLabels = {
  'NEW': 'Nuevo',
  'CONTACTED': 'Primer contacto',
  'QUALIFIED': 'Calificado',
  'APPOINTMENT': 'Cita agendada',
  'VISITED': 'Visita realizada',
  'PROPOSAL': 'Propuesta enviada',
  'NEGOTIATION': 'Negociación',
  'OFFER_ACCEPTED': 'Oferta aceptada',
  'WON': 'Cierre',
  'LOST': 'Perdido',
};

const stageTones = {
  'NEW': Tone.warning,
  'CONTACTED': Tone.info,
  'QUALIFIED': Tone.info,
  'APPOINTMENT': Tone.gold,
  'VISITED': Tone.manual,
  'PROPOSAL': Tone.manual,
  'NEGOTIATION': Tone.obsessed,
  'OFFER_ACCEPTED': Tone.success,
  'WON': Tone.success,
  'LOST': Tone.danger,
};

/// Canal general; la fuente precisa viene del catálogo (Control de Cambios #29, sourceLabel).
const sourceLabels = {
  'WHATSAPP_BOT': 'WhatsApp (asistente)',
  'WHATSAPP': 'WhatsApp',
  'PORTAL': 'Portal inmobiliario',
  'SOCIAL': 'Redes sociales',
  'CAMPAIGN': 'Campaña',
  'WEBSITE': 'Sitio web / búsqueda',
  'OUTDOOR': 'Publicidad exterior',
  'REFERRAL': 'Referido',
  'WALK_IN': 'Oficina',
  'EVENT': 'Evento',
  'PHONE': 'Llamada',
  'REACTIVATION': 'Reactivación',
  'OTHER': 'Otra',
};

const temperatureLabels = {'COLD': 'Frío', 'WARM': 'Tibio', 'HOT': 'Caliente'};
const temperatureTones = {'COLD': Tone.info, 'WARM': Tone.warning, 'HOT': Tone.danger};
const temperatureDots = {'COLD': '🔵', 'WARM': '🟡', 'HOT': '🔴'};

const lostReasonLabels = {
  'PRICE': 'Precio',
  'ZONE': 'Zona',
  'TIMING': 'Tiempo',
  'COMPETITION': 'Competencia',
  'NO_RESPONSE': 'Dejó de responder',
  'OTHER': 'Otro',
};

const taskTypeLabels = {
  'CALL': '📞 Llamada',
  'WHATSAPP': '💬 Mensaje WhatsApp',
  'EMAIL': '📧 Correo electrónico',
  'VISIT': '🏠 Visita al inmueble',
  'OFFICE_MEETING': '🏢 Reunión en oficina',
  'VIDEO_CALL': '🎥 Videollamada',
  'PROPOSAL': '📄 Envío de propuesta',
  'OFFER_FOLLOW_UP': '🤝 Seguimiento de oferta',
  'NOTE': '📝 Nota interna',
  'OTHER': '⚙️ Otro',
};

const taskResultLabels = {
  'CONTACTED': 'Contactado',
  'NO_ANSWER': 'No contestó',
  'RESCHEDULED': 'Reagendó',
  'REJECTED': 'Rechazó',
  'INTERESTED': 'Interesado',
  'ADVANCED': 'Avanzó',
};

const reminderLabels = {'': 'Sin recordatorio', '15': '15 minutos antes', '60': '1 hora antes', '1440': '1 día antes'};

const interestLabels = {
  'BUY': 'Comprar',
  'RENT': 'Rentar',
  'SELL': 'Vender su propiedad',
  'LEASE_OUT': 'Poner en renta su propiedad',
  'INVEST': 'Invertir',
  'OTHER': 'Información',
};

const assignmentLabels = {
  'PROPERTY_OWNER': 'Asesor de la propiedad',
  'CONTINUITY': 'Continuidad',
  'ROULETTE': 'Ronda',
  'GUARD': 'Guardia',
  'MANUAL': 'Manual',
};

const assignmentHints = {
  'PROPERTY_OWNER': 'Es el asesor responsable de la propiedad en 21Online',
  'CONTINUITY': 'El cliente ya tenía un lead activo con este asesor',
  'ROULETTE': 'Se repartió en la ronda entre los asesores en turno',
  'GUARD': 'El asesor de la propiedad no estaba disponible y se canalizó a la guardia',
  'MANUAL': 'Lo asignó una persona desde el panel',
};

const assignmentStatusLabels = {
  'ASSIGNED': 'Asignado',
  'PENDING_OWNER': 'Espera al asesor de la propiedad',
  'PENDING_SCHEDULE': 'Pendiente por horario',
  'UNASSIGNED': 'Sin asignar',
};

const assignmentStatusTones = {'ASSIGNED': Tone.success, 'PENDING_OWNER': Tone.info, 'PENDING_SCHEDULE': Tone.warning, 'UNASSIGNED': Tone.danger};

const advisorStatusLabels = {
  'AVAILABLE': 'Disponible ahora',
  'OFF_SHIFT': 'Fuera de horario',
  'ABSENT': 'Ausente',
  'PAUSED': 'En pausa',
  'LIMIT_REACHED': 'Límite alcanzado',
  'NO_CALENDAR': 'Sin calendario',
};

const advisorStatusTones = {
  'AVAILABLE': Tone.success,
  'OFF_SHIFT': Tone.neutral,
  'ABSENT': Tone.manual,
  'PAUSED': Tone.warning,
  'LIMIT_REACHED': Tone.info,
  'NO_CALENDAR': Tone.danger,
};

const shiftKindLabels = {'REGULAR': 'Jornada', 'GUARD': 'Guardia'};

const absenceKindLabels = {'VACATION': 'Vacaciones', 'PERMISSION': 'Permiso', 'REST': 'Descanso', 'UNAVAILABLE': 'No disponible'};

const controlLabels = {'AI': 'IA activa', 'HUMAN': 'Atención humana', 'PAUSED': 'IA en pausa'};
const controlTones = {'AI': Tone.success, 'HUMAN': Tone.info, 'PAUSED': Tone.neutral};

const businessTimezoneLabel = 'Hora de Ciudad de México (America/Mexico_City)';

/// Días ISO (1 = lunes).
const dayNames = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
const dayShort = ['', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
const _monthShort = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
const _monthLong = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

// --- Fechas de negocio como texto (sin zona del dispositivo) ------------------

DateTime _utcDate(String date) {
  final p = date.substring(0, 10).split('-').map(int.parse).toList();
  return DateTime.utc(p[0], p[1], p[2]);
}

String _isoDate(DateTime d) => '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

/// "2026-10-12" + n días.
String addDays(String date, int n) => _isoDate(_utcDate(date).add(Duration(days: n)));

/// Día ISO de la semana (1 = lunes … 7 = domingo).
int isoDay(String date) => _utcDate(date).weekday;

/// Primer día del mes de la fecha, desplazado n meses.
String addMonths(String date, int n) {
  final d = _utcDate(date);
  return _isoDate(DateTime.utc(d.year, d.month + n, 1));
}

/// "octubre de 2026".
String monthTitle(String date) {
  final d = _utcDate(date);
  return '${_monthLong[d.month - 1]} de ${d.year}';
}

String weekStart(String date) => addDays(date, 1 - isoDay(date));

int daysBetween(String from, String to) => _utcDate(to).difference(_utcDate(from)).inDays;

/// Minutos desde la medianoche de "YYYY-MM-DDTHH:mm[:ss]".
int minutesOf(String local) => int.parse(local.substring(11, 13)) * 60 + int.parse(local.substring(14, 16));

String hhmm(String local) => local.length >= 16 ? local.substring(11, 16) : local;

/// "lun 12 oct".
String formatDayLabel(String date) {
  final d = _utcDate(date);
  return '${dayShort[d.weekday].toLowerCase()} ${d.day} ${_monthShort[d.month - 1]}';
}

/// "lun 12 oct, 14:00".
String formatLocal(String local) => '${formatDayLabel(local)}, ${hhmm(local)}';

/// Hoy en Ciudad de México (UTC-6, sin horario de verano desde 2022).
String mxToday() => _isoDate(DateTime.now().toUtc().subtract(const Duration(hours: 6)));

// --- Instantes del servidor ---------------------------------------------------

DateTime? _parse(String? iso) => iso == null || iso.isEmpty ? null : DateTime.tryParse(iso);

/// "15 de marzo de 2026".
String formatDate(String? value) {
  if (value == null || value.isEmpty) return '—';
  final d = value.length == 10 ? _parse('${value}T12:00:00') : _parse(value)?.toLocal();
  if (d == null) return value;
  return DateFormat("d 'de' MMMM 'de' y", 'es_MX').format(d);
}

/// "15 mar 2026, 14:05".
String formatDateTime(String? value) {
  final d = _parse(value)?.toLocal();
  if (d == null) return '—';
  return DateFormat('d MMM y, HH:mm', 'es_MX').format(d);
}

/// "15 mar, 14:05" (CRM).
String formatShortDateTime(String iso) {
  final d = _parse(iso)?.toLocal();
  return d == null ? iso : DateFormat('d MMM, HH:mm', 'es_MX').format(d);
}

/// Un instante del servidor mostrado SIEMPRE en hora de Ciudad de México.
String formatInstantMx(String iso) {
  final d = _parse(iso)?.toUtc().subtract(const Duration(hours: 6));
  return d == null ? iso : DateFormat('EEE d MMM, HH:mm', 'es_MX').format(d);
}

String formatTime(String iso) {
  final d = _parse(iso)?.toLocal();
  return d == null ? '' : DateFormat('HH:mm').format(d);
}

/// "hace 5 min", "hace 3 h", "hace 2 días".
String timeAgo(String iso) {
  final d = _parse(iso);
  if (d == null) return '';
  final minutes = DateTime.now().difference(d).inMinutes.clamp(0, 1 << 31);
  if (minutes < 1) return 'ahora';
  if (minutes < 60) return 'hace $minutes min';
  final hours = (minutes / 60).round();
  if (hours < 24) return 'hace $hours h';
  final days = (hours / 24).round();
  return 'hace $days día${days == 1 ? '' : 's'}';
}

final _mxn0 = NumberFormat.currency(locale: 'es_MX', symbol: r'$', decimalDigits: 0);
final _mxn2 = NumberFormat.currency(locale: 'es_MX', symbol: r'$', decimalDigits: 2);

String formatPrice(num amount) => _mxn0.format(amount);

String formatMoney(Object? value) {
  if (value == null || value == '') return '—';
  final n = value is num ? value : num.tryParse('$value');
  return n == null ? '$value' : _mxn2.format(n);
}

String initials(String name) =>
    name.split(' ').where((p) => p.isNotEmpty).take(2).map((p) => p[0].toUpperCase()).join();

String whatsappLink(String phone) => 'https://wa.me/${phone.replaceAll(RegExp(r'\D'), '')}';

String plural(int n, String singular, [String? pluralForm]) => n == 1 ? singular : (pluralForm ?? '${singular}s');

/// Comprobantes de domicilio aceptados (Control de Cambios #13).
const addressProofKindLabels = {
  'ELECTRICITY': 'Recibo de luz / energía eléctrica (CFE u otro proveedor)',
  'BANK_STATEMENT': 'Estado de cuenta bancario',
  'TELMEX': 'Recibo de Telmex',
  'PHONE': 'Recibo de telefonía (cualquier operadora)',
  'INTERNET': 'Recibo de internet (cualquier proveedor)',
};

// --- Propiedades (códigos de la API de 21Online; igual que frontend/src/lib/crm/property-labels.ts) ---

const listingOperationLabels = {'venta': 'Venta', 'renta': 'Renta'};
const listingTypeLabels = {'habitacional': 'Habitacional', 'comercial': 'Comercial'};

/// Subtipos que 21Online acepta en México por tipo (igual que PropertyService.SUBTYPES).
const subtypesByType = {
  'habitacional': ['casa', 'casa-duplex', 'casa-en-condominio', 'departamento', 'penthouse', 'cabana', 'terreno', 'huerta', 'quinta', 'rancho', 'hacienda', 'edificio'],
  'comercial': ['local', 'oficinas', 'bodega', 'nave', 'fabrica', 'terreno', 'edificio', 'fraccionamiento', 'escuela', 'inmueble-productivo'],
};

const subtypeLabels = {
  'casa': 'Casa',
  'casa-duplex': 'Casa dúplex',
  'casa-en-condominio': 'Casa en condominio',
  'departamento': 'Departamento',
  'penthouse': 'Penthouse',
  'cabana': 'Cabaña',
  'terreno': 'Terreno',
  'huerta': 'Huerta',
  'quinta': 'Quinta',
  'rancho': 'Rancho',
  'hacienda': 'Hacienda',
  'edificio': 'Edificio',
  'local': 'Local',
  'oficinas': 'Oficinas',
  'oficina': 'Oficina',
  'bodega': 'Bodega',
  'nave': 'Nave industrial',
  'fabrica': 'Fábrica',
  'fraccionamiento': 'Fraccionamiento',
  'escuela': 'Escuela',
  'inmueble-productivo': 'Inmueble productivo',
};

const listingStatusLabels = {
  'enPromocion': 'En promoción',
  'suspendida': 'Suspendida',
  'cancelada': 'Cancelada',
  'conIntencion': 'Con intención',
  'conContratoFirmado': 'Con contrato firmado',
  'cerrada': 'Cerrada',
};

const listingStatusTones = {
  'enPromocion': Tone.success,
  'suspendida': Tone.warning,
  'cancelada': Tone.danger,
  'conIntencion': Tone.info,
  'conContratoFirmado': Tone.gold,
  'cerrada': Tone.obsessed,
};

/// Estatus que se pueden poner desde Genera (el cierre se registra en 21Online).
const editableListingStatuses = ['enPromocion', 'suspendida', 'conIntencion', 'conContratoFirmado', 'cancelada'];

const syncLabels = {'PENDING': 'Pendiente de enviar', 'DONE': 'En 21Online', 'FAILED': 'No se envió', 'NOT_REQUIRED': 'Sin envío'};
const syncTones = {'PENDING': Tone.warning, 'DONE': Tone.success, 'FAILED': Tone.danger, 'NOT_REQUIRED': Tone.neutral};

const contractLabels = {'exclusiva': 'Exclusiva', 'opcion': 'Opción'};

const mexicanStates = [
  'Aguascalientes', 'Baja California', 'Baja California Sur', 'Campeche', 'Chiapas', 'Chihuahua', 'Ciudad de México', 'Coahuila', 'Colima', 'Durango',
  'Estado de México', 'Guanajuato', 'Guerrero', 'Hidalgo', 'Jalisco', 'Michoacán', 'Morelos', 'Nayarit', 'Nuevo León', 'Oaxaca', 'Puebla', 'Querétaro',
  'Quintana Roo', 'San Luis Potosí', 'Sinaloa', 'Sonora', 'Tabasco', 'Tamaulipas', 'Tlaxcala', 'Veracruz', 'Yucatán', 'Zacatecas',
];


String subtypeLabel(String? code) {
  if (code == null || code.isEmpty) return 'Propiedad';
  return subtypeLabels[code] ?? '${code[0].toUpperCase()}${code.substring(1).replaceAll('-', ' ')}';
}

/// Precio según la operación ("/mes" en renta), en la moneda de la propiedad.
String listingPrice(String? operation, double? sale, double? rent, String currency) {
  final amount = operation == 'renta' ? rent : sale;
  if (amount == null) return 'Precio no capturado';
  return '${_mxn0.format(amount)}${currency == 'USD' ? ' USD' : ''}${operation == 'renta' ? ' /mes' : ''}';
}

/// "3 rec. · 2.5 baños · 220 m² const. · 300 m² terreno".
String listingFeatures({int? bedrooms, double? bathrooms, double? builtM2, double? landM2}) {
  final n = numText;
  return [
    if (bedrooms != null && bedrooms > 0) '$bedrooms rec.',
    if (bathrooms != null && bathrooms > 0) '${n(bathrooms)} ${bathrooms == 1 ? 'baño' : 'baños'}',
    if (builtM2 != null && builtM2 > 0) '${n(builtM2)} m² const.',
    if (landM2 != null && landM2 > 0) '${n(landM2)} m² terreno',
  ].join(' · ');
}

/// Número sin ".0" sobrante (5.0 → "5", 2.5 → "2.5").
String numText(num v) => v == v.roundToDouble() ? v.toInt().toString() : v.toString();
