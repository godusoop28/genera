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

const _methodLabels = {'DOCUSIGN': 'DocuSign', 'PHYSICAL': 'Firma física (escaneado)'};

/// Control de Cambios #20: los contratos se firman fuera del sistema (DocuSign o en papel). Aquí se
/// sube el PDF firmado, aunque no se haya generado en el sistema (también contratos históricos).
class SignedContractCard extends StatefulWidget {
  const SignedContractCard({super.key, required this.expedienteId, required this.onUploaded});
  final String expedienteId;
  final Future<void> Function() onUploaded;

  @override
  State<SignedContractCard> createState() => _SignedContractCardState();
}

class _SignedContractCardState extends State<SignedContractCard> {
  List<SignedContractUpload>? uploads;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final list = await api.listSignedContracts(widget.expedienteId);
      if (mounted) setState(() => uploads = list);
    } catch (_) {
      if (mounted) setState(() => uploads = const []);
    }
  }

  Future<void> view(SignedContractUpload u, int index) async {
    try {
      final versions = await api.listVersions(u.documentId);
      versions.sort((a, b) => b.versionNumber.compareTo(a.versionNumber));
      final v = index < versions.length ? versions[index] : versions.firstOrNull;
      if (v != null && mounted) {
        Navigator.of(context).push(MaterialPageRoute(builder: (_) => DocumentViewerScreen(versionId: v.id, title: 'Contrato firmado · versión ${v.versionNumber}')));
      }
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  Future<void> upload() async {
    var method = 'DOCUSIGN';
    var historical = false;
    var note = '';
    var busy = false;
    await showAppSheet(
      context,
      title: 'Subir contrato firmado',
      builder: (ctx, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        AppSelect<String>(
          label: '¿Cómo se firmó?',
          value: method,
          options: const [(value: 'DOCUSIGN', label: 'DocuSign (PDF descargado de DocuSign)'), (value: 'PHYSICAL', label: 'Firma física (impreso y escaneado)')],
          onChanged: (v) => setSheet(() => method = v),
        ),
        const SizedBox(height: 8),
        CheckboxListTile(
          dense: true,
          contentPadding: EdgeInsets.zero,
          controlAffinity: ListTileControlAffinity.leading,
          value: historical,
          title: const Text('Es de una operación anterior al sistema (contrato histórico)', style: TextStyle(fontSize: 13.5)),
          onChanged: (v) => setSheet(() => historical = v == true),
        ),
        AppTextField(label: 'Nota (opcional)', hint: 'Ej. Firmado el 14/10 en la oficina', onChanged: (v) => note = v),
        const SizedBox(height: 8),
        const MutedText('Formato principal: PDF. El asesor solo podrá consultarlo en el visor; el administrador puede descargarlo.', size: 12),
      ]),
      footer: (ctx, setSheet) => [
        Btn('Cancelar', variant: BtnVariant.ghost, onPressed: () => Navigator.pop(ctx)),
        Btn('Elegir PDF', icon: Icons.upload_outlined, loading: busy, onPressed: () async {
          final files = await pickUploadFiles(ctx, allowCamera: false);
          if (files == null) return;
          setSheet(() => busy = true);
          try {
            await api.uploadSignedContract(widget.expedienteId, files, method, historical, note);
            if (ctx.mounted) Navigator.pop(ctx);
            if (mounted) showToast(context, 'Contrato firmado cargado: el expediente queda con contrato firmado.');
            await load();
            await widget.onUploaded();
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
    final list = uploads;
    final canUpload = auth.can('DOCUMENT_UPLOAD') || auth.can('CONTRACT_GENERATE') || auth.can('CONTRACT_SIGN');
    return AppCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const CardHeader(
          title: 'Contrato firmado',
          description: 'Los contratos se firman fuera del sistema (DocuSign o impresos). Sube aquí el PDF firmado para completar el expediente.',
        ),
        if (list == null)
          const MutedText('Cargando…', size: 12)
        else if (list.isEmpty)
          const MutedText('Todavía no se ha subido el contrato firmado.', size: 12)
        else
          for (final (i, u) in list.indexed)
            Container(
              margin: const EdgeInsets.only(bottom: 8),
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: c.border)),
              child: Row(children: [
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Wrap(spacing: 6, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
                      const AppBadge('Firmado', tone: Tone.success),
                      Text(_methodLabels[u.signatureMethod] ?? u.signatureMethod, style: TextStyle(color: c.fg, fontSize: 13)),
                      if (u.historical) const AppBadge('Operación anterior al sistema'),
                    ]),
                    const SizedBox(height: 4),
                    MutedText('Subido ${formatDateTime(u.uploadedAt)}${u.uploadedByName != null ? ' por ${u.uploadedByName}' : ''}${u.note != null ? ' · ${u.note}' : ''}', size: 12),
                  ]),
                ),
                Btn('Ver', icon: Icons.visibility_outlined, small: true, variant: BtnVariant.secondary, onPressed: () => view(u, i)),
              ]),
            ),
        if (canUpload) ...[
          const SizedBox(height: 8),
          Align(alignment: Alignment.centerLeft, child: Btn('Subir contrato firmado', icon: Icons.upload_outlined, small: true, onPressed: upload)),
        ],
      ]),
    );
  }
}
