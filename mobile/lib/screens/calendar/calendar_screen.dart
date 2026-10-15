import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../api/models.dart';
import '../../labels.dart';
import '../../state/auth.dart';
import '../../ui/shell.dart';
import '../../ui/widgets.dart';
import '../crm/crm_ui.dart';
import 'calendar_sheets.dart';

enum CalView { month, week, people, agenda, day }

const _viewLabels = {CalView.month: 'Mes', CalView.week: 'Semana', CalView.people: 'Por asesor', CalView.agenda: 'Lista'};

/// Calendario de turnos de los asesores. Todas las fechas y horas son de
/// Ciudad de México, como texto, sin depender de la zona del teléfono.
class CalendarScreen extends StatefulWidget {
  const CalendarScreen({super.key, this.initialAdvisor});
  final String? initialAdvisor;

  @override
  State<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends State<CalendarScreen> {
  CalView view = CalView.month;
  String anchor = mxToday();
  bool anchorFromServer = false;
  late String advisorFilter = widget.initialAdvisor ?? '';
  String kindFilter = '';
  CalendarView? data;
  String? error;
  bool loading = false;
  int _request = 0;

  @override
  void initState() {
    super.initState();
    load();
  }

  String get from => switch (view) {
    CalView.day => anchor,
    CalView.month => weekStart(addMonths(anchor, 0)),
    _ => weekStart(anchor),
  };

  String get to => switch (view) {
    CalView.day => from,
    CalView.month => addDays(weekStart(addDays(addMonths(anchor, 1), -1)), 6),
    _ => addDays(from, 6),
  };

  Future<void> load() async {
    final id = ++_request;
    setState(() => loading = true);
    try {
      final res = await api.getCalendar(from, to);
      if (!mounted || id != _request) return;
      // La primera vez se usa el "hoy" del servidor (Ciudad de México), no el del teléfono.
      if (!anchorFromServer) {
        anchorFromServer = true;
        if (res.today != anchor) {
          anchor = res.today;
          return load();
        }
      }
      setState(() {
        data = res;
        error = null;
        loading = false;
      });
    } catch (err) {
      if (mounted && id == _request) {
        setState(() {
          error = crmErrorText(err);
          loading = false;
        });
      }
    }
  }

  void go(String nextAnchor, [CalView? nextView]) {
    setState(() {
      anchor = nextAnchor;
      if (nextView != null) view = nextView;
    });
    load();
  }

  void step(int n) => go(view == CalView.month ? addMonths(anchor, n) : addDays(anchor, view == CalView.day ? n : n * 7));

  void saved(String message) {
    showToast(context, message);
    load();
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final canView = auth.can('CALENDAR_VIEW_ALL') || auth.can('CALENDAR_VIEW_OWN');
    if (!canView) {
      return const Restricted(title: 'Calendario de asesores', route: '/calendario', description: 'Tu usuario no tiene permiso para consultar el calendario.');
    }
    final d = data;
    final editable = d != null && (d.canManage || d.canEditOwn);
    final fixedAdvisorId = d != null && !d.canManage && d.canEditOwn ? auth.user?.id : null;
    final editableAdvisors = d == null ? <CalendarAdvisor>[] : d.advisors.where((a) => d.canManage || a.userId == auth.user?.id).toList();
    final occurrences = (d?.occurrences ?? []).where((o) => (advisorFilter.isEmpty || o.advisorId == advisorFilter) && (kindFilter.isEmpty || o.kind == kindFilter)).toList();
    final absences = (d?.absences ?? []).where((a) => advisorFilter.isEmpty || a.advisorId == advisorFilter).toList();
    final days = [for (var i = 0; i <= daysBetween(from, to); i++) addDays(from, i)];
    bool canOpen(ShiftOccurrence o) => editable && (d.canManage || o.advisorId == auth.user?.id);

    void openShift(ShiftOccurrence? o, {String? date, String? advisorId, String? startTime, String? endTime}) {
      if (d == null) return;
      if (o != null && !canOpen(o)) return;
      showShiftSheet(
        context,
        advisors: editableAdvisors,
        occurrence: o,
        draft: o == null
            ? (advisorId: advisorId ?? (advisorFilter.isNotEmpty ? advisorFilter : (fixedAdvisorId ?? '')), date: date ?? anchor, startTime: startTime, endTime: endTime)
            : null,
        fixedAdvisorId: fixedAdvisorId,
        today: d.today,
        onSaved: saved,
      );
    }

    void openAbsence(CalendarAbsence? a) {
      if (d == null || !editable) return;
      showAbsenceSheet(context, advisors: editableAdvisors, absence: a, fixedAdvisorId: fixedAdvisorId, defaultDate: anchor, onSaved: saved);
    }

    final unit = view == CalView.month ? 'Mes' : (view == CalView.day ? 'Día' : 'Semana');
    final title = view == CalView.month ? monthTitle(anchor) : (view == CalView.day ? formatDayLabel(from) : '${formatDayLabel(from)} – ${formatDayLabel(to)}');
    final c = context.c;

    return AppScaffold(
      title: 'Calendario de asesores',
      route: '/calendario',
      actions: [
        if (editable)
          PopupMenuButton<String>(
            tooltip: 'Más acciones',
            icon: const Icon(Icons.more_vert),
            onSelected: (v) {
              if (v == 'copy') {
                showCopySheet(
                  context,
                  advisors: editableAdvisors,
                  selectedDay: anchor,
                  weekFrom: weekStart(anchor),
                  fixedAdvisorId: fixedAdvisorId,
                  today: d.today,
                  onSaved: saved,
                );
              }
              if (v == 'absence') openAbsence(null);
            },
            itemBuilder: (_) => const [
              PopupMenuItem(
                value: 'absence',
                child: ListTile(leading: Icon(Icons.flight_outlined), title: Text('Vacaciones o día libre'), contentPadding: EdgeInsets.zero),
              ),
              PopupMenuItem(
                value: 'copy',
                child: ListTile(leading: Icon(Icons.copy_outlined), title: Text('Repetir semana'), contentPadding: EdgeInsets.zero),
              ),
            ],
          ),
      ],
      fab: editable
          ? FloatingActionButton.extended(
              backgroundColor: Brand.gold,
              foregroundColor: Brand.ink,
              onPressed: () => openShift(null),
              icon: const Icon(Icons.edit_calendar_outlined),
              label: const Text('Agregar horario'),
            )
          : null,
      body: PageBody(
        onRefresh: load,
        bottomPadding: 96,
        children: [
          Row(
            children: [
              Icon(Icons.schedule, size: 14, color: c.muted),
              const SizedBox(width: 6),
              const Expanded(child: MutedText(businessTimezoneLabel, size: 12.5)),
            ],
          ),
          const SizedBox(height: 12),
          if (error != null && d == null)
            LoadError(message: error!, onRetry: load)
          else if (d == null)
            const StateMessage(kind: StateKind.loading, title: 'Cargando calendario…')
          else ...[
            _NowPanel(data: d),
            const SizedBox(height: 14),
            Row(
              children: [
                IconButton.outlined(tooltip: '$unit anterior', onPressed: () => step(-1), icon: const Icon(Icons.chevron_left)),
                const SizedBox(width: 4),
                OutlinedButton(
                  onPressed: () => go(d.today),
                  child: Text('Hoy', style: TextStyle(color: c.fg)),
                ),
                const SizedBox(width: 4),
                IconButton.outlined(tooltip: '$unit siguiente', onPressed: () => step(1), icon: const Icon(Icons.chevron_right)),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    title,
                    style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: c.fg),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                if (loading) const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
              ],
            ),
            const SizedBox(height: 12),
            FilterChips<CalView>(
              value: view == CalView.day ? CalView.week : view,
              options: [for (final v in _viewLabels.entries) (id: v.key, label: v.value, count: null)],
              onChanged: (v) {
                setState(() => view = v);
                load();
              },
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                if (d.advisors.length > 1) ...[
                  Expanded(
                    child: AppSelect<String>(
                      value: advisorFilter,
                      options: [(value: '', label: 'Todos los asesores'), for (final a in d.advisors) (value: a.userId, label: a.name)],
                      onChanged: (v) => setState(() => advisorFilter = v),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                Expanded(
                  child: AppSelect<String>(
                    value: kindFilter,
                    options: const [(value: '', label: 'Todos los horarios'), (value: 'REGULAR', label: 'Solo horario normal'), (value: 'GUARD', label: 'Solo guardias')],
                    onChanged: (v) => setState(() => kindFilter = v),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            if (view != CalView.people) ...[
              _Legend(data: d, selected: advisorFilter, onPick: (id) => setState(() => advisorFilter = advisorFilter == id ? '' : id)),
              const SizedBox(height: 12),
            ],
            if (view == CalView.month)
              _MonthGrid(days: days, month: anchor.substring(0, 7), occurrences: occurrences, absences: absences, today: d.today, onSelectDay: (day) => go(day, CalView.day))
            else if (view == CalView.people)
              _PeopleWeek(
                days: days,
                advisors: d.advisors.where((a) => advisorFilter.isEmpty || a.userId == advisorFilter).toList(),
                occurrences: occurrences,
                absences: absences,
                today: d.today,
                canAdd: (id) => editable && (d.canManage || id == auth.user?.id),
                onOpen: (o) => openShift(o),
                onAdd: (advisorId, date) => openShift(null, advisorId: advisorId, date: date),
                onOpenAbsence: openAbsence,
              )
            else if (d.occurrences.isEmpty && d.absences.isEmpty && view == CalView.agenda)
              StateMessage(
                kind: StateKind.empty,
                title: 'Sin turnos en estas fechas',
                description: editable ? 'Crea el primer turno con “Agregar horario”. Puedes repetirlo cada semana o copiar una semana completa.' : 'Aún no hay turnos registrados.',
              )
            else if (view == CalView.agenda)
              _Agenda(
                days: days,
                occurrences: occurrences,
                absences: absences,
                gaps: advisorFilter.isNotEmpty ? const [] : d.gaps,
                today: d.today,
                onOpen: (o) => openShift(o),
                onOpenAbsence: openAbsence,
              )
            else
              _TimeGrid(
                days: days,
                occurrences: occurrences,
                absences: absences,
                gaps: advisorFilter.isNotEmpty ? const [] : d.gaps,
                now: d.now,
                today: d.today,
                onOpen: (o) => openShift(o),
                onCreate: editable
                    ? (date, hour) =>
                          openShift(null, date: date, startTime: '${hour.toString().padLeft(2, '0')}:00', endTime: '${math.min(hour + 4, 23).toString().padLeft(2, '0')}:00')
                    : null,
                onSelectDay: (day) => go(day, CalView.day),
              ),
            if (view == CalView.day) ...[
              const SizedBox(height: 12),
              _Agenda(
                days: days,
                occurrences: occurrences,
                absences: absences,
                gaps: advisorFilter.isNotEmpty ? const [] : d.gaps,
                today: d.today,
                onOpen: (o) => openShift(o),
                onOpenAbsence: openAbsence,
              ),
              if (editable && anchor.compareTo(d.today) >= 0) ...[
                const SizedBox(height: 10),
                Btn(
                  'Agregar horario este día',
                  icon: Icons.add,
                  variant: BtnVariant.secondary,
                  onPressed: () => openShift(null, date: anchor),
                ),
              ],
            ],
            if (absences.isNotEmpty && view == CalView.week) ...[const SizedBox(height: 12), _AbsenceList(absences: absences, onOpen: openAbsence)],
            const SizedBox(height: 12),
            const _AuditPanel(),
          ],
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------

class _NowPanel extends StatelessWidget {
  const _NowPanel({required this.data});
  final CalendarView data;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final noCoverageNow = data.onShiftNow.where((p) => p.assignable).isEmpty;
    Widget person(PersonNow p, String trailing, {bool guard = false}) => Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Row(
        children: [
          AdvisorDot(p.advisorId),
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              p.name,
              style: TextStyle(fontWeight: FontWeight.w600, color: c.fg),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          const SizedBox(width: 6),
          MutedText(trailing, size: 12.5),
          if (guard) ...[const SizedBox(width: 6), const AppBadge('Guardia', tone: Tone.info)],
        ],
      ),
    );
    final warn = data.gaps.isNotEmpty || noCoverageNow;
    return Column(
      children: [
        AppCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Atendiendo ahora',
                style: TextStyle(fontWeight: FontWeight.w600, color: c.muted, fontSize: 13),
              ),
              if (data.onShiftNow.isEmpty) const Padding(padding: EdgeInsets.only(top: 6), child: MutedText('Nadie está atendiendo en este momento.')),
              for (final p in data.onShiftNow) person(p, 'hasta las ${hhmm(p.end)}', guard: p.kind == 'GUARD'),
              const SizedBox(height: 12),
              Text(
                'Después entran',
                style: TextStyle(fontWeight: FontWeight.w600, color: c.muted, fontSize: 13),
              ),
              if (data.nextUp.isEmpty) const Padding(padding: EdgeInsets.only(top: 6), child: MutedText('Nadie más en los próximos dos días.')),
              for (final p in data.nextUp) person(p, formatLocal(p.start)),
            ],
          ),
        ),
        const SizedBox(height: 10),
        AppCard(
          color: warn ? c.warningBg : null,
          borderColor: warn ? c.warningText.withValues(alpha: 0.25) : null,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Clientes nuevos',
                style: TextStyle(fontWeight: FontWeight.w600, color: c.muted, fontSize: 13),
              ),
              const SizedBox(height: 6),
              if (noCoverageNow)
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.warning_amber_rounded, color: c.warningText, size: 20),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Ahora nadie los está recibiendo. Quedan guardados y se reparten'
                        '${data.nextCoverage != null ? ' cuando entre alguien (${formatLocal(data.nextCoverage!)}).' : ' cuando haya un horario. Agrega horarios.'}',
                        style: TextStyle(color: c.warningText, fontSize: 14),
                      ),
                    ),
                  ],
                )
              else
                Text('Sí hay quien los reciba ahora.', style: TextStyle(color: c.successText, fontSize: 14)),
              if (data.gaps.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Text('Esta semana hay horas sin nadie atendiendo (en rojo en el calendario).', style: TextStyle(fontSize: 13, color: c.fg)),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _Legend extends StatelessWidget {
  const _Legend({required this.data, required this.selected, required this.onPick});
  final CalendarView data;
  final String selected;
  final ValueChanged<String> onPick;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          for (final a in data.advisors)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: Material(
                color: selected == a.userId ? Brand.gold.withValues(alpha: 0.15) : c.card,
                shape: StadiumBorder(side: BorderSide(color: selected == a.userId ? Brand.gold : c.border)),
                child: InkWell(
                  customBorder: const StadiumBorder(),
                  onTap: () => onPick(a.userId),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                    child: Row(
                      children: [
                        AdvisorDot(a.userId),
                        const SizedBox(width: 6),
                        Text(
                          a.name,
                          style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w500, color: c.fg),
                        ),
                        const SizedBox(width: 6),
                        AppBadge(label(advisorStatusLabels, a.status), tone: advisorStatusTones[a.status] ?? Tone.neutral),
                        if (a.needsCalendar) ...[
                          const SizedBox(width: 4),
                          AppBadge('Completar calendario${a.legacyShift != null ? ' (antes ${a.legacyShift})' : ''}', tone: Tone.danger),
                        ],
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

bool _absentOn(CalendarAbsence a, String d) =>
    a.start.substring(0, 10).compareTo(d) <= 0 && a.end.substring(0, 10).compareTo(d) >= 0 && !(a.end.substring(0, 10) == d && a.end.substring(11, 16) == '00:00');

/// Calendario del mes: cada día muestra quién trabaja. Al tocar un día se abre su detalle.
class _MonthGrid extends StatelessWidget {
  const _MonthGrid({required this.days, required this.month, required this.occurrences, required this.absences, required this.today, required this.onSelectDay});
  final List<String> days;
  final String month;
  final List<ShiftOccurrence> occurrences;
  final List<CalendarAbsence> absences;
  final String today;
  final ValueChanged<String> onSelectDay;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    const maxChips = 3;
    final weeks = [for (var i = 0; i < days.length; i += 7) days.sublist(i, math.min(i + 7, days.length))];
    return Container(
      decoration: BoxDecoration(
        color: c.card,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: c.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          Container(
            color: c.appBg,
            child: Row(
              children: [
                for (final d in const ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'])
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      child: Text(
                        d,
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: c.muted),
                      ),
                    ),
                  ),
              ],
            ),
          ),
          for (final week in weeks)
            IntrinsicHeight(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  for (var i = 0; i < week.length; i++)
                    Expanded(
                      child: Builder(
                        builder: (context) {
                          final d = week[i];
                          final inMonth = d.substring(0, 7) == month;
                          final isToday = d == today;
                          final items = occurrences.where((o) => o.date == d).toList()..sort((a, b) => a.start.compareTo(b.start));
                          final away = absences.where((a) => _absentOn(a, d)).toList();
                          final total = items.length + away.length;
                          return InkWell(
                            onTap: () => onSelectDay(d),
                            child: Container(
                              constraints: const BoxConstraints(minHeight: 84),
                              padding: const EdgeInsets.all(2),
                              decoration: BoxDecoration(
                                color: isToday ? Brand.gold.withValues(alpha: 0.1) : (inMonth ? null : c.appBg.withValues(alpha: 0.6)),
                                border: Border(
                                  top: BorderSide(color: c.border),
                                  left: i == 0 ? BorderSide.none : BorderSide(color: c.border),
                                ),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: [
                                  Center(
                                    child: Container(
                                      width: 24,
                                      height: 24,
                                      alignment: Alignment.center,
                                      decoration: BoxDecoration(color: isToday ? Brand.gold : null, shape: BoxShape.circle),
                                      child: Text(
                                        '${int.parse(d.substring(8, 10))}',
                                        style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: isToday ? Brand.ink : (inMonth ? c.fg : c.muted)),
                                      ),
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  for (final a in away.take(maxChips))
                                    _chip(context, bg: c.manualBg, border: c.manualText, text: c.manualText, icon: Icons.flight_outlined, label: a.advisorName.split(' ').first),
                                  for (final o in items.take(math.max(0, maxChips - away.length)))
                                    _chip(
                                      context,
                                      bg: advisorColor(o.advisorId).bg,
                                      border: advisorColor(o.advisorId).border,
                                      text: advisorColor(o.advisorId).text,
                                      icon: o.kind == 'GUARD' ? Icons.shield_outlined : null,
                                      label: o.advisorName.split(' ').first,
                                      sub: '${_compactTime(o.start)}–${_compactTime(o.end)}',
                                      struck: o.fullyAbsent,
                                    ),
                                  if (total > maxChips)
                                    Text(
                                      '+${total - maxChips} más',
                                      textAlign: TextAlign.center,
                                      style: TextStyle(fontSize: 10, fontWeight: FontWeight.w600, color: c.goldText),
                                    ),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  /// "09:00" → "9", "09:30" → "9:30" (cabe en una celda del mes en el celular).
  static String _compactTime(String local) {
    final h = int.parse(local.substring(11, 13));
    final m = local.substring(14, 16);
    return m == '00' ? '$h' : '$h:$m';
  }

  Widget _chip(BuildContext context, {required Color bg, required Color border, required Color text, required String label, IconData? icon, String? sub, bool struck = false}) =>
      Container(
        margin: const EdgeInsets.only(bottom: 2),
        padding: const EdgeInsets.symmetric(horizontal: 3, vertical: 1),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(4),
          border: Border(left: BorderSide(color: border, width: 3)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                if (icon != null) Icon(icon, size: 9, color: text),
                Expanded(
                  child: Text(
                    label,
                    maxLines: 1,
                    overflow: TextOverflow.clip,
                    textScaler: TextScaler.noScaling,
                    style: TextStyle(fontSize: 9.5, fontWeight: FontWeight.w600, color: text, decoration: struck ? TextDecoration.lineThrough : null),
                  ),
                ),
              ],
            ),
            if (sub != null)
              Text(
                sub,
                maxLines: 1,
                overflow: TextOverflow.clip,
                textScaler: TextScaler.noScaling,
                style: TextStyle(fontSize: 8.5, color: text),
              ),
          ],
        ),
      );
}

/// Una tarjeta por asesor con sus horarios de cada día de la semana.
class _PeopleWeek extends StatelessWidget {
  const _PeopleWeek({
    required this.days,
    required this.advisors,
    required this.occurrences,
    required this.absences,
    required this.today,
    required this.canAdd,
    required this.onOpen,
    required this.onAdd,
    required this.onOpenAbsence,
  });
  final List<String> days;
  final List<CalendarAdvisor> advisors;
  final List<ShiftOccurrence> occurrences;
  final List<CalendarAbsence> absences;
  final String today;
  final bool Function(String advisorId) canAdd;
  final ValueChanged<ShiftOccurrence> onOpen;
  final void Function(String advisorId, String date) onAdd;
  final ValueChanged<CalendarAbsence> onOpenAbsence;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    if (advisors.isEmpty) return const StateMessage(kind: StateKind.empty, title: 'No hay asesores', description: 'Da de alta asesores en Usuarios y permisos.');
    Widget cell(String advisorId, String d) {
      final blocks = occurrences.where((o) => o.advisorId == advisorId && o.date == d).toList()..sort((a, b) => a.start.compareTo(b.start));
      final away = absences.where((a) => a.advisorId == advisorId && _absentOn(a, d)).toList();
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final a in away)
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: Material(
                color: c.manualBg,
                borderRadius: BorderRadius.circular(8),
                child: InkWell(
                  borderRadius: BorderRadius.circular(8),
                  onTap: () => onOpenAbsence(a),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                    child: Row(
                      children: [
                        Icon(Icons.flight_outlined, size: 15, color: c.manualText),
                        const SizedBox(width: 6),
                        Text(
                          label(absenceKindLabels, a.kind),
                          style: TextStyle(color: c.manualText, fontWeight: FontWeight.w500),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          for (final o in blocks)
            Padding(
              padding: const EdgeInsets.only(bottom: 4),
              child: _ShiftBlock(occurrence: o, onTap: () => onOpen(o), big: true),
            ),
          if (blocks.isEmpty && away.isEmpty)
            canAdd(advisorId) && d.compareTo(today) >= 0
                ? OutlinedButton(
                    onPressed: () => onAdd(advisorId, d),
                    child: Text('+ Agregar', style: TextStyle(color: c.muted)),
                  )
                : Padding(padding: const EdgeInsets.symmetric(vertical: 8), child: MutedText('Descansa', size: 13)),
        ],
      );
    }

    return Column(
      children: spaced(
        advisors.map(
          (a) => AppCard(
            padding: EdgeInsets.zero,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Padding(
                  padding: const EdgeInsets.all(14),
                  child: Row(
                    children: [
                      AdvisorDot(a.userId),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          a.name,
                          style: TextStyle(fontWeight: FontWeight.w600, color: c.fg, fontSize: 15),
                        ),
                      ),
                      MutedText(label(advisorStatusLabels, a.status), size: 12.5),
                    ],
                  ),
                ),
                for (final d in days) ...[
                  Divider(height: 1, color: c.border),
                  Container(
                    color: d == today ? Brand.gold.withValues(alpha: 0.05) : null,
                    padding: const EdgeInsets.fromLTRB(14, 10, 14, 10),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(
                          width: 92,
                          child: Padding(
                            padding: const EdgeInsets.only(top: 6),
                            child: Text(
                              formatDayLabel(d),
                              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: d == today ? c.goldText : c.muted),
                            ),
                          ),
                        ),
                        Expanded(child: cell(a.userId, d)),
                      ],
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
        12,
      ),
    );
  }
}

class _ShiftBlock extends StatelessWidget {
  const _ShiftBlock({required this.occurrence, required this.onTap, this.big = false});
  final ShiftOccurrence occurrence;
  final VoidCallback onTap;
  final bool big;

  @override
  Widget build(BuildContext context) {
    final o = occurrence;
    final col = advisorColor(o.advisorId);
    return Opacity(
      opacity: o.fullyAbsent ? 0.6 : 1,
      child: Material(
        color: col.bg,
        borderRadius: BorderRadius.circular(8),
        child: InkWell(
          borderRadius: BorderRadius.circular(8),
          onTap: onTap,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
            decoration: BoxDecoration(
              border: Border(left: BorderSide(color: col.border, width: 4)),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${hhmm(o.start)} a ${hhmm(o.end)}',
                  style: TextStyle(fontSize: big ? 15 : 13, fontWeight: FontWeight.w600, color: col.text, decoration: o.fullyAbsent ? TextDecoration.lineThrough : null),
                ),
                if (o.kind == 'GUARD')
                  Row(
                    children: [
                      Icon(Icons.shield_outlined, size: 13, color: col.text),
                      const SizedBox(width: 3),
                      Text('Guardia', style: TextStyle(fontSize: 12, color: col.text)),
                    ],
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

String _mm(int minutes) => minutes >= 1440 ? '24:00' : '${(minutes ~/ 60).toString().padLeft(2, '0')}:${(minutes % 60).toString().padLeft(2, '0')}';

List<({String day, int startMin, int endMin})> _rangePieces(LocalRange r) {
  final out = <({String day, int startMin, int endMin})>[];
  var day = r.start.substring(0, 10);
  final endDay = r.end.substring(0, 10);
  var startMin = minutesOf(r.start);
  while (day.compareTo(endDay) < 0) {
    out.add((day: day, startMin: startMin, endMin: 1440));
    day = addDays(day, 1);
    startMin = 0;
  }
  final endMin = minutesOf(r.end);
  if (endMin > startMin) out.add((day: day, startMin: startMin, endMin: endMin));
  return out;
}

class _Agenda extends StatelessWidget {
  const _Agenda({
    required this.days,
    required this.occurrences,
    required this.absences,
    required this.gaps,
    required this.today,
    required this.onOpen,
    required this.onOpenAbsence,
  });
  final List<String> days;
  final List<ShiftOccurrence> occurrences;
  final List<CalendarAbsence> absences;
  final List<LocalRange> gaps;
  final String today;
  final ValueChanged<ShiftOccurrence> onOpen;
  final ValueChanged<CalendarAbsence> onOpenAbsence;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Column(
      children: spaced(
        days.map((d) {
          final items = occurrences.where((o) => o.date == d).toList()..sort((a, b) => a.start.compareTo(b.start));
          final dayAbsences = absences.where((a) => a.start.substring(0, 10).compareTo(d) <= 0 && a.end.substring(0, 10).compareTo(d) >= 0).toList();
          final dayGaps = gaps.expand(_rangePieces).where((g) => g.day == d).toList();
          return AppCard(
            padding: EdgeInsets.zero,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Container(
                  color: d == today ? Brand.gold.withValues(alpha: 0.1) : null,
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                  child: Text(
                    '${formatDayLabel(d)}${d == today ? ' · hoy' : ''}',
                    style: TextStyle(fontWeight: FontWeight.w600, color: d == today ? c.goldText : c.fg),
                  ),
                ),
                for (final o in items) ...[
                  Divider(height: 1, color: c.border),
                  InkWell(
                    onTap: () => onOpen(o),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                      child: Row(
                        children: [
                          Container(
                            width: 5,
                            height: 38,
                            decoration: BoxDecoration(color: advisorColor(o.advisorId).border, borderRadius: BorderRadius.circular(3)),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  o.advisorName,
                                  style: TextStyle(fontWeight: FontWeight.w600, color: c.fg),
                                  overflow: TextOverflow.ellipsis,
                                ),
                                MutedText('${hhmm(o.start)}–${hhmm(o.end)}${o.end.substring(0, 10) != o.date ? ' (termina al día siguiente)' : ''}', size: 12.5),
                              ],
                            ),
                          ),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              AppBadge(
                                label(shiftKindLabels, o.kind),
                                tone: o.kind == 'GUARD' ? Tone.info : Tone.neutral,
                                icon: o.kind == 'GUARD' ? Icons.shield_outlined : Icons.person_outline,
                              ),
                              if (o.fullyAbsent)
                                const Padding(
                                  padding: EdgeInsets.only(top: 4),
                                  child: AppBadge('Ausente', tone: Tone.manual),
                                ),
                              if (!o.fullyAbsent && !o.assignable) const Padding(padding: EdgeInsets.only(top: 4), child: AppBadge('Sin ronda')),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
                for (final a in dayAbsences) ...[
                  Divider(height: 1, color: c.border),
                  InkWell(
                    onTap: () => onOpenAbsence(a),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                      child: Row(
                        children: [
                          Icon(Icons.flight_outlined, size: 16, color: c.manualText),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('${a.advisorName}: ${label(absenceKindLabels, a.kind)}', style: TextStyle(color: c.fg, fontSize: 13.5)),
                                MutedText('${formatLocal(a.start)} – ${formatLocal(a.end)}', size: 12),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
                for (final g in dayGaps) ...[
                  Divider(height: 1, color: c.border),
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                    child: Row(
                      children: [
                        Icon(Icons.warning_amber_rounded, size: 15, color: c.dangerText),
                        const SizedBox(width: 6),
                        Text('Sin cobertura de ${_mm(g.startMin)} a ${_mm(g.endMin)}', style: TextStyle(fontSize: 12.5, color: c.dangerText)),
                      ],
                    ),
                  ),
                ],
                if (items.isEmpty && dayAbsences.isEmpty && dayGaps.isEmpty) ...[
                  Divider(height: 1, color: c.border),
                  const Padding(padding: EdgeInsets.all(14), child: MutedText('Sin turnos.')),
                ],
              ],
            ),
          );
        }),
        10,
      ),
    );
  }
}

/// Vista por horas (semana o día): bloques de cada turno, huecos sin cobertura en rojo y la hora actual.
class _TimeGrid extends StatelessWidget {
  const _TimeGrid({
    required this.days,
    required this.occurrences,
    required this.absences,
    required this.gaps,
    required this.now,
    required this.today,
    required this.onOpen,
    required this.onCreate,
    required this.onSelectDay,
  });
  final List<String> days;
  final List<ShiftOccurrence> occurrences;
  final List<CalendarAbsence> absences;
  final List<LocalRange> gaps;
  final String now;
  final String today;
  final ValueChanged<ShiftOccurrence> onOpen;
  final void Function(String date, int hour)? onCreate;
  final ValueChanged<String> onSelectDay;

  static const hourPx = 44.0;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    // Un turno nocturno se parte en dos piezas (una por día).
    final pieces = <({String key, String day, int startMin, int endMin, ShiftOccurrence o, bool continued})>[];
    for (final o in occurrences) {
      final startDay = o.start.substring(0, 10);
      final endDay = o.end.substring(0, 10);
      if (startDay == endDay) {
        pieces.add((key: '${o.shiftId}-${o.date}', day: startDay, startMin: minutesOf(o.start), endMin: minutesOf(o.end), o: o, continued: false));
      } else {
        pieces.add((key: '${o.shiftId}-${o.date}-a', day: startDay, startMin: minutesOf(o.start), endMin: 1440, o: o, continued: false));
        if (minutesOf(o.end) > 0) pieces.add((key: '${o.shiftId}-${o.date}-b', day: endDay, startMin: 0, endMin: minutesOf(o.end), o: o, continued: true));
      }
    }
    final visible = pieces.where((p) => days.contains(p.day)).toList();
    final gapPieces = gaps.expand(_rangePieces).where((g) => days.contains(g.day)).toList();
    final minHour = visible.fold<int>(7, (m, p) => math.min(m, p.startMin ~/ 60));
    final maxHour = visible.fold<int>(21, (m, p) => math.max(m, (p.endMin / 60).ceil()));
    final hours = [for (var h = minHour; h < maxHour; h++) h];
    final height = hours.length * hourPx;
    double y(int min) => (min - minHour * 60) / 60 * hourPx;
    final colWidth = days.length == 1 ? null : 120.0;

    Widget dayColumn(String d) {
      final dayPieces = visible.where((p) => p.day == d).toList()..sort((a, b) => a.startMin.compareTo(b.startMin));
      // Carriles para que los turnos simultáneos no se encimen.
      final lanes = <int>[];
      final laneOf = <String, int>{};
      for (final p in dayPieces) {
        var lane = lanes.indexWhere((end) => end <= p.startMin);
        if (lane == -1) {
          lane = lanes.length;
          lanes.add(p.endMin);
        } else {
          lanes[lane] = p.endMin;
        }
        laneOf[p.key] = lane;
      }
      final laneCount = math.max(1, lanes.length);
      return LayoutBuilder(
        builder: (context, box) {
          final w = box.maxWidth;
          return SizedBox(
            height: height,
            child: Stack(
              children: [
                for (final h in hours)
                  Positioned(
                    top: y(h * 60),
                    left: 0,
                    right: 0,
                    height: hourPx,
                    child: InkWell(
                      onTap: onCreate == null ? null : () => onCreate!(d, h),
                      child: Container(
                        decoration: BoxDecoration(
                          border: Border(top: BorderSide(color: c.border.withValues(alpha: 0.6))),
                        ),
                      ),
                    ),
                  ),
                for (final g in gapPieces.where((g) => g.day == d))
                  Positioned(
                    top: y(math.max(g.startMin, minHour * 60)),
                    left: 0,
                    right: 0,
                    height: math.max(2, y(math.min(g.endMin, maxHour * 60)) - y(math.max(g.startMin, minHour * 60))),
                    child: IgnorePointer(
                      child: Container(
                        color: c.dangerBg.withValues(alpha: 0.7),
                        alignment: Alignment.topLeft,
                        padding: const EdgeInsets.all(2),
                        child: g.endMin - g.startMin >= 60
                            ? Text(
                                'Sin cobertura',
                                style: TextStyle(fontSize: 9.5, color: c.dangerText, fontWeight: FontWeight.w500),
                              )
                            : null,
                      ),
                    ),
                  ),
                for (final p in dayPieces)
                  Builder(
                    builder: (context) {
                      final col = advisorColor(p.o.advisorId);
                      final lane = laneOf[p.key] ?? 0;
                      final top = y(math.max(p.startMin, minHour * 60));
                      final h = math.max(22.0, y(math.min(p.endMin, maxHour * 60)) - top);
                      return Positioned(
                        top: top,
                        height: h,
                        left: lane * w / laneCount + 2,
                        width: w / laneCount - 4,
                        child: Opacity(
                          opacity: p.o.fullyAbsent ? 0.5 : 1,
                          child: Material(
                            color: col.bg,
                            borderRadius: BorderRadius.circular(8),
                            elevation: 1,
                            child: InkWell(
                              borderRadius: BorderRadius.circular(8),
                              onTap: () => onOpen(p.o),
                              child: Container(
                                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 3),
                                decoration: BoxDecoration(
                                  border: Border(left: BorderSide(color: col.border, width: 4)),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: ClipRect(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        p.o.advisorName,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: col.text),
                                      ),
                                      Text('${p.continued ? '↳ ' : ''}${hhmm(p.o.start)}–${hhmm(p.o.end)}', maxLines: 1, style: TextStyle(fontSize: 10.5, color: col.text)),
                                      Text(
                                        '${p.o.kind == 'GUARD' ? 'Guardia' : 'Jornada'}${!p.o.assignable ? ' · sin ronda' : ''}${p.o.fullyAbsent ? ' · ausente' : (p.o.absentParts.isNotEmpty ? ' · ausencia parcial' : '')}',
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: TextStyle(fontSize: 10, color: col.text),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                if (d == today)
                  Positioned(
                    top: y(minutesOf(now)),
                    left: 0,
                    right: 0,
                    child: IgnorePointer(child: Container(height: 2, color: c.dangerText)),
                  ),
              ],
            ),
          );
        },
      );
    }

    final header = Row(
      children: [
        const SizedBox(width: 48),
        for (final d in days)
          _cell(
            colWidth,
            InkWell(
              onTap: () => onSelectDay(d),
              child: Container(
                color: d == today ? Brand.gold.withValues(alpha: 0.1) : c.appBg,
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      formatDayLabel(d),
                      style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: d == today ? c.goldText : c.fg),
                    ),
                    Builder(
                      builder: (_) {
                        final n = absences.where((a) => a.start.substring(0, 10).compareTo(d) <= 0 && a.end.substring(0, 10).compareTo(d) >= 0).length;
                        return Text(n > 0 ? '$n ausencia${n == 1 ? '' : 's'}' : ' ', style: TextStyle(fontSize: 10.5, color: c.manualText));
                      },
                    ),
                  ],
                ),
              ),
            ),
          ),
      ],
    );

    final grid = Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 48,
          height: height,
          child: Stack(
            children: [
              for (final h in hours.skip(1))
                Positioned(
                  top: y(h * 60) - 7,
                  right: 6,
                  child: Text('${h.toString().padLeft(2, '0')}:00', style: TextStyle(fontSize: 10, color: c.muted)),
                ),
            ],
          ),
        ),
        for (final d in days)
          _cell(
            colWidth,
            Container(
              decoration: BoxDecoration(
                border: Border(left: BorderSide(color: c.border)),
              ),
              child: dayColumn(d),
            ),
          ),
      ],
    );

    final content = Column(crossAxisAlignment: CrossAxisAlignment.start, children: [header, grid]);
    return Container(
      decoration: BoxDecoration(
        color: c.card,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: c.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: colWidth == null
          ? content
          : SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: SizedBox(width: 48 + colWidth * days.length, child: content),
            ),
    );
  }

  Widget _cell(double? width, Widget child) => width == null ? Expanded(child: child) : SizedBox(width: width, child: child);
}

class _AbsenceList extends StatelessWidget {
  const _AbsenceList({required this.absences, required this.onOpen});
  final List<CalendarAbsence> absences;
  final ValueChanged<CalendarAbsence> onOpen;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const SectionTitle('Ausencias en estas fechas'),
          for (final a in absences)
            InkWell(
              onTap: () => onOpen(a),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Wrap(
                  spacing: 8,
                  runSpacing: 4,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    AdvisorDot(a.advisorId),
                    Text(
                      a.advisorName,
                      style: TextStyle(fontWeight: FontWeight.w500, color: c.fg),
                    ),
                    AppBadge(label(absenceKindLabels, a.kind), tone: Tone.manual),
                    MutedText('${formatLocal(a.start)} – ${formatLocal(a.end)}${a.notes != null ? ' · ${a.notes}' : ''}', size: 12),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _AuditPanel extends StatefulWidget {
  const _AuditPanel();

  @override
  State<_AuditPanel> createState() => _AuditPanelState();
}

class _AuditPanelState extends State<_AuditPanel> {
  bool open = false;
  List<CalendarAuditEntry>? items;
  String? error;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return AppCard(
      padding: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          InkWell(
            onTap: () {
              setState(() => open = !open);
              if (items == null) {
                api.getCalendarAudit().then((r) => mounted ? setState(() => items = r) : null).catchError((Object err) {
                  if (mounted) setState(() => error = crmErrorText(err));
                  return null;
                });
              }
            },
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Row(
                children: [
                  Icon(Icons.history, size: 18, color: c.muted),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Historial de cambios del calendario',
                      style: TextStyle(fontWeight: FontWeight.w500, color: c.fg),
                    ),
                  ),
                  Icon(open ? Icons.expand_less : Icons.expand_more, color: c.muted),
                ],
              ),
            ),
          ),
          if (open) ...[
            Divider(height: 1, color: c.border),
            Padding(
              padding: const EdgeInsets.all(14),
              child: error != null
                  ? Text(error!, style: TextStyle(color: c.dangerText))
                  : items == null
                  ? const MutedText('Cargando…')
                  : items!.isEmpty
                  ? const MutedText('Sin cambios registrados.')
                  : Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        for (final a in items!)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 10),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(a.detail, style: TextStyle(color: c.fg, fontSize: 13.5)),
                                MutedText(
                                  '${a.action == 'CREATED' ? 'Creado' : (a.action == 'UPDATED' ? 'Modificado' : 'Eliminado')} por ${a.actorName ?? 'el sistema'} · ${formatInstantMx(a.createdAt)}',
                                  size: 12,
                                ),
                              ],
                            ),
                          ),
                      ],
                    ),
            ),
          ],
        ],
      ),
    );
  }
}
