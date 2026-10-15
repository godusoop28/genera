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

const _pollMs = 4000;
const _modeText = {'OFF': 'Asistente apagado', 'TEST_ONLY': 'Solo pruebas', 'ON': 'Asistente activo'};

class ConversationsScreen extends StatefulWidget {
  const ConversationsScreen({super.key});

  @override
  State<ConversationsScreen> createState() => _ConversationsScreenState();
}

class _ConversationsScreenState extends State<ConversationsScreen> {
  AssistantStatus? status;
  List<ConversationItem>? items;
  String? error;
  String filter = 'all';
  bool simulating = false;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    api.getAssistantStatus().then((s) => mounted ? setState(() => status = s) : null).catchError((_) => null);
    load();
    _timer = Timer.periodic(const Duration(milliseconds: _pollMs * 2), (_) => load());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> load() async {
    try {
      final list = await api.listConversations();
      if (mounted) {
        setState(() {
          items = list;
          error = null;
        });
      }
    } catch (err) {
      if (mounted) setState(() => error = crmErrorText(err));
    }
  }

  Future<void> simulate() async {
    setState(() => simulating = true);
    try {
      final id = await api.simulateMessage('Hola');
      await load();
      if (id != null && mounted) context.push('/conversaciones/$id');
    } catch (err) {
      if (mounted) showToast(context, crmErrorText(err));
    } finally {
      if (mounted) setState(() => simulating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    if (!auth.can('CONVERSATION_VIEW')) {
      return const Restricted(title: 'Conversaciones', route: '/conversaciones', description: 'Tu usuario no tiene permiso para consultar las conversaciones.');
    }
    final c = context.c;
    final all = items ?? [];
    bool matches(ConversationItem i) => filter == 'all' || (filter == 'attention' ? i.requiresAttention : i.control == filter);
    final visible = all.where(matches).toList();
    final s = status;
    return AppScaffold(
      title: 'Conversaciones',
      route: '/conversaciones',
      body: PageBody(
        onRefresh: load,
        subtitle: 'Lo que el asistente de WhatsApp habla con los clientes: qué entendió, a quién canalizó y qué falta atender.',
        children: [
          if (s != null) ...[
            Row(children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                decoration: BoxDecoration(
                  color: s.mode == 'ON' ? c.successBg : (s.mode == 'TEST_ONLY' ? c.infoBg : c.appBg),
                  borderRadius: BorderRadius.circular(99),
                  border: Border.all(color: c.border),
                ),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: BoxDecoration(shape: BoxShape.circle, color: s.mode == 'ON' ? c.successText : (s.mode == 'TEST_ONLY' ? c.infoText : Brand.mediumGrey)),
                  ),
                  const SizedBox(width: 8),
                  Text(_modeText[s.mode] ?? s.mode,
                      style: TextStyle(fontWeight: FontWeight.w500, fontSize: 13.5, color: s.mode == 'ON' ? c.successText : (s.mode == 'TEST_ONLY' ? c.infoText : c.muted))),
                ]),
              ),
            ]),
            const SizedBox(height: 12),
            _SetupNotice(status: s),
          ],
          if (auth.can('CONVERSATION_MANAGE')) ...[
            Btn('Probar el asistente', icon: Icons.science_outlined, variant: BtnVariant.secondary, loading: simulating, onPressed: simulate),
            const SizedBox(height: 12),
          ],
          FilterChips<String>(
            value: filter,
            onChanged: (f) => setState(() => filter = f),
            options: [
              (id: 'all', label: 'Todas', count: all.length),
              (id: 'attention', label: 'Requieren atención', count: all.where((i) => i.requiresAttention).length),
              for (final k in controlLabels.keys) (id: k, label: controlLabels[k]!, count: all.where((i) => i.control == k).length),
            ],
          ),
          const SizedBox(height: 14),
          if (error != null && items == null)
            LoadError(message: error!, onRetry: load)
          else if (items == null)
            const StateMessage(kind: StateKind.loading, title: 'Cargando…')
          else if (visible.isEmpty)
            StateMessage(
              kind: StateKind.empty,
              title: all.isEmpty ? 'Aún no hay conversaciones' : 'Sin conversaciones en este filtro.',
              description: all.isEmpty ? 'Cuando un cliente escriba por WhatsApp aparecerá aquí.' : null,
            )
          else
            AppCard(
              padding: EdgeInsets.zero,
              child: Column(children: [
                for (var i = 0; i < visible.length; i++) ...[
                  if (i > 0) Divider(height: 1, color: c.border),
                  _ConversationTile(item: visible[i]),
                ],
              ]),
            ),
        ],
      ),
    );
  }
}

/// Lo que falta configurar para que el bot funcione con clientes reales.
class _SetupNotice extends StatelessWidget {
  const _SetupNotice({required this.status});
  final AssistantStatus status;

  @override
  Widget build(BuildContext context) {
    final missing = [
      if (!status.aiConfigured) 'la llave de OpenAI (AI_API_KEY)',
      if (!status.inventoryAvailable) 'la llave de 21Online (ONLINE21_API_KEY) para consultar propiedades',
      if (!status.whatsappConfigured) 'WhatsApp Cloud API (WHATSAPP_PHONE_NUMBER_ID y WHATSAPP_ACCESS_TOKEN)',
      if (!status.webhookConfigured) 'el webhook de Meta (WHATSAPP_VERIFY_TOKEN y WHATSAPP_APP_SECRET)',
    ];
    if (missing.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Notice(
        tone: Tone.info,
        icon: Icons.warning_amber_rounded,
        title: 'Falta configurar en el servidor:',
        text: '${missing.join('; ')}.${status.aiConfigured ? ' Mientras tanto puedes probar al asistente con el simulador.' : ''}',
      ),
    );
  }
}

class _ConversationTile extends StatelessWidget {
  const _ConversationTile({required this.item});
  final ConversationItem item;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final i = item;
    return InkWell(
      onTap: () => context.push('/conversaciones/${i.id}'),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Avatar(i.contactName ?? '?'),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Expanded(child: Text(i.contactName ?? i.phone ?? 'Contacto', style: TextStyle(fontWeight: FontWeight.w600, color: c.fg), overflow: TextOverflow.ellipsis)),
                if (i.lastMessageAt != null) Text(timeAgo(i.lastMessageAt!), style: TextStyle(fontSize: 12, color: c.muted)),
              ]),
              const SizedBox(height: 2),
              Text(
                '${i.lastMessageRole == 'BOT' ? 'IA: ' : (i.lastMessageRole == 'TEAM' ? 'Equipo: ' : '')}${i.lastMessage ?? ''}',
                style: TextStyle(fontSize: 12.5, color: c.muted),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 6),
              Wrap(spacing: 6, runSpacing: 4, children: [
                AppBadge(label(controlLabels, i.control), tone: controlTones[i.control] ?? Tone.neutral),
                if (i.test) const AppBadge('Prueba', tone: Tone.manual, icon: Icons.science_outlined),
                if (i.requiresAttention) const AppBadge('Atención', tone: Tone.warning, icon: Icons.warning_amber_rounded),
              ]),
            ]),
          ),
        ]),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Una conversación: chat + contexto
// ---------------------------------------------------------------------------

class ConversationScreen extends StatefulWidget {
  const ConversationScreen({super.key, required this.id});
  final String id;

  @override
  State<ConversationScreen> createState() => _ConversationScreenState();
}

class _ConversationScreenState extends State<ConversationScreen> {
  ConversationDetail? detail;
  String? error;
  Timer? _timer;
  final _text = TextEditingController();
  final _scroll = ScrollController();
  bool sending = false;
  int _lastCount = 0;

  @override
  void initState() {
    super.initState();
    load();
    _timer = Timer.periodic(const Duration(milliseconds: _pollMs), (_) => load());
  }

  @override
  void dispose() {
    _timer?.cancel();
    _text.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> load() async {
    try {
      final d = await api.getConversation(widget.id);
      if (mounted) update(d);
    } catch (err) {
      if (mounted && detail == null) setState(() => error = crmErrorText(err));
    }
  }

  void update(ConversationDetail d) {
    setState(() {
      detail = d;
      error = null;
    });
    if (d.messages.length != _lastCount) {
      _lastCount = d.messages.length;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scroll.hasClients) _scroll.jumpTo(_scroll.position.maxScrollExtent);
      });
    }
  }

  Future<void> send() async {
    final d = detail;
    final value = _text.text.trim();
    if (d == null || value.isEmpty) return;
    setState(() => sending = true);
    try {
      if (d.conversation.test && d.conversation.control == 'AI') {
        // En la conversación de prueba escribes como si fueras el cliente.
        await api.simulateMessage(value);
        await load();
      } else {
        update(await api.replyToConversation(d.conversation.id, value));
      }
      _text.clear();
    } catch (err) {
      if (mounted) showToast(context, crmErrorText(err));
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  Future<void> setControl(String control) async {
    try {
      update(await api.setConversationControl(detail!.conversation.id, control));
    } catch (err) {
      if (mounted) showToast(context, crmErrorText(err));
    }
  }

  void openContext() {
    showAppSheet(context, title: 'Contexto', builder: (ctx, setSheet) => _ContextPanel(
          detail: detail!,
          onControl: (control) async {
            Navigator.pop(ctx);
            await setControl(control);
          },
          onResolved: () async {
            Navigator.pop(ctx);
            await load();
          },
          onReset: () async {
            Navigator.pop(ctx);
            await api.resetSimulation();
            if (mounted) {
              showToast(context, 'Conversación de prueba reiniciada');
              context.pop();
            }
          },
        ));
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final c = context.c;
    final d = detail;
    if (d == null) {
      return AppScaffold(
        title: 'Conversación',
        body: PageBody(children: [error != null ? LoadError(message: error!, onRetry: load) : const StateMessage(kind: StateKind.loading, title: 'Cargando…')]),
      );
    }
    final conv = d.conversation;
    final asClient = conv.test && conv.control == 'AI';
    return Scaffold(
      appBar: AppBar(
        titleSpacing: 0,
        title: Row(children: [
          Avatar(conv.contactName ?? '?', size: 34),
          const SizedBox(width: 10),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(conv.contactName ?? 'Contacto', style: const TextStyle(fontSize: 15.5, fontWeight: FontWeight.w600), overflow: TextOverflow.ellipsis),
              Text(conv.test ? 'Conversación de prueba (no se envía por WhatsApp)' : (conv.phone ?? ''),
                  style: TextStyle(fontSize: 11.5, color: c.muted, fontWeight: FontWeight.normal), overflow: TextOverflow.ellipsis),
            ]),
          ),
        ]),
        actions: [
          Padding(padding: const EdgeInsets.only(right: 4), child: Center(child: AppBadge(label(controlLabels, conv.control), tone: controlTones[conv.control] ?? Tone.neutral))),
          IconButton(
            tooltip: 'Contexto',
            icon: Badge(isLabelVisible: conv.requiresAttention, backgroundColor: c.warningText, smallSize: 8, child: const Icon(Icons.info_outline)),
            onPressed: openContext,
          ),
        ],
      ),
      body: SafeArea(
        top: false,
        child: Column(children: [
          if (conv.needsAttention != null)
            Container(
              width: double.infinity,
              color: c.warningBg,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Row(children: [
                Icon(Icons.warning_amber_rounded, size: 18, color: c.warningText),
                const SizedBox(width: 8),
                Expanded(child: Text(conv.needsAttention!, style: TextStyle(color: c.warningText, fontSize: 13))),
              ]),
            ),
          Expanded(
            child: Container(
              color: c.appBg,
              child: ListView.builder(
                controller: _scroll,
                padding: const EdgeInsets.all(14),
                itemCount: d.messages.length,
                itemBuilder: (context, index) => _Bubble(message: d.messages[index]),
              ),
            ),
          ),
          if (auth.can('CONVERSATION_MANAGE'))
            Container(
              decoration: BoxDecoration(color: c.card, border: Border(top: BorderSide(color: c.border))),
              padding: const EdgeInsets.fromLTRB(12, 8, 8, 8),
              child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                Expanded(
                  child: TextField(
                    controller: _text,
                    minLines: 1,
                    maxLines: 5,
                    textInputAction: TextInputAction.newline,
                    onChanged: (_) => setState(() {}),
                    decoration: InputDecoration(hintText: asClient ? 'Escribe como si fueras el cliente…' : 'Responder como equipo (pausa la IA)'),
                  ),
                ),
                const SizedBox(width: 8),
                Btn('', icon: Icons.send, loading: sending, onPressed: _text.text.trim().isEmpty ? null : send),
              ]),
            ),
        ]),
      ),
    );
  }
}

class _Bubble extends StatelessWidget {
  const _Bubble({required this.message});
  final ConversationMessage message;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final m = message;
    final mine = m.role != 'CLIENT';
    final team = m.role == 'TEAM';
    final bg = m.role == 'CLIENT' ? c.card : (m.role == 'BOT' ? Brand.gold.withValues(alpha: 0.22) : Brand.ink);
    final fg = team ? Colors.white : c.fg;
    return Align(
      alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 10),
        constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.8),
        padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 8),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(16),
            topRight: const Radius.circular(16),
            bottomLeft: Radius.circular(mine ? 16 : 5),
            bottomRight: Radius.circular(mine ? 5 : 16),
          ),
          border: m.role == 'CLIENT' ? Border.all(color: c.border) : null,
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(mainAxisSize: MainAxisSize.min, children: [
            if (m.role == 'BOT') Icon(Icons.smart_toy_outlined, size: 12, color: c.muted),
            if (team) Icon(Icons.person_outline, size: 12, color: Colors.white.withValues(alpha: 0.7)),
            if (m.role == 'BOT' || team) const SizedBox(width: 3),
            Text(
              '${m.role == 'CLIENT' ? 'Cliente' : (m.role == 'BOT' ? 'Asistente' : 'Equipo')} · ${formatTime(m.createdAt)}'.toUpperCase(),
              style: TextStyle(fontSize: 10, fontWeight: FontWeight.w600, letterSpacing: 0.5, color: team ? Colors.white.withValues(alpha: 0.7) : c.muted),
            ),
          ]),
          const SizedBox(height: 2),
          SelectableText(m.text, style: TextStyle(fontSize: 14, color: fg, height: 1.35)),
          if (m.status == 'FAILED') Text('No se envió: ${m.error ?? ''}', style: TextStyle(fontSize: 11.5, color: c.dangerText)),
        ]),
      ),
    );
  }
}

class _ContextPanel extends StatelessWidget {
  const _ContextPanel({required this.detail, required this.onControl, required this.onResolved, required this.onReset});
  final ConversationDetail detail;
  final Future<void> Function(String control) onControl;
  final Future<void> Function() onResolved;
  final Future<void> Function() onReset;

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final c = context.c;
    final conv = detail.conversation;
    final open = detail.escalations.where((e) => e.status == 'OPEN').toList();
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const SectionTitle('Lo que entendió'),
      Text(conv.intent, style: TextStyle(color: c.fg)),
      if (detail.summary != null) ...[const SizedBox(height: 14), const SectionTitle('Resumen para el asesor'), Text(detail.summary!, style: TextStyle(color: c.fg))],
      if (conv.needsAttention != null) ...[const SizedBox(height: 14), Notice(tone: Tone.warning, icon: Icons.warning_amber_rounded, text: conv.needsAttention)],
      if (open.isNotEmpty) ...[
        const SizedBox(height: 14),
        const SectionTitle('Pendientes'),
        for (final e in open)
          Container(
            width: double.infinity,
            margin: const EdgeInsets.only(bottom: 8),
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(borderRadius: BorderRadius.circular(12), border: Border.all(color: c.border)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(e.reason, style: TextStyle(color: c.fg)),
              if (auth.can('CONVERSATION_MANAGE'))
                LinkText('Marcar atendido', icon: Icons.check, onTap: () async {
                  await api.resolveEscalation(e.id);
                  await onResolved();
                }),
            ]),
          ),
      ],
      if (conv.leadId != null) ...[
        const SizedBox(height: 8),
        LinkText('Ver lead', trailingIcon: Icons.arrow_forward, onTap: () {
          Navigator.pop(context);
          context.push('/leads/${conv.leadId}');
        }),
      ],
      if (auth.can('CONVERSATION_MANAGE')) ...[
        const SizedBox(height: 14),
        Divider(color: c.border),
        const SizedBox(height: 10),
        const SectionTitle('Control de la IA'),
        conv.control == 'AI'
            ? Btn('Pausar IA', icon: Icons.pause, variant: BtnVariant.secondary, small: true, onPressed: () => onControl('PAUSED'))
            : Btn('Reanudar IA', icon: Icons.play_arrow, variant: BtnVariant.secondary, small: true, onPressed: () => onControl('AI')),
        const SizedBox(height: 8),
        const MutedText(
            'Con la IA en pausa el cliente solo recibe lo que escriba el equipo. Al reanudarla no contesta los mensajes que llegaron durante la pausa.',
            size: 12),
        if (conv.test) ...[const SizedBox(height: 8), Btn('Reiniciar prueba', icon: Icons.restart_alt, variant: BtnVariant.ghost, small: true, onPressed: onReset)],
      ],
    ]);
  }
}
