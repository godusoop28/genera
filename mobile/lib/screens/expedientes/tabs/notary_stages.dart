import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../api/api.dart' as api;
import '../../../api/client.dart';
import '../../../api/models.dart';
import '../../../labels.dart';
import '../../../state/auth.dart';
import '../../../ui/widgets.dart';
import '../document_viewer_screen.dart';

/// Control de Cambios #23: etapas 2 (envío inicial a notaría) y 3 (cierre, al registrar la oferta
/// aceptada). Se eligen con casillas los documentos y la versión de cada uno; todo queda en el
/// historial. El cierre alerta si un documento no está confirmado como vigente y al corriente.
class NotaryStages extends StatefulWidget {
  const NotaryStages({super.key, required this.expedienteId, required this.names});
  final String expedienteId;
  final Map<String?, String> names;

  @override
  State<NotaryStages> createState() => _NotaryStagesState();
}

class _NotaryStagesState extends State<NotaryStages> {
  NotaryOverview? overview;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final o = await api.getNotaryOverview(widget.expedienteId);
      if (mounted) setState(() => overview = o);
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  String label(NotaryDocument d) => d.participantId != null && widget.names[d.participantId] != null ? '${d.name} — ${widget.names[d.participantId]}' : d.name;

  void view(NotaryDocument d, NotaryVersion v) => Navigator.of(context).push(MaterialPageRoute(
        builder: (_) => DocumentViewerScreen(versionId: v.id, title: '${label(d)} · versión ${v.versionNumber}'),
      ));

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final o = overview;
    if (o == null) {
      return const AppCard(child: Padding(padding: EdgeInsets.all(12), child: Center(child: CircularProgressIndicator())));
    }
    final canSend = auth.can('DOCUMENT_EMAIL_SEND');
    final canCheck = auth.can('DOCUMENT_UPLOAD') || auth.can('DOCUMENT_ACCEPT');
    final canOffer = auth.can('EXPEDIENT_EDIT') || auth.can('DOCUMENT_EMAIL_SEND');
    final withFiles = o.documents.where((d) => d.versions.isNotEmpty).toList();
    final closingDocs = o.documents.where((d) => d.closingRequired).toList();

    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const CardHeader(
            title: 'Etapa 2 · Envío inicial a notaría',
            description: 'Cuando hay interés formal: elige qué documentos y qué versión mandar para que la notaría abra su expediente.',
          ),
          if (canSend)
            Align(
              alignment: Alignment.centerLeft,
              child: Btn('Enviar a notaría: envío inicial', icon: Icons.send_outlined, small: true, onPressed: withFiles.isEmpty
                  ? null
                  : () => _send(context, 'INITIAL', withFiles, (d) => d.status == 'ACCEPTED')),
            )
          else
            const MutedText('Tu usuario no tiene permiso para enviar documentos a notaría.', size: 12),
          _History(submissions: o.submissions.where((s) => s.kind == 'INITIAL').toList()),
        ]),
      ),
      AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const CardHeader(
            title: 'Etapa 3 · Documentos para cierre',
            description: 'Con la oferta aceptada se piden los documentos actualizados: vigentes, al corriente de pago y con fecha reciente.',
          ),
          if (!o.closingActive) ...[
            const Notice(tone: Tone.neutral, text: 'Esta etapa se activa al registrar la oferta aceptada. Hasta entonces solo aplica el envío inicial.'),
            if (canOffer) ...[
              const SizedBox(height: 10),
              Align(
                alignment: Alignment.centerLeft,
                child: Btn('Registrar oferta aceptada', icon: Icons.check_circle_outline, small: true, onPressed: () async {
                  final note = await askReason(
                    context,
                    title: 'Registrar oferta aceptada',
                    description: 'Activa la etapa de cierre: se piden los documentos actualizados y se habilita el envío de cierre.',
                    label: 'Nota (opcional)',
                    minLength: 0,
                    confirmLabel: 'Registrar',
                  );
                  if (note == null) return;
                  try {
                    final updated = await api.registerOfferAccepted(widget.expedienteId, note);
                    if (mounted) setState(() => overview = updated);
                    if (context.mounted) showToast(context, 'Oferta aceptada registrada: la etapa de cierre está activa.');
                  } catch (err) {
                    if (context.mounted) showToast(context, errorText(err));
                  }
                }),
              ),
            ],
          ] else ...[
            Notice(
              tone: Tone.success,
              text: 'Oferta aceptada registrada el ${formatDateTime(o.offerAcceptedAt)}${o.offerAcceptedByName != null ? ' por ${o.offerAcceptedByName}' : ''}'
                  '${o.offerNote != null && o.offerNote!.isNotEmpty ? ' · ${o.offerNote}' : ''}',
              action: canOffer
                  ? Btn('Quitar', small: true, variant: BtnVariant.ghost, onPressed: () async {
                      try {
                        final updated = await api.clearOfferAccepted(widget.expedienteId);
                        if (mounted) setState(() => overview = updated);
                      } catch (err) {
                        if (context.mounted) showToast(context, errorText(err));
                      }
                    })
                  : null,
            ),
            const SizedBox(height: 12),
            const SectionTitle('Documentos que deben ir actualizados'),
            if (closingDocs.isEmpty) const MutedText('Este expediente no tiene documentos de cierre en su listado.', size: 12),
            for (final d in closingDocs) _closingRow(context, d, canCheck),
            if (canSend) ...[
              const SizedBox(height: 10),
              Align(
                alignment: Alignment.centerLeft,
                child: Btn('Enviar a notaría: envío de cierre', icon: Icons.send_outlined, small: true, onPressed: withFiles.isEmpty
                    ? null
                    : () => _send(context, 'CLOSING', withFiles, (d) => d.closingRequired)),
              ),
            ],
          ],
          _History(submissions: o.submissions.where((s) => s.kind == 'CLOSING').toList()),
        ]),
      ),
    ]);
  }

  Widget _closingRow(BuildContext context, NotaryDocument d, bool canCheck) {
    final c = context.c;
    final current = d.current;
    final (text, tone) = current == null
        ? ('Sin archivo', Tone.neutral)
        : d.closingValid
            ? ('✅ Vigente y al corriente (V${current.versionNumber})', Tone.success)
            : d.closingCheckValid == false && d.closingCheckedVersion == current.versionNumber
                ? ('⚠️ No vigente / no al corriente', Tone.danger)
                : ('⚠️ Sin confirmar vigencia', Tone.warning);
    Future<void> check(bool valid, String note) async {
      try {
        await api.setClosingCheck(d.documentId, valid, note);
        if (context.mounted) showToast(context, valid ? 'Confirmado como vigente y al corriente.' : 'Marcado como no vigente.');
        await load();
      } catch (err) {
        if (context.mounted) showToast(context, errorText(err));
      }
    }

    return Container(
      margin: const EdgeInsets.only(top: 8),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(label(d), style: TextStyle(fontWeight: FontWeight.w500, color: c.fg)),
        const SizedBox(height: 4),
        AppBadge(text, tone: tone),
        if (d.closingCheckedByName != null)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: MutedText('Revisado por ${d.closingCheckedByName} el ${formatDateTime(d.closingCheckedAt)} (V${d.closingCheckedVersion ?? '?'})', size: 12),
          ),
        const SizedBox(height: 6),
        Wrap(spacing: 6, runSpacing: 6, children: [
          if (current != null) Btn('Ver V${current.versionNumber}', icon: Icons.visibility_outlined, small: true, variant: BtnVariant.ghost, onPressed: () => view(d, current)),
          if (canCheck && current != null && !d.closingValid)
            Btn('Confirmar vigente', icon: Icons.check_circle_outline, small: true, variant: BtnVariant.secondary, onPressed: () => check(true, '')),
          if (canCheck && current != null && d.closingCheckValid != false)
            Btn('No está vigente…', small: true, variant: BtnVariant.ghost, onPressed: () async {
              final note = await askReason(context, title: '${label(d)}: no está vigente', label: 'Motivo', minLength: 0, confirmLabel: 'Guardar');
              if (note != null) await check(false, note);
            }),
          if (canCheck && current != null)
            Btn('Pedir actualización…', small: true, variant: BtnVariant.ghost, onPressed: () async {
              final note = await askReason(
                context,
                title: 'Pedir versión actualizada: ${label(d)}',
                description: 'El cliente verá esta nota en su liga y subirá la versión nueva; la actual se conserva.',
                label: '¿Qué debe actualizar?',
                minLength: 10,
                confirmLabel: 'Pedir actualización',
              );
              if (note == null) return;
              try {
                await api.requestDocumentUpdate(d.documentId, note);
                if (context.mounted) showToast(context, 'Se pidió la versión actualizada al cliente.');
                await load();
              } catch (err) {
                if (context.mounted) showToast(context, errorText(err));
              }
            }),
        ]),
      ]),
    );
  }

  Future<void> _send(BuildContext context, String kind, List<NotaryDocument> docs, bool Function(NotaryDocument) defaultSelected) async {
    final closing = kind == 'CLOSING';
    final selected = <String, String>{
      for (final d in docs)
        if (d.current != null && defaultSelected(d)) d.documentId: d.current!.id,
    };
    var email = '';
    var note = '';
    var busy = false;

    List<String> warnings() => closing
        ? [
            for (final d in docs)
              if (selected.containsKey(d.documentId) && d.closingRequired && !(d.closingValid && d.versions.any((v) => v.id == selected[d.documentId] && v.current)))
                '${label(d)} V${d.versions.where((v) => v.id == selected[d.documentId]).firstOrNull?.versionNumber ?? '?'}',
          ]
        : const [];

    await showAppSheet(
      context,
      title: closing ? 'Envío de cierre a notaría' : 'Envío inicial a notaría',
      builder: (ctx, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const MutedText('Marca los documentos y la versión de cada uno.', size: 12),
        const SizedBox(height: 8),
        for (final d in docs)
          Container(
            margin: const EdgeInsets.only(bottom: 6),
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
            decoration: BoxDecoration(color: ctx.c.appBg, borderRadius: BorderRadius.circular(10)),
            child: Row(children: [
              Checkbox(
                value: selected.containsKey(d.documentId),
                onChanged: (v) => setSheet(() {
                  if (v == true && d.current != null) {
                    selected[d.documentId] = d.current!.id;
                  } else {
                    selected.remove(d.documentId);
                  }
                }),
              ),
              Expanded(child: Text(label(d), style: const TextStyle(fontSize: 13))),
              DropdownButton<String>(
                value: selected[d.documentId] ?? d.current?.id,
                underline: const SizedBox.shrink(),
                items: [for (final v in d.versions) DropdownMenuItem(value: v.id, child: Text('V${v.versionNumber}${v.current ? ' (vigente)' : ''}', style: const TextStyle(fontSize: 12)))],
                onChanged: !selected.containsKey(d.documentId) || d.versions.length < 2 ? null : (v) => setSheet(() => selected[d.documentId] = v!),
              ),
            ]),
          ),
        const SizedBox(height: 8),
        AppTextField(label: 'Correo de la notaría (opcional)', hint: 'Si lo dejas vacío solo se registra el envío', keyboardType: TextInputType.emailAddress, onChanged: (v) => setSheet(() => email = v)),
        const SizedBox(height: 8),
        AppTextField(label: 'Nota (opcional)', hint: 'Ej. Notaría 12, Lic. Ruiz', onChanged: (v) => note = v),
        if (warnings().isNotEmpty) ...[
          const SizedBox(height: 8),
          Notice(tone: Tone.warning, icon: Icons.warning_amber_rounded, text: 'Sin confirmar como vigentes y al corriente: ${warnings().join(', ')}.'),
        ],
      ]),
      footer: (ctx, setSheet) => [
        Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.pop(ctx)),
        Btn(email.trim().isNotEmpty ? 'Enviar y registrar' : 'Registrar envío', icon: Icons.send_outlined, loading: busy, onPressed: selected.isEmpty
            ? null
            : () async {
                final pending = warnings();
                if (pending.isNotEmpty) {
                  final ok = await confirmDialog(ctx,
                      title: '¿Enviar de todos modos?',
                      message: 'Estos documentos de cierre no están confirmados como vigentes y al corriente: ${pending.join(', ')}. El envío quedará registrado con esta advertencia.',
                      confirmLabel: 'Enviar de todos modos');
                  if (!ok) return;
                }
                setSheet(() => busy = true);
                try {
                  await api.sendToNotary(widget.expedienteId, kind, selected,
                      recipientEmail: email.trim().isEmpty ? null : email.trim(), note: note.trim().isEmpty ? null : note.trim(), confirmWarnings: pending.isNotEmpty);
                  if (ctx.mounted) Navigator.pop(ctx);
                  if (context.mounted) {
                    showToast(context, email.trim().isNotEmpty ? 'Envío registrado y correo en cola para la notaría.' : 'Envío registrado en el historial del expediente.');
                  }
                  await load();
                } catch (err) {
                  setSheet(() => busy = false);
                  if (context.mounted) showToast(context, errorText(err));
                }
              }),
      ],
    );
  }
}

class _History extends StatelessWidget {
  const _History({required this.submissions});
  final List<NotarySubmission> submissions;

  @override
  Widget build(BuildContext context) {
    if (submissions.isEmpty) return const SizedBox.shrink();
    final c = context.c;
    return Container(
      margin: const EdgeInsets.only(top: 12),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(color: c.appBg, borderRadius: BorderRadius.circular(10)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('Historial de envíos', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: c.fg)),
        for (final s in submissions)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: MutedText(
              '${formatDateTime(s.sentAt)}${s.sentByName != null ? ' · ${s.sentByName}' : ''}'
              '${s.recipientEmail != null ? ' · enviado a ${s.recipientEmail}' : ' · registrado (envío por fuera)'}'
              '${s.note != null ? ' · ${s.note}' : ''}\n'
              '${s.items.map((i) => '${i.documentName} V${i.versionNumber}').join(', ')}'
              '${s.warnings != null ? '\nAdvertencias aceptadas: ${s.warnings}' : ''}',
              size: 12,
            ),
          ),
      ]),
    );
  }
}
