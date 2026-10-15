import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../api/api.dart' as api;
import '../../../api/client.dart';
import '../../../api/models.dart';
import '../../../labels.dart';
import '../../../state/auth.dart';
import '../../../ui/widgets.dart';
import '../expediente_detail_screen.dart';

class ConsistencyTab extends StatefulWidget {
  const ConsistencyTab({super.key, required this.ctx});
  final ExpedienteCtx ctx;

  @override
  State<ConsistencyTab> createState() => _ConsistencyTabState();
}

class _ConsistencyTabState extends State<ConsistencyTab> {
  List<DataConflict>? conflicts;
  bool running = false;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final r = await api.getDataConflicts(widget.ctx.expediente.id);
      if (mounted) setState(() => conflicts = r);
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final c = context.c;
    const order = {'CRITICAL': 0, 'WARNING': 1, 'INFO': 2};
    final open = (conflicts ?? []).where((x) => !x.resolved).toList()..sort((a, b) => (order[a.severity] ?? 1).compareTo(order[b.severity] ?? 1));
    final critical = open.where((x) => x.severity == 'CRITICAL').length;
    final closed = (conflicts ?? []).where((x) => x.resolved).toList();
    return TabBody(
      onRefresh: load,
      children: [
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const CardHeader(
              title: 'Consistencia entre documentos',
              description:
                  'Compara nombres, CURP, RFC, propietarios, domicilio, clave catastral, folio real y superficies entre escritura, predial, plano, identificaciones y los datos capturados. Cada diferencia es una POSIBLE inconsistencia para ayudarte a revisar: solo las críticas (evidencia fuerte de otra persona u otro inmueble) impiden enviar el contrato a firma.',
            ),
            Wrap(spacing: 10, runSpacing: 8, crossAxisAlignment: WrapCrossAlignment.center, children: [
              AppBadge(
                open.isEmpty
                    ? 'Sin diferencias pendientes'
                    : (critical > 0 ? '$critical crítica(s) · ${open.length} por revisar' : '${open.length} posible(s) inconsistencia(s), ninguna crítica'),
                tone: open.isEmpty ? Tone.success : (critical > 0 ? Tone.danger : Tone.warning),
              ),
              Btn('Volver a comparar', icon: Icons.manage_search, small: true, variant: BtnVariant.secondary, loading: running, onPressed: () async {
                setState(() => running = true);
                try {
                  final r = await api.runConsistencyCheck(widget.ctx.expediente.id);
                  if (mounted) {
                    setState(() => conflicts = r);
                    showToast(this.context, 'Comparación actualizada.');
                  }
                } catch (err) {
                  if (context.mounted) showToast(context, errorText(err));
                } finally {
                  if (mounted) setState(() => running = false);
                }
              }),
            ]),
            const SizedBox(height: 10),
            const MutedText(
              'La comparación usa los datos que se leen automáticamente de cada documento. Si la lectura automática no está disponible, revisa los documentos visualmente.',
              size: 12,
            ),
            const SizedBox(height: 10),
            if (conflicts == null) const MutedText('Cargando…'),
            for (final x in open)
              Container(
                margin: const EdgeInsets.only(bottom: 8),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: x.severity == 'CRITICAL' ? c.dangerBg : (x.severity == 'INFO' ? c.appBg : c.warningBg),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: x.severity == 'CRITICAL' ? c.dangerText.withValues(alpha: 0.3) : (x.severity == 'INFO' ? c.border : c.warningText.withValues(alpha: 0.3))),
                ),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  AppBadge(
                    x.severity == 'CRITICAL' ? 'Crítica: bloquea la firma' : (x.severity == 'INFO' ? 'Probablemente igual' : 'Revisar'),
                    tone: x.severity == 'CRITICAL' ? Tone.danger : (x.severity == 'INFO' ? Tone.neutral : Tone.warning),
                  ),
                  const SizedBox(height: 6),
                  Text(x.description, style: TextStyle(fontSize: 13.5, color: x.severity == 'CRITICAL' ? c.dangerText : (x.severity == 'INFO' ? c.muted : c.warningText))),
                  const SizedBox(height: 8),
                  Row(children: [
                    Expanded(child: MutedText('Detectada ${formatDateTime(x.detectedAt)}', size: 12)),
                    if (auth.can('EXTRACTED_DATA_EDIT'))
                      Btn('Marcar como revisada', small: true, variant: BtnVariant.secondary, onPressed: () async {
                        final note = await askReason(
                          context,
                          title: 'Marcar diferencia como revisada',
                          descriptionWidget: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text(x.description),
                            const SizedBox(height: 6),
                            const Text(
                                'Explica por qué es aceptable (p. ej. "el predial está a nombre de uno de los copropietarios") o corrige el dato o el documento y vuelve a comparar.'),
                          ]),
                          label: 'Explicación',
                          minLength: 10,
                        );
                        if (note == null) return;
                        try {
                          await api.resolveConflict(x.id, note);
                          if (context.mounted) showToast(context, 'Diferencia marcada como revisada.');
                          await load();
                          await widget.ctx.reload();
                        } catch (err) {
                          if (context.mounted) showToast(context, errorText(err));
                        }
                      }),
                  ]),
                ]),
              ),
          ]),
        ),
        if (closed.isNotEmpty)
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const CardHeader(title: 'Diferencias resueltas'),
              for (final x in closed)
                Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(x.description, style: TextStyle(color: c.fg, fontSize: 13.5)),
                    const SizedBox(height: 4),
                    MutedText('${x.resolvedAutomatically ? 'Se cerró sola: los datos ya coinciden.' : 'Resuelta: ${x.resolutionNote ?? ''}'} · ${formatDateTime(x.resolvedAt)}', size: 12),
                  ]),
                ),
            ]),
          ),
      ],
    );
  }
}
