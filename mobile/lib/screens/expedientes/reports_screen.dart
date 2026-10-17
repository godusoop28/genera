import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../api/models.dart';
import '../../labels.dart';
import '../../state/auth.dart';
import '../../ui/shell.dart';
import '../../ui/widgets.dart';

/// Control de Cambios #24 (como /reportes en la web): cartera en dos niveles. Nivel 1: tipo de
/// propiedad + operación. Nivel 2: asesor asignado. De cualquier combinación se abre el listado
/// de expedientes ya filtrado.
class ReportsScreen extends StatefulWidget {
  const ReportsScreen({super.key});

  @override
  State<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends State<ReportsScreen> {
  String? propertyCaseType;
  String? operationType;
  String? advisorId;
  PortfolioReport? data;
  String? error;
  int _request = 0;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    final id = ++_request;
    try {
      final result = await api.getPortfolioReport(propertyCaseType: propertyCaseType, operationType: operationType, advisorId: advisorId);
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

  void selectCell(String? type, String? operation) {
    final same = type == propertyCaseType && operation == operationType;
    setState(() {
      propertyCaseType = same ? null : type;
      operationType = same ? null : operation;
    });
    load();
  }

  void selectAdvisor(String id) {
    setState(() => advisorId = advisorId == id ? null : id);
    load();
  }

  String get listRoute {
    final params = {'tipo': propertyCaseType, 'operacion': operationType, 'asesor': advisorId}..removeWhere((_, v) => v == null);
    return params.isEmpty ? '/expedientes' : Uri(path: '/expedientes', queryParameters: params).toString();
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    if (!auth.can('EXPEDIENT_VIEW_ALL') && !auth.can('EXPEDIENT_VIEW_OWN')) {
      return const Restricted(title: 'Reportes de cartera', route: '/reportes', description: 'Tu usuario no tiene permiso para consultar expedientes.');
    }
    final d = data;
    return AppScaffold(
      title: 'Reportes de cartera',
      route: '/reportes',
      body: PageBody(
        onRefresh: load,
        subtitle: auth.can('EXPEDIENT_VIEW_ALL')
            ? 'Expedientes por tipo de propiedad, operación y asesor asignado. Elige una combinación para ver sus expedientes.'
            : 'Tus expedientes asignados por tipo de propiedad y operación.',
        children: [
          if (error != null && d == null)
            LoadError(message: error!, onRetry: load)
          else if (d == null)
            const StateMessage(kind: StateKind.loading, title: 'Cargando la cartera…')
          else
            ..._content(context, d),
        ],
      ),
    );
  }

  List<Widget> _content(BuildContext context, PortfolioReport d) {
    final c = context.c;
    final selection = [
      if (propertyCaseType != null) label(propertyTypeLabels, propertyCaseType),
      if (operationType != null) label(operationLabels, operationType),
      if (advisorId != null) d.byAdvisor.where((a) => (a.advisorId ?? api.unassignedAdvisor) == advisorId).firstOrNull?.name ?? 'Asesor',
    ];
    final maxAdvisor = d.byAdvisor.fold(1, (m, a) => a.counts.total > m ? a.counts.total : m);

    Widget cell(String text, {required bool active, required VoidCallback onTap, bool strong = false, bool muted = false}) => Material(
      color: active ? Brand.gold : Colors.transparent,
      borderRadius: BorderRadius.circular(10),
      child: InkWell(
        borderRadius: BorderRadius.circular(10),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            border: strong ? Border.all(color: active ? Brand.gold : c.border) : null,
          ),
          alignment: Alignment.center,
          child: Text(
            text,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: strong ? 17 : 13,
              fontWeight: strong ? FontWeight.w600 : FontWeight.w500,
              color: active ? Brand.ink : (muted ? c.muted : c.fg),
            ),
          ),
        ),
      ),
    );

    return [
      Notice(
        tone: Tone.neutral,
        text: selection.isEmpty ? 'Toda la cartera' : 'Filtro: ${selection.join(' · ')}',
        action: Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            if (selection.isNotEmpty)
              Btn(
                'Quitar filtros',
                small: true,
                variant: BtnVariant.secondary,
                onPressed: () {
                  setState(() {
                    propertyCaseType = null;
                    operationType = null;
                    advisorId = null;
                  });
                  load();
                },
              ),
            Btn('Ver ${d.total.total} expediente${d.total.total == 1 ? '' : 's'}', small: true, onPressed: () => context.push(listRoute)),
          ],
        ),
      ),
      const SizedBox(height: 16),
      StatGrid(
        children: [
          StatCard(icon: Icons.folder_open_outlined, value: '${d.total.total}', label: 'Expedientes', hint: 'Con el filtro elegido'),
          StatCard(icon: Icons.schedule, tone: Tone.warning, value: '${d.total.pending}', label: 'Pendientes', hint: 'Esperan algo del cliente'),
          StatCard(icon: Icons.refresh, tone: Tone.info, value: '${d.total.inReview}', label: 'En revisión', hint: 'Documentos por revisar'),
          StatCard(icon: Icons.check_circle_outline, tone: Tone.success, value: '${d.total.complete}', label: 'Completos', hint: 'Documentación aprobada'),
        ],
      ),
      const SizedBox(height: 16),
      AppCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const CardHeader(title: 'Nivel 1 · Tipo de propiedad y operación', description: 'Toca una celda para filtrar; vuelve a tocarla para quitar el filtro.'),
            Table(
              columnWidths: const {0: FlexColumnWidth(1.6), 1: FlexColumnWidth(), 2: FlexColumnWidth(), 3: FlexColumnWidth(0.8)},
              defaultVerticalAlignment: TableCellVerticalAlignment.middle,
              children: [
                TableRow(
                  children: [
                    const MutedText('Tipo', size: 12),
                    for (final op in operationLabels.keys)
                      Padding(
                        padding: const EdgeInsets.all(3),
                        child: cell(operationLabels[op]!, active: propertyCaseType == null && operationType == op, onTap: () => selectCell(null, op)),
                      ),
                    const MutedText('Total', size: 12, center: true),
                  ],
                ),
                for (final type in propertyTypeLabels.keys)
                  TableRow(
                    children: [
                      Padding(
                        padding: const EdgeInsets.all(3),
                        child: cell(propertyTypeLabels[type]!, active: propertyCaseType == type && operationType == null, onTap: () => selectCell(type, null)),
                      ),
                      for (final op in operationLabels.keys)
                        Padding(
                          padding: const EdgeInsets.all(3),
                          child: cell(
                            '${d.cell(type, op)}',
                            active: propertyCaseType == type && operationType == op,
                            onTap: () => selectCell(type, op),
                            strong: true,
                            muted: d.cell(type, op) == 0,
                          ),
                        ),
                      Text(
                        '${operationLabels.keys.fold(0, (sum, op) => sum + d.cell(type, op))}',
                        textAlign: TextAlign.center,
                        style: TextStyle(fontWeight: FontWeight.w600, color: c.fg),
                      ),
                    ],
                  ),
              ],
            ),
          ],
        ),
      ),
      const SizedBox(height: 16),
      AppCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const CardHeader(title: 'Nivel 2 · Asesor asignado', description: 'Expedientes por asesor con el tipo y la operación elegidos. Toca un asesor para filtrar.'),
            if (d.byAdvisor.isEmpty)
              const Padding(padding: EdgeInsets.symmetric(vertical: 20), child: MutedText('Sin expedientes con este filtro', center: true))
            else
              for (final row in d.byAdvisor)
                Material(
                  color: advisorId == (row.advisorId ?? api.unassignedAdvisor) ? Brand.gold.withValues(alpha: 0.15) : Colors.transparent,
                  borderRadius: BorderRadius.circular(10),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(10),
                    onTap: () => selectAdvisor(row.advisorId ?? api.unassignedAdvisor),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 10),
                      child: Row(
                        children: [
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(row.name, style: TextStyle(fontWeight: FontWeight.w500, color: c.fg), maxLines: 1, overflow: TextOverflow.ellipsis),
                                MutedText(
                                  row.counts.total == 0
                                      ? 'Sin expedientes'
                                      : '${row.counts.pending} pendiente${row.counts.pending == 1 ? '' : 's'} · ${row.counts.inReview} en revisión · ${row.counts.complete} completo${row.counts.complete == 1 ? '' : 's'}',
                                  size: 12,
                                ),
                                const SizedBox(height: 6),
                                ClipRRect(
                                  borderRadius: BorderRadius.circular(4),
                                  child: LinearProgressIndicator(value: row.counts.total / maxAdvisor, minHeight: 5, backgroundColor: c.border, color: Brand.gold),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 12),
                          Text('${row.counts.total}', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600, color: c.fg)),
                        ],
                      ),
                    ),
                  ),
                ),
          ],
        ),
      ),
    ];
  }
}
