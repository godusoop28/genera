import 'package:flutter/material.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../api/models.dart';
import '../../labels.dart';
import '../../ui/widgets.dart';

/// Control de Cambios #30: horas en intervalos de 15 min para programar actividades (hora de CDMX).
final _times = [
  for (var h = 7; h <= 21; h++)
    for (final m in const [0, 15, 30, 45]) (value: '${h.toString().padLeft(2, '0')}:${m.toString().padLeft(2, '0')}', label: '${h.toString().padLeft(2, '0')}:${m.toString().padLeft(2, '0')}'),
];

/// "2026-10-26" + "15:30" en hora de Ciudad de México (UTC-6 fijo) → instante ISO.
String mxToIso(String date, String time) => DateTime.parse('${date}T$time:00-06:00').toUtc().toIso8601String();

String _todayMx() => DateTime.now().toUtc().subtract(const Duration(hours: 6)).toIso8601String().substring(0, 10);

/// La etapa que sugieren las actividades registradas (el asesor decide si la aplica).
class SuggestedStageNotice extends StatelessWidget {
  const SuggestedStageNotice({super.key, required this.detail, required this.onApply});
  final LeadDetail detail;
  final Future<void> Function(String stage) onApply;

  @override
  Widget build(BuildContext context) {
    final stage = detail.suggestedStage;
    if (stage == null) return const SizedBox.shrink();
    return Notice(
      tone: Tone.gold,
      icon: Icons.lightbulb_outline,
      text: 'Por las actividades registradas, este lead parece estar en "${label(stageLabels, stage)}".',
      action: Btn('Mover a ${label(stageLabels, stage)}', small: true, onPressed: () => onApply(stage)),
    );
  }
}

/// Actividades del lead: programadas (las vencidas destacadas) y realizadas.
class LeadTasksCard extends StatelessWidget {
  const LeadTasksCard({super.key, required this.detail, required this.canManage, required this.onChanged});
  final LeadDetail detail;
  final bool canManage;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final pending = detail.tasks.where((t) => t.status == 'SCHEDULED').toList()..sort((a, b) => (a.scheduledAt ?? '').compareTo(b.scheduledAt ?? ''));
    final done = detail.tasks.where((t) => t.status != 'SCHEDULED').toList();
    return AppCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        CardHeader(
          title: 'Actividades',
          description: 'Llamadas, visitas, propuestas… Las programadas aparecen en la agenda.',
          action: canManage ? Btn('Nueva', small: true, icon: Icons.add, onPressed: () => showNewTaskSheet(context, detail.lead, onChanged)) : null,
        ),
        if (pending.isEmpty) const MutedText('No hay actividades programadas. Programa la siguiente para no perder el seguimiento.'),
        for (final t in pending)
          Container(
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: t.overdue ? c.dangerBg : null,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: t.overdue ? c.dangerText.withValues(alpha: 0.4) : c.border),
            ),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(t.title ?? label(taskTypeLabels, t.type), style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
              const SizedBox(height: 2),
              MutedText(
                '${label(taskTypeLabels, t.type)} · ${t.scheduledAt != null ? formatInstantMx(t.scheduledAt!) : '—'}${t.durationMinutes != null ? ' · ${t.durationMinutes} min' : ''}${t.advisor != null ? ' · ${t.advisor!.name}' : ''}',
                size: 12.5,
              ),
              if (t.overdue) Padding(padding: const EdgeInsets.only(top: 4), child: Text('Vencida sin resultado', style: TextStyle(fontSize: 12.5, color: c.dangerText, fontWeight: FontWeight.w600))),
              if (t.notesBefore != null) Padding(padding: const EdgeInsets.only(top: 4), child: Text(t.notesBefore!, style: TextStyle(fontSize: 13.5, color: c.fg))),
              if (canManage) ...[
                const SizedBox(height: 8),
                Wrap(spacing: 8, children: [
                  Btn('Registrar resultado', small: true, variant: BtnVariant.secondary, onPressed: () => showCompleteTaskSheet(context, t, onChanged)),
                  Btn('Cancelar', small: true, variant: BtnVariant.ghost, onPressed: () async {
                    try {
                      await api.cancelLeadTask(t.id);
                      if (context.mounted) showToast(context, 'Actividad cancelada');
                      onChanged();
                    } catch (err) {
                      if (context.mounted) showToast(context, crmErrorText(err));
                    }
                  }),
                ]),
              ],
            ]),
          ),
        if (done.isNotEmpty)
          Expandable(
            title: 'Realizadas y canceladas (${done.length})',
            child: Column(children: [
              for (final t in done)
                Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(t.title ?? label(taskTypeLabels, t.type), style: TextStyle(fontSize: 13.5, color: c.fg, fontWeight: FontWeight.w500)),
                        if (t.outcomeNotes != null) Text(t.outcomeNotes!, style: TextStyle(fontSize: 13, color: c.fg)),
                        MutedText('${t.scheduledAt != null ? formatInstantMx(t.scheduledAt!) : ''}${t.nextAction != null ? ' · Siguiente: ${t.nextAction}' : ''}', size: 12),
                      ]),
                    ),
                    AppBadge(t.status == 'CANCELLED' ? 'Cancelada' : label(taskResultLabels, t.result), tone: t.status == 'CANCELLED' ? Tone.neutral : Tone.success),
                  ]),
                ),
            ]),
          ),
      ]),
    );
  }
}

/// Programar una actividad o registrar una ya realizada.
Future<void> showNewTaskSheet(BuildContext context, LeadItem lead, VoidCallback onChanged) {
  var done = false;
  var type = 'CALL';
  var title = '';
  var date = _todayMx();
  var time = '10:00';
  var duration = '30';
  var reminder = '60';
  var notes = '';
  var result = '';
  var outcome = '';
  var nextAction = '';
  String? nextDate;
  var nextTime = '10:00';
  var saving = false;
  String? error;

  return showAppSheet(
    context,
    title: 'Nueva actividad',
    builder: (ctx, setState) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Row(children: [
        Expanded(child: ChoiceTile(selected: !done, onTap: () => setState(() => done = false), title: 'Programar')),
        const SizedBox(width: 8),
        Expanded(child: ChoiceTile(selected: done, onTap: () => setState(() => done = true), title: 'Ya se realizó')),
      ]),
      const SizedBox(height: 14),
      AppSelect<String>(label: 'Tipo de actividad', value: type, options: [for (final e in taskTypeLabels.entries) (value: e.key, label: e.value)], onChanged: (v) => setState(() => type = v)),
      const SizedBox(height: 12),
      AppTextField(label: type == 'OTHER' ? 'Descripción (obligatoria)' : 'Título (opcional)', onChanged: (v) => title = v),
      const SizedBox(height: 12),
      DateField(label: done ? 'Fecha en que se realizó' : 'Fecha', value: date, onChanged: (v) => setState(() => date = v ?? date)),
      const SizedBox(height: 12),
      AppSelect<String>(label: 'Hora (CDMX)', value: time, options: _times, onChanged: (v) => setState(() => time = v)),
      const SizedBox(height: 12),
      AppTextField(label: 'Duración estimada (min)', value: duration, keyboardType: TextInputType.number, onChanged: (v) => duration = v),
      if (!done) ...[
        const SizedBox(height: 12),
        AppSelect<String>(label: 'Recordatorio', value: reminder, options: [for (final e in reminderLabels.entries) (value: e.key, label: e.value)], onChanged: (v) => setState(() => reminder = v)),
      ],
      const SizedBox(height: 12),
      AppTextField(label: 'Notas previas', maxLines: 2, onChanged: (v) => notes = v),
      if (done) ...[
        const SizedBox(height: 12),
        AppSelect<String>(label: 'Resultado', value: result, options: [(value: '', label: 'Elige el resultado'), for (final e in taskResultLabels.entries) (value: e.key, label: e.value)], onChanged: (v) => setState(() => result = v)),
        const SizedBox(height: 12),
        AppTextField(label: 'Resumen / notas', maxLines: 3, onChanged: (v) => outcome = v),
        const SizedBox(height: 12),
        AppTextField(label: 'Siguiente acción', onChanged: (v) => nextAction = v),
        const SizedBox(height: 12),
        DateField(label: 'Fecha de siguiente contacto (opcional)', value: nextDate, onChanged: (v) => setState(() => nextDate = v)),
        if (nextDate != null) ...[
          const SizedBox(height: 12),
          AppSelect<String>(label: 'Hora del siguiente contacto', value: nextTime, options: _times, onChanged: (v) => setState(() => nextTime = v)),
        ],
      ],
      if (error != null) ...[const SizedBox(height: 12), Notice(tone: Tone.danger, text: error)],
    ]),
    footer: (ctx, setState) => [
      Btn('Cancelar', variant: BtnVariant.secondary, onPressed: () => Navigator.of(ctx).pop()),
      Btn(done ? 'Registrar' : 'Programar', loading: saving, onPressed: () async {
        if (type == 'OTHER' && title.trim().isEmpty) return setState(() => error = 'Describe la actividad.');
        if (done && result.isEmpty && type != 'NOTE') return setState(() => error = 'Indica el resultado.');
        setState(() {
          saving = true;
          error = null;
        });
        try {
          await api.createLeadTask(lead.id, {
            'type': type,
            'title': title.trim().isEmpty ? null : title.trim(),
            'scheduledAt': mxToIso(date, time),
            'durationMinutes': int.tryParse(duration),
            'reminderMinutes': done || reminder.isEmpty ? null : int.parse(reminder),
            'notesBefore': notes.trim().isEmpty ? null : notes.trim(),
            'propertyKey': lead.property?.key,
            'propertyTitle': lead.property?.title,
            'done': done,
            'result': done && result.isNotEmpty ? result : null,
            'outcomeNotes': done && outcome.trim().isNotEmpty ? outcome.trim() : null,
            'nextAction': done && nextAction.trim().isNotEmpty ? nextAction.trim() : null,
            'nextContactAt': done && nextDate != null ? mxToIso(nextDate!, nextTime) : null,
          });
          if (ctx.mounted) {
            Navigator.of(ctx).pop();
            showToast(context, done ? 'Actividad registrada' : 'Actividad programada');
          }
          onChanged();
        } catch (err) {
          setState(() {
            saving = false;
            error = crmErrorText(err);
          });
        }
      }),
    ],
  );
}

/// Registrar el resultado de una actividad programada (y, si se indica, programar la siguiente).
Future<void> showCompleteTaskSheet(BuildContext context, LeadTaskItem task, VoidCallback onChanged) {
  var result = '';
  var minutes = task.durationMinutes?.toString() ?? '';
  var outcome = '';
  var nextAction = '';
  String? nextDate;
  var nextTime = '10:00';
  var saving = false;
  String? error;
  return showAppSheet(
    context,
    title: 'Resultado: ${label(taskTypeLabels, task.type)}',
    builder: (ctx, setState) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      AppSelect<String>(label: 'Resultado', value: result, options: [(value: '', label: 'Elige el resultado'), for (final e in taskResultLabels.entries) (value: e.key, label: e.value)], onChanged: (v) => setState(() => result = v)),
      const SizedBox(height: 12),
      AppTextField(label: 'Duración real (min)', value: minutes, keyboardType: TextInputType.number, onChanged: (v) => minutes = v),
      const SizedBox(height: 12),
      AppTextField(label: 'Resumen / notas', maxLines: 3, onChanged: (v) => outcome = v),
      const SizedBox(height: 12),
      AppTextField(label: 'Siguiente acción', onChanged: (v) => nextAction = v),
      const SizedBox(height: 12),
      DateField(label: 'Fecha de siguiente contacto (opcional)', value: nextDate, onChanged: (v) => setState(() => nextDate = v)),
      if (nextDate != null) ...[
        const SizedBox(height: 12),
        AppSelect<String>(label: 'Hora del siguiente contacto', value: nextTime, options: _times, onChanged: (v) => setState(() => nextTime = v)),
      ],
      if (error != null) ...[const SizedBox(height: 12), Notice(tone: Tone.danger, text: error)],
    ]),
    footer: (ctx, setState) => [
      Btn('Cancelar', variant: BtnVariant.secondary, onPressed: () => Navigator.of(ctx).pop()),
      Btn('Registrar resultado', loading: saving, onPressed: result.isEmpty
          ? null
          : () async {
              setState(() {
                saving = true;
                error = null;
              });
              try {
                await api.completeLeadTask(task.id, {
                  'result': result,
                  'actualMinutes': int.tryParse(minutes),
                  'outcomeNotes': outcome.trim().isEmpty ? null : outcome.trim(),
                  'nextAction': nextAction.trim().isEmpty ? null : nextAction.trim(),
                  'nextContactAt': nextDate != null ? mxToIso(nextDate!, nextTime) : null,
                });
                if (ctx.mounted) {
                  Navigator.of(ctx).pop();
                  showToast(context, 'Resultado registrado');
                }
                onChanged();
              } catch (err) {
                setState(() {
                  saving = false;
                  error = crmErrorText(err);
                });
              }
            }),
    ],
  );
}
