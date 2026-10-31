import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../../../api/client.dart';
import '../../../api/operations_api.dart' as ops;
import '../../../labels.dart';
import '../../../state/auth.dart';
import '../../../ui/widgets.dart';
import '../expediente_detail_screen.dart';

/// CC#33: fotos del inmueble (las que subió el cliente desde su liga y las del asesor). El asesor
/// elige cuáles se usan en el portal, la principal y el orden. Subir o elegir no publica nada.
class PhotosTab extends StatefulWidget {
  const PhotosTab({super.key, required this.ctx});
  final ExpedienteCtx ctx;

  @override
  State<PhotosTab> createState() => _PhotosTabState();
}

class _PhotosTabState extends State<PhotosTab> {
  ops.PhotosResponse? data;
  String? error;
  List<String> selection = [];
  String? mainId;
  bool busy = false;
  int? progress;

  String get _id => widget.ctx.expediente.id;

  @override
  void initState() {
    super.initState();
    load();
  }

  void _apply(ops.PhotosResponse r) {
    final selected = r.photos.where((p) => p.selectedForPortal).toList()..sort((a, b) => (a.portalOrder ?? 0).compareTo(b.portalOrder ?? 0));
    setState(() {
      data = r;
      selection = selected.map((p) => p.id).toList();
      mainId = selected.where((p) => p.mainPhoto).map((p) => p.id).firstOrNull;
    });
  }

  Future<void> load() async {
    try {
      _apply(await ops.listPhotos(_id));
    } catch (e) {
      if (mounted) setState(() => error = errorText(e));
    }
  }

  Future<void> _pick(ImageSource source) async {
    final d = data;
    if (d == null) return;
    final picker = ImagePicker();
    List<XFile> picked;
    try {
      picked = source == ImageSource.camera
          ? [?await picker.pickImage(source: ImageSource.camera, imageQuality: 85, maxWidth: 2400, maxHeight: 2400)]
          : await picker.pickMultiImage(imageQuality: 85, maxWidth: 2400, maxHeight: 2400);
    } catch (_) {
      if (mounted) showToast(context, 'No se pudo abrir la cámara o la galería. Revisa los permisos de la app.');
      return;
    }
    if (picked.isEmpty || !mounted) return;
    final limit = d.remaining < d.maxPerUpload ? d.remaining : d.maxPerUpload;
    if (picked.length > limit) {
      showToast(context, 'Puedes subir hasta $limit fotos en esta carga.');
      return;
    }
    // Vista previa: confirmar antes de subir, y quitar las que se eligieron por error.
    final files = <UploadFile>[];
    for (final x in picked) {
      final name = x.name.toLowerCase().endsWith('.heic') ? '${x.name.split('.').first}.jpg' : x.name;
      files.add(UploadFile(name: name, bytes: await x.readAsBytes(), contentType: name.toLowerCase().endsWith('.png') ? 'image/png' : 'image/jpeg'));
    }
    if (!mounted) return;
    final confirmed = await showAppSheet<List<UploadFile>>(
      context,
      title: 'Revisa las fotos antes de subirlas',
      builder: (ctx, setSheet) => Wrap(spacing: 8, runSpacing: 8, children: [
        for (final f in List.of(files))
          Stack(children: [
            ClipRRect(borderRadius: BorderRadius.circular(8), child: Image.memory(Uint8List.fromList(f.bytes), width: 96, height: 96, fit: BoxFit.cover)),
            Positioned(
              right: 2,
              top: 2,
              child: InkWell(
                onTap: () => setSheet(() => files.remove(f)),
                child: const CircleAvatar(radius: 12, backgroundColor: Colors.black54, child: Icon(Icons.close, size: 14, color: Colors.white)),
              ),
            ),
          ]),
      ]),
      footer: (ctx, _) => [Btn('Subir ${files.length} foto(s)', expand: true, onPressed: files.isEmpty ? null : () => Navigator.pop(ctx, files))],
    );
    if (confirmed == null || confirmed.isEmpty) return;
    setState(() => progress = 0);
    try {
      final r = await ops.uploadPhotos(_id, confirmed, onProgress: (p) => mounted ? setState(() => progress = p) : null);
      _apply(r);
      if (mounted) showToast(context, 'Fotos guardadas.');
    } catch (e) {
      if (mounted) showToast(context, errorText(e));
    } finally {
      if (mounted) setState(() => progress = null);
    }
  }

  Future<void> _save() async {
    setState(() => busy = true);
    final main = mainId ?? (selection.isEmpty ? null : selection.first);
    try {
      _apply(await ops.savePhotoSelection(_id, [for (final id in selection) (id: id, main: id == main)]));
      if (mounted) showToast(context, 'Selección para el portal guardada.');
    } catch (e) {
      if (mounted) showToast(context, errorText(e));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final canEdit = auth.can('EXPEDIENT_EDIT');
    final c = context.c;
    final d = data;
    if (error != null) return TabBody(onRefresh: load, children: [LoadError(message: error!, onRetry: load)]);
    if (d == null) return const TabBody(children: [StateMessage(kind: StateKind.loading, title: 'Cargando fotografías…')]);
    final byId = {for (final p in d.photos) p.id: p};
    final effectiveMain = mainId ?? (selection.isEmpty ? null : selection.first);
    return TabBody(onRefresh: load, children: [
      if (canEdit)
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const CardHeader(title: 'Agregar fotografías para el portal', description: 'Aunque el cliente no haya subido fotos. Se guardan separadas de los documentos.'),
            MutedText('JPG, PNG o WEBP · hasta ${d.maxBytes ~/ (1024 * 1024)} MB por foto · ${d.maxPerUpload} por carga · quedan ${d.remaining} de ${d.maxPhotos}.', size: 12),
            const SizedBox(height: 10),
            if (progress != null) MutedText('Subiendo… $progress%'),
            Wrap(spacing: 8, runSpacing: 8, children: [
              Btn('Agregar fotografías para el portal', icon: Icons.add_photo_alternate_outlined, variant: BtnVariant.secondary, onPressed: d.remaining == 0 || progress != null ? null : () => _pick(ImageSource.gallery)),
              Btn('Tomar foto', icon: Icons.photo_camera_outlined, variant: BtnVariant.ghost, onPressed: d.remaining == 0 || progress != null ? null : () => _pick(ImageSource.camera)),
            ]),
          ]),
        ),
      const SizedBox(height: 12),
      AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          CardHeader(title: 'Fotografías del inmueble (${d.photos.length})', description: 'Toca una foto para usarla o quitarla del portal.'),
          if (d.photos.isEmpty) const MutedText('Aún no hay fotografías.'),
          Wrap(spacing: 8, runSpacing: 8, children: [
            for (final p in d.photos)
              GestureDetector(
                onTap: canEdit ? () => setState(() => selection.contains(p.id) ? selection.remove(p.id) : selection.add(p.id)) : null,
                child: Container(
                  width: 150,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: selection.contains(p.id) ? Brand.gold : c.border, width: selection.contains(p.id) ? 2 : 1),
                  ),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    ClipRRect(
                      borderRadius: const BorderRadius.vertical(top: Radius.circular(9)),
                      child: p.url == null ? const SizedBox(height: 100) : Image.network(p.url!, height: 100, width: 150, fit: BoxFit.cover, errorBuilder: (_, _, _) => const SizedBox(height: 100)),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(6),
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Wrap(spacing: 4, runSpacing: 4, children: [
                          AppBadge(p.uploadedBy == 'CLIENT' ? 'Cliente' : (p.uploadedByName ?? 'Asesor'), tone: p.uploadedBy == 'CLIENT' ? Tone.info : Tone.neutral),
                          if (selection.contains(p.id)) AppBadge('Portal #${selection.indexOf(p.id) + 1}', tone: Tone.success),
                        ]),
                        MutedText(formatDateTime(p.uploadedAt), size: 11),
                        if (canEdit)
                          InkWell(
                            onTap: () async {
                              if (!await confirmDialog(context, title: 'Retirar foto', message: 'Deja de usarse y sale de la selección; el archivo se conserva.')) return;
                              try {
                                _apply(await ops.removePhoto(_id, p.id));
                              } catch (e) {
                                if (context.mounted) showToast(context, errorText(e));
                              }
                            },
                            child: Padding(padding: const EdgeInsets.only(top: 4), child: Text('Retirar', style: TextStyle(fontSize: 12, color: c.dangerText))),
                          ),
                      ]),
                    ),
                  ]),
                ),
              ),
          ]),
        ]),
      ),
      const SizedBox(height: 12),
      AppCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const CardHeader(title: 'Selección para el portal', description: 'Estas fotos, en este orden y con esta principal, se usan al publicar por el flujo autorizado. Guardar no publica nada.'),
          if (selection.isEmpty) const MutedText('No hay fotos seleccionadas.'),
          for (var i = 0; i < selection.length; i++)
            if (byId[selection[i]] != null)
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: byId[selection[i]]!.url == null ? null : ClipRRect(borderRadius: BorderRadius.circular(6), child: Image.network(byId[selection[i]]!.url!, width: 56, height: 42, fit: BoxFit.cover)),
                title: Text('${i + 1}. ${byId[selection[i]]!.originalFilename ?? 'Foto'}', maxLines: 1, overflow: TextOverflow.ellipsis),
                subtitle: effectiveMain == selection[i] ? const Text('Principal') : null,
                trailing: canEdit
                    ? Row(mainAxisSize: MainAxisSize.min, children: [
                        IconButton(
                          tooltip: 'Hacer principal',
                          icon: Icon(effectiveMain == selection[i] ? Icons.star : Icons.star_border, color: Brand.gold),
                          onPressed: () => setState(() => mainId = selection[i]),
                        ),
                        IconButton(
                          tooltip: 'Subir',
                          icon: const Icon(Icons.arrow_upward),
                          onPressed: i == 0 ? null : () => setState(() => selection.insert(i - 1, selection.removeAt(i))),
                        ),
                        IconButton(
                          tooltip: 'Bajar',
                          icon: const Icon(Icons.arrow_downward),
                          onPressed: i == selection.length - 1 ? null : () => setState(() => selection.insert(i + 1, selection.removeAt(i))),
                        ),
                      ])
                    : null,
              ),
          if (canEdit) ...[
            const SizedBox(height: 8),
            Btn('Guardar selección', loading: busy, onPressed: busy ? null : _save),
          ],
        ]),
      ),
    ]);
  }
}
