import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../../api/api.dart' as api;
import '../../../api/client.dart';
import '../../../api/models.dart';
import '../../../labels.dart';
import '../../../state/auth.dart';
import '../../../ui/file_pick.dart';
import '../../../ui/widgets.dart';
import '../expediente_detail_screen.dart';

class SummaryTab extends StatefulWidget {
  const SummaryTab({super.key, required this.ctx});
  final ExpedienteCtx ctx;

  @override
  State<SummaryTab> createState() => _SummaryTabState();
}

class _SummaryTabState extends State<SummaryTab> {
  PublicLinkStatus? linkStatus;
  bool busy = false;
  // Controles de cambios #14 y #22: documentos pendientes de entrega y servicios del inmueble.
  int? pendingDocs;
  String? services;

  ExpedienteResponse get e => widget.ctx.expediente;

  @override
  void initState() {
    super.initState();
    loadLink();
    loadExtras();
  }

  Future<void> loadExtras() async {
    try {
      final docs = await api.listDocuments(e.id);
      final data = await api.getClientData(e.id);
      if (!mounted) return;
      setState(() {
        pendingDocs = docs.where((d) => d.markedPending).length;
        services = data['availableServices'] as String?;
      });
    } catch (_) {}
  }

  Future<void> loadLink() async {
    try {
      final s = await api.getPublicLinkStatus(e.id);
      if (mounted) setState(() => linkStatus = s);
    } catch (_) {}
  }

  Future<void> run(Future<void> Function() action, String success) async {
    setState(() => busy = true);
    try {
      await action();
      if (mounted) showToast(context, success);
      await widget.ctx.reload();
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> generateLink() async {
    setState(() => busy = true);
    try {
      final link = await api.generatePublicLink(e.id);
      widget.ctx.setFreshPublicLink(frontendLink(link.url, 'carga'));
      if (mounted) showToast(context, 'Liga generada. Cópiala y compártela con el cliente; cualquier liga anterior dejó de funcionar.');
      await loadLink();
      await widget.ctx.reload();
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final c = context.c;
    final newLink = widget.ctx.freshPublicLink;
    final ls = linkStatus;
    final linkState = ls == null
        ? ('Sin liga', Tone.neutral)
        : ls.usable
        ? ('Activa', Tone.success)
        : ls.revokedAt != null
        ? ('Revocada', Tone.danger)
        : ('Vencida', Tone.warning);

    return TabBody(
      onRefresh: widget.ctx.reload,
      children: [
        AppCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const CardHeader(
                title: 'Datos del expediente',
                description: 'Cualquier corrección queda en la bitácora con quién la hizo y por qué; si ya había un contrato sin firmar, se genera una versión nueva.',
              ),
              FieldGrid(
                children: [
                  FieldView('Tipo de cliente', label(personTypeLabels, e.personType), upper: true),
                  FieldView('Quién firma', label(signerCharacterLabels, e.signerCharacter), upper: true),
                  FieldView('Acreditación de la propiedad', label(accreditationLabels, e.accreditationType), upper: true),
                  FieldView('Tipo de inmueble', label(propertyTypeLabels, e.propertyCaseType), upper: true),
                  FieldView('Régimen de condominio', e.condominiumRegime ? 'Sí' : 'No', upper: true),
                  FieldView('Situación jurídica', label(legalStatusLabels, e.declaredLegalStatus), upper: true),
                  FieldView('Domicilio del inmueble', (e.propertyAddress?.isNotEmpty ?? false) ? e.propertyAddress : 'Se tomará de la escritura o el predial', upper: true),
                  if (e.propertyReference != null) FieldView('Referencia que ve el cliente', e.propertyReference, upper: true),
                  FieldView('Servicios con los que cuenta', (services?.trim().isNotEmpty ?? false) ? services : 'Sin capturar (Datos del contrato)', upper: true),
                  FieldView('Documentos pendientes de entrega',
                      pendingDocs == null ? '—' : (pendingDocs == 0 ? 'Ninguno' : '$pendingDocs (no bloquean el avance)'), upper: true),
                  FieldView('Creado', formatDateTime(e.createdAt), upper: true),
                  FieldView('Última actualización', formatDateTime(e.updatedAt), upper: true),
                  if (e.decisionReason != null) FieldView('Motivo de la decisión', e.decisionReason, upper: true),
                ],
              ),
              if (auth.can('EXPEDIENT_EDIT') && e.correctable) ...[
                const SizedBox(height: 14),
                Btn('Corregir datos', icon: Icons.edit_outlined, small: true, variant: BtnVariant.secondary, onPressed: () => _correct(context)),
              ],
            ],
          ),
        ),
        _PrivacyCard(ctx: widget.ctx),
        if (auth.can('PUBLIC_LINK_GENERATE')) _DocumentTimingCard(ctx: widget.ctx),
        AppCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const CardHeader(
                title: 'Liga del cliente',
                description: 'El cliente no tiene cuenta: entra con esta liga. Cada liga vence y solo la más reciente funciona. Por seguridad solo se muestra al generarla.',
              ),
              Wrap(
                spacing: 8,
                runSpacing: 4,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  AppBadge(linkState.$1, tone: linkState.$2),
                  if (ls?.expiresAt != null && ls!.usable) MutedText('Vence el ${formatDate(ls.expiresAt)}', size: 12.5),
                  if (ls?.lastUsedAt != null) MutedText('· Último acceso: ${formatDateTime(ls!.lastUsedAt)}', size: 12.5),
                ],
              ),
              if (newLink != null) ...[
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.fromLTRB(12, 4, 4, 4),
                  decoration: BoxDecoration(
                    color: c.appBg,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: c.border),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: SelectableText(
                          newLink,
                          maxLines: 2,
                          style: TextStyle(fontFamily: 'GeistMono', fontSize: 12, color: c.fg),
                        ),
                      ),
                      IconButton(tooltip: 'Copiar liga', icon: const Icon(Icons.copy, size: 18), onPressed: () => copyText(context, newLink, 'Liga copiada.')),
                      IconButton(
                        tooltip: 'Compartir liga',
                        icon: const Icon(Icons.share_outlined, size: 18),
                        onPressed: () => SharePlus.instance.share(ShareParams(text: newLink, subject: 'Liga de tu expediente ${e.folio}')),
                      ),
                    ],
                  ),
                ),
              ],
              if (auth.can('PUBLIC_LINK_GENERATE')) ...[
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    Btn(ls != null ? 'Generar liga nueva' : 'Generar liga', icon: Icons.link, small: true, loading: busy, onPressed: generateLink),
                    if (ls?.usable ?? false)
                      Btn(
                        'Revocar liga',
                        icon: Icons.link_off,
                        small: true,
                        variant: BtnVariant.danger,
                        onPressed: busy
                            ? null
                            : () async {
                                if (!await confirmDialog(
                                  context,
                                  title: '¿Revocar la liga?',
                                  message: 'El cliente ya no podrá entrar con ella.',
                                  confirmLabel: 'Revocar',
                                  danger: true,
                                )) {
                                  return;
                                }
                                await run(() => api.revokePublicLink(e.id), 'Liga revocada: el cliente ya no puede entrar con ella.');
                                widget.ctx.setFreshPublicLink(null);
                                loadLink();
                              },
                      ),
                  ],
                ),
              ],
              const SizedBox(height: 10),
              const MutedText('Al abrirla, el cliente ve su nombre y el domicilio del inmueble parcialmente ocultos para confirmar que es su expediente.', size: 12),
            ],
          ),
        ),
        if (e.status == 'DOCUMENTS_APPROVED' && auth.can('RECEPTION_SIGN'))
          AppCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const CardHeader(title: 'Recepción documental', description: 'Todos los documentos obligatorios están aceptados. Firma la recepción para pasar al contrato.'),
                Btn('Firmar recepción', icon: Icons.draw_outlined, loading: busy, onPressed: () => run(() => api.signReception(e.id), 'Recepción firmada.')),
              ],
            ),
          ),
        if (auth.can('PROPERTY_DECIDE') && (e.correctable || e.status == 'CONTRACT_SIGNED') && e.status != 'DRAFT')
          AppCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                CardHeader(
                  title: 'Decisión sobre el inmueble',
                  description: e.status == 'CONTRACT_SIGNED'
                      ? 'El contrato está firmado por todas las partes: ya se puede aceptar el inmueble.'
                      : 'Para aceptar el inmueble primero debe estar firmado el contrato de intermediación. Rechazarlo es posible en cualquier momento.',
                ),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    if (e.status == 'CONTRACT_SIGNED')
                      Btn('Aceptar inmueble', icon: Icons.thumb_up_outlined, loading: busy, onPressed: () => run(() => api.acceptProperty(e.id), 'Inmueble aceptado.')),
                    Btn(
                      'Rechazar inmueble',
                      icon: Icons.thumb_down_outlined,
                      variant: BtnVariant.danger,
                      onPressed: busy
                          ? null
                          : () async {
                              final reason = await askReason(
                                context,
                                title: 'Rechazar inmueble',
                                label: 'Motivo del rechazo',
                                minLength: 5,
                                danger: true,
                                confirmLabel: 'Rechazar',
                              );
                              if (reason != null) await run(() => api.rejectProperty(e.id, reason), 'Inmueble rechazado.');
                            },
                    ),
                  ],
                ),
              ],
            ),
          ),
      ],
    );
  }

  Future<void> _correct(BuildContext context) async {
    final config = <String, Object?>{
      'personType': e.personType,
      'signedByAttorney': e.signedByAttorney,
      'accreditationType': e.accreditationType,
      'condominiumRegime': e.condominiumRegime,
      'propertyCaseType': e.propertyCaseType,
      'declaredLegalStatus': e.declaredLegalStatus,
      'propertyAddress': e.propertyAddress ?? '',
    };
    var reason = '';
    var saving = false;
    await showAppSheet(
      context,
      title: 'Corregir datos del expediente',
      builder: (ctx, setSheet) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Notice(
            tone: Tone.warning,
            text:
                'Si cambias el tipo de persona o la acreditación, la lista de documentos se recalcula. Si ya había un contrato sin firmar, queda sin efecto y hay que generar una versión nueva. Para cambiar el tipo de persona, primero ajusta los participantes.',
          ),
          const SizedBox(height: 14),
          AppSelect<String>(
            label: 'Tipo de cliente',
            value: config['personType'] as String,
            options: optionsOf(personTypeLabels),
            onChanged: (v) => setSheet(() => config['personType'] = v),
          ),
          const SizedBox(height: 12),
          AppSelect<String>(
            label: 'Acreditación de la propiedad',
            value: config['accreditationType'] as String,
            options: optionsOf(accreditationLabels),
            onChanged: (v) => setSheet(() => config['accreditationType'] = v),
          ),
          const SizedBox(height: 12),
          AppSelect<String>(
            label: 'Tipo de inmueble',
            value: config['propertyCaseType'] as String,
            options: optionsOf(propertyTypeLabels),
            onChanged: (v) => setSheet(() => config['propertyCaseType'] = v),
          ),
          const SizedBox(height: 12),
          AppSelect<String>(
            label: 'Situación jurídica declarada',
            value: config['declaredLegalStatus'] as String,
            options: optionsOf(legalStatusLabels),
            onChanged: (v) => setSheet(() => config['declaredLegalStatus'] = v),
          ),
          const SizedBox(height: 12),
          AppTextField(label: 'Domicilio del inmueble', value: config['propertyAddress'] as String, maxLines: 2, onChanged: (v) => config['propertyAddress'] = v),
          const SizedBox(height: 6),
          ToggleRow(label: 'Régimen de condominio', value: config['condominiumRegime'] == true, onChanged: (v) => setSheet(() => config['condominiumRegime'] = v)),
          if (config['personType'] == 'FISICA')
            ToggleRow(label: 'Firma un apoderado', value: config['signedByAttorney'] == true, onChanged: (v) => setSheet(() => config['signedByAttorney'] = v)),
          const SizedBox(height: 10),
          AppTextField(
            label: 'Motivo de la corrección',
            maxLines: 2,
            hint: 'Ej. El cliente aclaró que la propiedad se acredita con contrato privado.',
            onChanged: (v) => setSheet(() => reason = v),
          ),
        ],
      ),
      footer: (ctx, setSheet) => [
        Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.pop(ctx)),
        Btn(
          'Guardar corrección',
          loading: saving,
          onPressed: reason.trim().length < 5
              ? null
              : () async {
                  setSheet(() => saving = true);
                  await run(() => api.correctExpediente(e.id, config, reason.trim()), 'Datos corregidos y registrados en la bitácora.');
                  if (ctx.mounted) Navigator.pop(ctx);
                },
        ),
      ],
    );
  }
}

/// Antes de mandar la liga, el asesor elige qué documentos pide en la primera
/// entrega y cuáles puede subir el cliente después.
class _DocumentTimingCard extends StatefulWidget {
  const _DocumentTimingCard({required this.ctx});
  final ExpedienteCtx ctx;

  @override
  State<_DocumentTimingCard> createState() => _DocumentTimingCardState();
}

class _DocumentTimingCardState extends State<_DocumentTimingCard> {
  List<DocumentResponse>? documents;
  String? saving;

  @override
  void initState() {
    super.initState();
    api.listDocuments(widget.ctx.expediente.id).then((d) => mounted ? setState(() => documents = d) : null).catchError((Object err) {
      if (mounted) showToast(context, errorText(err));
      return null;
    });
  }

  Future<void> change(List<DocumentResponse> docs, bool deferred, [String? reason]) async {
    final targets = docs.where((d) => d.deferred != deferred).toList();
    if (targets.isEmpty) return;
    setState(() => saving = targets.length == 1 ? targets.first.id : 'all');
    try {
      final updated = await Future.wait(targets.map((d) => api.setDocumentDeferred(d.id, deferred, reason)));
      final byId = {for (final d in updated) d.id: d};
      if (mounted) setState(() => documents = documents?.map((d) => byId[d.id] ?? d).toList());
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    } finally {
      if (mounted) setState(() => saving = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final docs = documents;
    if (docs == null) {
      return const AppCard(
        child: CardHeader(title: '¿Qué documentos pides primero?', description: 'Cargando requisitos…'),
      );
    }
    final open = docs.where((d) => d.required && d.status != 'ACCEPTED' && d.status != 'NOT_APPLICABLE').toList();
    if (open.isEmpty) return const SizedBox.shrink();
    final ctx = widget.ctx;
    final names = {for (final p in ctx.participants) p.id: p};
    final ordered = [...ctx.participants]..sort((a, b) => a.ordinal.compareTo(b.ordinal));
    final groups = [
      for (final p in ordered) (title: '${p.fullName} · ${label(participantRoleLabels, p.role)}', docs: open.where((d) => d.participantId == p.id).toList()),
      (
        title: ctx.expediente.personType == 'MORAL' ? 'Inmueble y empresa' : 'Inmueble',
        docs: open.where((d) => d.participantId == null || !names.containsKey(d.participantId)).toList(),
      ),
    ].where((g) => g.docs.isNotEmpty);
    final later = open.where((d) => d.deferred).length;
    return AppCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const CardHeader(
            title: '¿Qué documentos pides primero?',
            description:
                'Marca lo que necesitas en la primera entrega. Lo demás el cliente lo podrá subir después desde su misma liga, cuando lo tenga; se sigue necesitando para aprobar la documentación y generar el contrato.',
          ),
          Wrap(
            spacing: 12,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text.rich(
                TextSpan(
                  children: [
                    TextSpan(
                      text: '${open.length - later}',
                      style: TextStyle(fontWeight: FontWeight.w600, color: c.fg),
                    ),
                    const TextSpan(text: ' se piden ahora · '),
                    TextSpan(
                      text: '$later',
                      style: TextStyle(fontWeight: FontWeight.w600, color: c.fg),
                    ),
                    const TextSpan(text: ' para después'),
                  ],
                ),
                style: TextStyle(fontSize: 13.5, color: c.muted),
              ),
              if (later > 0) LinkText('Pedir todos ahora', onTap: saving != null ? () {} : () => change(open, false)),
            ],
          ),
          const SizedBox(height: 10),
          for (final g in groups) ...[
            Padding(
              padding: const EdgeInsets.only(top: 8, bottom: 6),
              child: Text(
                g.title,
                style: TextStyle(fontWeight: FontWeight.w600, color: c.fg, fontSize: 13.5),
              ),
            ),
            for (final d in g.docs)
              Padding(
                padding: const EdgeInsets.only(bottom: 6),
                child: Material(
                  color: !d.deferred ? Color.alphaBlend(Brand.gold.withValues(alpha: 0.05), c.card) : c.card,
                  clipBehavior: Clip.antiAlias,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                    side: BorderSide(color: !d.deferred ? Brand.gold.withValues(alpha: 0.6) : c.border),
                  ),
                  child: CheckboxListTile(
                    value: !d.deferred,
                    dense: true,
                    controlAffinity: ListTileControlAffinity.leading,
                    onChanged: saving != null
                        ? null
                        : (v) async {
                            if (v == true) {
                              change([d], false);
                            } else {
                              final reason = await askReason(
                                context,
                                title: '¿Por qué no pides "${d.customName ?? documentTypeLabel(d.type)}" ahora?',
                                description:
                                    'El cliente podrá subirlo después desde su misma liga. Escribe la razón de esta excepción: queda en el expediente y en la bitácora para que el equipo entienda por qué no se pidió.',
                                label: 'Razón',
                                placeholder: 'Ej. El cliente está tramitando el certificado de libertad de gravamen y lo tendrá la próxima semana.',
                                minLength: 10,
                                confirmLabel: 'Dejar para después',
                              );
                              if (reason != null) change([d], true, reason);
                            }
                          },
                    title: Text(d.customName ?? documentTypeLabel(d.type), style: TextStyle(fontSize: 13.5, color: c.fg)),
                    subtitle: d.deferred && d.deferralReason != null ? Text('Razón: ${d.deferralReason}', style: TextStyle(fontSize: 12, color: c.muted)) : null,
                    secondary: Text(
                      !d.deferred ? 'Ahora' : 'Después',
                      style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: !d.deferred ? c.goldText : c.muted),
                    ),
                  ),
                ),
              ),
          ],
        ],
      ),
    );
  }
}

/// Control de Cambios #2: el aviso de privacidad no bloquea el alta; se ve si está pendiente o
/// firmado, se puede seguir sin él y cargar el aviso firmado en papel. (Imprimirlo: desde la web.)
class _PrivacyCard extends StatefulWidget {
  const _PrivacyCard({required this.ctx});
  final ExpedienteCtx ctx;

  @override
  State<_PrivacyCard> createState() => _PrivacyCardState();
}

class _PrivacyCardState extends State<_PrivacyCard> {
  Json? status;
  bool busy = false;

  ExpedienteResponse get e => widget.ctx.expediente;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final s = await api.getPrivacyStatus(e.id);
      if (mounted) setState(() => status = s);
    } catch (_) {}
  }

  Future<void> act(Future<void> Function() action, String success) async {
    setState(() => busy = true);
    try {
      await action();
      if (mounted) showToast(context, success);
      await load();
      await widget.ctx.reload();
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> uploadSigned() async {
    var secondary = true;
    final ok = await showAppSheet<bool>(
      context,
      title: 'Cargar aviso firmado en papel',
      builder: (ctx, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const MutedText('Sube el escaneo o la foto del aviso impreso con la firma del titular. Se guarda en los documentos del expediente.', size: 12),
        const SizedBox(height: 8),
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          value: secondary,
          onChanged: (v) => setSheet(() => secondary = v ?? false),
          title: const Text('Aceptó las finalidades secundarias (no marcó la casilla para negarse).', style: TextStyle(fontSize: 13)),
        ),
      ]),
      footer: (ctx, _) => [
        Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.pop(ctx, false)),
        Btn('Elegir archivo', icon: Icons.upload_outlined, onPressed: () => Navigator.pop(ctx, true)),
      ],
    );
    if (ok != true || !mounted) return;
    final files = await pickUploadFiles(context);
    if (files == null) return;
    await act(() => api.uploadPhysicalPrivacySignature(e.id, files, secondary), 'Aviso firmado cargado: quedó registrado en el expediente.');
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final s = status;
    final signed = s?['status'] == 'SIGNED';
    final canEdit = auth.can('EXPEDIENT_EDIT') || auth.can('DOCUMENT_UPLOAD');
    final beforeDocuments = e.status == 'DRAFT' || e.status == 'WAITING_PRIVACY';
    return AppCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const CardHeader(
          title: 'Aviso de privacidad',
          description: 'No detiene el alta: el titular puede firmarlo en su liga o en papel en la oficina. Se necesita firmado antes del contrato.',
        ),
        if (s == null)
          const MutedText('Cargando…', size: 12)
        else
          Wrap(spacing: 8, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
            AppBadge(signed ? 'Firmado' : 'Pendiente de firma', tone: signed ? Tone.success : Tone.warning),
            if (signed)
              MutedText(
                '${s['signatureMethod'] == 'PHYSICAL' ? 'En papel, en la oficina' : 'En su liga, con firma digital'} · ${formatDateTime(s['signedAt'] as String?)}'
                '${s['secondaryPurposesAccepted'] == false ? ' · finalidades secundarias y promocionales: no autorizadas' : s['secondaryPurposesAccepted'] == true ? ' · finalidades secundarias y promocionales: autorizadas' : ''}',
                size: 12,
              ),
          ]),
        if (canEdit) ...[
          const SizedBox(height: 12),
          Wrap(spacing: 6, runSpacing: 6, children: [
            if (beforeDocuments)
              Btn('Continuar sin firmar', icon: Icons.fast_forward_outlined, small: true, loading: busy, onPressed: () => act(() => api.deferPrivacySignature(e.id), 'El expediente sigue al paso de documentos. El aviso queda pendiente de firma.')),
            Btn(signed ? 'Cargar otro aviso firmado' : 'Cargar aviso firmado', icon: Icons.upload_outlined, small: true, variant: BtnVariant.secondary, onPressed: busy ? null : uploadSigned),
          ]),
          const SizedBox(height: 6),
          const MutedText('Para imprimirlo y firmarlo en la oficina, usa "Imprimir para firma" en la versión web.', size: 11),
        ],
      ]),
    );
  }
}
