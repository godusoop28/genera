import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../api/api.dart' as api;
import '../../../api/client.dart';
import '../../../api/models.dart';
import '../../../labels.dart';
import '../../../state/auth.dart';
import '../../../ui/file_pick.dart';
import '../../../ui/widgets.dart';
import '../expediente_detail_screen.dart';

const _inProgress = ['UPLOADED', 'PROCESSING'];

final _statusFilters = <String, ({String label, bool Function(DocumentResponse) matches})>{
  'ALL': (label: 'Todos', matches: (_) => true),
  'MISSING': (label: 'Faltantes', matches: (d) => d.required && d.status == 'PENDING'),
  'TO_REVIEW': (label: 'Por revisar', matches: (d) => d.status == 'UPLOADED' || d.status == 'READY_FOR_REVIEW'),
  'RETURNED': (label: 'Devueltos o rechazados', matches: (d) => d.status == 'RETURNED' || d.status == 'REJECTED'),
};

class DocumentsTab extends StatefulWidget {
  const DocumentsTab({super.key, required this.ctx});
  final ExpedienteCtx ctx;

  @override
  State<DocumentsTab> createState() => _DocumentsTabState();
}

class _DocumentsTabState extends State<DocumentsTab> {
  List<DocumentResponse>? documents;
  bool showOptional = false;
  String filter = 'ALL';
  bool reprocessingAll = false;
  Timer? _poll;

  ExpedienteCtx get ctx => widget.ctx;

  @override
  void initState() {
    super.initState();
    load();
  }

  @override
  void dispose() {
    _poll?.cancel();
    super.dispose();
  }

  Future<void> load() async {
    try {
      final docs = await api.listDocuments(ctx.expediente.id);
      if (!mounted) return;
      setState(() => documents = docs);
      // Mientras haya archivos procesándose, se actualiza solo (sin volver a subir nada).
      _poll?.cancel();
      if (docs.any((d) => d.pipelineStatus != null && _inProgress.contains(d.pipelineStatus))) {
        _poll = Timer(const Duration(seconds: 4), load);
      }
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  Future<void> onChanged([DocumentResponse? updated]) async {
    if (updated != null) setState(() => documents = documents?.map((d) => d.id == updated.id ? updated : d).toList());
    await load();
    await ctx.reload();
  }

  bool _isPending(DocumentResponse d) => d.required && d.status != 'ACCEPTED' && d.status != 'NOT_APPLICABLE';

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final c = context.c;
    final docs = documents;
    final canReprocess = auth.can('DOCUMENT_ACCEPT') || auth.can('EXTRACTED_DATA_EDIT') || auth.can('DOCUMENT_UPLOAD');
    final names = {for (final p in ctx.participants) p.id: p.fullName};
    final visible = (docs ?? []).where((d) => d.required || d.currentVersionNumber > 0 || d.status == 'NOT_APPLICABLE' || showOptional).toList();
    final pendingCount = visible.where(_isPending).length;
    final requiredCount = visible.where((d) => d.required).length;
    final withFiles = (docs ?? []).where((d) => d.currentVersionNumber > 0 && d.status != 'NOT_APPLICABLE').toList();
    final processing = (docs ?? []).any((d) => d.pipelineStatus != null && _inProgress.contains(d.pipelineStatus));
    final filtered = visible.where(_statusFilters[filter]!.matches).toList();
    final ordered = [...ctx.participants]..sort((a, b) => a.ordinal.compareTo(b.ordinal));
    final groups = [
      for (final p in ordered) (key: p.id!, title: p.fullName, subtitle: label(participantRoleLabels, p.role), docs: filtered.where((d) => d.participantId == p.id).toList()),
      (
        key: 'inmueble',
        title: ctx.expediente.personType == 'MORAL' ? 'Inmueble y empresa' : 'Inmueble',
        subtitle: null,
        docs: filtered.where((d) => d.participantId == null || !names.containsKey(d.participantId)).toList(),
      ),
    ].where((g) => g.docs.isNotEmpty).toList();

    return TabBody(
      onRefresh: onChanged,
      children: [
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            CardHeader(
              title: 'Documentos',
              description: docs == null
                  ? 'Cargando requisitos…'
                  : pendingCount > 0
                      ? 'Faltan $pendingCount documento(s) obligatorio(s) por aceptar o marcar como "No aplica".'
                      : requiredCount > 0
                          ? 'Todos los documentos obligatorios están resueltos.'
                          : 'Este expediente todavía no tiene documentos obligatorios registrados.',
            ),
            if (processing)
              Container(
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(color: c.appBg, borderRadius: BorderRadius.circular(10)),
                child: const Row(children: [
                  SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2)),
                  SizedBox(width: 8),
                  Expanded(child: MutedText('La extracción automática continúa. Puedes seguir trabajando; la información se actualiza sola.', size: 12)),
                ]),
              ),
            if (canReprocess && withFiles.isNotEmpty)
              Align(
                alignment: Alignment.centerLeft,
                child: Btn('Reprocesar todos con IA', icon: Icons.refresh, small: true, variant: BtnVariant.ghost, loading: reprocessingAll, onPressed: () async {
                  setState(() => reprocessingAll = true);
                  try {
                    final n = await api.reprocessAllDocuments(ctx.expediente.id);
                    if (context.mounted) showToast(context, 'Se volvieron a procesar $n archivo(s) con las reglas actuales; los datos se actualizan solos.');
                    load();
                  } catch (err) {
                    if (context.mounted) showToast(context, errorText(err));
                  } finally {
                    if (mounted) setState(() => reprocessingAll = false);
                  }
                }),
              ),
            if (docs == null)
              const Padding(padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator()))
            else ...[
              const SizedBox(height: 8),
              FilterChips<String>(
                value: filter,
                options: [for (final f in _statusFilters.entries) (id: f.key, label: f.value.label, count: visible.where(f.value.matches).length)],
                onChanged: (f) => setState(() => filter = f),
              ),
              const SizedBox(height: 14),
              if (groups.isEmpty) const MutedText('No hay documentos con este filtro.'),
              for (final g in groups) ...[
                Container(
                  padding: const EdgeInsets.only(bottom: 6),
                  margin: const EdgeInsets.only(bottom: 10, top: 6),
                  decoration: BoxDecoration(border: Border(bottom: BorderSide(color: c.border))),
                  child: Row(children: [
                    Expanded(
                      child: Text.rich(TextSpan(children: [
                        TextSpan(text: g.title, style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
                        if (g.subtitle != null) TextSpan(text: '  ${g.subtitle}', style: TextStyle(fontSize: 12, color: c.muted)),
                      ]), style: const TextStyle(fontSize: 14)),
                    ),
                    Builder(builder: (_) {
                      final n = g.docs.where(_isPending).length;
                      return Text(n > 0 ? '$n pendiente(s)' : 'Sin pendientes', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: n > 0 ? c.warningText : c.successText));
                    }),
                  ]),
                ),
                for (final d in g.docs)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: _DocumentRow(
                      key: ValueKey(d.id),
                      document: d,
                      allDocuments: docs,
                      names: names,
                      participantName: d.participantId != null ? names[d.participantId] : null,
                      onChanged: onChanged,
                      canReprocess: canReprocess,
                    ),
                  ),
              ],
            ],
            LinkText(showOptional ? 'Ocultar documentos que no aplican' : 'Mostrar también documentos que no aplican a este expediente',
                onTap: () => setState(() => showOptional = !showOptional)),
          ]),
        ),
        if (auth.can('DOCUMENT_EMAIL_SEND'))
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const CardHeader(title: 'Envío a notaría', description: 'Arma el correo con los documentos aceptados para que lo revises y lo envíes tú desde tu correo.'),
              Btn('Vista previa de envío', icon: Icons.mail_outline, small: true, onPressed: () async {
                try {
                  final preview = await api.getDocumentsEmailPreview(ctx.expediente.id);
                  if (context.mounted) _showEmailPreview(context, preview);
                } catch (err) {
                  if (context.mounted) showToast(context, errorText(err));
                }
              }),
            ]),
          ),
      ],
    );
  }

  void _showEmailPreview(BuildContext context, EmailPreview p) => showAppSheet(
        context,
        title: 'Vista previa de envío a notaría',
        builder: (ctx, _) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const MutedText('Esto no envía nada. Descarga los documentos y envíalos tú desde tu correo.', size: 12),
          const SizedBox(height: 12),
          Text.rich(TextSpan(children: [const TextSpan(text: 'Asunto: ', style: TextStyle(fontWeight: FontWeight.w600)), TextSpan(text: p.subject)])),
          const SizedBox(height: 10),
          SelectableText(p.body),
          const SizedBox(height: 12),
          for (final a in p.attachments)
            Container(
              margin: const EdgeInsets.only(bottom: 6),
              padding: const EdgeInsets.fromLTRB(12, 4, 4, 4),
              decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: ctx.c.border)),
              child: Row(children: [
                Expanded(child: Text(a.fileName, style: const TextStyle(fontSize: 13))),
                TextButton(onPressed: () => openUrl(ctx, a.downloadUrl), child: Text('Descargar', style: TextStyle(color: ctx.c.goldText))),
              ]),
            ),
          if (p.documentTypesWithoutFile.isNotEmpty)
            Notice(tone: Tone.warning, text: 'Sin archivo para adjuntar todavía: ${p.documentTypesWithoutFile.join(', ')}.'),
          const SizedBox(height: 10),
          Btn('Copiar asunto y texto', icon: Icons.copy, variant: BtnVariant.secondary, onPressed: () => copyText(ctx, '${p.subject}\n\n${p.body}', 'Correo copiado.')),
        ]),
      );
}

class _DocumentRow extends StatefulWidget {
  const _DocumentRow({
    super.key,
    required this.document,
    required this.allDocuments,
    required this.names,
    required this.participantName,
    required this.onChanged,
    required this.canReprocess,
  });

  final DocumentResponse document;
  final List<DocumentResponse> allDocuments;
  final Map<String?, String> names;
  final String? participantName;
  final Future<void> Function([DocumentResponse? updated]) onChanged;
  final bool canReprocess;

  @override
  State<_DocumentRow> createState() => _DocumentRowState();
}

class _DocumentRowState extends State<_DocumentRow> {
  bool uploading = false;
  int? progress;
  bool showData = false;
  List<ExtractedField>? fields;
  String? fieldsFor;
  List<ReviewHistory>? history;
  bool reprocessing = false;

  DocumentResponse get doc => widget.document;
  LatestVersion? get v => doc.latestVersion;

  Future<void> act(Future<DocumentResponse> Function() action, String success) async {
    try {
      final updated = await action();
      if (mounted) showToast(context, success);
      await widget.onChanged(updated);
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  Future<void> loadFields() async {
    final key = v?.aiAssessedAt ?? '';
    if (fields != null && fieldsFor == key) return;
    try {
      final f = await api.getExtractedFields(doc.id);
      if (mounted) {
        setState(() {
          fields = f;
          fieldsFor = key;
        });
      }
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  @override
  void didUpdateWidget(covariant _DocumentRow old) {
    super.didUpdateWidget(old);
    // Al terminar una extracción se vuelven a pedir los datos leídos.
    if (showData && old.document.latestVersion?.aiAssessedAt != v?.aiAssessedAt) loadFields();
  }

  Future<void> accept() async {
    final issues = v?.blockingIssues ?? const [];
    final auth = context.read<AuthState>();
    final label0 = documentTypeLabel(doc.type);
    if (issues.isNotEmpty) {
      if (!auth.can('DOCUMENT_QUALITY_OVERRIDE')) {
        showToast(context, 'Este archivo no se puede leer: devuélvelo al cliente o pide a un director o administrador que autorice la excepción.');
        return;
      }
      final justification = await askReason(
        context,
        title: 'Aceptar por excepción',
        descriptionWidget: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('La revisión automática indica que este archivo no se puede leer:'),
          Bullets(issues),
          const SizedBox(height: 6),
          const Text('La aceptación quedará registrada con tu usuario y esta justificación.'),
        ]),
        label: '¿Por qué es válido pese a las alertas?',
        minLength: 15,
        confirmLabel: 'Aceptar por excepción',
      );
      if (justification != null) await act(() => api.acceptDocument(doc.id, justification), 'Documento aceptado por excepción (registrado en la bitácora).');
      return;
    }
    if (v?.aiTypeMatches == false) {
      final ok = await confirmDialog(
        context,
        title: '¿Aceptar como $label0?',
        message:
            'La revisión automática indica que el archivo parece ser ${v?.aiDetectedKind != null ? 'un(a) ${v!.aiDetectedKind}' : 'otro documento'}. Si revisaste el archivo y sí es $label0, acéptalo; si es otro documento, usa "Cambiar tipo de documento".',
        confirmLabel: 'Sí, aceptarlo',
      );
      if (ok) await act(() => api.acceptDocument(doc.id), 'Documento aceptado.');
      return;
    }
    await act(() => api.acceptDocument(doc.id), (v?.warnings.isNotEmpty ?? false) ? 'Documento aceptado (las advertencias quedaron registradas).' : 'Documento aceptado.');
  }

  Future<void> upload() async {
    final files = await pickUploadFiles(context);
    if (files == null) return;
    setState(() {
      uploading = true;
      progress = null;
    });
    try {
      await api.uploadVersion(doc.id, files, onProgress: (p) {
        if (mounted) setState(() => progress = p);
      });
      if (mounted) showToast(context, 'Archivo cargado. El procesamiento y la extracción siguen en segundo plano: puedes seguir trabajando.');
      await widget.onChanged();
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    } finally {
      if (mounted) {
        setState(() {
          uploading = false;
          progress = null;
        });
      }
    }
  }

  Future<void> download() async {
    final version = v;
    if (version == null) return;
    try {
      final url = await api.getDownloadUrl(version.id);
      if (mounted) await openUrl(context, url);
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  Future<void> review(String mode) async {
    var reasonCode = 'ILLEGIBLE_INFORMATION';
    var comment = '';
    var busy = false;
    final docLabel = documentTypeLabel(doc.type);
    await showAppSheet(
      context,
      title: mode == 'return' ? 'Devolver "$docLabel" al cliente' : 'Rechazar "$docLabel"',
      builder: (ctx, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        AppSelect<String>(label: 'Motivo', value: reasonCode, options: optionsOf(returnReasonLabels), onChanged: (v) => setSheet(() => reasonCode = v)),
        const SizedBox(height: 12),
        AppTextField(
          label: 'Indicación para el cliente ${reasonCode == 'OTHER' ? '(obligatoria)' : '(recomendada)'}',
          maxLines: 3,
          hint: 'Ej. La foto salió cortada; tómala de nuevo mostrando la hoja completa.',
          onChanged: (v) => setSheet(() => comment = v),
        ),
        const SizedBox(height: 8),
        const MutedText('El cliente verá este motivo en su liga y recibirá un aviso por correo si registró uno.', size: 12),
      ]),
      footer: (ctx, setSheet) => [
        Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.pop(ctx)),
        Btn(busy ? 'Guardando…' : (mode == 'return' ? 'Devolver' : 'Rechazar'),
            variant: mode == 'reject' ? BtnVariant.danger : BtnVariant.primary,
            loading: busy,
            onPressed: reasonCode == 'OTHER' && comment.trim().isEmpty
                ? null
                : () async {
                    setSheet(() => busy = true);
                    await act(
                      () => mode == 'return' ? api.returnDocument(doc.id, reasonCode, comment.trim()) : api.rejectDocument(doc.id, reasonCode, comment.trim()),
                      mode == 'return' ? 'Guardado: el documento se devolvió y el cliente verá el motivo en su liga.' : 'Documento rechazado; el cliente verá el motivo.',
                    );
                    if (ctx.mounted) Navigator.pop(ctx);
                  }),
      ],
    );
  }

  Future<void> move(List<DocumentResponse> targets) async {
    var target = targets.first.id;
    var busy = false;
    final docLabel = documentTypeLabel(doc.type);
    await showAppSheet(
      context,
      title: 'Cambiar tipo de documento',
      builder: (ctx, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        MutedText(
            'El archivo cargado como $docLabel pasa al documento que elijas (sin que el cliente lo vuelva a subir) y se lee otra vez con ese tipo. $docLabel vuelve a quedar pendiente.'),
        const SizedBox(height: 12),
        AppSelect<String>(
          label: '¿A qué documento corresponde?',
          value: target,
          options: [
            for (final d in targets)
              (
                value: d.id,
                label:
                    '${documentTypeLabel(d.type)}${d.participantId != null && widget.names[d.participantId] != null ? ' — ${widget.names[d.participantId]}' : ''}${d.currentVersionNumber > 0 ? ' (ya tiene archivo: versión nueva)' : ''}'
              ),
          ],
          onChanged: (v) => setSheet(() => target = v),
        ),
      ]),
      footer: (ctx, setSheet) => [
        Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.pop(ctx)),
        Btn(busy ? 'Moviendo…' : 'Mover archivo', loading: busy, onPressed: () async {
          setSheet(() => busy = true);
          final t = widget.allDocuments.where((d) => d.id == target).firstOrNull;
          await act(() => api.moveDocumentFile(doc.id, target), 'El archivo se movió a ${t != null ? documentTypeLabel(t.type) : 'otro documento'} y se está leyendo con su tipo correcto.');
          if (ctx.mounted) Navigator.pop(ctx);
        }),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final c = context.c;
    final version = v;
    final issues = version?.blockingIssues ?? const <String>[];
    final warnings = version?.warnings ?? const <String>[];
    final inProgress = doc.pipelineStatus != null && _inProgress.contains(doc.pipelineStatus);
    final reviewable = ['READY_FOR_REVIEW', 'UPLOADED', 'RETURNED'].contains(doc.status) && version != null && !['QUEUED', 'PROCESSING'].contains(version.processingStatus);
    final mismatch = version?.aiTypeMatches == false;
    final docLabel = documentTypeLabel(doc.type);
    final moveTargets = widget.allDocuments.where((d) => d.id != doc.id && d.status != 'ACCEPTED').toList();
    final canUpload = auth.can('DOCUMENT_UPLOAD');

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(12), border: Border.all(color: c.border)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text.rich(TextSpan(children: [
          TextSpan(text: docLabel, style: TextStyle(fontWeight: FontWeight.w500, color: c.fg)),
          if (widget.participantName != null) TextSpan(text: ' — ${widget.participantName}', style: TextStyle(color: c.muted)),
        ]), style: const TextStyle(fontSize: 14)),
        const SizedBox(height: 2),
        MutedText(
          '${doc.required ? (doc.deferred && doc.status == 'PENDING' ? 'Obligatorio · el cliente lo sube después${doc.deferralReason != null ? ' (razón: ${doc.deferralReason})' : ''}' : 'Obligatorio') : 'Opcional / no aplica en este caso'}'
          '${version != null ? ' · Versión ${version.versionNumber}, cargada ${formatDateTime(version.uploadedAt)} por ${version.uploadedVia == 'PUBLIC_PORTAL' ? 'el cliente' : (version.uploadedByName ?? 'el staff')}' : ''}',
          size: 12,
        ),
        const SizedBox(height: 8),
        Wrap(spacing: 6, runSpacing: 4, children: [
          if (doc.pipelineStatus != null && doc.pipelineStatus != 'ACCEPTED')
            AppBadge(label(pipelineStatusLabels, doc.pipelineStatus), tone: pipelineStatusTone[doc.pipelineStatus] ?? Tone.neutral, icon: inProgress ? Icons.hourglass_top : null),
          if (doc.required && doc.deferred && doc.status == 'PENDING') const AppBadge('Para después'),
          AppBadge(label(documentStatusLabels, doc.status), tone: documentStatusTone[doc.status] ?? Tone.neutral),
        ]),
        if (issues.isNotEmpty && doc.status != 'ACCEPTED') ...[
          const SizedBox(height: 10),
          Notice(tone: Tone.danger, icon: Icons.warning_amber_rounded, title: 'No se puede leer: solo se acepta con autorización de excepción', child: Bullets(issues, color: c.dangerText)),
        ],
        if (mismatch && doc.status != 'ACCEPTED') ...[
          const SizedBox(height: 10),
          Notice(
            tone: Tone.warning,
            icon: Icons.info_outline,
            text:
                'El archivo parece ser ${version?.aiDetectedKind != null ? 'un(a) ${version!.aiDetectedKind}' : 'otro documento'} aunque fue cargado como $docLabel. Los datos que se leyeron se conservaron.',
            action: canUpload && moveTargets.isNotEmpty ? Btn('Cambiar tipo de documento', icon: Icons.swap_horiz, small: true, variant: BtnVariant.secondary, onPressed: () => move(moveTargets)) : null,
          ),
        ],
        if (warnings.isNotEmpty && doc.status != 'ACCEPTED') ...[
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: c.appBg, borderRadius: BorderRadius.circular(10)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('Advertencias (no impiden aceptarlo):', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w500, color: c.fg)),
              Bullets(warnings, size: 12),
            ]),
          ),
        ],
        if (inProgress) const Padding(padding: EdgeInsets.only(top: 8), child: MutedText('La extracción automática continúa. Puedes seguir trabajando; este documento se actualiza solo.', size: 12)),
        if (doc.status == 'RETURNED' || doc.status == 'REJECTED') ...[
          const SizedBox(height: 10),
          Notice(
            tone: Tone.warning,
            child: Text.rich(TextSpan(children: [
              TextSpan(text: '${doc.status == 'RETURNED' ? 'Devuelto al cliente' : 'Rechazado'} el ${formatDateTime(doc.lastReviewedAt)}. Motivo que ve el cliente: '),
              TextSpan(text: label(returnReasonLabels, doc.lastReviewReasonCode), style: const TextStyle(fontWeight: FontWeight.w600)),
              if (doc.lastReviewComment != null) TextSpan(text: ' — "${doc.lastReviewComment}"'),
            ])),
          ),
        ],
        if (doc.status == 'NOT_APPLICABLE') ...[
          const SizedBox(height: 10),
          Notice(tone: Tone.neutral, text: 'Marcado como "No aplica" el ${formatDateTime(doc.notApplicableAt)}: ${doc.notApplicableJustification ?? ''}'),
        ],
        const SizedBox(height: 10),
        Wrap(spacing: 6, runSpacing: 6, children: [
          if (reviewable && auth.can('DOCUMENT_ACCEPT')) Btn(issues.isNotEmpty ? 'Aceptar por excepción…' : 'Aceptar', icon: Icons.check, small: true, onPressed: accept),
          if (version != null)
            Btn(showData ? 'Ocultar datos leídos' : 'Datos leídos', icon: Icons.smart_toy_outlined, small: true, variant: BtnVariant.secondary, onPressed: () {
              setState(() => showData = !showData);
              if (showData) loadFields();
            }),
          if (version != null) Btn('Ver archivo', icon: Icons.download_outlined, small: true, variant: BtnVariant.secondary, onPressed: download),
          if (version != null && widget.canReprocess)
            Btn('Reprocesar con IA', icon: Icons.refresh, small: true, variant: BtnVariant.ghost, loading: reprocessing, onPressed: inProgress
                ? null
                : () async {
                    setState(() => reprocessing = true);
                    await act(() => api.reprocessDocument(doc.id), 'Se está volviendo a procesar con IA. Los datos se actualizan solos al terminar; puedes seguir trabajando.');
                    if (mounted) setState(() => reprocessing = false);
                  }),
          if (version != null && canUpload && doc.status != 'ACCEPTED' && moveTargets.isNotEmpty && !mismatch)
            Btn('Cambiar tipo', icon: Icons.swap_horiz, small: true, variant: BtnVariant.ghost, onPressed: () => move(moveTargets)),
          if (reviewable && auth.can('DOCUMENT_RETURN')) Btn('Devolver al cliente', small: true, variant: BtnVariant.secondary, onPressed: () => review('return')),
          if (reviewable && auth.can('DOCUMENT_REJECT')) Btn('Rechazar', small: true, variant: BtnVariant.danger, onPressed: () => review('reject')),
          if (canUpload && doc.status != 'NOT_APPLICABLE' && doc.status != 'ACCEPTED')
            Btn(
              uploading ? (progress != null && progress! < 100 ? 'Subiendo $progress%' : 'Preparando…') : (doc.currentVersionNumber > 0 ? 'Cargar nueva versión' : 'Cargar en nombre del cliente'),
              icon: Icons.upload_outlined,
              small: true,
              variant: BtnVariant.ghost,
              loading: uploading,
              onPressed: upload,
            ),
          // La identificación no se omite: si no hay INE se carga otra identificación oficial y se acepta con justificación.
          if (auth.can('DOCUMENT_MARK_NOT_APPLICABLE') && doc.type != 'INE' && doc.status != 'ACCEPTED' && doc.status != 'NOT_APPLICABLE')
            Btn('No aplica…', small: true, variant: BtnVariant.ghost, onPressed: () async {
              final j = await askReason(
                context,
                title: '"$docLabel" no aplica',
                description: 'Deja de pedirse al cliente y cuenta como resuelto. Explica por qué no aplica a este expediente.',
                label: 'Justificación',
                placeholder: 'Ej. El terreno no tiene contrato de agua todavía.',
                minLength: 15,
                confirmLabel: 'Marcar como No aplica',
              );
              if (j != null) await act(() => api.markNotApplicable(doc.id, j), 'Marcado como No aplica.');
            }),
          if (auth.can('DOCUMENT_MARK_NOT_APPLICABLE') && doc.status == 'NOT_APPLICABLE')
            Btn('Volver a solicitar', small: true, variant: BtnVariant.ghost, onPressed: () => act(() => api.requestAgain(doc.id), 'El documento se volvió a solicitar.')),
          if (version != null)
            Btn('Historial', icon: Icons.history, small: true, variant: BtnVariant.ghost, onPressed: () async {
              if (history != null) return setState(() => history = null);
              try {
                final h = await api.listReviews(doc.id);
                if (mounted) setState(() => history = h);
              } catch (err) {
                if (context.mounted) showToast(context, errorText(err));
              }
            }),
        ]),
        if (uploading && progress != null) Padding(padding: const EdgeInsets.only(top: 8), child: ProgressLine(progress! / 100)),
        if (showData && version != null) _ExtractedDataPanel(document: doc, fields: fields, onEdited: (f) => setState(() => fields = fields?.map((x) => x.id == f.id ? f : x).toList())),
        if (history != null)
          Container(
            margin: const EdgeInsets.only(top: 10),
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: c.appBg, borderRadius: BorderRadius.circular(10)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              if (history!.isEmpty) const MutedText('Sin decisiones todavía.', size: 12),
              for (final h in history!)
                Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: MutedText(
                    '${formatDateTime(h.reviewedAt)} — ${h.decision == 'ACCEPTED' ? (h.overrideJustification != null ? 'Aceptado POR EXCEPCIÓN' : 'Aceptado') : (h.decision == 'RETURNED' ? 'Devuelto' : 'Rechazado')}'
                    '${h.reasonCode != null ? ': ${label(returnReasonLabels, h.reasonCode)}' : ''}${h.comment != null ? ' — "${h.comment}"' : ''}'
                    '${h.overrideJustification != null ? ' — Justificación: "${h.overrideJustification}" (alertas: ${h.overriddenIssues})' : ''}',
                    size: 12,
                  ),
                ),
            ]),
          ),
      ]),
    );
  }
}

({String text, Tone tone}) _confidenceBadge(double? confidence) {
  if (confidence == null) return (text: 'sin dato de confianza', tone: Tone.neutral);
  final pct = (confidence * 100).round();
  if (confidence >= 0.8) return (text: 'confianza alta ($pct%)', tone: Tone.success);
  if (confidence >= 0.5) return (text: 'confianza media ($pct%)', tone: Tone.info);
  return (text: 'confianza baja ($pct%): revisar', tone: Tone.warning);
}

/// "Datos leídos del documento": qué detectó la IA, si se lee, cada dato con su confianza y página.
class _ExtractedDataPanel extends StatelessWidget {
  const _ExtractedDataPanel({required this.document, required this.fields, required this.onEdited});
  final DocumentResponse document;
  final List<ExtractedField>? fields;
  final ValueChanged<ExtractedField> onEdited;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final v = document.latestVersion!;
    final inProgress = document.pipelineStatus != null && _inProgress.contains(document.pipelineStatus);
    final legibility = v.qualityLevel == 'UNREADABLE'
        ? 'No legible'
        : v.aiLegible == false
            ? ((v.aiFieldsFound ?? 0) > 0 ? 'Difícil de leer: se extrajeron ${v.aiFieldsFound} datos, revísalos contra el archivo' : 'No legible')
            : v.aiLegible == true
                ? (v.qualityLevel == 'ACCEPTED_WITH_WARNINGS' ? 'Legible (con advertencias de calidad)' : 'Legible')
                : v.aiCheckFailed
                    ? 'Sin revisión automática: verifícalo visualmente'
                    : (inProgress ? 'En revisión…' : 'Sin determinar');
    final f = fields;
    return Container(
      margin: const EdgeInsets.only(top: 10),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(color: c.appBg, borderRadius: BorderRadius.circular(10)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        FieldView('Documento detectado', v.aiDetectedKind ?? (inProgress ? 'En revisión…' : '—')),
        const SizedBox(height: 8),
        FieldView('Legibilidad', legibility),
        const SizedBox(height: 8),
        FieldView('Cobertura',
            '${v.aiFieldsExpected != null && v.aiFieldsFound != null ? '${v.aiFieldsFound} de ${v.aiFieldsExpected} datos' : '—'}${v.aiPagesTotal != null ? ' · ${v.aiPagesAnalyzed ?? '?'} de ${v.aiPagesTotal} páginas revisadas' : ''}'),
        if (v.aiObservations != null) ...[const SizedBox(height: 8), MutedText('Observaciones: ${v.aiObservations}', size: 12)],
        const SizedBox(height: 10),
        if (f == null)
          const MutedText('Cargando datos leídos…', size: 12)
        else if (f.isEmpty)
          MutedText(inProgress ? 'La extracción automática continúa; los datos aparecerán aquí al terminar.' : 'No se leyeron datos de este archivo. Puedes reprocesarlo con IA o capturarlos a mano.',
              size: 12)
        else ...[
          for (final x in f.where((x) => !x.extra)) _FieldRow(field: x, onEdited: onEdited),
          if (f.any((x) => x.extra)) ...[
            const SizedBox(height: 8),
            Text('Otros datos encontrados', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: c.fg)),
            for (final x in f.where((x) => x.extra)) _FieldRow(field: x, onEdited: onEdited),
          ],
        ],
      ]),
    );
  }
}

class _FieldRow extends StatefulWidget {
  const _FieldRow({required this.field, required this.onEdited});
  final ExtractedField field;
  final ValueChanged<ExtractedField> onEdited;

  @override
  State<_FieldRow> createState() => _FieldRowState();
}

class _FieldRowState extends State<_FieldRow> {
  bool editing = false;
  bool saving = false;
  late String value = widget.field.confirmedValue ?? widget.field.detectedValue ?? '';

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final c = context.c;
    final f = widget.field;
    final confirmed = f.confirmedValue != null;
    final badge = _confidenceBadge(confirmed ? 1 : f.confidence);
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8),
      decoration: BoxDecoration(border: Border(top: BorderSide(color: c.border))),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        MutedText('${extractedFieldLabel(f.fieldName)}${f.sourcePage != null ? ' · página ${f.sourcePage}' : ''}', size: 12),
        const SizedBox(height: 2),
        if (editing)
          Row(children: [
            Expanded(child: AppTextField(value: value, autofocus: true, onChanged: (v) => value = v)),
            IconButton(
              tooltip: 'Guardar dato',
              icon: saving ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : Icon(Icons.check, color: c.successText),
              onPressed: saving
                  ? null
                  : () async {
                      if (value.trim().isEmpty) return;
                      setState(() => saving = true);
                      try {
                        widget.onEdited(await api.confirmField(f.id, value.trim()));
                        if (mounted) {
                          setState(() => editing = false);
                          showToast(this.context, 'Dato confirmado.');
                        }
                      } catch (err) {
                        if (context.mounted) showToast(context, errorText(err));
                      } finally {
                        if (mounted) setState(() => saving = false);
                      }
                    },
            ),
            IconButton(tooltip: 'Cancelar', icon: Icon(Icons.close, color: c.muted), onPressed: () => setState(() => editing = false)),
          ])
        else
          SelectableText(f.confirmedValue ?? f.detectedValue ?? '—', style: TextStyle(fontSize: 14, color: c.fg)),
        const SizedBox(height: 4),
        Row(children: [
          AppBadge(confirmed ? 'confirmado por el staff' : badge.text, tone: confirmed ? Tone.success : badge.tone),
          const Spacer(),
          if (auth.can('EXTRACTED_DATA_EDIT') && !editing)
            IconButton(tooltip: 'Editar dato', visualDensity: VisualDensity.compact, icon: Icon(Icons.edit_outlined, size: 16, color: c.muted), onPressed: () => setState(() => editing = true)),
        ]),
      ]),
    );
  }
}
