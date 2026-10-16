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
  List<CondominiumArea> areas = const [];
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
      final a = await api.getCondominiumAreas(widget.ctx.expediente.id).catchError((_) => <CondominiumArea>[]);
      if (mounted) setState(() => areas = a);
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
    final authorized = (conflicts ?? []).where((x) => x.resolved && !x.resolvedAutomatically).toList();
    final closed = (conflicts ?? []).where((x) => x.resolved && x.resolvedAutomatically).toList();
    // Control de Cambios #17: solo un directivo o administrador autoriza, con su contraseña.
    final canAuthorize = auth.can('INCONSISTENCY_AUTHORIZE');
    return TabBody(
      onRefresh: load,
      children: [
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const CardHeader(
              title: 'Consistencia entre documentos',
              description:
                  'Compara nombres, CURP, RFC, propietarios, domicilio, clave catastral, folio real y superficies entre escritura, predial, régimen de condominio, plano, identificaciones y los datos capturados. Cada diferencia queda pendiente de autorización de un directivo; solo las críticas impiden enviar el contrato a firma.',
            ),
            Wrap(spacing: 10, runSpacing: 8, crossAxisAlignment: WrapCrossAlignment.center, children: [
              AppBadge(
                open.isEmpty
                    ? 'Sin diferencias pendientes'
                    : (critical > 0 ? '$critical crítica(s) · ${open.length} pendiente(s) de autorización' : '${open.length} pendiente(s) de autorización, ninguna crítica'),
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
            if (!canAuthorize && open.isNotEmpty) ...[
              const Notice(tone: Tone.neutral, icon: Icons.lock_outline, text: 'Autorizar una inconsistencia requiere a un directivo o administrador de C21 Genera (con su contraseña).'),
              const SizedBox(height: 10),
            ],
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
                    if (canAuthorize)
                      Btn('Autorizar', icon: Icons.verified_user_outlined, small: true, variant: BtnVariant.secondary, onPressed: () => _authorize(x))
                    else
                      const Btn('Requiere directivo', icon: Icons.lock_outline, small: true, variant: BtnVariant.secondary, onPressed: null),
                  ]),
                ]),
              ),
          ]),
        ),
        if (areas.isNotEmpty) _CondominiumAreas(rows: areas),
        if (authorized.isNotEmpty)
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const CardHeader(title: 'Bitácora de inconsistencias autorizadas', description: 'Quién autorizó, cuándo y qué inconsistencia.'),
              for (final x in authorized)
                Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(x.description, style: TextStyle(color: c.fg, fontSize: 13.5)),
                    const SizedBox(height: 4),
                    Text('Autorizada${x.resolvedByName != null ? ' por ${x.resolvedByName}' : ''} · ${formatDateTime(x.resolvedAt)}',
                        style: TextStyle(fontSize: 12, color: c.successText, fontWeight: FontWeight.w500)),
                    if (x.resolutionNote != null) MutedText('Motivo: ${x.resolutionNote}', size: 12),
                  ]),
                ),
            ]),
          ),
        if (closed.isNotEmpty)
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const CardHeader(title: 'Diferencias que ya coinciden'),
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

  Future<void> _authorize(DataConflict x) async {
    var note = '';
    var password = '';
    var busy = false;
    String? error;
    await showAppSheet(
      context,
      title: 'Autorizar inconsistencia',
      builder: (ctx, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text(x.description),
        const SizedBox(height: 8),
        const MutedText('Queda registrada como "Autorizada por" tu nombre, con fecha y hora, en la bitácora del expediente.', size: 12),
        const SizedBox(height: 12),
        AppTextField(label: '¿Por qué se autoriza?', maxLines: 3, hint: 'Ej. El predial está a nombre de uno de los copropietarios.', onChanged: (v) => setSheet(() => note = v)),
        const SizedBox(height: 12),
        AppTextField(label: 'Tu contraseña', obscure: true, autofillHints: const [AutofillHints.password], onChanged: (v) => setSheet(() => password = v)),
        if (error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(error!, style: TextStyle(color: ctx.c.dangerText))),
      ]),
      footer: (ctx, setSheet) => [
        Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.pop(ctx)),
        Btn('Autorizar', icon: Icons.verified_user_outlined, loading: busy, onPressed: note.trim().length < 10 || password.isEmpty
            ? null
            : () async {
                setSheet(() {
                  busy = true;
                  error = null;
                });
                try {
                  await api.resolveConflict(x.id, note.trim(), password);
                  if (ctx.mounted) Navigator.pop(ctx);
                  if (mounted) showToast(context, 'Inconsistencia autorizada: queda registrada con tu nombre, fecha y hora.');
                  await load();
                  await widget.ctx.reload();
                } catch (err) {
                  setSheet(() {
                    busy = false;
                    error = errorText(err);
                  });
                }
              }),
      ],
    );
  }
}

/// Control de Cambios #15: m² de terreno e indivisos del Régimen de condominio vs. Escritura y Predial.
class _CondominiumAreas extends StatelessWidget {
  const _CondominiumAreas({required this.rows});
  final List<CondominiumArea> rows;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    String v(String? s) => s ?? 'sin dato';
    return AppCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const CardHeader(
          title: 'Cotejo del régimen de condominio',
          description: 'Superficie de terreno y de indivisos del Régimen de condominio, comparada con la Escritura y el Predial.',
        ),
        for (final r in rows)
          Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Expanded(child: Text(r.label, style: TextStyle(fontWeight: FontWeight.w500, color: c.fg))),
                AppBadge(r.consistent ? '✅ Consistente' : '⚠️ Difiere', tone: r.consistent ? Tone.success : Tone.warning),
              ]),
              const SizedBox(height: 6),
              MutedText('Régimen de condominio: ${v(r.condominiumRegime)}\nEscritura: ${v(r.deed)}\nPredial: ${v(r.propertyTax)}', size: 12.5),
            ]),
          ),
        const MutedText('Una diferencia no bloquea el expediente: aparece arriba como inconsistencia pendiente de autorización.', size: 12),
      ]),
    );
  }
}
