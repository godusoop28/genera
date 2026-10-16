import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../api/client.dart';
import 'widgets.dart';

/// Mismo límite que el backend (GET /public/upload-limits, 100 MB por archivo).
const maxUploadBytes = 100 * 1024 * 1024;

String _contentType(String name) {
  final ext = name.toLowerCase().split('.').last;
  return switch (ext) {
    'pdf' => 'application/pdf',
    'png' => 'image/png',
    'webp' => 'image/webp',
    _ => 'image/jpeg',
  };
}

/// Pide archivos con la cámara, la galería o el explorador. Las fotos se
/// reducen y se guardan como JPEG (así también se convierten las HEIC del iPhone).
Future<List<UploadFile>?> pickUploadFiles(BuildContext context, {bool multiple = true, bool allowCamera = true}) async {
  final source = await showModalBottomSheet<String>(
    context: context,
    builder: (ctx) => SafeArea(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        if (allowCamera) ListTile(leading: const Icon(Icons.photo_camera_outlined), title: const Text('Tomar foto'), onTap: () => Navigator.pop(ctx, 'camera')),
        ListTile(leading: const Icon(Icons.photo_library_outlined), title: const Text('Elegir de la galería'), onTap: () => Navigator.pop(ctx, 'gallery')),
        ListTile(leading: const Icon(Icons.picture_as_pdf_outlined), title: const Text('Archivo PDF o imagen'), onTap: () => Navigator.pop(ctx, 'files')),
        const SizedBox(height: 8),
      ]),
    ),
  );
  if (source == null) return null;
  final picker = ImagePicker();
  final out = <UploadFile>[];
  try {
    if (source == 'camera') {
      final x = await picker.pickImage(source: ImageSource.camera, imageQuality: 85, maxWidth: 2400, maxHeight: 2400);
      if (x != null) out.add(await _fromXFile(x));
    } else if (source == 'gallery') {
      final xs = multiple
          ? await picker.pickMultiImage(imageQuality: 85, maxWidth: 2400, maxHeight: 2400)
          : [?await picker.pickImage(source: ImageSource.gallery, imageQuality: 85, maxWidth: 2400, maxHeight: 2400)];
      for (final x in xs) {
        out.add(await _fromXFile(x));
      }
    } else {
      final files = await FilePicker.pickFiles(type: FileType.custom, allowedExtensions: const ['pdf', 'jpg', 'jpeg', 'png', 'webp']);
      for (final f in multiple ? files : files.take(1)) {
        out.add(UploadFile(name: f.name, bytes: await f.xFile.readAsBytes(), contentType: _contentType(f.name)));
      }
    }
  } catch (_) {
    if (context.mounted) showToast(context, 'No se pudo abrir el archivo. Revisa los permisos de la app e intenta de nuevo.');
    return null;
  }
  if (out.isEmpty) return null;
  final tooBig = out.where((f) => f.bytes.length > maxUploadBytes).toList();
  if (tooBig.isNotEmpty) {
    if (context.mounted) showToast(context, '"${tooBig.first.name}" pesa más de 100 MB, el máximo por archivo.');
    return null;
  }
  return out;
}

Future<UploadFile> _fromXFile(XFile x) async {
  final name = x.name.toLowerCase().endsWith('.heic') || x.name.toLowerCase().endsWith('.heif') ? '${x.name.split('.').first}.jpg' : x.name;
  return UploadFile(name: name, bytes: await x.readAsBytes(), contentType: _contentType(name));
}
