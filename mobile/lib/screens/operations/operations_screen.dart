import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../api/client.dart';
import '../../api/operations_api.dart' as ops;
import '../../labels.dart';
import '../../money.dart';
import '../../ui/shell.dart';
import '../../ui/widgets.dart';

Tone operationTone(ops.OperationItem o) => switch (o.status) {
      'CANCELLED' => Tone.danger,
      'CLOSED' => o.docStatus == 'COMPLETE_VALIDATED' ? Tone.success : Tone.warning,
      'CLOSING_REVIEW' => Tone.info,
      _ => Tone.neutral,
    };

/// CC#32: operaciones de cierre (cartera propia y propiedades externas) con su estado comercial y
/// documental. El alta de propiedades externas y la matriz documental se manejan desde la web.
class OperationsScreen extends StatefulWidget {
  const OperationsScreen({super.key});

  @override
  State<OperationsScreen> createState() => _OperationsScreenState();
}

class _OperationsScreenState extends State<OperationsScreen> {
  ops.OperationList? data;
  String? error;
  String status = '';
  String origin = '';
  bool pending = false;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final r = await ops.listOperations(status: status, origin: origin, pendingDocs: pending);
      if (mounted) setState(() => (data = r, error = null));
    } catch (e) {
      if (mounted) setState(() => error = errorText(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final d = data;
    return AppScaffold(
      title: 'Operaciones y cierres',
      route: '/operaciones',
      body: PageBody(
        subtitle: 'Cierre comercial y revisión documental (operación, PLD y PROFECO).',
        onRefresh: load,
        children: [
          if (d != null)
            StatGrid(children: [
              StatCard(icon: Icons.check_circle_outline, tone: Tone.success, value: '${d.totals.closed}', label: 'Cerradas'),
              StatCard(icon: Icons.warning_amber_outlined, tone: Tone.warning, value: '${d.totals.closedPendingDocs}', label: 'Cerradas con documentación pendiente'),
              StatCard(icon: Icons.fact_check_outlined, tone: Tone.info, value: '${d.totals.inReview}', label: 'En revisión de cierre'),
              StatCard(icon: Icons.public, value: '${d.totals.external}', label: 'Propiedades externas'),
            ]),
          const SizedBox(height: 12),
          FilterChips<String>(
            value: status,
            wrap: true,
            options: [(id: '', label: 'Todas', count: null), for (final e in ops.operationStatusLabels.entries) (id: e.key, label: e.value, count: null)],
            onChanged: (v) => setState(() {
              status = v;
              load();
            }),
          ),
          const SizedBox(height: 8),
          FilterChips<String>(
            value: origin,
            options: [(id: '', label: 'Propias y externas', count: null), for (final e in ops.originLabels.entries) (id: e.key, label: e.value, count: null)],
            onChanged: (v) => setState(() {
              origin = v;
              load();
            }),
          ),
          ToggleRow(
            label: 'Solo con documentación pendiente',
            value: pending,
            onChanged: (v) => setState(() {
              pending = v;
              load();
            }),
          ),
          const SizedBox(height: 8),
          if (error != null && d == null) LoadError(message: error!, onRetry: load),
          if (d == null && error == null) const StateMessage(kind: StateKind.loading, title: 'Cargando operaciones…'),
          if (d != null && d.items.isEmpty) const StateMessage(kind: StateKind.empty, title: 'Sin operaciones', description: 'Inicia el cierre desde un expediente o registra una propiedad externa en la web.'),
          for (final o in d?.items ?? <ops.OperationItem>[])
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: AppCard(
                onTap: () => context.push('/operaciones/${o.id}').then((_) => load()),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [
                    Expanded(child: Text('${o.folio} · ${o.propertyDescription}', style: const TextStyle(fontWeight: FontWeight.w600))),
                    AppBadge(o.displayStatus, tone: operationTone(o)),
                  ]),
                  const SizedBox(height: 4),
                  MutedText('${ops.originLabels[o.origin] ?? o.origin} · Asesor: ${o.advisorName ?? '—'}', size: 12),
                  MutedText('Documentación: ${ops.docStatusLabels[o.docStatus] ?? o.docStatus} · ${o.requirementsSatisfied}/${o.requirementsTotal} cumplidos', size: 12),
                  if (o.closedOn != null) MutedText('Cerrada el ${formatDate(o.closedOn)} · ${o.closedByName ?? ''}', size: 12),
                  if (o.amount != null) MutedText(formatMoneyWithCurrency(o.amount, currency: o.currency ?? 'MXN'), size: 12),
                ]),
              ),
            ),
        ],
      ),
    );
  }
}
