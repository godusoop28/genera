import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../api/client.dart';
import '../../api/operations_api.dart' as ops;
import '../../labels.dart';
import '../../money.dart';
import '../../state/auth.dart';
import '../../ui/file_pick.dart';
import '../../ui/money_field.dart';
import '../../ui/shell.dart';
import '../../ui/widgets.dart';
import 'operations_screen.dart';

const _reqTone = {
  'PENDING': Tone.neutral,
  'RECEIVED': Tone.info,
  'IN_REVIEW': Tone.info,
  'VALIDATED': Tone.success,
  'NEEDS_CORRECTION': Tone.danger,
  'NOT_APPLICABLE': Tone.warning,
};

/// CC#32: ficha de la operación: estado comercial y documental, partes, faltantes y requisitos de
/// Operación / PLD / PROFECO con su evidencia, revisión y "No aplica".
class OperationDetailScreen extends StatefulWidget {
  const OperationDetailScreen({super.key, required this.id});
  final String id;

  @override
  State<OperationDetailScreen> createState() => _OperationDetailScreenState();
}

class _OperationDetailScreenState extends State<OperationDetailScreen> {
  ops.OperationDetail? d;
  String? error;
  String section = 'OPERATION';
  bool busy = false;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final r = await ops.getOperation(widget.id);
      if (mounted) setState(() => (d = r, error = null));
    } catch (e) {
      if (mounted) setState(() => error = errorText(e));
    }
  }

  Future<void> run(Future<ops.OperationDetail> Function() action, String ok) async {
    setState(() => busy = true);
    try {
      final r = await action();
      if (mounted) {
        setState(() => d = r);
        showToast(context, ok);
      }
    } catch (e) {
      if (mounted) showToast(context, errorText(e));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> _close(ops.OperationDetail detail) async {
    final op = detail.operation;
    var amount = op.amount == null ? '' : op.amount!.toStringAsFixed(op.amount! % 1 == 0 ? 0 : 2);
    var currency = op.currency ?? 'MXN';
    String? due;
    var owner = op.advisorName ?? '';
    final pending = op.docStatus != 'COMPLETE_VALIDATED';
    final ok = await showAppSheet<bool>(
      context,
      title: 'Registrar cierre',
      builder: (ctx, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const MutedText('El cierre comercial es independiente de la revisión documental. La fecha es hoy.'),
        const SizedBox(height: 12),
        MoneyField(label: 'Monto de la operación', value: amount, currency: currency, onChanged: (v) => setSheet(() => amount = v)),
        const SizedBox(height: 12),
        AppSelect<String>(label: 'Moneda', value: currency, options: const [(value: 'MXN', label: 'MXN'), (value: 'USD', label: 'USD')], onChanged: (v) => setSheet(() => currency = v)),
        if (pending) ...[
          const SizedBox(height: 12),
          const Notice(tone: Tone.warning, text: 'La documentación no está completa y validada: quedará como "Cerrada — Documentación pendiente". Indica responsable y fecha compromiso.'),
          const SizedBox(height: 12),
          DateField(label: 'Fecha compromiso', value: due, onChanged: (v) => setSheet(() => due = v)),
          const SizedBox(height: 12),
          AppTextField(label: 'Responsable', value: owner, onChanged: (v) => owner = v),
        ],
      ]),
      footer: (ctx, _) => [Btn('Registrar cierre', expand: true, onPressed: () => Navigator.pop(ctx, true))],
    );
    if (ok != true) return;
    await run(
      () => ops.closeOperation(op.id, {
        'amount': amount.isEmpty ? null : double.tryParse(amount),
        'currency': currency,
        if (pending) 'docDueDate': due,
        if (pending) 'docFollowUpOwner': owner.trim().isEmpty ? null : owner.trim(),
      }),
      'Cierre registrado.',
    );
  }

  Future<void> _evidence(ops.OperationDetail detail, ops.RequirementView r) async {
    final choice = await showModalBottomSheet<String>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          if (detail.expedienteDocuments.isNotEmpty) ListTile(leading: const Icon(Icons.link), title: const Text('Vincular documento del expediente'), onTap: () => Navigator.pop(ctx, 'doc')),
          ListTile(leading: const Icon(Icons.attach_file), title: const Text('Cargar archivo o foto'), onTap: () => Navigator.pop(ctx, 'file')),
          ListTile(leading: const Icon(Icons.notes), title: const Text('Dato o referencia'), onTap: () => Navigator.pop(ctx, 'note')),
        ]),
      ),
    );
    if (choice == null || !mounted) return;
    String? date;
    var text = '';
    var docId = detail.expedienteDocuments.isEmpty ? '' : '${detail.expedienteDocuments.first['documentId']}';
    List<UploadFile>? files;
    if (choice == 'file') {
      files = await pickUploadFiles(context, multiple: false);
      if (files == null || !mounted) return;
    }
    final ok = await showAppSheet<bool>(
      context,
      title: r.label,
      builder: (ctx, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        if (choice == 'doc')
          AppSelect<String>(
            label: 'Documento ya entregado (no se vuelve a pedir)',
            value: docId,
            options: [for (final doc in detail.expedienteDocuments) (value: '${doc['documentId']}', label: '${doc['name'] ?? doc['type'] ?? 'Documento'} · ${doc['status']}')],
            onChanged: (v) => setSheet(() => docId = v),
          ),
        if (choice == 'note') AppTextField(label: 'Dato o referencia', hint: 'Ej. número de registro, folio de consulta', onChanged: (v) => text = v),
        if (choice == 'file') MutedText(files!.first.name),
        const SizedBox(height: 12),
        DateField(label: r.validityDays != null ? 'Fecha del documento (para su vigencia)' : 'Fecha del documento (opcional)', value: date, onChanged: (v) => setSheet(() => date = v)),
      ]),
      footer: (ctx, _) => [Btn('Guardar evidencia', expand: true, onPressed: () => Navigator.pop(ctx, true))],
    );
    if (ok != true) return;
    final id = detail.operation.id;
    await run(
      () => switch (choice) {
        'doc' => ops.linkEvidenceDocument(id, r.id, docId, date),
        'file' => ops.attachEvidenceFile(id, r.id, files!.first, date),
        _ => ops.addEvidenceNote(id, r.id, text.trim(), date),
      },
      'Evidencia registrada: queda como Recibido hasta su revisión.',
    );
  }

  Future<void> _review(ops.OperationDetail detail, ops.RequirementView r) async {
    final id = detail.operation.id;
    if (r.naPending) {
      final approve = await showModalBottomSheet<bool>(
        context: context,
        builder: (ctx) => SafeArea(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            ListTile(leading: const Icon(Icons.check), title: const Text('Validar "No aplica"'), onTap: () => Navigator.pop(ctx, true)),
            ListTile(leading: const Icon(Icons.close), title: const Text('Rechazar "No aplica"'), onTap: () => Navigator.pop(ctx, false)),
          ]),
        ),
      );
      if (approve == null || !mounted) return;
      if (approve) return run(() => ops.decideNotApplicable(id, r.id, true, null), '"No aplica" validado.');
      final why = await askReason(context, title: 'Rechazar "No aplica"', label: 'Por qué sí aplica');
      if (why != null) await run(() => ops.decideNotApplicable(id, r.id, false, why), '"No aplica" rechazado.');
      return;
    }
    final status = await showModalBottomSheet<String>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(title: const Text('En revisión'), onTap: () => Navigator.pop(ctx, 'IN_REVIEW')),
          ListTile(title: const Text('Validar'), enabled: r.currentEvidence != null, subtitle: r.currentEvidence == null ? const Text('Necesita evidencia') : null, onTap: () => Navigator.pop(ctx, 'VALIDATED')),
          ListTile(title: const Text('Requiere corrección'), onTap: () => Navigator.pop(ctx, 'NEEDS_CORRECTION')),
        ]),
      ),
    );
    if (status == null || !mounted) return;
    String? obs;
    if (status == 'NEEDS_CORRECTION') {
      obs = await askReason(context, title: 'Requiere corrección', label: 'Qué hay que corregir');
      if (obs == null) return;
    }
    await run(() => ops.reviewRequirement(id, r.id, status, obs), 'Revisión registrada.');
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final detail = d;
    if (detail == null) {
      return AppScaffold(
        title: 'Operación',
        body: error != null ? LoadError(message: error!, onRetry: load) : const StateMessage(kind: StateKind.loading, title: 'Cargando operación…'),
      );
    }
    final op = detail.operation;
    final canManage = auth.can('OPERATION_MANAGE') && op.status != 'CANCELLED';
    final canReview = auth.can('OPERATION_REVIEW');
    final locked = op.docStatus == 'COMPLETE_VALIDATED';
    final reqs = detail.requirements.where((r) => r.section == section).toList();
    final c = context.c;

    return AppScaffold(
      title: op.folio,
      body: PageBody(onRefresh: load, subtitle: op.propertyDescription, children: [
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Wrap(spacing: 6, runSpacing: 6, children: [
              AppBadge(op.displayStatus, tone: operationTone(op)),
              AppBadge('Documentación: ${ops.docStatusLabels[op.docStatus]}', tone: locked ? Tone.success : Tone.neutral),
              AppBadge(ops.originLabels[op.origin] ?? op.origin),
            ]),
            const SizedBox(height: 10),
            FieldView('C21 Genera representa', ops.representedLabel(op.operationType, op.representedSide)),
            FieldView('Asesor responsable', op.advisorName),
            if (op.closedOn != null) FieldView('Cierre', '${formatDate(op.closedOn)} · ${op.closedByName ?? ''}'),
            if (op.amount != null) FieldView('Monto', formatMoneyWithCurrency(op.amount, currency: op.currency ?? 'MXN')),
            if (op.docDueDate != null && !locked) FieldView('Compromiso de documentación', '${formatDate(op.docDueDate)} · ${op.docFollowUpOwner ?? ''}'),
            if (detail.expedienteFolio != null && op.expedienteId != null)
              LinkText('Expediente ${detail.expedienteFolio}', onTap: () => context.push('/expedientes/${op.expedienteId}')),
            if (op.origin == 'EXTERNAL') ...[
              FieldView('Origen de la propiedad', detail.externalSource),
              FieldView('Oficina o tercero participante', detail.externalOffice),
              FieldView('Participación de C21 Genera', detail.generaParticipation),
            ],
            const SizedBox(height: 10),
            Wrap(spacing: 8, runSpacing: 8, children: [
              if (canManage && op.status == 'IN_PROCESS')
                Btn('Pasar a revisión de cierre', variant: BtnVariant.secondary, small: true, onPressed: busy ? null : () => run(() => ops.moveToClosingReview(op.id), 'En revisión de cierre.')),
              if (canManage && (op.status == 'IN_PROCESS' || op.status == 'CLOSING_REVIEW')) Btn('Registrar cierre', small: true, onPressed: busy ? null : () => _close(detail)),
              if (canReview && (op.status == 'CLOSED' || op.status == 'CANCELLED'))
                Btn('Reabrir', variant: BtnVariant.secondary, small: true, onPressed: busy
                    ? null
                    : () async {
                        final why = await askReason(context, title: 'Reabrir operación', description: 'Vuelve a revisión de cierre; se conserva el historial.');
                        if (why != null) await run(() => ops.reopenOperation(op.id, why), 'Operación reabierta.');
                      }),
              if (canManage && op.status != 'CLOSED')
                Btn('Cancelar', variant: BtnVariant.danger, small: true, onPressed: busy
                    ? null
                    : () async {
                        final why = await askReason(context, title: 'Cancelar operación', danger: true);
                        if (why != null) await run(() => ops.cancelOperation(op.id, why), 'Operación cancelada.');
                      }),
            ]),
          ]),
        ),
        const SizedBox(height: 12),
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            CardHeader(title: 'Faltantes', description: '${detail.satisfied}/${detail.total} requisitos cumplidos'),
            MutedText('${ops.partyLabel(op.operationType, 'SELLER')}: ${detail.missingBySide['SELLER'] ?? 0} · ${ops.partyLabel(op.operationType, 'BUYER')}: ${detail.missingBySide['BUYER'] ?? 0} · Operación: ${detail.missingBySide['OPERATION'] ?? 0}'),
            if (detail.needsUpdate > 0) MutedText('Requieren actualización: ${detail.needsUpdate}'),
            if (!detail.matrixApproved) ...[
              const SizedBox(height: 8),
              const Notice(tone: Tone.warning, text: 'La matriz documental está en borrador: falta la aprobación del área legal o de cumplimiento.'),
            ],
            if (canReview && !locked) ...[
              const SizedBox(height: 8),
              if (detail.blockers.isNotEmpty) Bullets(detail.blockers),
              Btn('Declarar completo y validado', icon: Icons.verified_user_outlined, onPressed: busy || detail.blockers.isNotEmpty ? null : () => run(() => ops.authorizeOperation(op.id), 'Expediente de cierre completo y validado.')),
            ],
            if (locked) MutedText('Completo y validado por ${detail.docAuthorizedByName ?? ''}.'),
          ]),
        ),
        const SizedBox(height: 12),
        for (final side in ['SELLER', 'BUYER'])
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: AppCard(
              child: Builder(builder: (_) {
                final p = detail.parties.where((x) => x.side == side).firstOrNull;
                return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  CardHeader(title: ops.partyLabel(op.operationType, side), action: p == null ? null : AppBadge(p.representedByGenera ? 'Cliente de C21 Genera' : 'Contraparte', tone: p.representedByGenera ? Tone.gold : Tone.neutral)),
                  if (p == null) Text('Falta identificarlo (se captura desde la web).', style: TextStyle(color: c.warningText)),
                  if (p != null) ...[
                    FieldView('Nombre', p.fullName),
                    if (p.representativeName != null) FieldView('Representante', '${p.representativeName}${p.representativeRole != null ? ' (${p.representativeRole})' : ''}'),
                    if (p.email != null || p.phone != null) FieldView('Contacto', [p.email, p.phone].whereType<String>().join(' · ')),
                    if (!p.representedByGenera && p.counterpartContact != null) FieldView('Sus papeles se piden por', p.counterpartContact),
                  ],
                ]);
              }),
            ),
          ),
        FilterChips<String>(
          value: section,
          options: [for (final e in ops.sectionLabels.entries) (id: e.key, label: e.value, count: detail.requirements.where((r) => r.section == e.key && !r.satisfied).length)],
          onChanged: (v) => setState(() => section = v),
        ),
        const SizedBox(height: 10),
        if (reqs.isEmpty) const MutedText('La matriz vigente no pide requisitos de esta sección.'),
        for (final r in reqs)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: AppCard(
              borderColor: r.satisfied ? c.successText.withValues(alpha: 0.3) : (r.expired || r.status == 'NEEDS_CORRECTION' ? c.dangerText.withValues(alpha: 0.3) : null),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(
                  '${r.partySide == null ? 'Operación' : ops.partyLabel(op.operationType, r.partySide!)} · ${r.label}${r.mandatory ? ' (obligatorio)' : ''}',
                  style: const TextStyle(fontWeight: FontWeight.w500),
                ),
                const SizedBox(height: 4),
                Wrap(spacing: 4, runSpacing: 4, children: [
                  AppBadge(r.naPending ? 'No aplica (por validar)' : (ops.requirementStatusLabels[r.status] ?? r.status), tone: _reqTone[r.status] ?? Tone.neutral),
                  if (r.expired) const AppBadge('Requiere actualización', tone: Tone.danger),
                  if (r.dateMissing) const AppBadge('Falta fecha del documento', tone: Tone.warning),
                ]),
                MutedText('Se solicita a: ${ops.requestedFromLabels[r.requestedFrom] ?? '—'}${r.dueDate != null ? ' · compromiso ${formatDate(r.dueDate)}' : ''}', size: 12),
                if (r.currentEvidence != null)
                  InkWell(
                    onTap: r.currentEvidence!.kind == 'FILE'
                        ? () async {
                            try {
                              final url = await ops.evidenceDownload(op.id, r.currentEvidence!.id);
                              if (context.mounted) await openUrl(context, url);
                            } catch (e) {
                              if (context.mounted) showToast(context, errorText(e));
                            }
                          }
                        : null,
                    child: Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(
                        'v${r.currentEvidence!.versionNumber} · ${r.currentEvidence!.title}${r.currentEvidence!.expiresOn != null ? ' · vence ${formatDate(r.currentEvidence!.expiresOn)}' : ''} · ${r.currentEvidence!.attachedByName ?? ''}',
                        style: TextStyle(fontSize: 12, color: r.currentEvidence!.kind == 'FILE' ? Brand.darkGold : c.fg),
                      ),
                    ),
                  ),
                if (r.previousEvidence.isNotEmpty) MutedText('${r.previousEvidence.length} versión(es) anterior(es)', size: 11),
                if (r.observation != null) MutedText('Observación: ${r.observation}', size: 12),
                if (r.status == 'NOT_APPLICABLE') MutedText('No aplica — ${r.naJustification ?? ''}', size: 12),
                if (r.reviewedByName != null) MutedText('Revisó: ${r.reviewedByName}', size: 11),
                if (!locked && (canManage || canReview))
                  Wrap(spacing: 6, children: [
                    if (canManage) Btn('Evidencia', icon: Icons.attach_file, variant: BtnVariant.ghost, small: true, onPressed: busy ? null : () => _evidence(detail, r)),
                    if (canManage && !r.mandatory && r.status != 'NOT_APPLICABLE')
                      Btn('No aplica…', variant: BtnVariant.ghost, small: true, onPressed: busy
                          ? null
                          : () async {
                              final why = await askReason(context, title: 'No aplica', label: 'Justificación', description: 'El rol autorizado debe validarlo.');
                              if (why != null) await run(() => ops.requestNotApplicable(op.id, r.id, why), 'Solicitud registrada.');
                            }),
                    if (canReview && (r.status != 'NOT_APPLICABLE' || r.naPending))
                      Btn(r.naPending ? 'Decidir "No aplica"' : 'Revisar', variant: BtnVariant.secondary, small: true, onPressed: busy ? null : () => _review(detail, r)),
                  ]),
              ]),
            ),
          ),
        const SizedBox(height: 12),
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const CardHeader(title: 'Historial'),
            for (final h in detail.history)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('${h['detail'] ?? ''}', style: const TextStyle(fontSize: 13)),
                  MutedText('${h['actorName'] ?? 'Sistema'} · ${formatDateTime('${h['occurredAt']}')}', size: 11),
                ]),
              ),
          ]),
        ),
      ]),
    );
  }
}
