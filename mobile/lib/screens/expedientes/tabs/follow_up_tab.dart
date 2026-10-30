import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../api/api.dart' as api;
import '../../../api/client.dart';
import '../../../api/models.dart';
import '../../../labels.dart';
import '../../../state/auth.dart';
import '../../../ui/closed_badge.dart';
import '../../../ui/widgets.dart';
import '../expediente_detail_screen.dart';

class FollowUpTab extends StatefulWidget {
  const FollowUpTab({super.key, required this.ctx});
  final ExpedienteCtx ctx;

  @override
  State<FollowUpTab> createState() => _FollowUpTabState();
}

class _FollowUpTabState extends State<FollowUpTab> {
  ComplianceChecklist? compliance;

  /// null = cargando; "not_started" = el contrato aún no se firma por todas las partes.
  Object? closing;
  List<ClosingTask> tasks = [];
  List<ClosingNote> notes = [];
  final noteCtrl = TextEditingController();

  String get id => widget.ctx.expediente.id;

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    noteCtrl.dispose();
    super.dispose();
  }

  Future<void> load() async {
    api.getComplianceChecklist(id).then((r) => mounted ? setState(() => compliance = r) : null).catchError((_) => null);
    try {
      final c = await api.getClosingCase(id);
      final results = await Future.wait([api.getClosingTasks(id), api.getClosingNotes(id)]);
      if (mounted) {
        setState(() {
          closing = c;
          tasks = results[0] as List<ClosingTask>;
          notes = results[1] as List<ClosingNote>;
        });
      }
    } catch (err) {
      if (err is ApiError && err.status == 404 && mounted) setState(() => closing = 'not_started');
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final c = context.c;
    final cl = closing;
    final closedOn = cl is ClosingCase ? (cl.closedOn ?? widget.ctx.expediente.closingCompletedOn) : null;
    return TabBody(
      onRefresh: load,
      children: [
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const CardHeader(title: 'Seguimiento posterior a la firma', description: 'Se abre automáticamente cuando el contrato queda firmado por todas las partes.'),
            if (cl == null) const MutedText('Cargando…'),
            if (cl == 'not_started') const MutedText('Todavía no inicia: el contrato no se ha firmado por todas las partes.'),
            if (cl is ClosingCase) ...[
              Wrap(spacing: 8, runSpacing: 6, crossAxisAlignment: WrapCrossAlignment.center, children: [
                AppBadge(label(closingStatusLabels, cl.status), tone: cl.status == 'COMPLETED' ? Tone.success : Tone.info),
                // CC#32: operación cerrada, con fecha de cierre y asesor responsable.
                if (cl.status == 'COMPLETED') ClosedBadge(closedOn: closedOn ?? '', withDate: closedOn != null),
              ]),
              if (cl.status == 'COMPLETED') ...[
                const SizedBox(height: 6),
                MutedText(
                  '${closedOn != null ? 'Fecha de cierre: ${formatClosingDate(closedOn, long: true)}' : 'Fecha de cierre sin registrar'}'
                  ' · Asesor responsable: ${widget.ctx.expediente.assignedAdvisorName ?? 'sin asesor asignado'}',
                  size: 12.5,
                ),
              ],
              const SizedBox(height: 10),
              for (final t in tasks)
                Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
                  child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Icon(t.done ? Icons.check_circle : Icons.radio_button_unchecked, size: 18, color: t.done ? c.successText : c.muted),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(t.title, style: TextStyle(color: c.fg, fontSize: 13.5)),
                        MutedText(
                          t.done
                              ? 'Hecho ${formatDateTime(t.doneAt)}${t.doneNote != null ? ' — ${t.doneNote}' : ''}'
                              : (t.dueDate != null ? 'Fecha límite: ${formatDate(t.dueDate)}' : 'Pendiente'),
                          size: 12,
                        ),
                        if (!t.done && auth.can('EXPEDIENT_EDIT'))
                          Padding(
                            padding: const EdgeInsets.only(top: 6),
                            child: Btn('Marcar como hecha', small: true, variant: BtnVariant.secondary, onPressed: () async {
                              try {
                                await api.completeClosingTask(id, t.id);
                                if (context.mounted) showToast(context, 'Tarea marcada como hecha.');
                                load();
                              } catch (err) {
                                if (context.mounted) showToast(context, errorText(err));
                              }
                            }),
                          ),
                      ]),
                    ),
                  ]),
                ),
              const SizedBox(height: 10),
              Text('Notas', style: TextStyle(fontWeight: FontWeight.w500, color: c.fg)),
              const SizedBox(height: 8),
              for (final n in notes)
                Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(n.note, style: TextStyle(color: c.fg, fontSize: 13.5)),
                    MutedText(formatDateTime(n.createdAt), size: 12),
                  ]),
                ),
              if (auth.can('EXPEDIENT_EDIT'))
                Row(children: [
                  Expanded(child: AppTextField(controller: noteCtrl, hint: 'Agregar nota…', onChanged: (_) => setState(() {}))),
                  const SizedBox(width: 8),
                  Btn('Agregar', small: true, onPressed: noteCtrl.text.trim().isEmpty
                      ? null
                      : () async {
                          try {
                            await api.addClosingNote(id, noteCtrl.text.trim());
                            noteCtrl.clear();
                            load();
                          } catch (err) {
                            if (context.mounted) showToast(context, errorText(err));
                          }
                        }),
                ]),
            ],
          ]),
        ),
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const CardHeader(title: 'Control documental de cumplimiento', description: 'Verificación interna; no constituye una garantía legal frente al cliente.'),
            if (compliance == null)
              const MutedText('Cargando…')
            else ...[
              Align(
                alignment: Alignment.centerLeft,
                child: AppBadge(compliance!.allPassed ? 'Todo en orden' : 'Hay puntos pendientes', tone: compliance!.allPassed ? Tone.success : Tone.warning),
              ),
              const SizedBox(height: 10),
              for (final i in compliance!.items)
                Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
                  child: Row(children: [
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(label(complianceLabels, i.code), style: TextStyle(fontWeight: FontWeight.w500, color: c.fg, fontSize: 13.5)),
                        MutedText(i.detail, size: 12),
                      ]),
                    ),
                    const SizedBox(width: 8),
                    AppBadge(i.passed ? 'Cumplido' : 'Pendiente', tone: i.passed ? Tone.success : Tone.warning),
                  ]),
                ),
            ],
          ]),
        ),
      ],
    );
  }
}
