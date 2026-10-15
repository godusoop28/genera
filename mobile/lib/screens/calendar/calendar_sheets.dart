import 'package:flutter/material.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../api/models.dart';
import '../../labels.dart';
import '../../ui/widgets.dart';

// Formularios del calendario pensados para usarse fácil: botones grandes,
// pocas preguntas y en palabras simples. Las opciones poco comunes quedan en
// "Más opciones". Todas las horas son de Ciudad de México.

/// Horas cada 30 minutos con texto fácil de leer ("9:00 de la mañana").
final _timeOptions = [
  for (var i = 0; i < 48; i++)
    () {
      final h = i ~/ 2;
      final m = i.isEven ? '00' : '30';
      final value = '${h.toString().padLeft(2, '0')}:$m';
      final h12 = h % 12 == 0 ? 12 : h % 12;
      final part = h < 12 ? (h == 0 ? 'de la noche' : 'de la mañana') : (h < 19 ? 'de la tarde' : 'de la noche');
      return (value: value, label: '$h12:$m $part');
    }(),
];

String _timeLabel(String value) => _timeOptions.where((t) => t.value == value).firstOrNull?.label ?? value;

Widget _question(BuildContext context, String title, {String? help, required Widget child}) => Padding(
      padding: const EdgeInsets.only(bottom: 20),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text(title, style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: context.c.fg)),
        if (help != null) ...[const SizedBox(height: 4), MutedText(help)],
        const SizedBox(height: 10),
        child,
      ]),
    );

Widget _timeSelect(String label, String value, ValueChanged<String> onChanged) {
  final options = _timeOptions.any((t) => t.value == value) ? _timeOptions : [(value: value, label: value), ..._timeOptions];
  return AppSelect<String>(label: label, value: value, options: options, onChanged: onChanged);
}

Widget _advisorPicker(BuildContext context, List<CalendarAdvisor> advisors, String value, ValueChanged<String> onChanged, bool fixed) {
  if (fixed || advisors.length == 1) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(color: context.c.appBg, borderRadius: BorderRadius.circular(12)),
      child: Text(
        advisors.where((a) => a.userId == value).firstOrNull?.name ?? (advisors.isNotEmpty ? advisors.first.name : ''),
        style: TextStyle(fontWeight: FontWeight.w600, color: context.c.fg, fontSize: 15),
      ),
    );
  }
  return AppSelect<String>(value: value, options: [for (final a in advisors) (value: a.userId, label: a.name)], onChanged: onChanged);
}

Widget _errorBox(String? message) => message == null ? const SizedBox.shrink() : Notice(tone: Tone.danger, text: message);

const _weekdays = [1, 2, 3, 4, 5];
const _weekend = [6, 7];
const _allDays = [1, 2, 3, 4, 5, 6, 7];
bool _same(List<int> a, List<int> b) => a.length == b.length && a.every(b.contains);

typedef ShiftDraft = ({String advisorId, String date, String? startTime, String? endTime});

/// Agregar o cambiar el horario de un asesor.
Future<void> showShiftSheet(
  BuildContext context, {
  required List<CalendarAdvisor> advisors,
  required ShiftOccurrence? occurrence,
  required ShiftDraft? draft,
  required String? fixedAdvisorId,
  required String today,
  required void Function(String message) onSaved,
}) {
  final o = occurrence;
  final editing = o != null;
  final isSeries = o?.recurrence == 'WEEKLY';
  final baseDate = o?.date ?? draft?.date ?? today;
  var advisorId = o?.advisorId ?? fixedAdvisorId ?? ((draft?.advisorId.isNotEmpty ?? false) ? draft!.advisorId : (advisors.isNotEmpty ? advisors.first.userId : ''));
  var kind = o?.kind ?? 'REGULAR';
  var repeat = o != null ? o.recurrence == 'WEEKLY' : true;
  var days = o != null && o.days.isNotEmpty ? [...o.days] : (editing ? [isoDay(baseDate)] : [..._weekdays]);
  var date = o?.date ?? (baseDate.compareTo(today) < 0 ? today : baseDate);
  String? until = o?.seriesEnd;
  var startTime = o != null ? hhmm(o.start) : (draft?.startTime ?? '09:00');
  var endTime = o != null ? hhmm(o.end) : (draft?.endTime ?? '14:00');
  var assignable = o?.assignable ?? true;
  var notes = o?.notes ?? '';
  String? scope = isSeries ? null : 'SERIES';
  var saving = false;
  var more = false;
  String? error;
  final isPast = editing && !isSeries && o.date.compareTo(today) < 0;
  final dayName = dayNames[isoDay(baseDate)].toLowerCase();
  final dayPlural = dayName == 'sábado' || dayName == 'domingo' ? '${dayName}s' : (dayName.endsWith('s') ? dayName : '${dayName}s');

  bool onlyThisDay() => editing && isSeries && scope == 'OCCURRENCE';
  bool weekly() => !onlyThisDay() && repeat;
  bool valid() =>
      advisorId.isNotEmpty && startTime != endTime && (!weekly() || days.isNotEmpty) && (until == null || until!.compareTo(date) >= 0) && scope != null;

  Map<String, Object?> input() => {
        'advisorId': advisorId,
        'kind': kind,
        'recurrence': weekly() ? 'WEEKLY' : 'ONCE',
        'days': weekly() ? days : <int>[],
        'startDate': onlyThisDay() ? o!.date : date,
        'endDate': weekly() && until != null ? until : null,
        'startTime': startTime,
        'endTime': endTime,
        'assignable': assignable,
        'notes': notes.trim().isEmpty ? null : notes.trim(),
      };

  return showAppSheet(
    context,
    title: editing ? 'Cambiar horario' : 'Agregar horario',
    builder: (ctx, setState) {
      final c = ctx.c;
      return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        if (isPast) ...[const Notice(tone: Tone.neutral, text: 'Este horario ya pasó. Se guarda como historial y no se puede cambiar.'), const SizedBox(height: 16)],
        if (editing && isSeries)
          _question(ctx, '¿Qué quieres cambiar?',
              child: Column(children: [
                ChoiceTile(selected: scope == 'OCCURRENCE', onTap: () => setState(() => scope = 'OCCURRENCE'), title: 'Solo el ${formatDayLabel(o.date)}'),
                const SizedBox(height: 8),
                ChoiceTile(selected: scope == 'SERIES', onTap: () => setState(() => scope = 'SERIES'), title: 'Todos los $dayPlural de aquí en adelante'),
              ])),
        if (scope != null) ...[
          _question(ctx, '¿Quién?', child: _advisorPicker(ctx, advisors, advisorId, (v) => setState(() => advisorId = v), fixedAdvisorId != null || onlyThisDay())),
          if (!onlyThisDay())
            _question(
              ctx,
              repeat ? '¿Qué días trabaja?' : '¿Qué día?',
              child: repeat
                  ? Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                      Wrap(spacing: 8, runSpacing: 8, children: [
                        for (final preset in [('Lunes a viernes', _weekdays), ('Sábado y domingo', _weekend), ('Toda la semana', _allDays)])
                          ChoiceChip(
                            label: Text(preset.$1),
                            selected: _same(days, preset.$2),
                            selectedColor: Brand.gold.withValues(alpha: 0.2),
                            onSelected: (_) => setState(() => days = [...preset.$2]),
                          ),
                      ]),
                      const SizedBox(height: 10),
                      Wrap(spacing: 6, runSpacing: 6, children: [
                        for (final d in _allDays)
                          SizedBox(
                            width: 64,
                            height: 48,
                            child: Material(
                              color: days.contains(d) ? Brand.gold : c.card,
                              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12), side: BorderSide(color: days.contains(d) ? Brand.gold : c.border, width: 2)),
                              child: InkWell(
                                borderRadius: BorderRadius.circular(12),
                                onTap: () => setState(() => days = days.contains(d) ? (days.where((x) => x != d).toList()) : ([...days, d]..sort())),
                                child: Center(
                                  child: Text(dayNames[d].substring(0, 3),
                                      style: TextStyle(fontWeight: FontWeight.w600, color: days.contains(d) ? Brand.ink : c.muted)),
                                ),
                              ),
                            ),
                          ),
                      ]),
                    ])
                  : DateField(label: 'Fecha', value: date, first: today, onChanged: (v) => setState(() => date = v ?? date)),
            ),
          _question(
            ctx,
            '¿A qué hora?',
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Row(children: [
                Expanded(child: _timeSelect('Entra', startTime, (v) => setState(() => startTime = v))),
                const SizedBox(width: 10),
                Expanded(child: _timeSelect('Sale', endTime, (v) => setState(() => endTime = v))),
              ]),
              const SizedBox(height: 8),
              if (startTime == endTime)
                Text('La hora de entrada y de salida no pueden ser iguales.', style: TextStyle(color: c.dangerText))
              else if (endTime.compareTo(startTime) <= 0)
                Row(children: [
                  Icon(Icons.nightlight_outlined, size: 18, color: c.infoText),
                  const SizedBox(width: 6),
                  Expanded(child: Text('Sale al día siguiente a las ${_timeLabel(endTime)}.', style: TextStyle(color: c.infoText))),
                ])
              else
                Row(children: [
                  Icon(Icons.wb_sunny_outlined, size: 18, color: c.muted),
                  const SizedBox(width: 6),
                  Expanded(child: MutedText('De ${_timeLabel(startTime)} a ${_timeLabel(endTime)}.')),
                ]),
              const SizedBox(height: 6),
              const MutedText('¿Trabaja en dos partes (por ejemplo, mañana y tarde)? Guarda este horario y luego agrega el segundo.', size: 12.5),
            ]),
          ),
          _question(
            ctx,
            '¿Es una guardia?',
            help: 'Una guardia es el turno de quien atiende a los clientes cuando el asesor de la propiedad no está.',
            child: Column(children: [
              ChoiceTile(selected: kind == 'REGULAR', onTap: () => setState(() => kind = 'REGULAR'), title: 'No, es su horario normal'),
              const SizedBox(height: 8),
              ChoiceTile(selected: kind == 'GUARD', onTap: () => setState(() => kind = 'GUARD'), title: 'Sí, es guardia', icon: Icons.shield_outlined),
            ]),
          ),
          Container(
            decoration: BoxDecoration(borderRadius: BorderRadius.circular(12), border: Border.all(color: c.border)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              InkWell(
                onTap: () => setState(() => more = !more),
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Row(children: [
                    Expanded(child: Text('Más opciones', style: TextStyle(fontWeight: FontWeight.w500, color: c.fg, fontSize: 15))),
                    Icon(more ? Icons.expand_less : Icons.expand_more, color: c.muted),
                  ]),
                ),
              ),
              if (more)
                Padding(
                  padding: const EdgeInsets.fromLTRB(14, 4, 14, 0),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                    if (!editing)
                      _question(ctx, '¿Se repite cada semana?',
                          child: Column(children: [
                            ChoiceTile(selected: repeat, onTap: () => setState(() => repeat = true), title: 'Sí, cada semana'),
                            const SizedBox(height: 8),
                            ChoiceTile(selected: !repeat, onTap: () => setState(() => repeat = false), title: 'No, solo un día'),
                          ])),
                    if (weekly()) ...[
                      _question(ctx, '¿Desde cuándo?', child: DateField(label: 'Desde', value: date, first: today, onChanged: (v) => setState(() => date = v ?? date))),
                      _question(ctx, '¿Hasta cuándo?',
                          help: 'Déjalo vacío si no tiene fecha de fin.', child: DateField(label: 'Hasta', value: until, first: date, onChanged: (v) => setState(() => until = v))),
                    ],
                    _question(
                      ctx,
                      '¿Recibe clientes nuevos en este horario?',
                      help: 'Si eliges “No”, el horario se ve en el calendario pero no le llegan clientes del reparto automático.',
                      child: Column(children: [
                        ChoiceTile(selected: assignable, onTap: () => setState(() => assignable = true), title: 'Sí'),
                        const SizedBox(height: 8),
                        ChoiceTile(selected: !assignable, onTap: () => setState(() => assignable = false), title: 'No'),
                      ]),
                    ),
                    _question(ctx, 'Nota (opcional)', child: AppTextField(value: notes, hint: 'Ej. cubre a Eva', onChanged: (v) => notes = v)),
                  ]),
                ),
            ]),
          ),
        ],
        const SizedBox(height: 12),
        _errorBox(error),
      ]);
    },
    footer: (ctx, setState) {
      Future<void> run(Future<void> Function() action, String message) async {
        setState(() {
          saving = true;
          error = null;
        });
        try {
          await action();
          if (ctx.mounted) Navigator.pop(ctx);
          onSaved(message);
        } catch (err) {
          setState(() {
            saving = false;
            error = crmErrorText(err);
          });
        }
      }

      return [
        if (editing && scope != null)
          Btn(onlyThisDay() ? 'Quitar solo este día' : 'Quitar este horario',
              variant: BtnVariant.danger, onPressed: saving || isPast ? null : () => run(() => api.deleteShift(o.shiftId, scope ?? 'SERIES', o.date), 'Horario quitado')),
        Btn('Cancelar', variant: BtnVariant.secondary, onPressed: () => Navigator.pop(ctx)),
        Btn(saving ? 'Guardando…' : 'Guardar',
            loading: saving,
            onPressed: !valid() || isPast
                ? null
                : () => run(() => editing ? api.updateShift(o.shiftId, input(), scope ?? 'SERIES', o.date) : api.createShift(input()), editing ? 'Horario guardado' : 'Horario agregado')),
      ];
    },
  );
}

const _absenceOrder = ['VACATION', 'REST', 'PERMISSION', 'UNAVAILABLE'];
const _absenceText = {'VACATION': 'Vacaciones', 'REST': 'Día de descanso', 'PERMISSION': 'Permiso', 'UNAVAILABLE': 'No puede atender'};

/// Vacaciones o días libres: mientras dure, no le llegan clientes aunque tenga horario.
Future<void> showAbsenceSheet(
  BuildContext context, {
  required List<CalendarAdvisor> advisors,
  required CalendarAbsence? absence,
  required String? fixedAdvisorId,
  required String defaultDate,
  required void Function(String message) onSaved,
}) {
  final a = absence;
  final fullDays = a == null || (a.start.substring(11, 16) == '00:00' && (a.end.substring(11, 16) == '00:00' || a.end.substring(11, 16) == '23:59'));
  var advisorId = a?.advisorId ?? fixedAdvisorId ?? (advisors.isNotEmpty ? advisors.first.userId : '');
  var kind = a?.kind ?? 'VACATION';
  var fromDay = a?.start.substring(0, 10) ?? defaultDate;
  var toDay = a != null ? (a.end.substring(11, 16) == '00:00' ? addDays(a.end.substring(0, 10), -1) : a.end.substring(0, 10)) : defaultDate;
  var hours = !fullDays;
  var fromTime = a != null && !fullDays ? hhmm(a.start) : '09:00';
  var toTime = a != null && !fullDays ? hhmm(a.end) : '14:00';
  var notes = a?.notes ?? '';
  var saving = false;
  var more = false;
  String? error;

  String start() => hours ? '${fromDay}T$fromTime:00' : '${fromDay}T00:00:00';
  String end() => hours ? '${fromDay}T$toTime:00' : '${addDays(toDay, 1)}T00:00:00';
  bool valid() => advisorId.isNotEmpty && end().compareTo(start()) > 0 && (hours || toDay.compareTo(fromDay) >= 0);
  Map<String, Object?> input() => {'advisorId': advisorId, 'kind': kind, 'start': start(), 'end': end(), 'notes': notes.trim().isEmpty ? null : notes.trim()};

  return showAppSheet(
    context,
    title: a != null ? 'Cambiar vacaciones o día libre' : 'Vacaciones o día libre',
    builder: (ctx, setState) {
      final c = ctx.c;
      return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const MutedText('Mientras no esté, no le llegan clientes nuevos aunque tenga horario.', size: 14.5),
        const SizedBox(height: 16),
        _question(ctx, '¿Quién?', child: _advisorPicker(ctx, advisors, advisorId, (v) => setState(() => advisorId = v), fixedAdvisorId != null)),
        _question(ctx, '¿Por qué?',
            child: Column(children: [
              for (final k in _absenceOrder) ...[ChoiceTile(selected: kind == k, onTap: () => setState(() => kind = k), title: _absenceText[k]!), const SizedBox(height: 8)],
            ])),
        if (hours)
          _question(ctx, '¿Qué día y a qué hora?',
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                DateField(label: 'Día', value: fromDay, onChanged: (v) => setState(() => fromDay = v ?? fromDay)),
                const SizedBox(height: 10),
                Row(children: [
                  Expanded(child: _timeSelect('Desde', fromTime, (v) => setState(() => fromTime = v))),
                  const SizedBox(width: 10),
                  Expanded(child: _timeSelect('Hasta', toTime, (v) => setState(() => toTime = v))),
                ]),
              ]))
        else
          _question(ctx, '¿Qué días?',
              help: 'Incluye el primero y el último día.',
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                DateField(label: 'Del día', value: fromDay, onChanged: (v) => setState(() => fromDay = v ?? fromDay)),
                const SizedBox(height: 10),
                DateField(label: 'Al día', value: toDay, first: fromDay, onChanged: (v) => setState(() => toDay = v ?? toDay)),
              ])),
        Container(
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(12), border: Border.all(color: c.border)),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            InkWell(
              onTap: () => setState(() => more = !more),
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Row(children: [
                  Expanded(child: Text('Más opciones', style: TextStyle(fontWeight: FontWeight.w500, color: c.fg, fontSize: 15))),
                  Icon(more ? Icons.expand_less : Icons.expand_more, color: c.muted),
                ]),
              ),
            ),
            if (more)
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 4, 14, 0),
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  _question(ctx, '¿Es solo unas horas?',
                      child: Column(children: [
                        ChoiceTile(selected: !hours, onTap: () => setState(() => hours = false), title: 'No, días completos'),
                        const SizedBox(height: 8),
                        ChoiceTile(selected: hours, onTap: () => setState(() => hours = true), title: 'Sí, solo unas horas'),
                      ])),
                  _question(ctx, 'Nota (opcional)', child: AppTextField(value: notes, onChanged: (v) => notes = v)),
                ]),
              ),
          ]),
        ),
        const SizedBox(height: 12),
        _errorBox(error),
      ]);
    },
    footer: (ctx, setState) {
      Future<void> run(Future<void> Function() action, String message) async {
        setState(() {
          saving = true;
          error = null;
        });
        try {
          await action();
          if (ctx.mounted) Navigator.pop(ctx);
          onSaved(message);
        } catch (err) {
          setState(() {
            saving = false;
            error = crmErrorText(err);
          });
        }
      }

      return [
        if (a != null) Btn('Quitar', variant: BtnVariant.danger, onPressed: saving ? null : () => run(() => api.deleteAbsence(a.id), 'Se quitó')),
        Btn('Cancelar', variant: BtnVariant.secondary, onPressed: () => Navigator.pop(ctx)),
        Btn(saving ? 'Guardando…' : 'Guardar',
            loading: saving, onPressed: !valid() ? null : () => run(() => a != null ? api.updateAbsence(a.id, input()) : api.createAbsence(input()), 'Guardado')),
      ];
    },
  );
}

/// Repetir los horarios de una semana (o un día) en otra fecha.
Future<void> showCopySheet(
  BuildContext context, {
  required List<CalendarAdvisor> advisors,
  required String selectedDay,
  required String weekFrom,
  required String? fixedAdvisorId,
  required String today,
  required void Function(String message) onSaved,
}) {
  var what = 'week';
  var target = addDays(weekFrom, 7).compareTo(today) < 0 ? today : addDays(weekFrom, 7);
  var advisorId = fixedAdvisorId ?? '';
  var saving = false;
  String? error;

  return showAppSheet(
    context,
    title: 'Repetir horarios en otra fecha',
    builder: (ctx, setState) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      _question(ctx, '¿Qué quieres repetir?',
          child: Column(children: [
            ChoiceTile(selected: what == 'week', onTap: () => setState(() => what = 'week'), title: 'La semana del ${formatDayLabel(weekFrom)}'),
            const SizedBox(height: 8),
            ChoiceTile(selected: what == 'day', onTap: () => setState(() => what = 'day'), title: 'Solo el ${formatDayLabel(selectedDay)}'),
          ])),
      _question(ctx, what == 'week' ? '¿A partir de qué lunes?' : '¿En qué día?',
          child: DateField(label: 'Fecha destino', value: target, first: today, onChanged: (v) => setState(() => target = v ?? target))),
      if (fixedAdvisorId == null && advisors.length > 1)
        _question(ctx, '¿De quién?',
            child: AppSelect<String>(
              value: advisorId,
              options: [(value: '', label: 'De todos'), for (final a in advisors) (value: a.userId, label: a.name)],
              onChanged: (v) => setState(() => advisorId = v),
            )),
      _errorBox(error),
    ]),
    footer: (ctx, setState) => [
      Btn('Cerrar', variant: BtnVariant.secondary, onPressed: () => Navigator.pop(ctx)),
      Btn(saving ? 'Repitiendo…' : 'Repetir', loading: saving, onPressed: () async {
        setState(() {
          saving = true;
          error = null;
        });
        try {
          final res = await api.copyShifts(what == 'day' ? selectedDay : weekFrom, what == 'day' ? 1 : 7, target, advisorId.isEmpty ? null : advisorId);
          if (res.skipped == 0) {
            if (ctx.mounted) Navigator.pop(ctx);
            onSaved('Se repitieron ${res.created} horario(s)');
          } else {
            setState(() {
              saving = false;
              error = res.created > 0
                  ? 'Se repitieron ${res.created}. ${res.skipped} no, porque ya había otro horario a esa hora.'
                  : 'No se repitió ninguno: ya había horarios a esas horas.';
            });
          }
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
