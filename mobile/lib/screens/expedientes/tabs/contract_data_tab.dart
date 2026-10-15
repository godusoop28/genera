import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../api/api.dart' as api;
import '../../../api/client.dart';
import '../../../api/models.dart';
import '../../../detected_data.dart';
import '../../../state/auth.dart';
import '../../../ui/widgets.dart';
import '../expediente_detail_screen.dart';

const _housingChecklist = [
  ('LICENSES', 'Autorizaciones, licencias y permisos de construcción, especificaciones técnicas, materiales, seguridad, uso de suelo y servicios'),
  ('PLANS', 'Planos estructurales, arquitectónicos y de instalaciones (o dictamen estructural)'),
  ('CIVIL_PROTECTION', 'Programa Interno de Protección Civil'),
  ('LIENS', 'Información sobre gravámenes que afecten la propiedad'),
  ('TAXES', 'Condiciones de pago de contribuciones, derechos y servicios'),
];

const _landChecklist = [
  ('LAND_USE', 'Uso de suelo conforme al plan de desarrollo urbano y licencia vigente'),
  ('SERVICES_FEASIBILITY', 'Estudio de factibilidad para la instalación de servicios básicos'),
  ('CONSTRUCTION_RULES', 'Reglamento de construcción del fraccionamiento o condominio (en su caso)'),
  ('CIVIL_PROTECTION', 'Programa Interno de Protección Civil'),
  ('LIENS', 'Información sobre gravámenes que afecten la propiedad'),
  ('TAXES', 'Condiciones de pago de contribuciones, derechos y servicios'),
];

String _text(Object? v) => v == null ? '' : '$v';

/// Datos manuales del contrato y datos legales. Los cambios sin guardar se
/// autoguardan como borrador en el servidor (la misma clave que usa la web),
/// así sobreviven a cerrar la app o a una caída de red.
class ContractDataTab extends StatefulWidget {
  const ContractDataTab({super.key, required this.ctx});
  final ExpedienteCtx ctx;

  @override
  State<ContractDataTab> createState() => _ContractDataTabState();
}

class _ContractDataTabState extends State<ContractDataTab> {
  ContractReadiness? readiness;
  Json? data;
  Json? legal;
  String reason = '';
  bool saving = false;
  DetectedData? detected;
  String? savedSnapshot;
  String? loadError;
  String draftStatus = 'idle';
  Timer? _draftTimer;

  ExpedienteResponse get e => widget.ctx.expediente;
  String get draftKey => 'contract-data:${e.id}';

  @override
  void initState() {
    super.initState();
    loadSide();
    load();
  }

  @override
  void dispose() {
    _draftTimer?.cancel();
    super.dispose();
  }

  void loadSide() {
    final ownerId = widget.ctx.participants.where((p) => p.role == 'OWNER').firstOrNull?.id;
    api.getExpedienteExtractedFields(e.id).then((obs) => mounted ? setState(() => detected = buildDetectedData(obs, ownerId)) : null).catchError((_) => null);
    api.getContractReadiness(e.id).then((r) => mounted ? setState(() => readiness = r) : null).catchError((_) => null);
  }

  Future<void> load() async {
    try {
      final results = await Future.wait([api.getClientData(e.id), api.getLegalDetails(e.id), api.loadDraft(draftKey).catchError((_) => null)]);
      final serverData = results[0] as Json;
      final serverLegal = results[1] as Json;
      final payload = results[2] as String?;
      savedSnapshot = jsonEncode({'data': serverData, 'legal': serverLegal});
      Json? draft;
      try {
        draft = payload == null ? null : Json.from(jsonDecode(payload) as Map);
      } catch (_) {}
      final differs = draft != null && (jsonEncode({'data': draft['data'], 'legal': draft['legal']}) != savedSnapshot || '${draft['reason'] ?? ''}'.trim().isNotEmpty);
      if (!mounted) return;
      setState(() {
        data = differs ? Json.from(draft!['data'] as Map) : serverData;
        legal = differs ? Json.from(draft!['legal'] as Map) : serverLegal;
        if (differs) reason = '${draft!['reason'] ?? ''}';
      });
      if (differs) showToast(context, 'Se recuperaron cambios que no habías guardado. Revísalos y guarda.');
    } catch (err) {
      if (mounted) setState(() => loadError = errorText(err));
    }
  }

  void changed(VoidCallback fn) {
    setState(fn);
    _draftTimer?.cancel();
    _draftTimer = Timer(const Duration(seconds: 1), saveDraft);
  }

  Future<void> saveDraft() async {
    if (data == null || legal == null || savedSnapshot == null) return;
    final same = jsonEncode({'data': data, 'legal': legal}) == savedSnapshot && reason.trim().isEmpty;
    setState(() => draftStatus = 'saving');
    try {
      if (same) {
        await api.deleteDraft(draftKey);
        if (mounted) setState(() => draftStatus = 'idle');
      } else {
        await api.saveDraft(draftKey, jsonEncode({'data': data, 'legal': legal, 'reason': reason}));
        if (mounted) setState(() => draftStatus = 'saved');
      }
    } catch (_) {
      if (mounted) setState(() => draftStatus = 'error');
    }
  }

  Json section(String key) => Json.from((legal![key] as Map?) ?? {});
  void setSection(String key, String field, Object? value) => changed(() => legal = {...legal!, key: {...section(key), field: value}});
  void setData(String field, Object? value) => changed(() => data = {...data!, field: value});
  Object? numOrNull(String v) => v.trim().isEmpty ? null : (num.tryParse(v.trim()) ?? v.trim());

  List<MapEntry<String, Detected>> get fillable {
    final d = detected;
    if (d == null || legal == null || data == null) return [];
    return [
      ...d.legal.entries.where((x) => legalValue(legal!, x.key).isEmpty),
      if (d.landAreaM2 != null && _text(data!['landAreaM2']).isEmpty) MapEntry('landAreaM2', d.landAreaM2!),
      if (d.builtAreaM2 != null && _text(data!['builtAreaM2']).isEmpty) MapEntry('builtAreaM2', d.builtAreaM2!),
    ];
  }

  void fillFromDocuments() {
    final d = detected;
    if (d == null) return;
    changed(() {
      var next = Json.from(legal!);
      for (final x in d.legal.entries) {
        if (legalValue(next, x.key).isNotEmpty) continue;
        final [sec, field] = x.key.split('.');
        next = {...next, sec: {...Json.from((next[sec] as Map?) ?? {}), field: x.value.value}};
      }
      legal = next;
      if (d.landAreaM2 != null && _text(data!['landAreaM2']).isEmpty) data = {...data!, 'landAreaM2': d.landAreaM2!.value};
      if (d.builtAreaM2 != null && _text(data!['builtAreaM2']).isEmpty) data = {...data!, 'builtAreaM2': d.builtAreaM2!.value};
      if (reason.trim().isEmpty) reason = 'Datos tomados de los documentos del expediente';
    });
    showToast(context, 'Se llenaron los campos vacíos con lo detectado en los documentos. Revísalos y guarda.');
  }

  Future<void> save() async {
    setState(() => saving = true);
    final d = data!;
    Object? blankNull(Object? v) => v == '' ? null : v;
    try {
      await api.updateClientData(e.id, {
        'authorizedPrice': blankNull(d['authorizedPrice']),
        'contractSignatureDate': blankNull(d['contractSignatureDate']),
        'email': d['email'],
        'phone': d['phone'],
        'notificationAddress': d['notificationAddress'],
        'visitInstructions': d['visitInstructions'],
        'marketingDataAuthorized': d['marketingDataAuthorized'],
        'receiveAdsAuthorized': d['receiveAdsAuthorized'],
        'additionalServicesRequested': d['additionalServicesRequested'],
        'bedrooms': d['bedrooms'],
        'bathrooms': d['bathrooms'],
        'parkingSpots': d['parkingSpots'],
        'conservationStatus': d['conservationStatus'],
        'availableServices': d['availableServices'],
        'relevantFeatures': d['relevantFeatures'],
        'landAreaM2': blankNull(d['landAreaM2']),
        'builtAreaM2': blankNull(d['builtAreaM2']),
        'reason': reason.trim().isEmpty ? null : reason.trim(),
      });
      await api.updateLegalDetails(e.id, legal!, reason.trim());
      if (!mounted) return;
      showToast(context, 'Datos del contrato guardados.');
      // Lo guardado pasa a ser la referencia y el borrador se borra.
      savedSnapshot = jsonEncode({'data': data, 'legal': legal});
      _draftTimer?.cancel();
      try {
        await api.deleteDraft(draftKey);
      } catch (_) {}
      setState(() {
        reason = '';
        draftStatus = 'idle';
      });
      loadSide();
      await widget.ctx.reload();
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final c = context.c;
    if (loadError != null && (data == null || legal == null)) {
      return TabBody(children: [LoadError(message: loadError!, onRetry: () {
        setState(() => loadError = null);
        load();
      })]);
    }
    if (data == null || legal == null) return const TabBody(children: [StateMessage(kind: StateKind.loading, title: 'Cargando…')]);
    final editable = auth.can('EXPEDIENT_EDIT') && e.correctable;
    final moral = e.personType == 'MORAL';
    final represented = moral || e.signedByAttorney;
    final land = e.propertyCaseType == 'RESIDENTIAL_LAND';
    final checklist = land ? _landChecklist : _housingChecklist;
    final dd = detected;
    final d = data!;

    Widget field(String labelText, Object? value, ValueChanged<String> onChanged, {bool number = false, bool date = false, String? hint, Detected? found}) {
      final differs = found != null && found.value != _text(value);
      return Padding(
        padding: const EdgeInsets.only(bottom: 14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          date
              ? DateField(label: labelText, value: _text(value).isEmpty ? null : _text(value), enabled: editable, helper: hint, onChanged: (v) => onChanged(v ?? ''))
              : AppTextField(
                  label: labelText,
                  value: _text(value),
                  enabled: editable,
                  helper: hint,
                  keyboardType: number ? const TextInputType.numberWithOptions(decimal: true) : null,
                  onChanged: onChanged,
                ),
          if (found != null && differs)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Row(children: [
                Expanded(
                  child: Text.rich(TextSpan(children: [
                    TextSpan(text: 'Detectado en ${found.source}: '),
                    TextSpan(text: found.value, style: TextStyle(fontWeight: FontWeight.w500, color: c.fg)),
                  ]), style: TextStyle(fontSize: 12, color: c.muted)),
                ),
                if (editable)
                  LinkText('Usar', onTap: () => onChanged(found.value)),
              ]),
            ),
          if (found != null && !differs) Padding(padding: const EdgeInsets.only(top: 4), child: Text('Coincide con ${found.source}.', style: TextStyle(fontSize: 12, color: c.successText))),
        ]),
      );
    }

    Widget legalField(String sec, String f, String labelText, {bool date = false, String? hint}) => field(
          labelText,
          section(sec)[f],
          (v) => setSection(sec, f, date && v.isEmpty ? null : v),
          date: date,
          hint: hint,
          found: dd?.legal['$sec.$f'],
        );

    final fill = fillable;
    return TabBody(
        children: [
          if (readiness != null)
            AppCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                CardHeader(title: readiness!.ready ? 'Listo para generar el contrato' : 'Qué falta para generar el contrato', description: 'Variante del contrato: ${readiness!.variant}'),
                if (readiness!.ready)
                  Row(children: [
                    Icon(Icons.check_circle_outline, size: 18, color: c.successText),
                    const SizedBox(width: 6),
                    Expanded(child: Text('No falta nada. Ve a "Contrato y firmas" para generarlo.', style: TextStyle(color: c.successText))),
                  ])
                else ...[
                  const SectionTitle('Requisitos del proceso'),
                  if (readiness!.blockers.isEmpty) Text('Completos.', style: TextStyle(color: c.successText)),
                  for (final b in readiness!.blockers) _alertLine(c, b),
                  const SizedBox(height: 12),
                  const SectionTitle('Datos del contrato'),
                  if (readiness!.missingData.isEmpty) Text('Completos.', style: TextStyle(color: c.successText)),
                  for (final m in readiness!.missingData) _alertLine(c, m),
                  const SizedBox(height: 6),
                  const MutedText('Los datos personales de cada participante se capturan en la pestaña "Participantes".', size: 12),
                ],
              ]),
            ),
          if (editable && fill.isNotEmpty)
            AppCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Row(children: [
                  Icon(Icons.auto_awesome, size: 18, color: c.goldText),
                  const SizedBox(width: 8),
                  Expanded(child: Text('Hay ${fill.length} dato(s) detectados en los documentos para campos vacíos', style: TextStyle(fontWeight: FontWeight.w500, color: c.fg))),
                ]),
                const SizedBox(height: 6),
                const MutedText('La revisión automática los leyó de la escritura, el acta, el poder o el predial. Revísalos contra el documento antes de guardar.', size: 13),
                const SizedBox(height: 10),
                Align(alignment: Alignment.centerLeft, child: Btn('Llenar campos vacíos', variant: BtnVariant.secondary, onPressed: fillFromDocuments)),
              ]),
            ),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const CardHeader(title: 'Precio, firma y autorizaciones'),
              field('Precio autorizado (MXN)', d['authorizedPrice'], (v) => setData('authorizedPrice', numOrNull(v)), number: true),
              field('Fecha de firma del contrato', d['contractSignatureDate'], (v) => setData('contractSignatureDate', v.isEmpty ? null : v), date: true),
              field('Medios de publicidad autorizados por el cliente', legal!['advertisingMedia'], (v) => changed(() => legal = {...legal!, 'advertisingMedia': v}),
                  hint: 'Ej. portales inmobiliarios, redes sociales y letrero en el inmueble.'),
              Padding(
                padding: const EdgeInsets.only(bottom: 14),
                child: YesNo(
                  label: '¿Autoriza ceder su información a terceros con fines mercadotécnicos?',
                  value: d['marketingDataAuthorized'] as bool?,
                  enabled: editable,
                  onChanged: (v) => setData('marketingDataAuthorized', v),
                ),
              ),
              Padding(
                padding: const EdgeInsets.only(bottom: 14),
                child: YesNo(label: '¿Acepta recibir publicidad?', value: d['receiveAdsAuthorized'] as bool?, enabled: editable, onChanged: (v) => setData('receiveAdsAuthorized', v)),
              ),
              field('Instrucciones para visitas', d['visitInstructions'], (v) => setData('visitInstructions', v)),
              field('Servicios adicionales solicitados (Anexo D)', d['additionalServicesRequested'], (v) => setData('additionalServicesRequested', v)),
            ]),
          ),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const CardHeader(title: 'Datos del cliente para notificaciones (cláusula décima segunda)'),
              field('Correo electrónico', d['email'], (v) => setData('email', v)),
              field('Teléfono', d['phone'], (v) => setData('phone', v)),
              field('Domicilio para notificaciones', d['notificationAddress'], (v) => setData('notificationAddress', v),
                  hint: 'Si se deja vacío se usa el domicilio del propietario principal.'),
            ]),
          ),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const CardHeader(title: 'Características del inmueble (Anexo A)'),
              field('Superficie de terreno (m²)', d['landAreaM2'], (v) => setData('landAreaM2', numOrNull(v)), number: true, found: dd?.landAreaM2),
              field(land ? 'Superficie de construcción (m², si hay)' : 'Superficie de construcción (m²)', d['builtAreaM2'], (v) => setData('builtAreaM2', numOrNull(v)),
                  number: true, found: dd?.builtAreaM2),
              field('Estacionamientos', d['parkingSpots'], (v) => setData('parkingSpots', numOrNull(v)), number: true),
              if (!land) ...[
                field('Recámaras', d['bedrooms'], (v) => setData('bedrooms', numOrNull(v)), number: true),
                field('Baños', d['bathrooms'], (v) => setData('bathrooms', numOrNull(v)), number: true),
              ],
              field('Estado general de conservación', d['conservationStatus'], (v) => setData('conservationStatus', v)),
              field('Servicios con los que cuenta', d['availableServices'], (v) => setData('availableServices', v)),
              field('Características relevantes', d['relevantFeatures'], (v) => setData('relevantFeatures', v)),
            ]),
          ),
          if (moral)
            AppCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                const CardHeader(title: 'Constitución de la persona moral (declaración II.a.2)'),
                legalField('company', 'companyType', 'Tipo de sociedad', hint: 'Ej. Sociedad Anónima de Capital Variable'),
                legalField('company', 'rfc', 'RFC de la empresa'),
                legalField('company', 'instrumentNumber', 'Número de instrumento (acta constitutiva)'),
                legalField('company', 'instrumentDate', 'Fecha del instrumento', date: true),
                legalField('company', 'notaryTitle', 'Notario o Corredor', hint: 'Escribe: Notario o Corredor'),
                legalField('company', 'notaryNumber', 'Número de notaría / correduría'),
                legalField('company', 'notaryPlace', 'Lugar de la notaría'),
                legalField('company', 'notaryName', 'Nombre del notario / corredor'),
                legalField('company', 'commerceRegistryPlace', 'Registro Público de Comercio de'),
                legalField('company', 'mercantileFolio', 'Folio mercantil'),
              ]),
            ),
          if (represented)
            AppCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                CardHeader(title: moral ? 'Facultades del representante legal (declaración II.b)' : 'Poder del apoderado (declaración II.b)'),
                legalField('representation', 'capacity', 'Carácter con el que comparece', hint: moral ? 'Ej. representante legal / administrador único' : 'Ej. apoderado'),
                legalField('representation', 'instrumentNumber', 'Número del instrumento (poder)'),
                legalField('representation', 'instrumentDate', 'Fecha del instrumento', date: true),
                legalField('representation', 'notaryTitle', 'Notario o Corredor'),
                legalField('representation', 'notaryNumber', 'Número de notaría'),
                legalField('representation', 'notaryPlace', 'Lugar de la notaría'),
                legalField('representation', 'notaryName', 'Nombre del notario'),
                legalField('representation', 'registryPlace', moral ? 'Registro Público de Comercio de' : 'Registro Público de Comercio de (si está inscrito)'),
                legalField('representation', 'registryFolio', 'Folio mercantil'),
              ]),
            ),
          if (e.accreditationType == 'ESCRITURA_PUBLICA')
            AppCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                const CardHeader(title: 'Escritura con la que se acredita la propiedad (declaración II.d)'),
                legalField('deed', 'number', 'Número de escritura'),
                legalField('deed', 'date', 'Fecha de la escritura', date: true),
                legalField('deed', 'notaryName', 'Nombre del notario'),
                legalField('deed', 'notaryNumber', 'Número de notaría'),
                legalField('deed', 'notaryPlace', 'Lugar de la notaría', hint: 'Ej. Cuernavaca, Morelos'),
                legalField('deed', 'registryData', 'Datos registrales (opcional)', hint: 'Folio real o datos de inscripción, si ya se confirmaron'),
              ]),
            )
          else
            AppCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                const CardHeader(title: 'Contrato privado con el que se acredita la propiedad (declaración II.d)'),
                legalField('privateContract', 'sellerName', 'Vendedor en ese contrato'),
                legalField('privateContract', 'buyerName', 'Comprador (el cliente)'),
                legalField('privateContract', 'date', 'Fecha del contrato', date: true),
                legalField('privateContract', 'ratificationDate', 'Fecha de ratificación', date: true),
                legalField('privateContract', 'ratifiedBefore', 'Ratificado ante', hint: 'Ej. la fe pública del Notario Público'),
                legalField('privateContract', 'notaryNumber', 'Número de notaría'),
                legalField('privateContract', 'notaryPlace', 'Lugar de la notaría'),
                legalField('privateContract', 'notaryName', 'Nombre del notario'),
                legalField('privateContract', 'registryDate', 'Fecha de inscripción en el RPP', date: true),
                legalField('privateContract', 'registryPlace', 'Registro Público de la Propiedad de'),
                legalField('privateContract', 'realFolio', 'Folio real'),
              ]),
            ),
          if (e.condominiumRegime)
            AppCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                const CardHeader(title: 'Régimen de condominio (declaración II.f)'),
                legalField('condominium', 'deedNumber', 'Número de escritura del régimen'),
                legalField('condominium', 'date', 'Fecha', date: true),
                legalField('condominium', 'notaryNumber', 'Número de notaría'),
                legalField('condominium', 'notaryPlace', 'Lugar de la notaría'),
                legalField('condominium', 'notaryName', 'Nombre del notario'),
                legalField('condominium', 'registryDate', 'Fecha de inscripción', date: true),
                legalField('condominium', 'realFolio', 'Folio real'),
              ]),
            ),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              CardHeader(
                title: land ? 'Documentación del terreno (declaración II.g)' : 'Documentación de la vivienda (declaración II.g)',
                description: '¿El cliente cuenta con esta documentación o información?',
              ),
              for (final (key, question) in checklist)
                Padding(
                  padding: const EdgeInsets.only(bottom: 14),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(question, style: TextStyle(fontSize: 13.5, color: c.fg)),
                    const SizedBox(height: 6),
                    YesNo(
                      value: (legal!['propertyChecklist'] as Map?)?[key] as bool?,
                      enabled: editable,
                      onChanged: (v) => changed(() => legal = {
                            ...legal!,
                            'propertyChecklist': {...Json.from((legal!['propertyChecklist'] as Map?) ?? {}), key: v},
                          }),
                    ),
                  ]),
                ),
            ]),
          ),
          if (editable)
            AppCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                AppTextField(
                  label: 'Motivo del cambio (queda en la bitácora)',
                  value: reason,
                  hint: 'Ej. Captura de datos de la escritura',
                  onChanged: (v) => changed(() => reason = v),
                ),
                const SizedBox(height: 12),
                Btn(saving ? 'Guardando…' : 'Guardar datos del contrato', icon: Icons.save_outlined, loading: saving, expand: true, onPressed: save),
                const SizedBox(height: 8),
                MutedText(
                  switch (draftStatus) {
                    'saving' => 'Guardando borrador…',
                    'saved' => 'Guardado como borrador; presiona "Guardar" para aplicarlo al contrato.',
                    'error' => 'No se pudo guardar el borrador (revisa tu conexión).',
                    _ => 'Tus cambios se guardan como borrador mientras capturas.',
                  },
                  size: 12,
                ),
                const SizedBox(height: 4),
                const MutedText('Si ya existe un contrato sin firmar, al guardar cambios quedará sin efecto y habrá que generar una versión nueva.', size: 12),
              ]),
            )
          else
            MutedText(e.correctable ? 'No tienes permiso para editar estos datos.' : 'Los datos están bloqueados porque el contrato ya se firmó o el expediente ya se decidió.'),
        ],
    );
  }

  Widget _alertLine(AppColors c, String text) => Padding(
        padding: const EdgeInsets.only(bottom: 4),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(Icons.error_outline, size: 16, color: c.warningText),
          const SizedBox(width: 6),
          Expanded(child: Text(text, style: TextStyle(fontSize: 13.5, color: c.warningText))),
        ]),
      );
}
