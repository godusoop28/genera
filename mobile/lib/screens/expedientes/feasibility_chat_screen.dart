import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../api/models.dart';
import '../../labels.dart';
import '../../ui/widgets.dart';

const _suggestions = [
  '¿Qué escuelas, hospitales y centros comerciales hay cerca y a qué distancia?',
  'Redacta un mensaje para el propietario explicando el precio recomendado.',
  '¿Cómo respondo si el comprador dice que el precio está alto?',
  '¿Cuánto se rentaría este inmueble y qué rendimiento anual daría?',
];

/// Chat de consultas adicionales sobre una factibilidad (equivale a FeasibilityChat.tsx de la web).
class FeasibilityChatScreen extends StatefulWidget {
  const FeasibilityChatScreen({super.key, required this.reportId, required this.versionNumber});
  final String reportId;
  final int versionNumber;

  @override
  State<FeasibilityChatScreen> createState() => _FeasibilityChatScreenState();
}

class _FeasibilityChatScreenState extends State<FeasibilityChatScreen> {
  List<FeasibilityChatMessage>? messages;
  final input = TextEditingController();
  final scroll = ScrollController();
  bool sending = false;
  Timer? poll;

  bool get pending => (messages ?? const []).any((m) => m.status == 'PENDING');

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    poll?.cancel();
    input.dispose();
    scroll.dispose();
    super.dispose();
  }

  void _show(List<FeasibilityChatMessage> list) {
    if (!mounted) return;
    setState(() => messages = list);
    poll?.cancel();
    if (pending) poll = Timer(const Duration(seconds: 4), load);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (scroll.hasClients) scroll.animateTo(scroll.position.maxScrollExtent, duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
    });
  }

  Future<void> load() async {
    try {
      _show(await api.getFeasibilityChat(widget.reportId));
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  Future<void> send() async {
    final q = input.text.trim();
    if (q.isEmpty || sending || pending) return;
    setState(() => sending = true);
    try {
      _show(await api.askFeasibilityChat(widget.reportId, q));
      input.clear();
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final list = messages;
    return Scaffold(
      appBar: AppBar(title: Text('Consultas · factibilidad v${widget.versionNumber}', overflow: TextOverflow.ellipsis)),
      body: SafeArea(
        child: Column(children: [
          Expanded(
            child: list == null
                ? const Center(child: MutedText('Cargando…'))
                : ListView(controller: scroll, padding: const EdgeInsets.all(14), children: [
                    if (list.isEmpty) ...[
                      const MutedText(
                          'Pregunta lo que necesites sobre la propiedad o la factibilidad. La IA usa el reporte, los datos y documentos del expediente y, si hace falta, investiga en internet.'),
                      const SizedBox(height: 10),
                      for (final s in _suggestions)
                        Padding(
                          padding: const EdgeInsets.only(bottom: 6),
                          child: AppCard(padding: const EdgeInsets.all(12), onTap: () => setState(() => input.text = s), child: Text(s, style: TextStyle(color: c.fg, fontSize: 13.5))),
                        ),
                    ],
                    for (final m in list) _Bubble(message: m),
                  ]),
          ),
          Container(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 10),
            decoration: BoxDecoration(color: c.card, border: Border(top: BorderSide(color: c.border))),
            child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
              Expanded(
                child: TextField(
                  controller: input,
                  minLines: 1,
                  maxLines: 5,
                  maxLength: 4000,
                  textInputAction: TextInputAction.newline,
                  decoration: InputDecoration(hintText: pending ? 'La IA está respondiendo…' : 'Escribe tu pregunta', counterText: ''),
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filled(
                tooltip: 'Enviar',
                onPressed: sending || pending ? null : send,
                icon: sending ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.send),
              ),
            ]),
          ),
        ]),
      ),
    );
  }
}

class _Bubble extends StatelessWidget {
  const _Bubble({required this.message});
  final FeasibilityChatMessage message;

  /// Las respuestas vienen en Markdown; en el celular se muestran como texto limpio y seleccionable.
  static String plain(String text) => text
      .replaceAllMapped(RegExp(r'\*\*(.+?)\*\*'), (m) => m[1]!)
      .replaceAllMapped(RegExp(r'\[([^\]]+)\]\((https?://[^)\s]+)\)'), (m) => '${m[1]} (${m[2]})')
      .replaceAll(RegExp(r'^#{1,6}\s+', multiLine: true), '')
      .replaceAll(RegExp(r'^\s*[-*]\s+', multiLine: true), '• ')
      .replaceAll(RegExp(r'^\|?\s*:?-{2,}.*$', multiLine: true), '')
      .replaceAll('`', '');

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final m = message;
    final user = m.role == 'USER';
    final Widget body = user
        ? SelectableText(m.content ?? '', style: const TextStyle(color: Colors.white, fontSize: 14))
        : m.status == 'PENDING'
            ? Row(mainAxisSize: MainAxisSize.min, children: [
                const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2)),
                const SizedBox(width: 8),
                Flexible(child: MutedText('Analizando… puede tardar hasta un par de minutos si investiga en internet.', size: 13)),
              ])
            : m.status == 'FAILED'
                ? Text('${m.error ?? 'La IA no pudo responder.'} Vuelve a preguntar.', style: TextStyle(color: c.dangerText, fontSize: 13.5))
                : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    SelectableText(plain(m.content ?? ''), style: TextStyle(color: c.fg, fontSize: 14, height: 1.35)),
                    if (m.sources.isNotEmpty) ...[
                      const SizedBox(height: 6),
                      Expandable(
                        title: 'Fuentes (${m.sources.length})',
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          for (final s in m.sources) LinkText(s.title.isEmpty ? s.url : s.title, icon: Icons.open_in_new, onTap: () => openUrl(context, s.url)),
                        ]),
                      ),
                    ],
                  ]);
    return Align(
      alignment: user ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 10),
        constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.86),
        padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 10),
        decoration: BoxDecoration(
          color: user ? Brand.ink : c.card,
          borderRadius: BorderRadius.circular(16),
          border: user ? null : Border.all(color: c.border),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          body,
          const SizedBox(height: 4),
          Text('${user && m.authorName != null ? '${m.authorName} · ' : ''}${formatDateTime(m.createdAt)}',
              style: TextStyle(fontSize: 10.5, color: user ? Colors.white60 : c.muted)),
        ]),
      ),
    );
  }
}
