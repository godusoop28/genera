import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../api/models.dart';
import '../../labels.dart';
import '../../state/auth.dart';
import '../../ui/property_key_sheet.dart';
import '../../ui/shell.dart';
import '../../ui/widgets.dart';

/// Control de Cambios #27 (adenda, como /claves en la web): consecutivos en uso y disponibles por
/// combinación, expedientes sin clave (para migrarlos uno por uno) y el listado de claves asignadas.
class PropertyKeysScreen extends StatefulWidget {
  const PropertyKeysScreen({super.key});

  @override
  State<PropertyKeysScreen> createState() => _PropertyKeysScreenState();
}

class _PropertyKeysScreenState extends State<PropertyKeysScreen> {
  String operationType = '';
  String advisorId = '';
  String prefix = '';
  List<AdvisorOption> advisors = const [];
  PropertyKeyOverview? data;
  String? error;
  Timer? _debounce;
  int _request = 0;

  @override
  void initState() {
    super.initState();
    load();
    api.listAssignableAdvisors().then((a) {
      if (mounted) setState(() => advisors = a);
    }).catchError((_) {});
  }

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }

  Future<void> load() async {
    final id = ++_request;
    try {
      final result = await api.getPropertyKeys(
          operationType: operationType.isEmpty ? null : operationType, advisorId: advisorId.isEmpty ? null : advisorId, prefix: prefix);
      if (mounted && id == _request) {
        setState(() {
          data = result;
          error = null;
        });
      }
    } catch (err) {
      if (mounted && id == _request) setState(() => error = errorText(err));
    }
  }

  Future<void> edit(String expedienteId, String folio, String? currentKey, String? suggestion) async {
    final saved = await showPropertyKeySheet(context, expedienteId: expedienteId, folio: folio, currentKey: currentKey, suggestion: suggestion);
    if (saved && mounted) {
      showToast(context, 'Clave asignada a $folio.');
      await load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    if (!auth.can('EXPEDIENT_KEY_ASSIGN')) {
      return const Restricted(title: 'Claves de inmuebles', route: '/claves', description: 'Solo el administrador o el directivo consultan y asignan claves.');
    }
    final d = data;
    return AppScaffold(
      title: 'Claves de inmuebles',
      route: '/claves',
      body: PageBody(
        onRefresh: load,
        subtitle: 'Tipo + operación + iniciales del asesor + consecutivo (p. ej. CVEA03). Consulta qué consecutivos están en uso y asigna la clave a las propiedades que aún no la tienen.',
        children: [
          AppSelect<String>(
            value: operationType,
            options: [(value: '', label: 'Venta y renta'), for (final e in operationLabels.entries) (value: e.key, label: e.value)],
            onChanged: (v) {
              setState(() => operationType = v);
              load();
            },
          ),
          const SizedBox(height: 10),
          AppSelect<String>(
            value: advisorId,
            options: [(value: '', label: 'Todos los asesores'), for (final a in advisors) (value: a.id, label: a.name)],
            onChanged: (v) {
              setState(() => advisorId = v);
              load();
            },
          ),
          const SizedBox(height: 10),
          AppTextField(
            hint: 'Combinación, p. ej. CVEA',
            maxLength: 18,
            onChanged: (v) {
              prefix = v.toUpperCase().replaceAll(RegExp('[^A-Z]'), '');
              _debounce?.cancel();
              _debounce = Timer(const Duration(milliseconds: 300), load);
            },
          ),
          const SizedBox(height: 12),
          if (error != null && d == null)
            LoadError(message: error!, onRetry: load)
          else if (d == null)
            const StateMessage(kind: StateKind.loading, title: 'Cargando claves…')
          else
            ..._content(context, d),
        ],
      ),
    );
  }

  List<Widget> _content(BuildContext context, PropertyKeyOverview d) {
    String pad(int n) => n.toString().padLeft(2, '0');
    return [
      AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const CardHeader(title: 'Consecutivos por combinación', description: 'En uso, huecos libres y el siguiente que se asignará automáticamente.'),
          if (d.combinations.isEmpty) const MutedText('Sin claves con este filtro'),
          for (final k in d.combinations) ...[
            Text(k.prefix, style: const TextStyle(fontFamily: 'GeistMono', fontWeight: FontWeight.w600)),
            MutedText('En uso: ${k.used.isEmpty ? 'ninguno' : k.used.map((n) => '${k.prefix}${pad(n)}').join(', ')}', size: 12),
            MutedText('Disponibles: ${[...k.gaps.map(pad), if (k.nextKey != null) '${k.nextKey!.substring(k.prefix.length)}…'].join(', ')}', size: 12),
            MutedText('Siguiente automática: ${k.nextKey ?? '—'}', size: 12),
            const SizedBox(height: 10),
          ],
        ]),
      ),
      const SizedBox(height: 16),
      AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          CardHeader(
            title: 'Expedientes sin clave (${d.withoutKey.length})',
            description: 'Asigna a cada uno su clave; la sugerida es el siguiente consecutivo de su combinación, pero puedes capturar otra.',
          ),
          if (d.withoutKey.isEmpty) const MutedText('Todos los expedientes de este filtro ya tienen clave.'),
          for (final p in d.withoutKey)
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text('${p.folio} · ${p.ownerDisplayName}', style: const TextStyle(fontSize: 14)),
              subtitle: Text(
                [p.propertyTypeLabel ?? 'Tipo sin clasificar', label(operationLabels, p.operationType), p.advisorName ?? 'Sin asesor', if (p.suggestedKey != null) 'Sugerida: ${p.suggestedKey}']
                    .join(' · '),
                style: const TextStyle(fontSize: 12),
              ),
              trailing: Btn('Asignar', small: true, onPressed: () => edit(p.expedienteId, p.folio, null, p.suggestedKey)),
              onTap: () => context.push('/expedientes/${p.expedienteId}'),
            ),
        ]),
      ),
      const SizedBox(height: 16),
      AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          CardHeader(title: 'Claves asignadas (${d.assigned.length})'),
          if (d.assigned.isEmpty) const MutedText('Sin claves con este filtro'),
          for (final a in d.assigned)
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(a.key, style: const TextStyle(fontFamily: 'GeistMono', fontWeight: FontWeight.w600)),
              subtitle: Text(
                [a.folio, a.ownerDisplayName, a.propertyTypeLabel ?? 'Sin clasificar', label(operationLabels, a.operationType), a.advisorName ?? 'Sin asesor'].join(' · '),
                style: const TextStyle(fontSize: 12),
              ),
              trailing: IconButton(tooltip: 'Corregir clave ${a.key}', icon: const Icon(Icons.edit_outlined), onPressed: () => edit(a.expedienteId, a.folio, a.key, null)),
              onTap: () => context.push('/expedientes/${a.expedienteId}'),
            ),
        ]),
      ),
    ];
  }
}
