import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../../api/api.dart' as api;
import '../../../api/client.dart';
import '../../../api/models.dart';
import '../../../labels.dart';
import '../../../state/auth.dart';
import '../../../ui/file_pick.dart';
import '../../../ui/signature_pad.dart';
import '../../../ui/widgets.dart';
import '../expediente_detail_screen.dart';

SigningLink _withFrontendUrl(SigningLink l) => l.withUrl(frontendLink(l.url, 'firma'));

class ContractTab extends StatefulWidget {
  const ContractTab({super.key, required this.ctx});
  final ExpedienteCtx ctx;

  @override
  State<ContractTab> createState() => _ContractTabState();
}

class _ContractTabState extends State<ContractTab> {
  ContractReadiness? readiness;
  ContractCalculations? calc;
  List<ContractGeneration>? contracts;
  String? busy;
  bool showSuperseded = false;

  ExpedienteResponse get e => widget.ctx.expediente;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    api.getContractReadiness(e.id).then((r) => mounted ? setState(() => readiness = r) : null).catchError((_) => null);
    api.getContractData(e.id).then((r) => mounted ? setState(() => calc = r) : null).catchError((_) => null);
    try {
      final list = await api.listContracts(e.id);
      if (mounted) setState(() => contracts = list);
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  Future<void> refresh() async {
    await load();
    await widget.ctx.reload();
  }

  Future<void> generate(String mode) async {
    setState(() => busy = mode);
    try {
      final links = await api.generateContract(e.id, mode);
      widget.ctx.setFreshSigningLinks(links.map(_withFrontendUrl).toList());
      if (mounted) {
        showToast(
            context,
            mode == 'draft'
                ? 'Borrador generado. Está marcado como INCOMPLETO y no se puede firmar.'
                : 'Contrato generado y enviado a firma. Comparte con cada firmante su liga personal.');
      }
      await refresh();
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    } finally {
      if (mounted) setState(() => busy = null);
    }
  }

  Future<void> download(String contractId, String file) async {
    try {
      final url = await api.getContractDownload(contractId, file);
      if (mounted) await openUrl(context, url);
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final c = context.c;
    final links = widget.ctx.freshSigningLinks;
    final current = (contracts ?? []).where((x) => x.status != 'SUPERSEDED').toList();
    final superseded = (contracts ?? []).where((x) => x.status == 'SUPERSEDED').toList();
    final r = readiness;
    return TabBody(
      onRefresh: refresh,
      children: [
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const CardHeader(
              title: 'Generar contrato',
              description:
                  'Se genera con el texto del modelo registrado ante PROFECO, llenado con los datos del expediente. Solo lo jurídicamente indispensable impide enviarlo a firma; un borrador para revisión se puede generar siempre.',
            ),
            if (calc != null && (num.tryParse(calc!.price) ?? 0) > 0)
              Padding(
                padding: const EdgeInsets.only(bottom: 14),
                child: FieldGrid(children: [
                  FieldView('Precio', formatMoney(calc!.price)),
                  FieldView('Comisión (5%)', formatMoney(calc!.commission)),
                  FieldView('Comisión + IVA', formatMoney(calc!.totalCommissionWithVat)),
                  FieldView('Pena convencional', formatMoney(calc!.penalty)),
                  FieldView('Exclusividad', '${calc!.exclusivityDays} días (hasta ${formatDate(calc!.exclusivityEndDate)})'),
                ]),
              )
            else
              Padding(
                padding: const EdgeInsets.only(bottom: 14),
                child: Text('Todavía no hay precio autorizado; captúralo en "Datos del contrato".', style: TextStyle(color: c.warningText, fontSize: 13.5)),
              ),
            if (r != null && !r.ready)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Notice(
                  tone: Tone.warning,
                  title: 'Para enviar a firma falta lo indispensable:',
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Bullets([...r.blockers, ...r.missingData], color: c.warningText),
                    const SizedBox(height: 4),
                    const Text('Mientras tanto puedes generar un borrador para revisarlo.', style: TextStyle(fontSize: 12)),
                  ]),
                ),
              ),
            if (r != null && r.ready)
              const Padding(padding: EdgeInsets.only(bottom: 12), child: Notice(tone: Tone.success, text: 'No falta nada indispensable: el contrato se puede enviar a firma.')),
            if (r != null && r.reviewItems.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: Expandable(
                  title: 'Conviene revisar (${r.reviewItems.length}): no bloquea; en el contrato queda en blanco para llenarse a mano',
                  child: Bullets(r.reviewItems, size: 12),
                ),
              ),
            if (r != null && r.inconsistencies.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Expandable(
                  title: 'Posibles inconsistencias entre documentos (${r.inconsistencies.length}): ayuda para el revisor, no bloquean',
                  child: Bullets(r.inconsistencies, size: 12),
                ),
              ),
            if (auth.can('CONTRACT_GENERATE') && e.correctable) ...[
              Btn('Generar contrato y enviar a firma', icon: Icons.description_outlined, loading: busy == 'final', onPressed: busy != null || !(r?.ready ?? false) ? null : () => generate('final')),
              const SizedBox(height: 8),
              Btn('Generar borrador para revisión', variant: BtnVariant.secondary, loading: busy == 'draft', onPressed: busy != null ? null : () => generate('draft')),
            ],
          ]),
        ),
        if (links.isNotEmpty)
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const CardHeader(
                title: 'Ligas personales de firma',
                description: 'Cada firmante tiene su propia liga (solo sirve una vez y vence). Por seguridad solo se muestran ahora: cópialas y compártelas.',
              ),
              for (final l in links)
                Container(
                  margin: const EdgeInsets.only(bottom: 8),
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text.rich(TextSpan(children: [TextSpan(text: l.signerName), TextSpan(text: ' · ${l.signerCapacity}', style: TextStyle(color: c.muted))]),
                        style: TextStyle(color: c.fg, fontSize: 13.5)),
                    Row(children: [
                      Expanded(child: SelectableText(l.url, maxLines: 2, style: TextStyle(fontFamily: 'GeistMono', fontSize: 11.5, color: c.fg))),
                      IconButton(tooltip: 'Copiar liga', icon: const Icon(Icons.copy, size: 18), onPressed: () => copyText(context, l.url, 'Liga copiada.')),
                      IconButton(
                        tooltip: 'Compartir liga',
                        icon: const Icon(Icons.share_outlined, size: 18),
                        onPressed: () => SharePlus.instance.share(ShareParams(text: l.url, subject: 'Firma del contrato')),
                      ),
                    ]),
                    MutedText('Vence el ${formatDate(l.expiresAt)} · ${l.emailed ? 'También se envió por correo.' : 'No tiene correo registrado: compártela tú.'}', size: 12),
                  ]),
                ),
            ]),
          ),
        if (contracts == null) const MutedText('Cargando contratos…'),
        for (final ct in current)
          _ContractCard(
            contract: ct,
            onDownload: download,
            onChanged: refresh,
            onNewLink: (link) => widget.ctx.setFreshSigningLinks([...links.where((x) => x.signatureId != link.signatureId), _withFrontendUrl(link)]),
          ),
        if (superseded.isNotEmpty)
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              LinkText('${showSuperseded ? 'Ocultar' : 'Ver'} versiones sin efecto (${superseded.length})', onTap: () => setState(() => showSuperseded = !showSuperseded)),
              if (showSuperseded)
                for (final s in superseded)
                  Container(
                    margin: const EdgeInsets.only(top: 8),
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
                    child: Row(children: [
                      Expanded(child: MutedText('Versión ${s.versionNumber} · ${formatDateTime(s.generatedAt)} — ${s.supersededReason ?? ''}', size: 12.5)),
                      if (s.hasPdf) Btn('PDF', icon: Icons.download_outlined, small: true, variant: BtnVariant.ghost, onPressed: () => download(s.id, 'pdf')),
                    ]),
                  ),
            ]),
          ),
      ],
    );
  }
}

class _ContractCard extends StatefulWidget {
  const _ContractCard({required this.contract, required this.onDownload, required this.onChanged, required this.onNewLink});
  final ContractGeneration contract;
  final Future<void> Function(String id, String file) onDownload;
  final Future<void> Function() onChanged;
  final ValueChanged<SigningLink> onNewLink;

  @override
  State<_ContractCard> createState() => _ContractCardState();
}

class _ContractCardState extends State<_ContractCard> {
  String deliveryMethod = 'En persona';
  bool uploading = false;

  ContractGeneration get ct => widget.contract;

  Future<void> uploadAutograph() async {
    final files = await pickUploadFiles(context, multiple: false);
    if (files == null || files.isEmpty) return;
    setState(() => uploading = true);
    try {
      await api.registerAutographSignature(ct.id, files.first);
      if (mounted) showToast(context, 'Contrato firmado a mano registrado como evidencia.');
      await widget.onChanged();
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    } finally {
      if (mounted) setState(() => uploading = false);
    }
  }

  Future<void> signIntermediary() async {
    final signature = ct.signatures.where((s) => s.party == 'INTERMEDIARY' && s.status == 'PENDING').firstOrNull;
    var typedName = '';
    String? image;
    var accepted = false;
    var busy = false;
    await showAppSheet(
      context,
      title: 'Firmar como intermediaria',
      builder: (ctx, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Text.rich(
          TextSpan(children: [
            const TextSpan(text: 'Firma en representación de la intermediaria: '),
            TextSpan(text: signature?.signerName ?? '', style: TextStyle(fontWeight: FontWeight.w600, color: ctx.c.fg)),
            TextSpan(text: ' (${signature?.signerCapacity ?? ''}). Tu usuario queda registrado junto con la fecha, hora y la huella del documento.'),
          ]),
          style: TextStyle(color: ctx.c.muted, fontSize: 13.5),
        ),
        const SizedBox(height: 12),
        Align(
          alignment: Alignment.centerLeft,
          child: Btn('Leer el contrato (PDF)', icon: Icons.download_outlined, small: true, variant: BtnVariant.secondary, onPressed: () => widget.onDownload(ct.id, 'pdf')),
        ),
        const SizedBox(height: 14),
        AppTextField(label: 'Escribe el nombre completo tal como aparece: ${signature?.signerName ?? ''}', onChanged: (v) => setSheet(() => typedName = v)),
        const SizedBox(height: 14),
        SignaturePad(onChanged: (v) => setSheet(() => image = v)),
        CheckboxListTile(
          value: accepted,
          contentPadding: EdgeInsets.zero,
          controlAffinity: ListTileControlAffinity.leading,
          onChanged: (v) => setSheet(() => accepted = v ?? false),
          title: Text(
            'Leí el contrato versión ${ct.versionNumber} (huella ${(ct.documentSha256 ?? '').length > 12 ? ct.documentSha256!.substring(0, 12) : ct.documentSha256 ?? ''}…) y lo firmo electrónicamente.',
            style: const TextStyle(fontSize: 13.5),
          ),
        ),
      ]),
      footer: (ctx, setSheet) => [
        Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.pop(ctx)),
        Btn(busy ? 'Firmando…' : 'Firmar contrato', loading: busy, onPressed: typedName.trim().isEmpty || image == null || !accepted
            ? null
            : () async {
                setSheet(() => busy = true);
                try {
                  await api.signAsIntermediary(ct.id, typedName: typedName, signatureImageBase64: image!, documentSha256: ct.documentSha256 ?? '', accepted: accepted);
                  if (ctx.mounted) Navigator.pop(ctx);
                  if (mounted) showToast(context, 'Firma de la intermediaria registrada.');
                  await widget.onChanged();
                } catch (err) {
                  setSheet(() => busy = false);
                  if (mounted) showToast(context, errorText(err));
                }
              }),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final c = context.c;
    final awaiting = ct.status == 'GENERATED' || ct.status == 'PARTIALLY_SIGNED';
    final intermediaryPending = ct.signatures.any((s) => s.party == 'INTERMEDIARY' && s.status == 'PENDING');
    return AppCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('Contrato — versión ${ct.versionNumber}', style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
              MutedText('Generado ${formatDateTime(ct.generatedAt)}${ct.variantSummary != null ? ' · ${ct.variantSummary}' : ''}', size: 12),
            ]),
          ),
          const SizedBox(width: 8),
          Flexible(child: AppBadge(label(contractStatusLabels, ct.status), tone: contractStatusTone[ct.status] ?? Tone.neutral)),
        ]),
        if (ct.documentSha256 != null) Padding(padding: const EdgeInsets.only(top: 4), child: SelectableText('Huella SHA-256: ${ct.documentSha256}', style: TextStyle(fontFamily: 'GeistMono', fontSize: 10.5, color: c.muted))),
        if (ct.status == 'DRAFT_INCOMPLETE' && ct.missingItems.isNotEmpty) ...[
          const SizedBox(height: 10),
          Notice(tone: Tone.warning, icon: Icons.error_outline, title: 'Borrador incompleto: no se puede firmar', text: 'Pendientes: ${ct.missingItems.join('; ')}'),
        ],
        const SizedBox(height: 10),
        Wrap(spacing: 6, runSpacing: 6, children: [
          if (ct.hasPdf) Btn('PDF', icon: Icons.download_outlined, small: true, variant: BtnVariant.secondary, onPressed: () => widget.onDownload(ct.id, 'pdf')),
          Btn('Word', icon: Icons.download_outlined, small: true, variant: BtnVariant.ghost, onPressed: () => widget.onDownload(ct.id, 'docx')),
          if (ct.hasSignedPackage) Btn('Contrato firmado + constancia', icon: Icons.download_outlined, small: true, onPressed: () => widget.onDownload(ct.id, 'signed')),
        ]),
        if (ct.signatures.isNotEmpty) ...[
          const SizedBox(height: 14),
          const SectionTitle('Firmas'),
          for (final s in ct.signatures)
            Container(
              margin: const EdgeInsets.only(bottom: 8),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Expanded(
                    child: Text.rich(TextSpan(children: [TextSpan(text: s.signerName), TextSpan(text: ' · ${s.signerCapacity}', style: TextStyle(color: c.muted))]),
                        style: TextStyle(color: c.fg, fontSize: 13.5)),
                  ),
                  AppBadge(s.status == 'SIGNED' ? 'Firmada' : (s.status == 'VOIDED' ? 'Sin efecto' : 'Pendiente'),
                      tone: s.status == 'SIGNED' ? Tone.success : (s.status == 'VOIDED' ? Tone.neutral : Tone.warning)),
                ]),
                MutedText(
                  s.status == 'SIGNED'
                      ? 'Firmó ${formatDateTime(s.signedAt)} · ${s.method == 'AUTOGRAPH_SCAN' ? 'firma autógrafa escaneada' : 'firma electrónica'}${s.ipAddress != null ? ' · IP ${s.ipAddress}' : ''}'
                      : s.status == 'VOIDED'
                          ? 'Sin efecto: ${s.voidedReason ?? ''}'
                          : s.party == 'INTERMEDIARY'
                              ? 'Pendiente: firma desde el sistema un usuario autorizado'
                              : 'Pendiente${s.linkExpiresAt != null ? ' · su liga vence ${formatDate(s.linkExpiresAt)}' : ''}${s.hasEmail ? ' · se le envió por correo' : ' · sin correo: compártele la liga'}',
                  size: 12,
                ),
                if (s.status == 'PENDING' && s.party == 'CLIENT' && awaiting && auth.can('CONTRACT_GENERATE'))
                  Btn('Liga nueva', small: true, variant: BtnVariant.ghost, onPressed: () async {
                    try {
                      widget.onNewLink(await api.reissueSigningLink(s.id));
                      if (context.mounted) showToast(context, 'Liga nueva generada; la anterior dejó de funcionar.');
                    } catch (err) {
                      if (context.mounted) showToast(context, errorText(err));
                    }
                  }),
              ]),
            ),
        ],
        if (awaiting && auth.can('CONTRACT_SIGN')) ...[
          const SizedBox(height: 8),
          Divider(color: c.border),
          const SizedBox(height: 8),
          if (intermediaryPending) ...[Btn('Firmar como intermediaria', icon: Icons.draw_outlined, onPressed: signIntermediary), const SizedBox(height: 8)],
          Btn('Cargar contrato firmado a mano (escaneado)', icon: Icons.upload_outlined, variant: BtnVariant.secondary, loading: uploading, onPressed: uploadAutograph),
          const SizedBox(height: 6),
          const MutedText('La carga del contrato firmado a mano registra como firmadas todas las firmas pendientes de esta versión, con el archivo como evidencia.', size: 12),
        ],
        if (ct.status == 'SIGNED' && auth.can('CONTRACT_GENERATE')) ...[
          const SizedBox(height: 8),
          Divider(color: c.border),
          const SizedBox(height: 8),
          AppTextField(label: '¿Cómo se entregó al cliente su tanto firmado?', value: deliveryMethod, onChanged: (v) => deliveryMethod = v),
          const SizedBox(height: 8),
          Btn('Registrar entrega', small: true, onPressed: () async {
            try {
              await api.markContractDelivered(ct.id, deliveryMethod.trim().isEmpty ? 'En persona' : deliveryMethod.trim());
              if (context.mounted) showToast(context, 'Entrega registrada.');
              await widget.onChanged();
            } catch (err) {
              if (context.mounted) showToast(context, errorText(err));
            }
          }),
        ],
        if (ct.status == 'DELIVERED')
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: Text('Entregado al cliente el ${formatDateTime(ct.deliveredAt)} (${ct.deliveryMethod ?? ''}).', style: TextStyle(color: c.successText, fontSize: 13.5)),
          ),
      ]),
    );
  }
}
