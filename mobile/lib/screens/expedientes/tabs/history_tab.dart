import 'package:flutter/material.dart';

import '../../../api/api.dart' as api;
import '../../../api/models.dart';
import '../../../labels.dart';
import '../../../ui/widgets.dart';
import '../expediente_detail_screen.dart';

String _actorText(String? type, String? name, String? role) => switch (type) {
      'CLIENT' => 'Cliente (desde su liga)',
      'SYSTEM' => 'Sistema (automático)',
      'STAFF' => '${name ?? 'Usuario interno'}${role != null ? ' · ${label(roleLabels, role)}' : ''}',
      _ => 'No registrado (movimiento anterior a la bitácora con usuario)',
    };

class HistoryTab extends StatefulWidget {
  const HistoryTab({super.key, required this.ctx});
  final ExpedienteCtx ctx;

  @override
  State<HistoryTab> createState() => _HistoryTabState();
}

class _HistoryTabState extends State<HistoryTab> {
  String view = 'actividad';
  List<ActivityResponse>? activity;
  List<ChangeResponse>? changes;
  List<NotificationResponse>? notifications;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    final id = widget.ctx.expediente.id;
    await Future.wait([
      api.getActivity(id).then((r) => activity = r).catchError((_) => activity = []),
      api.getChanges(id).then((r) => changes = r).catchError((_) => changes = []),
      api.getNotifications(id).then((r) => notifications = r).catchError((_) => notifications = []),
    ]);
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    Widget box(Widget child) => Container(
          width: double.infinity,
          margin: const EdgeInsets.only(bottom: 8),
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
          child: child,
        );
    return TabBody(
      onRefresh: load,
      children: [
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const CardHeader(title: 'Bitácora del expediente', description: 'Quién hizo cada acción, con qué rol, sobre qué documento, y cuándo.'),
            FilterChips<String>(
              value: view,
              options: const [(id: 'actividad', label: 'Movimientos', count: null), (id: 'cambios', label: 'Correcciones de datos', count: null), (id: 'avisos', label: 'Avisos enviados', count: null)],
              onChanged: (v) => setState(() => view = v),
            ),
            const SizedBox(height: 14),
            if (view == 'actividad') ...[
              if (activity == null) const MutedText('Cargando…'),
              if (activity?.isEmpty ?? false) const MutedText('Sin movimientos todavía.'),
              for (final a in activity ?? <ActivityResponse>[])
                Container(
                  margin: const EdgeInsets.only(bottom: 12),
                  padding: const EdgeInsets.only(left: 10),
                  decoration: const BoxDecoration(border: Border(left: BorderSide(color: Brand.gold, width: 2))),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(a.message, style: TextStyle(fontSize: 13.5, color: c.fg)),
                    MutedText(
                      '${formatDateTime(a.occurredAt)} · ${_actorText(a.actorType, a.actorName, a.actorRole)}${a.documentLabel != null ? ' · Documento: ${a.documentLabel}' : ''}',
                      size: 12,
                    ),
                  ]),
                ),
            ],
            if (view == 'cambios') ...[
              if (changes == null) const MutedText('Cargando…'),
              if (changes?.isEmpty ?? false) const MutedText('No se han corregido datos.'),
              for (final ch in changes ?? <ChangeResponse>[])
                box(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text.rich(TextSpan(children: [
                    TextSpan(text: ch.section, style: const TextStyle(fontWeight: FontWeight.w600)),
                    TextSpan(text: ' — ${ch.field}: '),
                    TextSpan(text: ch.oldValue ?? 'vacío', style: TextStyle(color: c.muted, decoration: TextDecoration.lineThrough)),
                    const TextSpan(text: ' → '),
                    TextSpan(text: ch.newValue ?? 'vacío'),
                  ]), style: TextStyle(fontSize: 13.5, color: c.fg)),
                  MutedText('${formatDateTime(ch.changedAt)} · ${_actorText(ch.actorType, ch.actorName, ch.actorRole)}${ch.reason != null ? ' · Motivo: ${ch.reason}' : ''}', size: 12),
                ])),
            ],
            if (view == 'avisos') ...[
              if (notifications == null) const MutedText('Cargando…'),
              if (notifications?.isEmpty ?? false) const MutedText('No se han enviado avisos.'),
              for (final n in notifications ?? <NotificationResponse>[])
                box(Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [
                    Expanded(
                      child: Text.rich(TextSpan(children: [TextSpan(text: n.kind), TextSpan(text: ' → ${n.recipient}', style: TextStyle(color: c.muted))]),
                          style: TextStyle(fontSize: 13.5, color: c.fg)),
                    ),
                    AppBadge(label(notificationStatusLabels, n.status),
                        tone: n.status == 'SENT' ? Tone.success : (n.status == 'FAILED' ? Tone.danger : (n.status == 'SKIPPED' ? Tone.neutral : Tone.warning))),
                  ]),
                  MutedText('${n.subject} · ${formatDateTime(n.createdAt)}${n.attempts > 1 ? ' · ${n.attempts} intentos' : ''}', size: 12),
                  if (n.lastError != null) Text('Error de entrega: ${n.lastError}', style: TextStyle(fontSize: 12, color: c.dangerText)),
                ])),
            ],
          ]),
        ),
      ],
    );
  }
}
