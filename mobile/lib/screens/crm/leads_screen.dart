import 'dart:async';

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

const groupLabels = {
  'ALL': 'Todos',
  'UNASSIGNED': 'Sin asignar',
  'PENDING': 'Pendientes por horario',
  'NEW': 'Nuevos',
  'OPEN': 'En seguimiento',
  'STALE': 'Sin actividad reciente',
  'WON': 'Cierres',
  'LOST': 'Perdidos',
};

class LeadsScreen extends StatefulWidget {
  const LeadsScreen({super.key, this.initialGroup, this.initialAdvisor, this.initialSource});
  final String? initialGroup, initialAdvisor, initialSource;

  @override
  State<LeadsScreen> createState() => _LeadsScreenState();
}

class _LeadsScreenState extends State<LeadsScreen> {
  late String group = groupLabels.containsKey(widget.initialGroup) ? widget.initialGroup! : 'ALL';
  late String advisorId = widget.initialAdvisor ?? '';
  late String source = widget.initialSource ?? '';
  String query = '';
  bool board = false;
  LeadPage? data;
  String? error;
  List<AdvisorRouting> advisors = [];
  Timer? _debounce;
  int _request = 0;

  bool get seesAll => context.read<AuthState>().can('LEAD_VIEW_ALL');

  @override
  void initState() {
    super.initState();
    load();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (seesAll) api.listAdvisors().then((a) => mounted ? setState(() => advisors = a) : null).catchError((_) => null);
    });
  }

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }

  Future<void> load() async {
    final id = ++_request;
    try {
      final result = await api.listLeads(q: query.trim(), group: board ? 'ALL' : group, advisorId: advisorId, source: source);
      if (mounted && id == _request) {
        setState(() {
          data = result;
          error = null;
        });
      }
    } catch (err) {
      if (mounted && id == _request) setState(() => error = crmErrorText(err));
    }
  }

  void onQuery(String v) {
    query = v;
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), load);
  }

  bool get filtering => query.trim().isNotEmpty || advisorId.isNotEmpty || source.isNotEmpty || group != 'ALL';

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    if (!auth.canSeeCrm) return const Restricted(title: 'Leads', route: '/leads', description: 'Tu usuario no tiene permiso para consultar leads.');
    final counts = data?.counts;
    String count(String k) => counts == null ? '—' : '${counts[k] ?? 0}';
    return AppScaffold(
      title: 'Leads',
      route: '/leads',
      actions: [
        IconButton(
          tooltip: board ? 'Ver como lista' : 'Ver como tablero por etapa',
          icon: Icon(board ? Icons.view_list_outlined : Icons.view_column_outlined),
          onPressed: () {
            setState(() => board = !board);
            load();
          },
        ),
      ],
      fab: auth.can('LEAD_MANAGE')
          ? FloatingActionButton.extended(
              backgroundColor: Brand.gold,
              foregroundColor: Brand.ink,
              onPressed: () => showNewLeadSheet(context, advisors),
              icon: const Icon(Icons.add),
              label: const Text('Registrar lead'),
            )
          : null,
      body: PageBody(
        onRefresh: load,
        bottomPadding: 96,
        subtitle: seesAll ? 'Prospectos que llegan por WhatsApp, portales, campañas y la oficina, con su propiedad y su asesor.' : 'Tus prospectos asignados.',
        children: [
          StatGrid(children: [
            StatCard(icon: Icons.group_outlined, value: count('ALL'), label: 'Leads', hint: filtering ? 'Con los filtros actuales' : 'Todos los registrados'),
            StatCard(icon: Icons.inbox_outlined, tone: Tone.warning, value: count('NEW'), label: 'Nuevos', hint: 'Aún sin primer contacto'),
            StatCard(
              icon: Icons.person_add_alt,
              tone: Tone.danger,
              value: counts == null ? '—' : '${(counts['UNASSIGNED'] ?? 0) + (counts['PENDING'] ?? 0)}',
              label: 'Sin asesor',
              hint: counts == null ? 'Necesitan un asesor' : '${counts['PENDING'] ?? 0} esperan turno',
            ),
            StatCard(icon: Icons.check_circle_outline, tone: Tone.success, value: count('WON'), label: 'Cierres', hint: 'Transacciones concretadas'),
          ]),
          const SizedBox(height: 16),
          AppTextField(hint: 'Buscar por nombre, teléfono o propiedad', onChanged: onQuery, value: query),
          const SizedBox(height: 10),
          if (seesAll) ...[
            AppSelect<String>(
              value: advisorId,
              options: [(value: '', label: 'Todos los asesores'), for (final a in advisors) (value: a.userId, label: a.name)],
              onChanged: (v) {
                setState(() => advisorId = v);
                load();
              },
            ),
            const SizedBox(height: 10),
          ],
          AppSelect<String>(
            value: source,
            options: [(value: '', label: 'Todas las fuentes'), ...optionsOf(sourceLabels)],
            onChanged: (v) {
              setState(() => source = v);
              load();
            },
          ),
          const SizedBox(height: 14),
          if (!board) ...[
            FilterChips<String>(
              value: group,
              options: [for (final g in groupLabels.entries) (id: g.key, label: g.value, count: counts?[g.key])],
              onChanged: (g) {
                setState(() => group = g);
                load();
              },
            ),
            const SizedBox(height: 14),
          ],
          if (error != null && data == null)
            LoadError(message: error!, onRetry: load)
          else if (data == null)
            const StateMessage(kind: StateKind.loading, title: 'Cargando leads…')
          else if (data!.items.isEmpty)
            StateMessage(
              kind: filtering ? StateKind.noResults : StateKind.empty,
              title: filtering ? 'Sin resultados' : 'Aún no hay leads',
              description: filtering ? 'Ningún lead coincide con estos filtros.' : 'Cuando el asistente de WhatsApp canalice a un cliente, o registres uno a mano, aparecerá aquí.',
              action: filtering
                  ? Btn('Quitar filtros', variant: BtnVariant.secondary, onPressed: () {
                      setState(() {
                        query = '';
                        advisorId = '';
                        source = '';
                        group = 'ALL';
                      });
                      load();
                    })
                  : null,
            )
          else if (board)
            _Board(leads: data!.items)
          else ...[
            ...spaced(data!.items.map((l) => _LeadCard(lead: l)), 10),
            if (data!.total > data!.items.length)
              Padding(
                padding: const EdgeInsets.only(top: 14),
                child: MutedText('Mostrando ${data!.items.length} de ${data!.total}. Usa la búsqueda o los filtros para encontrar el resto.', size: 12, center: true),
              ),
          ],
        ],
      ),
    );
  }
}

class _LeadCard extends StatelessWidget {
  const _LeadCard({required this.lead});
  final LeadItem lead;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return AppCard(
      onTap: () => context.push('/leads/${lead.id}'),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('${lead.temperature != null ? '${temperatureDots[lead.temperature] ?? ''} ' : ''}${lead.name}', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15, color: c.fg), overflow: TextOverflow.ellipsis),
              Text('${label(interestLabels, lead.interest)} · ${timeAgo(lead.createdAt)}', style: TextStyle(fontSize: 12, color: c.muted)),
              if (lead.nextTaskAt != null)
                Text(
                  '${label(taskTypeLabels, lead.nextTaskType)}${lead.nextTaskOverdue ? ' · vencida' : ''} · ${formatInstantMx(lead.nextTaskAt!)}',
                  style: TextStyle(fontSize: 12, color: lead.nextTaskOverdue ? c.dangerText : c.muted, fontWeight: lead.nextTaskOverdue ? FontWeight.w600 : null),
                )
              else if (lead.stale)
                Text('Sin actividad reciente', style: TextStyle(fontSize: 12, color: c.warningText, fontWeight: FontWeight.w600)),
            ]),
          ),
          StageBadge(lead.stage),
        ]),
        const SizedBox(height: 10),
        PropertyMini(property: lead.property),
        const SizedBox(height: 10),
        Wrap(spacing: 8, runSpacing: 6, crossAxisAlignment: WrapCrossAlignment.center, children: [
          AdvisorChip(advisor: lead.advisor, emptyLabel: lead.assignmentStatus == 'PENDING_SCHEDULE' ? 'Pendiente por horario' : 'Sin asignar'),
          if (lead.assignmentStatus == 'PENDING_OWNER') const AppBadge('Espera su turno', tone: Tone.info),
          if (lead.coordinationNote != null) const AppBadge('Coordinar', tone: Tone.warning),
        ]),
        const SizedBox(height: 8),
        Row(children: [
          Expanded(child: SourceLabel(source: lead.source, sourceLabel: lead.sourceLabel, detail: lead.sourceDetail, size: 12)),
          Text('Ver', style: TextStyle(color: c.goldText, fontWeight: FontWeight.w600)),
          Icon(Icons.arrow_forward, size: 16, color: c.goldText),
        ]),
      ]),
    );
  }
}

/// Tablero por etapa (solo lectura: el cambio de etapa se hace en el detalle del lead).
class _Board extends StatelessWidget {
  const _Board({required this.leads});
  final List<LeadItem> leads;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        for (final stage in stageOrder)
          Builder(builder: (context) {
            final items = leads.where((l) => l.stage == stage).toList();
            return Container(
              width: 240,
              margin: const EdgeInsets.only(right: 10),
              decoration: BoxDecoration(color: c.appBg, borderRadius: BorderRadius.circular(14), border: Border.all(color: c.border)),
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Padding(
                  padding: const EdgeInsets.all(10),
                  child: Row(children: [StageBadge(stage), const Spacer(), Text('${items.length}', style: TextStyle(fontSize: 12, color: c.muted))]),
                ),
                Divider(height: 1, color: c.border),
                Padding(
                  padding: const EdgeInsets.all(8),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                    if (items.isEmpty) const Padding(padding: EdgeInsets.symmetric(vertical: 14), child: MutedText('Sin leads', size: 12, center: true)),
                    for (final l in items)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: AppCard(
                          padding: const EdgeInsets.all(10),
                          onTap: () => context.push('/leads/${l.id}'),
                          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text(l.name, style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13.5, color: c.fg), overflow: TextOverflow.ellipsis),
                            Text(l.property?.title ?? label(interestLabels, l.interest), style: TextStyle(fontSize: 12, color: c.muted), overflow: TextOverflow.ellipsis),
                            const SizedBox(height: 6),
                            Text(l.advisor?.name ?? 'Sin asignar',
                                style: TextStyle(fontSize: 12, color: l.advisor == null ? c.warningText : c.muted, fontWeight: l.advisor == null ? FontWeight.w600 : null),
                                overflow: TextOverflow.ellipsis),
                          ]),
                        ),
                      ),
                  ]),
                ),
              ]),
            );
          }),
      ]),
    );
  }
}

/// Alta manual de un lead (llamada, oficina, referido). Los del bot llegan solos.
Future<void> showNewLeadSheet(BuildContext context, List<AdvisorRouting> advisors) {
  final auth = context.read<AuthState>();
  final canAssign = auth.can('LEAD_ASSIGN');
  var name = '', phone = '', email = '', sourceDetail = '', propertyReference = '', summary = '', exceptionReason = '';
  var source = 'PHONE', interest = 'BUY', assign = 'auto';
  var saving = false;
  String? error;
  AdvisorRouting? chosen() => advisors.where((a) => a.userId == assign).firstOrNull;

  return showAppSheet(
    context,
    title: 'Registrar lead',
    builder: (ctx, setState) {
      final ch = chosen();
      return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        AppTextField(label: 'Nombre', onChanged: (v) => setState(() => name = v)),
        const SizedBox(height: 12),
        AppTextField(label: 'Teléfono', keyboardType: TextInputType.phone, onChanged: (v) => phone = v),
        const SizedBox(height: 12),
        AppTextField(label: 'Correo', keyboardType: TextInputType.emailAddress, onChanged: (v) => email = v),
        const SizedBox(height: 12),
        AppSelect<String>(
          label: '¿Cómo llegó?',
          value: source,
          options: [for (final e in sourceLabels.entries.where((e) => e.key != 'WHATSAPP_BOT')) (value: e.key, label: e.value)],
          onChanged: (v) => setState(() => source = v),
        ),
        const SizedBox(height: 12),
        AppTextField(label: 'Detalle de la fuente', hint: 'Ej. Inmuebles24, campaña de Facebook', onChanged: (v) => sourceDetail = v),
        const SizedBox(height: 12),
        AppSelect<String>(label: '¿Qué busca?', value: interest, options: optionsOf(interestLabels), onChanged: (v) => setState(() => interest = v)),
        const SizedBox(height: 12),
        AppTextField(label: 'Propiedad de 21Online (opcional)', hint: 'Número (634294) o clave (GN 14)', onChanged: (v) => propertyReference = v),
        const SizedBox(height: 12),
        AppTextField(label: 'Lo que nos dijo', maxLines: 3, onChanged: (v) => summary = v),
        const SizedBox(height: 12),
        if (canAssign)
          AppSelect<String>(
            label: 'Asesor',
            value: assign,
            options: [
              (value: 'auto', label: 'Automático (asesor de la propiedad o ruleta)'),
              (value: 'none', label: 'Dejar sin asignar'),
              for (final a in advisors) (value: a.userId, label: '${a.name} · ${a.status == 'AVAILABLE' ? 'disponible' : a.statusDetail.toLowerCase()}'),
            ],
            onChanged: (v) => setState(() => assign = v),
          )
        else
          const MutedText('Quedará asignado a ti.'),
        if (ch != null && ch.status != 'AVAILABLE') ...[
          const SizedBox(height: 12),
          AppTextField(
            label: '${ch.name} no está disponible (${ch.statusDetail.toLowerCase()}): motivo de la excepción',
            onChanged: (v) => setState(() => exceptionReason = v),
          ),
        ],
        if (error != null) ...[const SizedBox(height: 12), Notice(tone: Tone.danger, text: error)],
      ]);
    },
    footer: (ctx, setState) {
      final ch = chosen();
      final blocked = name.trim().isEmpty || (ch != null && ch.status != 'AVAILABLE' && exceptionReason.trim().isEmpty);
      return [
        Btn('Cancelar', variant: BtnVariant.secondary, onPressed: () => Navigator.pop(ctx)),
        Btn(saving ? 'Guardando…' : 'Registrar', loading: saving, onPressed: blocked
            ? null
            : () async {
                setState(() {
                  saving = true;
                  error = null;
                });
                String? opt(String v) => v.trim().isEmpty ? null : v.trim();
                try {
                  final created = await api.createLead({
                    'name': name.trim(),
                    'phone': opt(phone),
                    'email': opt(email),
                    'source': source,
                    'sourceDetail': opt(sourceDetail),
                    'interest': interest,
                    'propertyReference': opt(propertyReference),
                    'summary': opt(summary),
                    'advisorId': canAssign && assign != 'auto' && assign != 'none' ? assign : null,
                    'exceptionReason': opt(exceptionReason),
                    'autoAssign': canAssign && assign == 'auto',
                  });
                  if (ctx.mounted) Navigator.pop(ctx);
                  if (context.mounted) context.push('/leads/${created.lead.id}');
                } catch (err) {
                  setState(() {
                    saving = false;
                    error = crmErrorText(err);
                  });
                }
              }),
      ];
    },
  );
}
