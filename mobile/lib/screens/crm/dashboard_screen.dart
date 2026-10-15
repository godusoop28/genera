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
import 'crm_ui.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  Period period = Period.d30;
  CrmDashboard? data;
  String? error;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final result = await api.getCrmDashboard(periodFrom(period));
      if (mounted) {
        setState(() {
          data = result;
          error = null;
        });
      }
    } catch (err) {
      if (mounted) setState(() => error = crmErrorText(err));
    }
  }

  String pct(int part, int total) => total == 0 ? '0%' : '${(part * 100 / total).round()}%';

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    if (!auth.canSeeCrm) {
      return const Restricted(title: 'Dashboard', route: '/dashboard', description: 'Tu usuario no tiene permiso para consultar leads.');
    }
    final d = data;
    return AppScaffold(
      title: 'Dashboard',
      route: '/dashboard',
      body: PageBody(
        onRefresh: load,
        subtitle: 'Cómo van los leads: de dónde llegan, a quién se asignan y en qué etapa están.',
        children: [
          FilterChips<Period>(
            value: period,
            options: [for (final p in Period.values) (id: p, label: periodLabels[p]!, count: null)],
            onChanged: (p) {
              setState(() => period = p);
              load();
            },
          ),
          const SizedBox(height: 16),
          if (error != null && d == null)
            LoadError(message: error!, onRetry: load)
          else if (d == null)
            const StateMessage(kind: StateKind.loading, title: 'Cargando indicadores…')
          else
            ..._content(d, auth),
        ],
      ),
    );
  }

  List<Widget> _content(CrmDashboard d, AuthState auth) {
    final c = context.c;
    final maxStage = stageOrder.fold<int>(1, (m, s) => (d.byStage[s] ?? 0) > m ? d.byStage[s]! : m);
    final sources = d.bySource.entries.toList()..sort((a, b) => b.value.compareTo(a.value));
    return spaced([
      if (d.pendingAssignment > 0)
        Notice(
          tone: Tone.info,
          icon: Icons.schedule,
          title: '${d.pendingAssignment} lead${d.pendingAssignment == 1 ? '' : 's'} esperando turno',
          text: 'Se asignan solos cuando empiece un turno elegible. Revisa el calendario si no hay turnos próximos.',
          action: Btn('Ver pendientes', icon: Icons.arrow_forward, small: true, variant: BtnVariant.secondary, onPressed: () => context.push('/leads?grupo=PENDING')),
        ),
      if (d.unassigned > 0 && auth.can('LEAD_ASSIGN'))
        Notice(
          tone: Tone.warning,
          icon: Icons.warning_amber_rounded,
          title: '${d.unassigned} lead${d.unassigned == 1 ? '' : 's'} sin asignar',
          text: 'No están en espera automática (registrados sin asignar o antes del calendario). Asígnalos para que no se enfríen.',
          action: Btn('Ver y asignar', icon: Icons.arrow_forward, small: true, onPressed: () => context.push('/leads?grupo=UNASSIGNED')),
        ),
      StatGrid(children: [
        StatCard(icon: Icons.group_outlined, value: '${d.total}', label: 'Leads del periodo', hint: '${d.open} siguen en seguimiento', onTap: () => context.push('/leads')),
        StatCard(icon: Icons.check_circle_outline, tone: Tone.info, value: '${d.assigned}', label: 'Asignados', hint: '${pct(d.assigned, d.total)} del total'),
        StatCard(icon: Icons.schedule, tone: Tone.warning, value: '${d.staleNew}', label: 'Sin primer contacto', hint: 'Nuevos con más de 24 h', onTap: () => context.push('/leads?grupo=NEW')),
        StatCard(
          icon: Icons.trending_up,
          tone: Tone.success,
          value: '${d.won}',
          label: 'Cerrados ganados',
          hint: d.closed > 0 ? '${pct(d.won, d.closed)} de los cerrados' : 'Aún sin cierres',
          onTap: () => context.push('/leads?grupo=WON'),
        ),
      ]),
      AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const CardHeader(title: 'Embudo', description: 'Leads del periodo por etapa de seguimiento.'),
          if (d.total == 0)
            const Padding(padding: EdgeInsets.symmetric(vertical: 16), child: MutedText('Sin leads en el periodo', center: true))
          else
            LayoutBuilder(builder: (context, box) {
              final w = (box.maxWidth - 10) / 2;
              return Wrap(spacing: 10, runSpacing: 10, children: [
                for (final s in stageOrder)
                  Container(
                    width: w,
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(borderRadius: BorderRadius.circular(12), border: Border.all(color: c.border)),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(stageLabels[s]!, style: TextStyle(fontSize: 12, color: c.muted), overflow: TextOverflow.ellipsis),
                      const SizedBox(height: 4),
                      Text('${d.byStage[s] ?? 0}', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w600, color: c.fg)),
                      const SizedBox(height: 8),
                      ProgressLine((d.byStage[s] ?? 0) / maxStage,
                          color: s == 'LOST' ? c.dangerText.withValues(alpha: 0.6) : (s == 'WON' ? c.successText : Brand.gold)),
                    ]),
                  ),
              ]);
            }),
        ]),
      ),
      AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const CardHeader(title: '¿De dónde llegan?', description: 'Leads del periodo por fuente.'),
          BarList(rows: [
            for (final e in sources)
              (
                key: e.key,
                label: Text(label(sourceLabels, e.key)),
                count: e.value,
                leading: Icon(sourceIcons[e.key] ?? Icons.label_outline, size: 16, color: c.muted),
                onTap: () => context.push('/leads?fuente=${e.key}'),
              ),
          ]),
        ]),
      ),
      AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const CardHeader(title: 'Leads por asesor', description: 'Asignados en el periodo y cuántos cerró cada uno.'),
          BarList(emptyLabel: 'Sin asignaciones en el periodo', rows: [
            for (final row in d.byAdvisor)
              (
                key: row.advisorId,
                label: Text.rich(TextSpan(children: [
                  TextSpan(text: row.name),
                  if (row.won > 0) TextSpan(text: ' · ${row.won} ganado${row.won == 1 ? '' : 's'}', style: TextStyle(fontSize: 12, color: c.successText)),
                ])),
                count: row.leads,
                leading: Avatar(row.name, size: 26),
                onTap: () => context.push('/leads?asesor=${row.advisorId}'),
              ),
          ]),
        ]),
      ),
      AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const CardHeader(title: 'Propiedades con más interés', description: 'Por cuántos leads preguntaron por ellas (datos de 21Online).'),
          if (d.topProperties.isEmpty)
            const Padding(padding: EdgeInsets.symmetric(vertical: 16), child: MutedText('Ningún lead mencionó una propiedad en el periodo', center: true))
          else
            ...[
              for (final p in d.topProperties)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Row(children: [
                    Expanded(child: PropertyMini(property: p.property)),
                    Text.rich(TextSpan(children: [
                      TextSpan(text: '${p.leads}', style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
                      TextSpan(text: ' lead${p.leads == 1 ? '' : 's'}', style: TextStyle(color: c.muted)),
                    ])),
                  ]),
                ),
            ],
        ]),
      ),
      AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          CardHeader(title: 'Últimos leads', action: LinkText('Ver todos', trailingIcon: Icons.arrow_forward, onTap: () => context.push('/leads'))),
          if (d.recent.isEmpty)
            const Padding(padding: EdgeInsets.symmetric(vertical: 16), child: MutedText('Sin leads en el periodo', center: true))
          else
            for (final lead in d.recent)
              InkWell(
                onTap: () => context.push('/leads/${lead.id}'),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [
                      Expanded(child: Text(lead.name, style: TextStyle(fontWeight: FontWeight.w600, color: c.fg), overflow: TextOverflow.ellipsis)),
                      Text(timeAgo(lead.createdAt), style: TextStyle(fontSize: 12, color: c.muted)),
                    ]),
                    const SizedBox(height: 4),
                    SourceLabel(source: lead.source, detail: lead.sourceDetail, size: 12),
                    const SizedBox(height: 6),
                    Row(children: [Expanded(child: AdvisorChip(advisor: lead.advisor)), StageBadge(lead.stage)]),
                  ]),
                ),
              ),
        ]),
      ),
    ]);
  }
}
