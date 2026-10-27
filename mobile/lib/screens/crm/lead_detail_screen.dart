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
import 'lead_followup.dart';

class LeadDetailScreen extends StatefulWidget {
  const LeadDetailScreen({super.key, required this.id});
  final String id;

  @override
  State<LeadDetailScreen> createState() => _LeadDetailScreenState();
}

class _LeadDetailScreenState extends State<LeadDetailScreen> {
  LeadDetail? detail;
  String? error;
  List<AdvisorRouting> advisors = [];

  @override
  void initState() {
    super.initState();
    load();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (context.read<AuthState>().can('LEAD_ASSIGN')) {
        api.listAdvisors().then((a) => mounted ? setState(() => advisors = a) : null).catchError((_) => null);
      }
    });
  }

  Future<void> load() async {
    try {
      final d = await api.getLead(widget.id);
      if (mounted) {
        setState(() {
          detail = d;
          error = null;
        });
      }
    } catch (err) {
      if (mounted) setState(() => error = crmErrorText(err));
    }
  }

  void update(LeadDetail next) => setState(() => detail = next);

  /// Ejecuta una acción del lead y muestra el resultado.
  Future<bool> run(Future<LeadDetail> Function() action, String success) async {
    try {
      update(await action());
      if (mounted) showToast(context, success);
      return true;
    } catch (err) {
      if (mounted) showToast(context, crmErrorText(err));
      return false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final d = detail;
    if (d == null) {
      return AppScaffold(
        title: 'Lead',
        body: PageBody(children: [
          error != null ? LoadError(message: error!, onRetry: load) : const StateMessage(kind: StateKind.loading, title: 'Cargando lead…'),
        ]),
      );
    }
    final lead = d.lead;
    final c = context.c;
    return AppScaffold(
      title: lead.name,
      actions: [
        if (lead.phone != null) IconButton(tooltip: 'WhatsApp', icon: const Icon(Icons.chat_outlined), onPressed: () => openUrl(context, whatsappLink(lead.phone!))),
        if (lead.phone != null) IconButton(tooltip: 'Llamar', icon: const Icon(Icons.call_outlined), onPressed: () => openUrl(context, 'tel:${lead.phone}')),
      ],
      body: PageBody(
        onRefresh: load,
        children: spaced([
          Wrap(spacing: 12, runSpacing: 4, children: [
            if (lead.phone != null) MutedText(lead.phone!),
            if (lead.email != null) MutedText(lead.email!),
            MutedText('Recibido ${timeAgo(lead.createdAt)}'),
          ]),
          if (lead.coordinationNote != null)
            Notice(tone: Tone.warning, icon: Icons.warning_amber_rounded, title: 'Requiere coordinación:', text: lead.coordinationNote),
          if (lead.stale) Notice(tone: Tone.warning, icon: Icons.schedule, text: 'Sin actividad desde ${formatShortDateTime(lead.lastActivityAt)}: corre el riesgo de enfriarse.'),
          if (auth.can('LEAD_MANAGE'))
            SuggestedStageNotice(detail: d, onApply: (stage) async => run(() => api.changeLeadStage(lead.id, stage), 'Etapa: ${label(stageLabels, stage)}')),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              CardHeader(
                title: 'Ficha del prospecto',
                action: Wrap(spacing: 6, children: [
                  if (lead.temperature != null) AppBadge('${temperatureDots[lead.temperature] ?? ''} ${label(temperatureLabels, lead.temperature)}', tone: temperatureTones[lead.temperature] ?? Tone.neutral),
                  StageBadge(lead.stage),
                ]),
              ),
              FieldGrid(children: [
                FieldView('Tipo de operación', label(interestLabels, lead.interest)),
                FieldView('Fuente', null, child: SourceLabel(source: lead.source, sourceLabel: lead.sourceLabel, detail: lead.sourceDetail, color: c.fg, size: 14)),
                FieldView('Tipo de inmueble', lead.propertyTypeLabel ?? '—'),
                FieldView('Zona de interés', lead.zone ?? '—'),
                FieldView('Presupuesto', _budget(lead)),
                FieldView('Probabilidad de cierre', lead.closeProbability != null ? '${lead.closeProbability} %' : '—'),
                FieldView('Cierre estimado', lead.expectedCloseOn ?? '—'),
                FieldView('Primer contacto', lead.firstContactOn ?? formatShortDateTime(lead.createdAt)),
                FieldView('Último movimiento', timeAgo(lead.lastActivityAt)),
                if (lead.expedienteFolio != null) FieldView('Expediente', lead.expedienteFolio),
                if (lead.stage == 'LOST') FieldView('Motivo de pérdida', [if (lead.lostReasonCode != null) label(lostReasonLabels, lead.lostReasonCode), ?lead.lostReason].join(' — ')),
              ]),
              if (lead.desiredFeatures != null) ...[
                const SizedBox(height: 12),
                const SectionTitle('Características deseadas'),
                Text(lead.desiredFeatures!, style: TextStyle(color: c.fg, fontSize: 14)),
              ],
              if (lead.summary != null) ...[
                const SizedBox(height: 14),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: c.appBg, borderRadius: BorderRadius.circular(12)),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const SectionTitle('Lo que nos dijo'),
                    Text(lead.summary!, style: TextStyle(color: c.fg, fontSize: 14)),
                  ]),
                ),
              ],
            ]),
          ),
          LeadTasksCard(detail: d, canManage: auth.can('LEAD_MANAGE'), onChanged: load),
          if (auth.can('LEAD_MANAGE')) _StageCard(lead: lead, run: run),
          _AdvisorCard(detail: d, advisors: advisors, run: run),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              CardHeader(title: 'Propiedad de interés', description: lead.property != null ? 'Consulta la ficha completa en 21Online.' : null),
              if (lead.property != null)
                PropertyMini(property: lead.property)
              else
                MutedText(lead.interest == 'SELL' || lead.interest == 'LEASE_OUT'
                    ? 'Este prospecto quiere vender o rentar su propiedad: todavía no está en el inventario.'
                    : 'No mencionó una propiedad específica; el asesor le ofrecerá opciones según lo que busca.'),
            ]),
          ),
          if (lead.interest == 'SELL' || lead.interest == 'LEASE_OUT')
            AppCard(
              color: Brand.gold.withValues(alpha: 0.06),
              borderColor: Brand.gold.withValues(alpha: 0.5),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const CardHeader(title: '¿Firmó la intermediación?', description: 'Los propietarios que firman pasan al Módulo 1 como expediente.'),
                Btn('Crear expediente', icon: Icons.note_add_outlined, variant: BtnVariant.dark, onPressed: () => context.push('/expedientes/nuevo')),
              ]),
            ),
          if (lead.conversationId != null && auth.can('CONVERSATION_VIEW')) _ConversationPreview(conversationId: lead.conversationId!, name: lead.name),
          if (auth.can('LEAD_MANAGE')) _NoteCard(leadId: lead.id, run: run),
          if (d.assignments.isNotEmpty)
            AppCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const CardHeader(title: 'Asignación: por qué y cuándo'),
                ...spaced(d.assignments.map((e) => _AssignmentEntry(entry: e)), 10),
              ]),
            ),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const CardHeader(title: 'Historial'),
              if (d.activities.isEmpty) const MutedText('Sin movimientos.'),
              for (final a in d.activities)
                Padding(
                  padding: const EdgeInsets.only(bottom: 14),
                  child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Container(
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(color: c.appBg, shape: BoxShape.circle),
                      child: Icon(_activityIcons[a.kind] ?? Icons.smart_toy_outlined, size: 16, color: c.muted),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(a.text, style: TextStyle(fontSize: 14, color: c.fg)),
                        MutedText('${formatShortDateTime(a.createdAt)}${a.actorName != null ? ' · ${a.actorName}' : ''}', size: 12),
                      ]),
                    ),
                  ]),
                ),
            ]),
          ),
        ]),
      ),
    );
  }
}

const _activityIcons = {
  'CREATED': Icons.smart_toy_outlined,
  'ASSIGNED': Icons.how_to_reg_outlined,
  'REASSIGNED': Icons.how_to_reg_outlined,
  'STAGE': Icons.check_circle_outline,
  'NOTE': Icons.sticky_note_2_outlined,
  'BOT': Icons.smart_toy_outlined,
  'NOTICE': Icons.notifications_none,
  'FOLLOW_UP': Icons.forum_outlined,
  'TASK': Icons.event_available_outlined,
  'PROPERTY_SHARED': Icons.home_outlined,
  'EXPEDIENTE': Icons.folder_open_outlined,
  'EDIT': Icons.edit_outlined,
};

String _budget(LeadItem lead) {
  final min = lead.budgetMin;
  final max = lead.budget;
  if (min != null && max != null) return '${formatPrice(min)} – ${formatPrice(max)}';
  if (max != null && max > 0) return 'Hasta ${formatPrice(max)}';
  if (min != null) return 'Desde ${formatPrice(min)}';
  return '—';
}

typedef _Run = Future<bool> Function(Future<LeadDetail> Function() action, String success);

class _StageCard extends StatefulWidget {
  const _StageCard({required this.lead, required this.run});
  final LeadItem lead;
  final _Run run;

  @override
  State<_StageCard> createState() => _StageCardState();
}

class _StageCardState extends State<_StageCard> {
  String? pending;
  String reason = '';
  String reasonCode = '';
  bool saving = false;

  bool get lostInvalid => reasonCode.isEmpty || (reasonCode == 'OTHER' && reason.trim().isEmpty);

  Future<void> apply(String stage) async {
    if (stage == 'LOST' && lostInvalid) return;
    setState(() => saving = true);
    final ok = await widget.run(
      () => api.changeLeadStage(widget.lead.id, stage, stage == 'LOST' && reason.trim().isNotEmpty ? reason.trim() : null, stage == 'LOST' ? reasonCode : null),
      'Etapa: ${stageLabels[stage]}',
    );
    if (mounted) {
      setState(() {
        saving = false;
        if (ok) {
          pending = null;
          reason = '';
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final lead = widget.lead;
    final funnel = stageOrder.where((s) => s != 'LOST').toList();
    final reached = lead.stage == 'LOST' ? -1 : funnel.indexOf(lead.stage);
    return AppCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const CardHeader(title: 'Etapa', description: 'Avanza el lead conforme progresa el seguimiento.'),
        for (var i = 0; i < funnel.length; i++)
          Material(
            color: funnel[i] == lead.stage ? Brand.gold.withValues(alpha: 0.15) : Colors.transparent,
            borderRadius: BorderRadius.circular(10),
            child: InkWell(
              borderRadius: BorderRadius.circular(10),
              onTap: funnel[i] == lead.stage || saving ? null : () => funnel[i] == 'WON' ? setState(() => pending = 'WON') : apply(funnel[i]),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
                child: Row(children: [
                  Container(
                    width: 22,
                    height: 22,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: i <= reached ? Brand.gold : c.card,
                      shape: BoxShape.circle,
                      border: Border.all(color: i <= reached ? Brand.gold : c.border),
                    ),
                    child: i <= reached ? const Icon(Icons.check, size: 14, color: Brand.ink) : Text('${i + 1}', style: TextStyle(fontSize: 11, color: c.muted)),
                  ),
                  const SizedBox(width: 12),
                  Text(stageLabels[funnel[i]]!,
                      style: TextStyle(fontSize: 14, color: funnel[i] == lead.stage ? c.fg : c.muted, fontWeight: funnel[i] == lead.stage ? FontWeight.w600 : null)),
                ]),
              ),
            ),
          ),
        if (pending == 'WON') ...[
          const SizedBox(height: 12),
          Notice(
            tone: Tone.success,
            text: '¿Confirmas que la transacción se concretó?',
            action: Wrap(spacing: 8, children: [
              Btn('Sí, registrar cierre', small: true, loading: saving, onPressed: () => apply('WON')),
              Btn('Cancelar', small: true, variant: BtnVariant.ghost, onPressed: () => setState(() => pending = null)),
            ]),
          ),
        ],
        const SizedBox(height: 10),
        if (lead.stage != 'LOST')
          pending == 'LOST'
              ? Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  AppSelect<String>(
                    label: 'Motivo de pérdida',
                    value: reasonCode,
                    options: [(value: '', label: 'Elige el motivo'), for (final e in lostReasonLabels.entries) (value: e.key, label: e.value)],
                    onChanged: (v) => setState(() => reasonCode = v),
                  ),
                  const SizedBox(height: 8),
                  AppTextField(hint: reasonCode == 'OTHER' ? 'Describe el motivo (obligatorio)' : 'Detalle (opcional)', onChanged: (v) => setState(() => reason = v)),
                  const SizedBox(height: 8),
                  Wrap(spacing: 8, children: [
                    Btn('Marcar como perdido', small: true, variant: BtnVariant.danger, loading: saving, onPressed: lostInvalid ? null : () => apply('LOST')),
                    Btn('Cancelar', small: true, variant: BtnVariant.ghost, onPressed: () => setState(() => pending = null)),
                  ]),
                ])
              : TextButton(
                  onPressed: () => setState(() => pending = 'LOST'),
                  child: Text('Marcar como perdido', style: TextStyle(color: c.dangerText)),
                )
        else
          Btn('Reabrir seguimiento', small: true, variant: BtnVariant.secondary, loading: saving, onPressed: () => apply('CONTACTED')),
      ]),
    );
  }
}

const _noticeText = {
  'DONE': (label: 'Ficha enviada por WhatsApp', tone: Tone.success),
  'PENDING': (label: 'Ficha en envío', tone: Tone.warning),
  'FAILED': (label: 'No se pudo enviar la ficha', tone: Tone.danger),
  'NOT_REQUIRED': (label: 'Sin WhatsApp del asesor: ficha no enviada', tone: Tone.neutral),
};

class _AdvisorCard extends StatefulWidget {
  const _AdvisorCard({required this.detail, required this.advisors, required this.run});
  final LeadDetail detail;
  final List<AdvisorRouting> advisors;
  final _Run run;

  @override
  State<_AdvisorCard> createState() => _AdvisorCardState();
}

class _AdvisorCardState extends State<_AdvisorCard> {
  String target = '';
  String reason = '';
  bool saving = false;
  bool sending = false;

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final c = context.c;
    final lead = widget.detail.lead;
    final options = widget.advisors.where((a) => a.userId != lead.advisor?.id || lead.assignmentStatus != 'ASSIGNED').toList();
    final selected = widget.advisors.where((a) => a.userId == target).firstOrNull;
    final needsException = selected != null && selected.status != 'AVAILABLE';
    final notice = _noticeText[widget.detail.advisorNoticeStatus] ?? _noticeText['NOT_REQUIRED']!;
    return AppCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        CardHeader(
          title: 'Asesor que atiende',
          action: AppBadge(label(assignmentStatusLabels, lead.assignmentStatus), tone: assignmentStatusTones[lead.assignmentStatus] ?? Tone.neutral),
        ),
        AdvisorChip(advisor: lead.advisor, emptyLabel: lead.assignmentStatus == 'PENDING_SCHEDULE' ? 'Pendiente por horario' : 'Sin asignar'),
        if (lead.assignmentStatus == 'ASSIGNED' && lead.assignmentMethod != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: MutedText('Motivo: ${label(assignmentLabels, lead.assignmentMethod)} — ${lead.assignmentRule ?? assignmentHints[lead.assignmentMethod] ?? ''}.', size: 12.5),
          ),
        if (lead.assignmentStatus == 'PENDING_OWNER' || lead.assignmentStatus == 'PENDING_SCHEDULE')
          Container(
            margin: const EdgeInsets.only(top: 10),
            width: double.infinity,
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: c.appBg, borderRadius: BorderRadius.circular(10)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(lead.pendingReason ?? 'En espera de un turno disponible.', style: TextStyle(fontSize: 12.5, color: c.fg)),
              const SizedBox(height: 4),
              MutedText(
                lead.nextShiftAt != null
                    ? 'Próximo turno disponible: ${formatInstantMx(lead.nextShiftAt!)} (se asigna solo).'
                    : 'No hay un próximo turno en el calendario: asígnalo manualmente o configura turnos.',
                size: 12.5,
              ),
            ]),
          ),
        if (lead.assignmentStatus == 'UNASSIGNED')
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text('${lead.pendingReason ?? 'Sin asesor.'} Asígnalo manualmente.', style: TextStyle(fontSize: 12.5, color: c.warningText)),
          ),
        if (lead.propertyAdvisor != null && lead.propertyAdvisor!.id != lead.advisor?.id) ...[
          const SizedBox(height: 10),
          Divider(color: c.border),
          Row(children: [
            Icon(Icons.home_outlined, size: 15, color: c.muted),
            const SizedBox(width: 6),
            Expanded(
              child: Text.rich(TextSpan(children: [
                TextSpan(text: 'Asesor responsable de la propiedad: ', style: TextStyle(color: c.muted)),
                TextSpan(text: lead.propertyAdvisor!.name, style: TextStyle(color: c.fg, fontWeight: FontWeight.w500)),
              ]), style: const TextStyle(fontSize: 12.5)),
            ),
          ]),
        ],
        if (lead.assignmentStatus == 'ASSIGNED') ...[
          const SizedBox(height: 10),
          Divider(color: c.border),
          const SizedBox(height: 6),
          Wrap(spacing: 8, runSpacing: 6, crossAxisAlignment: WrapCrossAlignment.center, children: [
            AppBadge(notice.label, tone: notice.tone, icon: Icons.notifications_none),
            if (auth.can('LEAD_MANAGE'))
              Btn('Reenviar ficha', small: true, variant: BtnVariant.ghost, loading: sending, onPressed: () async {
                setState(() => sending = true);
                await widget.run(() => api.resendLeadNotice(lead.id), 'Ficha reenviada al asesor');
                if (mounted) setState(() => sending = false);
              }),
          ]),
          if (widget.detail.advisorNoticeStatus == 'NOT_REQUIRED')
            const Padding(padding: EdgeInsets.only(top: 6), child: MutedText('Captura su WhatsApp en Asesores para que reciba la ficha de cada lead.', size: 12)),
        ],
        if (auth.can('LEAD_ASSIGN')) ...[
          const SizedBox(height: 14),
          AppSelect<String>(
            label: lead.advisor != null ? 'Reasignar a' : 'Asignar a',
            value: target,
            options: [(value: '', label: 'Elige un asesor'), for (final a in options) (value: a.userId, label: '${a.name} · ${label(advisorStatusLabels, a.status)}')],
            onChanged: (v) => setState(() => target = v),
          ),
          if (needsException) ...[
            const SizedBox(height: 10),
            Notice(
              tone: Tone.warning,
              text:
                  '${selected.name}: ${selected.statusDetail}${selected.nextShift != null ? ' · próximo turno ${hhmm(selected.nextShift!)}' : ''}. Para asignarlo de todos modos indica el motivo de la excepción.',
              child: Padding(
                padding: const EdgeInsets.only(top: 8),
                child: AppTextField(hint: 'Ej. el cliente pidió específicamente a este asesor', onChanged: (v) => setState(() => reason = v)),
              ),
            ),
          ],
          const SizedBox(height: 10),
          Btn(
            lead.advisor != null ? 'Reasignar' : 'Asignar',
            icon: lead.advisor != null ? Icons.how_to_reg_outlined : Icons.person_add_alt,
            variant: BtnVariant.secondary,
            loading: saving,
            onPressed: target.isEmpty || (needsException && reason.trim().isEmpty)
                ? null
                : () async {
                    setState(() => saving = true);
                    final ok = await widget.run(() => api.assignLead(lead.id, target, needsException ? reason.trim() : null), 'Lead asignado');
                    if (mounted) {
                      setState(() {
                        saving = false;
                        if (ok) {
                          target = '';
                          reason = '';
                        }
                      });
                    }
                  },
          ),
          const SizedBox(height: 8),
          const MutedText('Si el asesor tiene WhatsApp configurado en Asesores, recibe un aviso con los datos del lead.', size: 12),
        ],
      ]),
    );
  }
}

class _NoteCard extends StatefulWidget {
  const _NoteCard({required this.leadId, required this.run});
  final String leadId;
  final _Run run;

  @override
  State<_NoteCard> createState() => _NoteCardState();
}

class _NoteCardState extends State<_NoteCard> {
  final ctrl = TextEditingController();
  bool saving = false;

  @override
  void dispose() {
    ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const CardHeader(title: 'Agregar nota'),
          AppTextField(controller: ctrl, maxLines: 3, hint: 'Ej. Le llamé, quedó de confirmar la visita el jueves.', onChanged: (_) => setState(() {})),
          const SizedBox(height: 10),
          Btn('Guardar nota', icon: Icons.sticky_note_2_outlined, variant: BtnVariant.secondary, loading: saving, onPressed: ctrl.text.trim().isEmpty
              ? null
              : () async {
                  setState(() => saving = true);
                  final ok = await widget.run(() => api.addLeadNote(widget.leadId, ctrl.text.trim()), 'Nota agregada');
                  if (mounted) {
                    setState(() {
                      saving = false;
                      if (ok) ctrl.clear();
                    });
                  }
                }),
        ]),
      );
}

class _ConversationPreview extends StatefulWidget {
  const _ConversationPreview({required this.conversationId, required this.name});
  final String conversationId;
  final String name;

  @override
  State<_ConversationPreview> createState() => _ConversationPreviewState();
}

class _ConversationPreviewState extends State<_ConversationPreview> {
  ConversationDetail? data;

  @override
  void initState() {
    super.initState();
    api.getConversation(widget.conversationId).then((d) => mounted ? setState(() => data = d) : null).catchError((_) => null);
  }

  @override
  Widget build(BuildContext context) {
    final d = data;
    if (d == null) return const SizedBox.shrink();
    final c = context.c;
    final messages = d.messages.length > 3 ? d.messages.sublist(d.messages.length - 3) : d.messages;
    return AppCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        CardHeader(
          title: 'Conversación de WhatsApp',
          action: AppBadge(label(controlLabels, d.conversation.control), tone: controlTones[d.conversation.control] ?? Tone.neutral),
        ),
        for (final m in messages)
          Align(
            alignment: m.role == 'CLIENT' ? Alignment.centerLeft : Alignment.centerRight,
            child: Container(
              margin: const EdgeInsets.only(bottom: 8),
              constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.72),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(color: m.role == 'CLIENT' ? c.appBg : Brand.gold.withValues(alpha: 0.15), borderRadius: BorderRadius.circular(12)),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(
                  (m.role == 'CLIENT' ? widget.name.split(' ').first : (m.role == 'BOT' ? 'Asistente' : 'Equipo')).toUpperCase(),
                  style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w600, color: c.muted, letterSpacing: 0.5),
                ),
                Text(m.text, style: TextStyle(fontSize: 13.5, color: c.fg)),
              ]),
            ),
          ),
        LinkText('Abrir conversación completa', icon: Icons.forum_outlined, onTap: () => context.push('/conversaciones/${widget.conversationId}')),
      ]),
    );
  }
}

const _outcomeLabels = {'ASSIGNED': 'Asignado', 'REASSIGNED': 'Reasignado', 'PENDING': 'En espera', 'KEPT': 'Se conservó', 'CONFLICT': 'Conflicto'};

class _AssignmentEntry extends StatelessWidget {
  const _AssignmentEntry({required this.entry});
  final LeadAssignmentEntry entry;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final e = entry;
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(12), border: Border.all(color: c.border)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Wrap(spacing: 8, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
          AppBadge(_outcomeLabels[e.outcome] ?? e.outcome, tone: e.outcome == 'CONFLICT' ? Tone.warning : (e.outcome == 'PENDING' ? Tone.info : Tone.success)),
          if (e.advisor != null) Text(e.advisor!.name, style: TextStyle(fontWeight: FontWeight.w500, color: c.fg)),
          if (e.reason != null) MutedText('· ${label(assignmentLabels, e.reason)}', size: 12),
        ]),
        const SizedBox(height: 4),
        MutedText(formatInstantMx(e.createdAt), size: 11.5),
        const SizedBox(height: 4),
        Text(e.rule, style: TextStyle(fontSize: 13.5, color: c.fg)),
        if (e.detail != null) MutedText(e.detail!, size: 12),
        MutedText(
          '${e.roundMode != null ? 'Ronda ${e.roundMode == 'EQUAL' ? 'equitativa' : 'ponderada'} · ' : ''}${e.shiftId != null ? 'Dentro de un turno del calendario · ' : ''}${e.actorName != null ? 'Por ${e.actorName}' : 'Automático'}',
          size: 12,
        ),
      ]),
    );
  }
}
