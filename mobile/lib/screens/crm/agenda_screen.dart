import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../api/models.dart';
import '../../labels.dart';
import '../../ui/shell.dart';
import '../../ui/widgets.dart';

/// Control de Cambios #30, parte 4: agenda de actividades de los leads en
/// lista (las vencidas sin resultado arriba). El asesor ve las suyas; quien ve
/// todos los leads, las de todo el equipo.
class AgendaScreen extends StatefulWidget {
  const AgendaScreen({super.key});

  @override
  State<AgendaScreen> createState() => _AgendaScreenState();
}

class _AgendaScreenState extends State<AgendaScreen> {
  List<LeadTaskItem>? tasks;
  String? error;
  int days = 7;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    // Medianoche de hoy en Ciudad de México (UTC-6 fijo).
    final today = DateTime.now().toUtc().subtract(const Duration(hours: 6)).toIso8601String().substring(0, 10);
    final from = DateTime.parse('${today}T00:00:00-06:00').toUtc();
    try {
      final t = await api.getAgenda(from.toIso8601String(), from.add(Duration(days: days)).toIso8601String());
      if (mounted) {
        setState(() {
          tasks = t;
          error = null;
        });
      }
    } catch (err) {
      if (mounted) setState(() => error = crmErrorText(err));
    }
  }

  /// "2026-10-26" en hora de CDMX.
  static String _day(String iso) => DateTime.parse(iso).toUtc().subtract(const Duration(hours: 6)).toIso8601String().substring(0, 10);

  @override
  Widget build(BuildContext context) {
    final list = tasks;
    final c = context.c;
    final overdue = (list ?? []).where((t) => t.overdue).toList();
    final upcoming = (list ?? []).where((t) => !t.overdue && t.scheduledAt != null).toList()..sort((a, b) => a.scheduledAt!.compareTo(b.scheduledAt!));
    final byDay = <String, List<LeadTaskItem>>{};
    for (final t in upcoming) {
      byDay.putIfAbsent(_day(t.scheduledAt!), () => []).add(t);
    }
    return AppScaffold(
      title: 'Agenda',
      body: PageBody(
        onRefresh: load,
        children: spaced([
          FilterChips<int>(
            value: days,
            options: const [(id: 1, label: 'Hoy', count: null), (id: 7, label: '7 días', count: null), (id: 30, label: '30 días', count: null)],
            onChanged: (v) {
              setState(() {
                days = v;
                tasks = null;
              });
              load();
            },
          ),
          if (error != null && list == null)
            LoadError(message: error!, onRetry: load)
          else if (list == null)
            const StateMessage(kind: StateKind.loading, title: 'Cargando agenda…')
          else ...[
            if (overdue.isNotEmpty)
              Notice(
                tone: Tone.danger,
                icon: Icons.warning_amber_rounded,
                title: '${overdue.length} vencida${overdue.length == 1 ? '' : 's'} sin resultado',
                child: Column(children: [for (final t in overdue) _TaskTile(task: t)]),
              ),
            if (byDay.isEmpty && overdue.isEmpty) const StateMessage(kind: StateKind.empty, title: 'Sin actividades', description: 'No hay actividades programadas en este periodo.'),
            for (final entry in byDay.entries)
              AppCard(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(formatDayLabel(entry.key), style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
                  const SizedBox(height: 8),
                  for (final t in entry.value) _TaskTile(task: t),
                ]),
              ),
          ],
        ]),
      ),
    );
  }
}

class _TaskTile extends StatelessWidget {
  const _TaskTile({required this.task});
  final LeadTaskItem task;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final t = task;
    return InkWell(
      onTap: () => context.push('/leads/${t.leadId}'),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          SizedBox(width: 52, child: Text(t.scheduledAt != null ? formatInstantMx(t.scheduledAt!).split(', ').last : '', style: TextStyle(color: c.muted, fontSize: 13))),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(t.leadName ?? 'Lead', style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
              MutedText('${label(taskTypeLabels, t.type)}${t.title != null ? ' · ${t.title}' : ''}${t.advisor != null ? ' · ${t.advisor!.name}' : ''}', size: 12.5),
              if (t.status == 'DONE') MutedText('Realizada · ${label(taskResultLabels, t.result)}', size: 12),
            ]),
          ),
          Icon(Icons.chevron_right, color: c.muted),
        ]),
      ),
    );
  }
}
