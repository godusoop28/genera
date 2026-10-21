import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../api/models.dart';
import '../../labels.dart';
import '../../state/auth.dart';
import '../../ui/property_type_picker.dart';
import '../../ui/shell.dart';
import '../../ui/widgets.dart';

const _draftKey = 'nuevo-expediente';
const _minDuplicateReason = 15;
const _steps = ['Cliente', 'Inmueble', 'Revisión'];

/// Vista previa (solo informativa) de los documentos que pedirá el sistema.
/// La lista real la calcula el backend (DocumentRequirementPolicy.java).
List<({String type, String label, String? detail, String? note, String kind})> previewRequirements({
  required String personType,
  required bool signedByAttorney,
  required String accreditationType,
  required bool condominiumRegime,
  required String propertyCaseType,
  required List<({String role, String fullName})> participants,
}) {
  final items = <({String type, String label, String? detail, String? note, String kind})>[];
  final moral = personType == 'MORAL';
  void doc(String type, [String? detail, String? note, String kind = 'required']) =>
      items.add((type: type, label: documentTypeLabel(type), detail: detail, note: note, kind: kind));
  for (var i = 0; i < participants.length; i++) {
    final p = participants[i];
    final name = p.fullName.trim().isEmpty ? 'Participante ${i + 1}' : p.fullName.trim();
    final isCompany = moral && p.role == 'OWNER';
    final isOwner = p.role == 'OWNER' || p.role == 'CO_OWNER';
    if (!isCompany) {
      doc('INE', name);
      doc('PROOF_OF_ADDRESS', name);
    }
    if (isOwner) doc('TAX_STATUS_CERTIFICATE', name);
    // El estado civil lo declara el cliente en su liga: el acta solo es obligatoria si está casado.
    if (isOwner && !isCompany) doc('MARRIAGE_CERTIFICATE', name, 'Solo si está casado; lo declara el cliente', 'conditional');
  }
  if (moral) doc('INCORPORATION_DEED', 'De la empresa');
  doc(accreditationType == 'ESCRITURA_PUBLICA' ? 'DEED' : 'PRIVATE_CONTRACT', 'Del inmueble');
  doc('CADASTRAL_PLAN', 'Del inmueble');
  doc('RPP_REGISTRATION_SLIP', 'Del inmueble');
  doc('PROPERTY_TAX', 'Comprobante de pago vigente');
  final land = propertyCaseType == 'RESIDENTIAL_LAND';
  doc('ELECTRICITY_RECEIPT', 'Del inmueble', land ? 'Opcional en terreno' : null, land ? 'optional' : 'required');
  doc('WATER_RECEIPT', 'Del inmueble', land ? 'Opcional en terreno' : null, land ? 'optional' : 'required');
  if (signedByAttorney || moral) doc('POWER_OF_ATTORNEY', moral ? 'Facultades del representante legal' : 'Del apoderado');
  if (condominiumRegime) doc('CONDOMINIUM_REGIME', 'Del inmueble');
  return items;
}

const _previewIcons = {
  'INE': Icons.badge_outlined,
  'PROOF_OF_ADDRESS': Icons.place_outlined,
  'TAX_STATUS_CERTIFICATE': Icons.account_balance_outlined,
  'MARRIAGE_CERTIFICATE': Icons.favorite_border,
  'INCORPORATION_DEED': Icons.apartment_outlined,
  'DEED': Icons.history_edu_outlined,
  'PRIVATE_CONTRACT': Icons.history_edu_outlined,
  'CADASTRAL_PLAN': Icons.map_outlined,
  'RPP_REGISTRATION_SLIP': Icons.home_outlined,
  'PROPERTY_TAX': Icons.receipt_long_outlined,
  'ELECTRICITY_RECEIPT': Icons.bolt_outlined,
  'WATER_RECEIPT': Icons.water_drop_outlined,
  'POWER_OF_ATTORNEY': Icons.how_to_reg_outlined,
  'CONDOMINIUM_REGIME': Icons.apartment_outlined,
};

class NewExpedienteScreen extends StatefulWidget {
  const NewExpedienteScreen({super.key});

  @override
  State<NewExpedienteScreen> createState() => _NewExpedienteScreenState();
}

class _NewExpedienteScreenState extends State<NewExpedienteScreen> {
  bool restoring = true;
  bool resumed = false;
  int step = 1;
  String personType = 'FISICA';
  bool signedByAttorney = false;
  List<String> owners = [''];
  List<String> representatives = [''];
  String attorney = '';
  String accreditationType = 'ESCRITURA_PUBLICA';
  // Control de Cambios #26: se elige la subcategoría del catálogo; la categoría y el tratamiento de documentos salen de ella.
  String? propertySubtypeId;
  String propertyTypeDetail = '';
  List<CatalogCategory>? catalog;
  CatalogSubtype? get selectedSubtype => findSubtype(catalog, propertySubtypeId);
  String get propertyCaseType => selectedSubtype?.caseType ?? 'HOUSING';
  String operationType = 'SALE';

  /// Control de Cambios #24: asesor responsable, obligatorio desde el inicio del alta.
  String assignedAdvisorId = '';
  List<AdvisorOption>? advisors;
  bool advisorsFailed = false;
  bool condominiumRegime = false;
  String propertyReference = '';

  /// Documentos que no están en el listado, con el nombre capturado (Control de Cambios #1).
  List<String> extraDocuments = [];
  bool showErrors = false;
  bool submitting = false;
  bool created = false;
  String? error;
  List<DuplicateMatch> duplicates = [];
  String duplicateReason = '';
  String? authorizedFolios;
  bool checkingDuplicates = false;
  String draftStatus = 'idle';
  Timer? _saveTimer;

  @override
  void initState() {
    super.initState();
    api.listPropertyCatalog().then((c) {
      if (mounted) setState(() => catalog = c);
    }).catchError((Object _) {});
    restore().then((_) => loadAdvisors());
  }

  Future<void> loadAdvisors() async {
    try {
      final list = await api.listAssignableAdvisors();
      if (!mounted) return;
      final me = context.read<AuthState>().user?.id;
      setState(() {
        advisors = list;
        // Si quien da de alta es asesor, se propone a sí mismo; un asesor dado de baja se descarta.
        if (!list.any((a) => a.id == assignedAdvisorId)) {
          assignedAdvisorId = list.any((a) => a.id == me && a.roleCode == 'ADVISOR') ? me! : '';
        }
      });
    } catch (_) {
      if (mounted) setState(() => advisorsFailed = true);
    }
  }

  @override
  void dispose() {
    _saveTimer?.cancel();
    super.dispose();
  }

  /// Lo capturado se autoguarda en el servidor (bajo el usuario) hasta que se crea el expediente.
  Future<void> restore() async {
    try {
      final payload = await api.loadDraft(_draftKey);
      if (payload != null) {
        final d = jsonDecode(payload) as Map<String, dynamic>;
        personType = d['personType'] as String? ?? personType;
        signedByAttorney = d['signedByAttorney'] == true;
        owners = [for (final o in (d['owners'] as List? ?? [])) '${(o as Map)['fullName'] ?? ''}'];
        if (owners.isEmpty) owners = [''];
        representatives = [for (final o in (d['representatives'] as List? ?? [])) '${(o as Map)['fullName'] ?? ''}'];
        if (representatives.isEmpty) representatives = [''];
        attorney = '${(d['attorney'] as Map?)?['fullName'] ?? ''}';
        accreditationType = d['accreditationType'] as String? ?? accreditationType;
        propertySubtypeId = d['propertySubtypeId'] as String?;
        propertyTypeDetail = d['propertyTypeDetail'] as String? ?? '';
        operationType = d['operationType'] as String? ?? operationType;
        assignedAdvisorId = d['assignedAdvisorId'] as String? ?? '';
        condominiumRegime = d['condominiumRegime'] == true;
        propertyReference = d['propertyReference'] as String? ?? '';
        extraDocuments = [for (final n in (d['extraDocuments'] as List? ?? [])) '$n'];
        step = (d['step'] as num?)?.toInt() ?? 1;
        resumed = step > 1;
      }
    } catch (_) {
      // Sin borrador o sin conexión: se empieza en blanco.
    }
    if (mounted) setState(() => restoring = false);
  }

  void changed(VoidCallback fn) {
    setState(fn);
    _saveTimer?.cancel();
    _saveTimer = Timer(const Duration(milliseconds: 800), saveDraft);
  }

  Future<void> saveDraft() async {
    if (created) return;
    setState(() => draftStatus = 'saving');
    Map<String, String> person(String role, String name) => {'role': role, 'fullName': name};
    final payload = jsonEncode({
      'personType': personType,
      'signedByAttorney': signedByAttorney,
      'owners': [for (var i = 0; i < owners.length; i++) person(i == 0 ? 'OWNER' : 'CO_OWNER', owners[i])],
      'representatives': [for (final r in representatives) person('LEGAL_REPRESENTATIVE', r)],
      'attorney': person('ATTORNEY', attorney),
      'accreditationType': accreditationType,
      'propertySubtypeId': propertySubtypeId,
      'propertyTypeDetail': propertyTypeDetail,
      'operationType': operationType,
      'assignedAdvisorId': assignedAdvisorId,
      'condominiumRegime': condominiumRegime,
      'propertyReference': propertyReference,
      'extraDocuments': extraDocuments,
      'step': step,
    });
    try {
      await api.saveDraft(_draftKey, payload);
      if (mounted) setState(() => draftStatus = 'saved');
    } catch (_) {
      if (mounted) setState(() => draftStatus = 'error');
    }
  }

  bool get isMoral => personType == 'MORAL';

  List<({String role, String fullName})> get participants {
    if (isMoral) return [(role: 'OWNER', fullName: owners.first), for (final r in representatives) (role: 'LEGAL_REPRESENTATIVE', fullName: r)];
    final holders = [for (var i = 0; i < owners.length; i++) (role: i == 0 ? 'OWNER' : 'CO_OWNER', fullName: owners[i])];
    return signedByAttorney ? [...holders, (role: 'ATTORNEY', fullName: attorney)] : holders;
  }

  bool get advisorValid => assignedAdvisorId.isNotEmpty;
  bool get clientValid => advisorValid && participants.every((p) => p.fullName.trim().isNotEmpty);
  String? get propertyTypeProblem => propertyTypeError(catalog, propertySubtypeId, propertyTypeDetail);
  bool get propertyValid => propertyReference.trim().isNotEmpty && propertyTypeProblem == null;

  String get missingNamesText {
    if (!advisorValid) return 'Selecciona el asesor asignado al inmueble.';
    final missing = [
      for (var i = 0; i < (isMoral ? 1 : owners.length); i++)
        if (owners[i].trim().isEmpty) isMoral ? 'la razón social de la empresa' : (i == 0 ? 'el nombre del propietario principal' : 'el nombre del copropietario ${i + 1}'),
      if (isMoral)
        for (var i = 0; i < representatives.length; i++)
          if (representatives[i].trim().isEmpty) 'el nombre del representante legal${representatives.length > 1 ? ' ${i + 1}' : ''}',
      if (!isMoral && signedByAttorney && attorney.trim().isEmpty) 'el nombre del apoderado',
    ];
    return missing.isEmpty ? '' : 'Falta capturar ${missing.join(', ')}.';
  }

  String _foliosKey(List<DuplicateMatch> m) => (m.map((d) => d.folio).toList()..sort()).join(',');
  bool get duplicatesAuthorized => duplicates.isNotEmpty && authorizedFolios == _foliosKey(duplicates);

  void goTo(int next) => changed(() {
    error = null;
    step = next;
  });

  /// true si se puede seguir: no hay expedientes en curso del cliente, o ya se autorizó abrir otro.
  Future<bool> checkDuplicates() async {
    final holders = participants.where((p) => p.role == 'OWNER' || p.role == 'CO_OWNER').map((p) => p.fullName.trim()).toList();
    setState(() => checkingDuplicates = true);
    try {
      final matches = await api.checkClientDuplicates(holders);
      setState(() => duplicates = matches);
      if (matches.isEmpty || authorizedFolios == _foliosKey(matches)) return true;
      setState(
        () =>
            error = matches.length == 1 ? 'Este cliente ya tiene el expediente ${matches.first.folio} en curso.' : 'Este cliente ya tiene ${matches.length} expedientes en curso.',
      );
      return false;
    } catch (_) {
      // Si la verificación falla por red, el backend vuelve a revisarlo al crear.
      return true;
    } finally {
      if (mounted) setState(() => checkingDuplicates = false);
    }
  }

  Future<void> next() async {
    if (step == 1 && !clientValid) {
      setState(() {
        showErrors = true;
        error = missingNamesText;
      });
      return;
    }
    if (step == 1 && !await checkDuplicates()) return;
    if (step == 2 && !propertyValid) {
      setState(() {
        showErrors = true;
        error = propertyReference.trim().isEmpty ? 'Captura una referencia del inmueble para que el cliente lo reconozca.' : propertyTypeProblem;
      });
      return;
    }
    setState(() => showErrors = false);
    if (step < 3) goTo(step + 1);
  }

  Future<void> create() async {
    if (submitting || created) return;
    setState(() => error = null);
    if (!clientValid || !propertyValid) {
      goTo(clientValid ? 2 : 1);
      setState(() {
        showErrors = true;
        error = clientValid ? (propertyTypeProblem ?? 'Captura una referencia del inmueble antes de crear el expediente.') : missingNamesText;
      });
      return;
    }
    setState(() => submitting = true);
    if (!await checkDuplicates()) {
      setState(() => submitting = false);
      goTo(1);
      return;
    }
    try {
      final result = await api.createExpediente({
        'personType': personType,
        'signedByAttorney': personType == 'FISICA' && signedByAttorney,
        'accreditationType': accreditationType,
        'condominiumRegime': condominiumRegime,
        'propertySubtypeId': propertySubtypeId,
        'propertyTypeDetail': selectedSubtype?.requiresDetail == true ? propertyTypeDetail.trim() : null,
        'operationType': operationType,
        'assignedAdvisorId': assignedAdvisorId,
        'propertyReference': propertyReference.trim(),
        // Solo el nombre: lo demás se pide al cliente en su liga o se lee de sus documentos.
        'participants': [
          for (final p in participants) {'role': p.role, 'fullName': p.fullName.trim()},
        ],
        'duplicateAuthorizationReason': duplicatesAuthorized ? duplicateReason.trim() : null,
      });
      created = true;
      _saveTimer?.cancel();
      // Los documentos agregados a mano se guardan en el expediente igual que los del listado.
      final failed = <String>[];
      for (final name in extraDocuments) {
        try {
          await api.addCustomDocument(result.id, name, null);
        } catch (_) {
          failed.add(name);
        }
      }
      try {
        await api.deleteDraft(_draftKey);
      } catch (_) {}
      if (!mounted) return;
      showToast(
        context,
        failed.isEmpty
            ? 'Expediente ${result.folio} creado.'
            : 'Expediente ${result.folio} creado. No se pudo agregar: ${failed.join(', ')}; agrégalo desde la pestaña Documentos.',
      );
      // Un asesor solo ve los expedientes que tiene asignados (Control de Cambios #24).
      final auth = context.read<AuthState>();
      context.pushReplacement(auth.can('EXPEDIENT_VIEW_ALL') || result.assignedAdvisorId == auth.user?.id ? '/expedientes/${result.id}' : '/expedientes');
    } catch (err) {
      // Lo capturado se conserva: el usuario puede reintentar sin volver a escribir nada.
      setState(() {
        submitting = false;
        error = err is ApiError ? 'No se pudo crear el expediente: ${err.message}' : 'No se pudo conectar con el servidor. Lo capturado sigue aquí; intenta de nuevo.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    if (!auth.can('EXPEDIENT_CREATE')) {
      return const Restricted(title: 'Nuevo expediente', route: '/expedientes/nuevo', description: 'Tu usuario no tiene permiso para crear expedientes.');
    }
    if (restoring) {
      return const AppScaffold(
        title: 'Nuevo expediente',
        route: '/expedientes/nuevo',
        body: PageBody(
          children: [StateMessage(kind: StateKind.loading, title: 'Recuperando lo que ya habías capturado…')],
        ),
      );
    }
    final c = context.c;
    final preview = previewRequirements(
      personType: personType,
      signedByAttorney: signedByAttorney,
      accreditationType: accreditationType,
      condominiumRegime: condominiumRegime,
      propertyCaseType: propertyCaseType,
      participants: participants,
    );
    return AppScaffold(
      title: 'Nuevo expediente',
      route: '/expedientes/nuevo',
      bottomBar: _bottomBar(c),
      body: PageBody(
        subtitle:
            'Solo lo necesario para saber qué documentos pedir. El domicilio, la situación jurídica y los demás datos se leen de los documentos o los captura el cliente en su liga.',
        children: [
          _Stepper(current: step, onSelect: (s) => s < step ? goTo(s) : null),
          if (resumed) ...[
            const SizedBox(height: 14),
            Notice(
              tone: Tone.gold,
              text: 'Recuperamos tu borrador en el paso ${_steps[step - 1]}.',
              action: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  Btn(
                    'Revisar desde el inicio',
                    small: true,
                    variant: BtnVariant.secondary,
                    onPressed: () {
                      setState(() => resumed = false);
                      goTo(1);
                    },
                  ),
                  Btn('Continuar donde me quedé', small: true, variant: BtnVariant.dark, onPressed: () => setState(() => resumed = false)),
                ],
              ),
            ),
          ],
          const SizedBox(height: 16),
          if (step == 1) ..._stepClient(c),
          if (step == 2) _stepProperty(c),
          if (step == 3) _stepReview(c, preview),
          const SizedBox(height: 16),
          _previewCard(c, preview),
        ],
      ),
    );
  }

  Widget _bottomBar(AppColors c) => Container(
    decoration: BoxDecoration(
      color: c.card,
      border: Border(top: BorderSide(color: c.border)),
    ),
    padding: EdgeInsets.fromLTRB(16, 10, 16, 10 + MediaQuery.of(context).padding.bottom),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (error != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.warning_amber_rounded, size: 18, color: c.dangerText),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    error!,
                    style: TextStyle(color: c.dangerText, fontWeight: FontWeight.w500, fontSize: 13),
                  ),
                ),
              ],
            ),
          )
        else
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: MutedText(switch (draftStatus) {
              'saving' => 'Guardando borrador…',
              'saved' => 'Borrador guardado.',
              'error' => 'No se pudo guardar el borrador; se reintentará con el siguiente cambio.',
              _ => 'Lo que captures se guarda automáticamente como borrador.',
            }, size: 12),
          ),
        Row(
          children: [
            if (step > 1) ...[
              Expanded(
                child: Btn('Anterior', icon: Icons.arrow_back, variant: BtnVariant.secondary, onPressed: submitting || created ? null : () => goTo(step - 1)),
              ),
              const SizedBox(width: 10),
            ],
            Expanded(
              child: step < 3
                  ? Btn('Continuar', variant: BtnVariant.dark, loading: checkingDuplicates, onPressed: next)
                  : Btn(created ? 'Abriendo expediente…' : (submitting ? 'Creando…' : 'Crear expediente'), icon: Icons.check, loading: submitting || created, onPressed: create),
            ),
          ],
        ),
      ],
    ),
  );

  List<Widget> _stepClient(AppColors c) {
    String? nameError(String value, String message) => showErrors && value.trim().isEmpty ? message : null;
    Widget personBox({required String title, required String value, required ValueChanged<String> onChanged, String? errorText, VoidCallback? onRemove, bool company = false}) =>
        Container(
          margin: const EdgeInsets.only(bottom: 12),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: c.border),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      title,
                      style: TextStyle(fontWeight: FontWeight.w600, color: c.fg),
                    ),
                  ),
                  if (onRemove != null) Btn('Quitar', icon: Icons.delete_outline, small: true, variant: BtnVariant.ghost, onPressed: onRemove),
                ],
              ),
              const SizedBox(height: 10),
              AppTextField(
                label: company ? 'Razón social' : 'Nombre completo (como aparece en su identificación)',
                hint: company ? 'Ej. Inmobiliaria del Sol, S.A. de C.V.' : 'Ej. Juan Pérez López',
                value: value,
                error: errorText,
                onChanged: (v) => changed(() => onChanged(v)),
              ),
            ],
          ),
        );

    return [
      AppCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const CardHeader(title: 'Asesor asignado', description: 'Quién es responsable de este inmueble. Solo el administrador o un directivo podrá cambiarlo después.'),
            if (advisorsFailed)
              Text('No se pudo cargar la lista de asesores. Sal y vuelve a entrar para intentarlo de nuevo.', style: TextStyle(color: c.dangerText, fontSize: 13))
            else
              AppSelect<String>(
                label: 'Asesor asignado',
                value: assignedAdvisorId,
                enabled: advisors != null,
                options: [
                  (value: '', label: advisors == null ? 'Cargando asesores…' : 'Selecciona un asesor'),
                  for (final a in advisors ?? const <AdvisorOption>[]) (value: a.id, label: a.roleCode == 'ADVISOR' ? a.name : '${a.name} (${label(roleLabels, a.roleCode)})'),
                ],
                onChanged: (v) => changed(() => assignedAdvisorId = v),
              ),
            if (showErrors && !advisorValid)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text('Selecciona el asesor asignado.', style: TextStyle(color: c.dangerText, fontSize: 12.5)),
              ),
          ],
        ),
      ),
      const SizedBox(height: 16),
      AppCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const CardHeader(title: '¿Quién es el cliente?', description: 'Define qué documentos y qué datos pide el contrato.'),
            ChoiceTile(
              selected: personType == 'FISICA',
              onTap: () => changed(() => personType = 'FISICA'),
              icon: Icons.person_outline,
              title: 'Persona física',
              subtitle: 'Uno o varios propietarios personas. Pueden firmar ellos mismos o un apoderado.',
            ),
            const SizedBox(height: 8),
            ChoiceTile(
              selected: personType == 'MORAL',
              onTap: () => changed(() => personType = 'MORAL'),
              icon: Icons.apartment_outlined,
              title: 'Persona moral (empresa)',
              subtitle: 'Una empresa es la propietaria; firma su representante legal. No aplica estado civil.',
            ),
            if (!isMoral) ...[
              const SizedBox(height: 12),
              Divider(color: c.border),
              ToggleRow(
                label: 'Firma mediante apoderado',
                description: 'Se pedirá su identificación y el poder notarial.',
                value: signedByAttorney,
                onChanged: (v) => changed(() => signedByAttorney = v),
              ),
            ],
          ],
        ),
      ),
      const SizedBox(height: 16),
      AppCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            CardHeader(
              title: isMoral ? 'Empresa y representante legal' : 'Propietarios',
              description: isMoral
                  ? 'La empresa titular del inmueble y quién la representa. Solo el nombre: lo demás se lee de sus documentos.'
                  : 'Registra a todos los titulares del inmueble; no hay límite. Cada uno tendrá sus propios documentos y firma. Solo el nombre: estado civil y contacto los captura el cliente.',
            ),
            for (var i = 0; i < (isMoral ? 1 : owners.length); i++)
              personBox(
                title: isMoral ? 'Empresa titular' : (i == 0 ? 'Propietario principal' : 'Copropietario ${i + 1}'),
                value: owners[i],
                company: isMoral,
                onChanged: (v) => owners[i] = v,
                errorText: nameError(
                  owners[i],
                  isMoral ? 'Captura la razón social de la empresa.' : (i == 0 ? 'Captura el nombre del propietario principal.' : 'Captura el nombre del copropietario.'),
                ),
                onRemove: !isMoral && i > 0 ? () => changed(() => owners.removeAt(i)) : null,
              ),
            if (!isMoral)
              Align(
                alignment: Alignment.centerLeft,
                child: LinkText('Agregar copropietario', icon: Icons.add, onTap: () => changed(() => owners.add(''))),
              ),
            if (isMoral) ...[
              for (var i = 0; i < representatives.length; i++)
                personBox(
                  title: 'Representante legal ${representatives.length > 1 ? i + 1 : ''}',
                  value: representatives[i],
                  onChanged: (v) => representatives[i] = v,
                  errorText: nameError(representatives[i], 'Captura el nombre del representante legal.'),
                  onRemove: i > 0 ? () => changed(() => representatives.removeAt(i)) : null,
                ),
              Align(
                alignment: Alignment.centerLeft,
                child: LinkText('Agregar otro representante', icon: Icons.add, onTap: () => changed(() => representatives.add(''))),
              ),
            ],
            if (duplicates.isNotEmpty && !duplicatesAuthorized) ...[const SizedBox(height: 12), _duplicateWarning(c)],
            if (!isMoral && signedByAttorney) ...[
              const SizedBox(height: 12),
              personBox(title: 'Apoderado', value: attorney, onChanged: (v) => attorney = v, errorText: nameError(attorney, 'Captura el nombre del apoderado.')),
            ],
          ],
        ),
      ),
    ];
  }

  Widget _duplicateWarning(AppColors c) {
    final canAuthorize = context.read<AuthState>().can('EXPEDIENT_DUPLICATE_AUTHORIZE');
    return Notice(
      tone: Tone.warning,
      icon: Icons.warning_amber_rounded,
      title: duplicates.length == 1 ? 'Este cliente ya está dado de alta' : 'Este cliente ya está dado de alta en ${duplicates.length} expedientes',
      text: 'Revisa que no estés duplicando el expediente. Si es la misma propiedad, continúa en el que ya existe.',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 10),
          for (final m in duplicates)
            Container(
              margin: const EdgeInsets.only(bottom: 8),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: c.card,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: c.border),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          m.folio,
                          style: TextStyle(fontFamily: 'GeistMono', fontWeight: FontWeight.w600, color: c.fg),
                        ),
                      ),
                      LinkText('Abrir existente', onTap: () => context.push('/expedientes/${m.id}')),
                    ],
                  ),
                  Text(m.matchedName, style: TextStyle(color: c.fg)),
                  MutedText(
                    '${label(backendStatusLabels, m.status)}${m.property != null ? ' · ${m.property}' : ''}${m.advisorName != null ? ' · Asesor: ${m.advisorName}' : ''} · Alta ${formatDate(m.createdAt)}',
                    size: 12,
                  ),
                ],
              ),
            ),
          if (canAuthorize) ...[
            const SizedBox(height: 6),
            AppTextField(
              label: '¿Es otra propiedad? Autoriza abrir otro expediente',
              hint: 'Ej. Segunda propiedad del cliente: departamento en Providencia',
              maxLines: 2,
              value: duplicateReason,
              onChanged: (v) => setState(() => duplicateReason = v),
              helper: 'Mínimo $_minDuplicateReason caracteres. La autorización queda en la bitácora con tu nombre.',
            ),
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerRight,
              child: Btn(
                'Autorizar y continuar',
                small: true,
                variant: BtnVariant.dark,
                onPressed: duplicateReason.trim().length < _minDuplicateReason
                    ? null
                    : () {
                        setState(() => authorizedFolios = _foliosKey(duplicates));
                        goTo(2);
                      },
              ),
            ),
          ] else
            const Text('Si es otra propiedad del mismo cliente, se necesita una autorización especial: pide a un administrador o director que dé de alta este expediente.'),
        ],
      ),
    );
  }

  Widget _stepProperty(AppColors c) => AppCard(
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const CardHeader(title: 'Inmueble', description: 'Con esto se define qué documentos del inmueble se piden.'),
        AppTextField(
          label: 'Referencia para que el cliente reconozca el inmueble',
          value: propertyReference,
          maxLength: 120,
          hint: 'Ej. Casa en Coto Austriaco, Zapopan',
          helper: 'El cliente la ve al abrir su liga, antes de entregar datos. Sin número exterior ni datos completos: el domicilio legal se toma de los documentos.',
          error: showErrors && propertyReference.trim().isEmpty ? 'Captura una referencia del inmueble.' : null,
          onChanged: (v) => changed(() => propertyReference = v),
        ),
        const SizedBox(height: 14),
        PropertyTypePicker(
          subtypeId: propertySubtypeId,
          detail: propertyTypeDetail,
          error: showErrors ? propertyTypeProblem : null,
          onChanged: (id, detail, _) => changed(() {
            propertySubtypeId = id;
            propertyTypeDetail = detail;
          }),
        ),
        const SizedBox(height: 14),
        AppSelect<String>(label: 'Operación', value: operationType, options: optionsOf(operationLabels), onChanged: (v) => changed(() => operationType = v)),
        const SizedBox(height: 14),
        AppSelect<String>(
          label: '¿Con qué documento se acredita la propiedad?',
          value: accreditationType,
          options: optionsOf(accreditationLabels),
          onChanged: (v) => changed(() => accreditationType = v),
        ),
        const SizedBox(height: 10),
        Divider(color: c.border),
        ToggleRow(
          label: 'Está en régimen de condominio',
          description: 'Se pedirá la escritura del régimen de condominio.',
          value: condominiumRegime,
          onChanged: (v) => changed(() => condominiumRegime = v),
        ),
        if (selectedSubtype != null && propertyCaseType == 'COMMERCIAL') ...[
          const SizedBox(height: 12),
          const Notice(
            tone: Tone.warning,
            icon: Icons.warning_amber_rounded,
            text:
                'El contrato registrado ante PROFECO solo ampara inmuebles destinados a casa habitación. Puedes integrar el expediente, pero el sistema no generará el contrato para este tipo de inmueble.',
          ),
        ],
        const SizedBox(height: 12),
        const Notice(
          tone: Tone.info,
          icon: Icons.info_outline,
          text: 'No necesitas capturar el domicilio ni la situación jurídica: se toman de la escritura, el predial y el certificado de libertad de gravamen cuando se leen.',
        ),
      ],
    ),
  );

  Widget _stepReview(AppColors c, List<({String type, String label, String? detail, String? note, String kind})> preview) {
    Widget section(String title, int target, List<Widget> rows) => Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(child: SectionTitle(title)),
              Btn('Editar', icon: Icons.edit_outlined, small: true, variant: BtnVariant.ghost, onPressed: () => goTo(target)),
            ],
          ),
          FieldGrid(children: rows),
        ],
      ),
    );
    String roleTitle(String role) => switch (role) {
      'OWNER' => isMoral ? 'Empresa titular' : 'Propietario principal',
      'CO_OWNER' => 'Copropietario',
      'ATTORNEY' => 'Apoderado',
      _ => 'Representante legal',
    };
    final required = preview.where((i) => i.kind == 'required').length + extraDocuments.length;
    final conditional = preview.where((i) => i.kind == 'conditional').length;
    final optional = preview.where((i) => i.kind == 'optional').length;
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const CardHeader(title: 'Revisa antes de crear', description: 'Puedes volver a cualquier paso; lo capturado se conserva.'),
          section('Cliente', 1, [
            FieldView('Asesor asignado', advisors?.where((a) => a.id == assignedAdvisorId).firstOrNull?.name ?? '—'),
            FieldView('Tipo de cliente', isMoral ? 'Persona moral (empresa)' : 'Persona física'),
            if (!isMoral) FieldView('Firma', signedByAttorney ? 'Mediante apoderado' : 'Los propietarios'),
            for (final p in participants) FieldView(roleTitle(p.role), p.fullName.trim().isEmpty ? '—' : p.fullName.trim()),
          ]),
          if (duplicatesAuthorized)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Notice(
                tone: Tone.warning,
                icon: Icons.warning_amber_rounded,
                text: 'Autorización especial: este cliente ya tiene ${duplicates.map((d) => d.folio).join(', ')}. Motivo: ${duplicateReason.trim()}. Quedará en la bitácora.',
              ),
            ),
          Divider(color: c.border),
          const SizedBox(height: 8),
          section('Inmueble', 2, [
            FieldView('Referencia para el cliente', propertyReference.trim().isEmpty ? '—' : propertyReference.trim()),
            FieldView('Tipo de inmueble', selectedSubtype?.label(propertyTypeDetail) ?? '—'),
            FieldView('Operación', label(operationLabels, operationType)),
            FieldView('Acreditación', label(accreditationLabels, accreditationType)),
            FieldView('Régimen de condominio', condominiumRegime ? 'Sí' : 'No'),
          ]),
          Text.rich(
            TextSpan(
              children: [
                const TextSpan(text: 'Al crear el expediente se registran '),
                TextSpan(
                  text: '$required documentos obligatorios',
                  style: TextStyle(fontWeight: FontWeight.w600, color: c.fg),
                ),
                if (conditional > 0) TextSpan(text: ', $conditional que dependen de lo que declare el cliente'),
                if (optional > 0) TextSpan(text: ' y $optional opcionales'),
                const TextSpan(text: '. Después podrás generar la liga para que el cliente los cargue.'),
              ],
            ),
            style: TextStyle(fontSize: 13.5, color: c.muted, height: 1.4),
          ),
        ],
      ),
    );
  }

  Widget _previewCard(AppColors c, List<({String type, String label, String? detail, String? note, String kind})> preview) {
    final groups = [
      ('Obligatorios', preview.where((i) => i.kind == 'required').toList()),
      ('Solo si aplica', preview.where((i) => i.kind == 'conditional').toList()),
      ('Opcionales', preview.where((i) => i.kind == 'optional').toList()),
    ];
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const CardHeader(title: 'Documentos previstos', description: 'Se ajustan con lo que capturas, según las reglas del sistema.'),
          for (final (title, items) in groups)
            if (items.isNotEmpty) ...[
              SectionTitle('$title (${items.length})'),
              for (final item in items)
                Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: c.border),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 34,
                        height: 34,
                        decoration: BoxDecoration(color: c.appBg, borderRadius: BorderRadius.circular(8)),
                        child: Icon(_previewIcons[item.type] ?? Icons.description_outlined, size: 18, color: c.muted),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              item.label,
                              style: TextStyle(fontWeight: FontWeight.w500, color: c.fg, fontSize: 13.5),
                            ),
                            if (item.detail != null) MutedText(item.detail!, size: 12),
                            if (item.note != null)
                              Text(
                                item.note!,
                                style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: c.warningText),
                              ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: 6),
            ],
          if (extraDocuments.isNotEmpty) ...[
            SectionTitle('Agregados (${extraDocuments.length})'),
            for (final name in extraDocuments)
              Container(
                margin: const EdgeInsets.only(bottom: 8),
                padding: const EdgeInsets.fromLTRB(12, 4, 4, 4),
                decoration: BoxDecoration(borderRadius: BorderRadius.circular(12), border: Border.all(color: c.border)),
                child: Row(children: [
                  Expanded(child: Text(name, style: TextStyle(color: c.fg, fontSize: 13.5))),
                  TextButton(
                    onPressed: () => changed(() => extraDocuments = extraDocuments.where((n) => n != name).toList()),
                    child: Text('Quitar', style: TextStyle(color: c.goldText)),
                  ),
                ]),
              ),
            const SizedBox(height: 6),
          ],
          Align(
            alignment: Alignment.centerLeft,
            child: Btn('Agregar otro documento', icon: Icons.add, small: true, variant: BtnVariant.secondary, onPressed: () async {
              final name = await askReason(
                context,
                title: 'Agregar otro documento',
                description: 'Para un documento que no está en la lista. Se guarda en el expediente igual que los demás y se le pide al cliente en su liga.',
                label: 'Nombre del documento',
                placeholder: 'Ej. Constancia de no adeudo de mantenimiento',
                minLength: 3,
                confirmLabel: 'Agregar',
              );
              if (name == null) return;
              final clean = name.trim();
              if (extraDocuments.any((n) => n.toLowerCase() == clean.toLowerCase())) return;
              changed(() => extraDocuments = [...extraDocuments, clean]);
            }),
          ),
          const SizedBox(height: 10),
          const Notice(
            tone: Tone.warning,
            icon: Icons.info_outline,
            text: 'Lo que no aplique a este caso se puede marcar como “No aplica”, con justificación, durante la revisión.',
          ),
        ],
      ),
    );
  }
}

class _Stepper extends StatelessWidget {
  const _Stepper({required this.current, required this.onSelect});
  final int current;
  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Row(
      children: [
        for (var i = 0; i < _steps.length; i++) ...[
          if (i > 0) Expanded(flex: 2, child: Container(height: 2, color: i + 1 <= current ? Brand.gold : c.border)),
          Flexible(
            flex: 3,
            child: InkWell(
              borderRadius: BorderRadius.circular(12),
              onTap: i + 1 < current ? () => onSelect(i + 1) : null,
              child: Padding(
                padding: const EdgeInsets.all(4),
                child: Column(
                  children: [
                    Container(
                      width: 32,
                      height: 32,
                      alignment: Alignment.center,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: i + 1 == current ? Brand.darkGold : (i + 1 < current ? Brand.gold : c.card),
                        border: Border.all(color: i + 1 == current ? Brand.darkGold : (i + 1 < current ? Brand.gold : c.border), width: 2),
                      ),
                      child: i + 1 < current
                          ? const Icon(Icons.check, size: 16, color: Brand.ink)
                          : Text(
                              '${i + 1}',
                              style: TextStyle(fontWeight: FontWeight.w600, color: i + 1 == current ? Colors.white : c.muted),
                            ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      _steps[i],
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(fontSize: 13, color: i + 1 <= current ? c.fg : c.muted, fontWeight: i + 1 == current ? FontWeight.w600 : null),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ],
    );
  }
}
