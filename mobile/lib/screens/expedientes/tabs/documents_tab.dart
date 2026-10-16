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
import '../document_viewer_screen.dart';
import '../expediente_detail_screen.dart';
import 'notary_stages.dart';

const _inProgress = ['UPLOADED', 'PROCESSING'];

/// Nombre con el que se muestra: el capturado si se agregó a mano (Control de Cambios #1), si no el del tipo.
String _documentName(DocumentResponse d) => d.customName ?? documentTypeLabel(d.type);

final _statusFilters = <String, ({String label, bool Function(DocumentResponse) matches})>{
  'ALL': (label: 'Todos', matches: (_) => true),
  'MISSING': (label: 'Faltantes', matches: (d) => d.required && d.status == 'PENDING'),
  // Control de Cambios #14: los que el asesor marcó "Pendiente" (se entregan después).
  'MARKED_PENDING': (label: 'Pendientes de entrega', matches: (d) => d.markedPending),
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
    final markedPendingCount = (docs ?? []).where((d) => d.markedPending).length;
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
              title: 'Etapa 1 · Documentos para comercialización',
              description: docs == null
                  ? 'Cargando requisitos…'
                  : pendingCount > 0
                      ? 'Faltan $pendingCount documento(s) obligatorio(s) por aceptar o marcar como "No aplica".'
                      : requiredCount > 0
                          ? 'Todos los documentos obligatorios están resueltos.'
                          : 'Este expediente todavía no tiene documentos obligatorios registrados.',
            ),
            const MutedText('Lo necesario para publicar y promover el inmueble; no tienen que estar al corriente.', size: 12),
            const SizedBox(height: 8),
            if (markedPendingCount > 0) ...[
              Notice(
                tone: Tone.info,
                icon: Icons.schedule,
                text: '${markedPendingCount == 1 ? '1 documento está pendiente' : '$markedPendingCount documentos están pendientes'} de entrega (marcados por el asesor; no bloquean el avance).',
              ),
              const SizedBox(height: 8),
            ],
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
            if (auth.can('EXPEDIENT_EDIT') || auth.can('DOCUMENT_UPLOAD'))
              Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: Btn('Agregar otro documento', icon: Icons.add, small: true, variant: BtnVariant.secondary, onPressed: () => _addDocument(context)),
                ),
              ),
            LinkText(showOptional ? 'Ocultar documentos que no aplican' : 'Mostrar también documentos que no aplican a este expediente',
                onTap: () => setState(() => showOptional = !showOptional)),
          ]),
        ),
        // Control de Cambios #23: etapas 2 (envío inicial a notaría) y 3 (cierre).
        NotaryStages(key: ValueKey((docs ?? []).map((d) => '${d.id}:${d.currentVersionNumber}:${d.status}').join('|')), expedienteId: ctx.expediente.id, names: names),
      ],
    );
  }

  /// Control de Cambios #1: documento que no está en el listado, con el nombre que se le dé.
  Future<void> _addDocument(BuildContext context) async {
    var name = '';
    String owner = '';
    var busy = false;
    final ordered = [...ctx.participants]..sort((a, b) => a.ordinal.compareTo(b.ordinal));
    await showAppSheet(
      context,
      title: 'Agregar otro documento',
      builder: (ctx0, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const MutedText('Para un documento que no está en la lista. Se guarda en el expediente igual que los demás y se le pide al cliente en su liga.', size: 12),
        const SizedBox(height: 12),
        AppTextField(label: 'Nombre del documento', hint: 'Ej. Constancia de no adeudo de mantenimiento', onChanged: (v) => setSheet(() => name = v)),
        const SizedBox(height: 12),
        AppSelect<String>(
          label: '¿De quién es?',
          value: owner,
          options: [(value: '', label: 'Del inmueble / del expediente'), for (final p in ordered) (value: p.id!, label: p.fullName)],
          onChanged: (v) => setSheet(() => owner = v),
        ),
      ]),
      footer: (ctx0, setSheet) => [
        Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.pop(ctx0)),
        Btn(busy ? 'Agregando…' : 'Agregar', loading: busy, onPressed: name.trim().length < 3
            ? null
            : () async {
                setSheet(() => busy = true);
                try {
                  await api.addCustomDocument(ctx.expediente.id, name.trim(), owner.isEmpty ? null : owner);
                  if (ctx0.mounted) Navigator.pop(ctx0);
                  if (context.mounted) showToast(context, 'Se agregó "${name.trim()}" a los documentos del expediente.');
                  await onChanged();
                } catch (err) {
                  setSheet(() => busy = false);
                  if (context.mounted) showToast(context, errorText(err));
                }
              }),
      ],
    );
  }
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
  List<DocumentVersionItem>? versions;
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
    final label0 = _documentName(doc);
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

  /// "Ver archivo" abre el visor protegido (Control de Cambios #6), nunca descarga el archivo.
  void view(String versionId, int versionNumber) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => DocumentViewerScreen(versionId: versionId, title: '${_documentName(doc)} · versión $versionNumber'),
    ));
  }

  /// Control de Cambios #18: corrige el nombre de un documento agregado a mano (el archivo no cambia).
  Future<void> rename() async {
    var name = doc.customName ?? '';
    var busy = false;
    await showAppSheet(
      context,
      title: 'Editar nombre',
      builder: (ctx, setSheet) => AppTextField(label: 'Nombre del documento', value: name, autofocus: true, maxLength: 120, onChanged: (v) => setSheet(() => name = v)),
      footer: (ctx, setSheet) => [
        Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.pop(ctx)),
        Btn('Guardar', loading: busy, onPressed: name.trim().length < 3
            ? null
            : () async {
                setSheet(() => busy = true);
                await act(() => api.renameDocument(doc.id, name.trim()), 'Nombre actualizado.');
                if (ctx.mounted) Navigator.pop(ctx);
              }),
      ],
    );
  }

  /// Control de Cambios #14: "Marcar como pendiente" con nota y fecha estimada opcionales.
  Future<void> markPending() async {
    var note = '';
    String? due;
    var busy = false;
    await showAppSheet(
      context,
      title: 'Marcar como pendiente: ${_documentName(doc)}',
      builder: (ctx, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const MutedText('Todavía no está listo pero se entregará después. No bloquea el avance del expediente y, al subirse, pasa a entregado.', size: 12),
        const SizedBox(height: 12),
        AppTextField(label: 'Nota (opcional)', maxLines: 2, hint: 'Ej. El cliente lo tramita en el municipio.', onChanged: (v) => note = v),
        const SizedBox(height: 12),
        DateField(label: 'Fecha estimada de entrega (opcional)', value: due, first: DateTime.now().toIso8601String(), onChanged: (v) => setSheet(() => due = v)),
      ]),
      footer: (ctx, setSheet) => [
        Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.pop(ctx)),
        Btn('Marcar pendiente', icon: Icons.schedule, loading: busy, onPressed: () async {
          setSheet(() => busy = true);
          await act(() => api.markDocumentPending(doc.id, note.trim(), due), 'Marcado como pendiente de entrega (no bloquea el expediente).');
          if (ctx.mounted) Navigator.pop(ctx);
        }),
      ],
    );
  }

  Future<void> toggleVersions() async {
    if (versions != null) return setState(() => versions = null);
    try {
      final list = await api.listVersions(doc.id);
      list.sort((a, b) => b.versionNumber.compareTo(a.versionNumber));
      if (mounted) setState(() => versions = list);
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  Future<void> review(String mode) async {
    var reasonCode = 'ILLEGIBLE_INFORMATION';
    var comment = '';
    var busy = false;
    final docLabel = _documentName(doc);
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
    final docLabel = _documentName(doc);
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
                    '${_documentName(d)}${d.participantId != null && widget.names[d.participantId] != null ? ' — ${widget.names[d.participantId]}' : ''}${d.currentVersionNumber > 0 ? ' (ya tiene archivo: versión nueva)' : ''}'
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
          await act(() => api.moveDocumentFile(doc.id, target), 'El archivo se movió a ${t != null ? _documentName(t) : 'otro documento'} y se está leyendo con su tipo correcto.');
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
    final docLabel = _documentName(doc);
    final resolved = doc.status == 'ACCEPTED' || doc.status == 'NOT_APPLICABLE';
    final moveTargets = widget.allDocuments.where((d) => d.id != doc.id && d.status != 'ACCEPTED').toList();
    final canUpload = auth.can('DOCUMENT_UPLOAD');

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(12), border: Border.all(color: c.border)),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Expanded(
            child: Text.rich(TextSpan(children: [
              TextSpan(text: docLabel, style: TextStyle(fontWeight: FontWeight.w500, color: c.fg)),
              if (widget.participantName != null) TextSpan(text: ' — ${widget.participantName}', style: TextStyle(color: c.muted)),
            ]), style: const TextStyle(fontSize: 14)),
          ),
          if (doc.customName != null && canUpload)
            IconButton(tooltip: 'Editar nombre', visualDensity: VisualDensity.compact, icon: Icon(Icons.edit_outlined, size: 18, color: c.goldText), onPressed: rename),
        ]),
        if (doc.type == 'PROOF_OF_ADDRESS') ...[
          const SizedBox(height: 4),
          const MutedText('Se acepta: recibo de luz, estado de cuenta bancario, recibo de Telmex, de telefonía o de internet.', size: 12),
          const SizedBox(height: 6),
          AppSelect<String>(
            label: 'Tipo de comprobante entregado',
            value: doc.addressProofKind ?? '',
            options: [(value: '', label: 'Sin indicar'), for (final e in addressProofKindLabels.entries) (value: e.key, label: e.value)],
            enabled: canUpload || auth.can('DOCUMENT_ACCEPT'),
            onChanged: (v) => act(() => api.setAddressProofKind(doc.id, v.isEmpty ? null : v), 'Tipo de comprobante guardado.'),
          ),
        ],
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
          if (doc.required && doc.deferred && doc.status == 'PENDING' && doc.exceptionAt == null && !doc.markedPending) const AppBadge('Para después'),
          if (doc.markedPending) const AppBadge('Pendiente de entrega', tone: Tone.info),
          if (doc.exceptionAt != null && doc.status != 'ACCEPTED') const AppBadge('Excepción aprobada', tone: Tone.info),
          if (doc.updateRequestedAt != null) const AppBadge('Requiere actualización', tone: Tone.warning),
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
        if (doc.exceptionAt != null && doc.status != 'ACCEPTED') ...[
          const SizedBox(height: 10),
          Notice(
            tone: Tone.neutral,
            text:
                'Excepción aprobada el ${formatDateTime(doc.exceptionAt)}: ${doc.exceptionNote ?? ''}. No bloquea la aprobación ni el contrato; el cliente puede entregarlo después desde su liga.',
          ),
        ],
        if (doc.markedPending) ...[
          const SizedBox(height: 10),
          Notice(
            tone: Tone.info,
            text: 'Pendiente de entrega desde el ${formatDateTime(doc.pendingAt)}'
                '${doc.pendingDueDate != null ? ' · entrega estimada: ${formatDate(doc.pendingDueDate)}' : ''}'
                '${doc.pendingNote != null ? ' · ${doc.pendingNote}' : ''}. No bloquea el avance; al subirse el archivo queda como entregado.',
          ),
        ],
        if (doc.updateRequestedAt != null) ...[
          const SizedBox(height: 10),
          Notice(
            tone: Tone.warning,
            text: 'Se pidió una versión actualizada el ${formatDateTime(doc.updateRequestedAt)}: ${doc.updateRequestNote ?? ''}. La versión actual se conserva en el historial.',
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
          if (version != null) Btn('Ver archivo', icon: Icons.visibility_outlined, small: true, variant: BtnVariant.secondary, onPressed: () => view(version.id, version.versionNumber)),
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
          if ((canUpload || auth.can('DOCUMENT_ACCEPT')) && !doc.markedPending && !resolved)
            Btn('Marcar como pendiente…', icon: Icons.schedule, small: true, variant: BtnVariant.ghost, onPressed: markPending),
          if ((canUpload || auth.can('DOCUMENT_ACCEPT')) && doc.markedPending)
            Btn('Quitar pendiente', small: true, variant: BtnVariant.ghost, onPressed: () => act(() => api.clearDocumentPending(doc.id), 'Se quitó la marca de pendiente.')),
          if (auth.can('DOCUMENT_EXCEPTION') && doc.required && doc.exceptionAt == null && !resolved)
            Btn('Aprobar excepción…', small: true, variant: BtnVariant.ghost, onPressed: () async {
              final note = await askReason(
                context,
                title: 'Aprobar excepción: $docLabel',
                description: 'El documento sigue faltando, pero no bloqueará la aprobación de la documentación ni el contrato. El cliente podrá entregarlo después desde su liga.',
                label: 'Nota justificatoria',
                placeholder: 'Ej. La boleta está en trámite en el Registro Público.',
                minLength: 10,
                confirmLabel: 'Aprobar excepción',
              );
              if (note != null) await act(() => api.approveException(doc.id, note), 'Excepción aprobada (queda en el historial).');
            }),
          if (auth.can('DOCUMENT_EXCEPTION') && doc.exceptionAt != null && doc.status != 'ACCEPTED')
            Btn('Retirar excepción', small: true, variant: BtnVariant.ghost, onPressed: () => act(() => api.revokeException(doc.id), 'Se retiró la excepción: el documento vuelve a ser necesario.')),
          if (auth.can('DOCUMENT_UPDATE_REQUEST') && doc.currentVersionNumber > 0 && doc.updateRequestedAt == null && doc.status != 'NOT_APPLICABLE')
            Btn('Requiere actualización…', small: true, variant: BtnVariant.ghost, onPressed: () async {
              final note = await askReason(
                context,
                title: 'Pedir versión actualizada: $docLabel',
                description: 'El cliente verá esta nota en su liga y podrá subir la nueva versión. La versión actual se conserva como historial.',
                label: '¿Qué debe actualizar?',
                placeholder: 'Ej. La constancia tiene más de 3 meses; súbela con fecha reciente.',
                minLength: 10,
                confirmLabel: 'Pedir actualización',
              );
              if (note != null) await act(() => api.requestDocumentUpdate(doc.id, note), 'Se pidió la versión actualizada al cliente.');
            }),
          if (auth.can('DOCUMENT_UPDATE_REQUEST') && doc.updateRequestedAt != null)
            Btn('Cancelar actualización', small: true, variant: BtnVariant.ghost, onPressed: () => act(() => api.cancelDocumentUpdate(doc.id), 'Se canceló la solicitud de actualización.')),
          if (doc.currentVersionNumber > 0) Btn('Versiones (${doc.currentVersionNumber})', icon: Icons.layers_outlined, small: true, variant: BtnVariant.ghost, onPressed: toggleVersions),
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
        if (versions != null)
          Container(
            margin: const EdgeInsets.only(top: 10),
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: c.appBg, borderRadius: BorderRadius.circular(10)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              for (final ver in versions!)
                Row(children: [
                  Expanded(
                    child: MutedText(
                      'V${ver.versionNumber}${ver.versionNumber == doc.currentVersionNumber ? ' (activa)' : ''} · cargada ${formatDateTime(ver.uploadedAt)} por ${ver.uploadedVia == 'PUBLIC_PORTAL' ? 'el cliente' : (ver.uploadedByName ?? 'el staff')}',
                      size: 12,
                    ),
                  ),
                  TextButton(onPressed: () => view(ver.id, ver.versionNumber), child: Text('Ver', style: TextStyle(color: c.goldText))),
                ]),
              const MutedText('Ninguna versión se elimina: las anteriores quedan para trazabilidad y auditoría.', size: 11),
            ]),
          ),
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
    final choosing = f.alternatives.length > 1 && !confirmed;
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
        // Control de Cambios #19: la escritura trae varios valores (antecedentes); una persona elige el del instrumento actual.
        if (choosing)
          Notice(
            tone: Tone.warning,
            icon: Icons.warning_amber_rounded,
            title: 'Se encontraron varios valores. ¿Cuál corresponde a este instrumento?',
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              for (final a in f.alternatives)
                Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Row(children: [
                    Expanded(
                      child: Text.rich(TextSpan(children: [
                        TextSpan(text: a.value, style: const TextStyle(fontWeight: FontWeight.w600)),
                        if (a.context != null) TextSpan(text: ' — ${a.context}', style: TextStyle(fontSize: 12, color: c.muted)),
                        if (a.page != null) TextSpan(text: ' · pág. ${a.page}', style: TextStyle(fontSize: 12, color: c.muted)),
                      ])),
                    ),
                    if (auth.can('EXTRACTED_DATA_EDIT'))
                      Btn('Es este', small: true, variant: BtnVariant.secondary, loading: saving, onPressed: () async {
                        setState(() => saving = true);
                        try {
                          widget.onEdited(await api.confirmField(f.id, a.value));
                          if (mounted) showToast(this.context, 'Valor elegido: queda registrado con tu nombre.');
                        } catch (err) {
                          if (context.mounted) showToast(context, errorText(err));
                        } finally {
                          if (mounted) setState(() => saving = false);
                        }
                      }),
                  ]),
                ),
            ]),
          ),
        if (confirmed && f.confirmedByName != null)
          Padding(
            padding: const EdgeInsets.only(top: 2),
            child: Text('Confirmado por ${f.confirmedByName}${f.confirmedAt != null ? ' · ${formatDateTime(f.confirmedAt)}' : ''}', style: TextStyle(fontSize: 12, color: c.successText)),
          ),
        const SizedBox(height: 4),
        Row(children: [
          AppBadge(confirmed ? 'confirmado por el staff' : (choosing ? 'elegir valor' : badge.text), tone: confirmed ? Tone.success : (choosing ? Tone.warning : badge.tone)),
          const Spacer(),
          if (auth.can('EXTRACTED_DATA_EDIT') && !editing)
            IconButton(tooltip: 'Editar dato', visualDensity: VisualDensity.compact, icon: Icon(Icons.edit_outlined, size: 16, color: c.muted), onPressed: () => setState(() => editing = true)),
        ]),
      ]),
    );
  }
}
