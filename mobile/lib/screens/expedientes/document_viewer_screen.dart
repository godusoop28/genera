import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../ui/widgets.dart';

/// Visor de documentos sin descarga (Control de Cambios #6): el servidor manda cada página como
/// imagen con marca de agua de quien consulta; el archivo original nunca llega al teléfono.
/// Descargar el original solo aparece para quien tiene el permiso (administrador).
class DocumentViewerScreen extends StatefulWidget {
  const DocumentViewerScreen({super.key, required this.versionId, required this.title});
  final String versionId;
  final String title;

  @override
  State<DocumentViewerScreen> createState() => _DocumentViewerScreenState();
}

class _DocumentViewerScreenState extends State<DocumentViewerScreen> {
  int? pageCount;
  bool canDownload = false;
  String? error;
  int page = 0;
  final Map<int, Uint8List> pages = {};
  final Set<int> loading = {};
  final controller = PageController();

  @override
  void initState() {
    super.initState();
    api.getViewer(widget.versionId).then((v) {
      if (!mounted) return;
      setState(() {
        pageCount = v.pageCount;
        canDownload = v.canDownload;
      });
      _load(0);
    }).catchError((Object err) {
      if (mounted) setState(() => error = errorText(err, 'No se pudo abrir el documento.'));
    });
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  /// La página actual y la siguiente, de una en una (el servidor las genera al vuelo).
  Future<void> _load(int index) async {
    final total = pageCount ?? 0;
    if (index < 0 || index >= total || pages.containsKey(index) || loading.contains(index)) return;
    loading.add(index);
    try {
      final bytes = await api.getViewerPage(widget.versionId, index + 1);
      if (!mounted) return;
      setState(() => pages[index] = Uint8List.fromList(bytes));
      _load(index + 1);
    } catch (err) {
      if (mounted) setState(() => error = errorText(err, 'No se pudo mostrar la página.'));
    } finally {
      loading.remove(index);
    }
  }

  Future<void> _download() async {
    try {
      final url = await api.getDownloadUrl(widget.versionId);
      if (mounted) await openUrl(context, url);
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final total = pageCount;
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title, overflow: TextOverflow.ellipsis),
        actions: [
          if (canDownload) IconButton(tooltip: 'Descargar original', icon: const Icon(Icons.download_outlined), onPressed: _download),
        ],
      ),
      body: Column(children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          color: c.appBg,
          child: Row(children: [
            Icon(Icons.verified_user_outlined, size: 16, color: c.goldText),
            const SizedBox(width: 6),
            Expanded(
              child: MutedText(
                canDownload ? 'Vista protegida con marca de agua.' : 'Solo consulta: el documento no se puede descargar ni imprimir.',
                size: 12,
              ),
            ),
          ]),
        ),
        Expanded(
          child: error != null
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(error!, style: TextStyle(color: c.dangerText))))
              : total == null
                  ? const Center(child: CircularProgressIndicator())
                  : PageView.builder(
                      controller: controller,
                      itemCount: total,
                      onPageChanged: (i) {
                        setState(() => page = i);
                        _load(i);
                      },
                      itemBuilder: (_, i) {
                        final bytes = pages[i];
                        if (bytes == null) return const Center(child: CircularProgressIndicator());
                        return InteractiveViewer(maxScale: 5, child: Center(child: Image.memory(bytes, gaplessPlayback: true)));
                      },
                    ),
        ),
        if (total != null && total > 1)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 10),
            child: Text('Página ${page + 1} de $total', style: TextStyle(color: c.fg, fontSize: 13)),
          ),
      ]),
    );
  }
}
