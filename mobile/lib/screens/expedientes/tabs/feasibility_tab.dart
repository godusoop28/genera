import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../../../api/api.dart' as api;
import '../../../api/client.dart';
import '../../../api/models.dart';
import '../../../labels.dart';
import '../../../ui/file_pick.dart';
import '../../../ui/widgets.dart';
import '../expediente_detail_screen.dart';

const _maxPhotos = 10;

const _classification = {
  'ALINEADO': ('Precio alineado al mercado', Tone.success),
  'LIMITE_SUPERIOR': ('En el límite superior', Tone.warning),
  'FUERA_DE_RANGO': ('Fuera del rango competitivo', Tone.danger),
  'SIN_DATOS': ('Sin evidencia suficiente', Tone.neutral),
};

const _groups = {'ARRIBA': 'Por arriba', 'SIMILAR': 'Similar', 'DEBAJO': 'Por debajo'};

/// Factibilidad comercial con IA (equivale a FeasibilityTab.tsx de la web): con los documentos que
/// envió el cliente genera el reporte en Word y la presentación para el propietario en PowerPoint.
class FeasibilityTab extends StatefulWidget {
  const FeasibilityTab({super.key, required this.ctx});
  final ExpedienteCtx ctx;

  @override
  State<FeasibilityTab> createState() => _FeasibilityTabState();
}

class _FeasibilityTabState extends State<FeasibilityTab> {
  bool available = true;
  List<FeasibilityReport>? reports;
  late String operation = widget.ctx.expediente.operationType == 'RENT' ? 'RENT' : 'SALE';
  String authorizedPrice = '';
  final price = TextEditingController();
  String notes = '';
  List<UploadFile> photos = [];
  bool sending = false;
  int? progress;
  Timer? poll;

  String get id => widget.ctx.expediente.id;
  bool get running => (reports ?? const []).any((r) => r.inProgress);

  @override
  void initState() {
    super.initState();
    load();
    api.getContractData(id).then((c) {
      final p = num.tryParse(c.price) ?? 0;
      if (p <= 0 || !mounted) return;
      authorizedPrice = p.toStringAsFixed(0);
      // El precio autorizado del contrato es de venta: no aplica a una renta mensual.
      if (operation == 'SALE' && price.text.isEmpty) setState(() => price.text = authorizedPrice);
    }).catchError((_) => null);
  }

  @override
  void dispose() {
    poll?.cancel();
    price.dispose();
    super.dispose();
  }

  Future<void> load() async {
    try {
      final r = await api.listFeasibility(id);
      if (!mounted) return;
      setState(() {
        available = r.available;
        reports = r.reports;
      });
      poll?.cancel();
      if (running) poll = Timer(const Duration(seconds: 15), load);
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  Future<void> addPhotos() async {
    final files = await pickUploadFiles(context);
    if (files == null) return;
    final images = files.where((f) => f.contentType.startsWith('image/')).toList();
    if (images.length < files.length && mounted) showToast(context, 'Solo se aceptan fotografías JPG, PNG o WEBP.');
    setState(() => photos = [...photos, ...images].take(_maxPhotos).toList());
  }

  Future<void> submit() async {
    setState(() {
      sending = true;
      progress = null;
    });
    try {
      await api.requestFeasibility(id,
          operation: operation,
          askingPrice: price.text.replaceAll(RegExp(r'[^0-9.]'), ''), notes: notes, photos: photos, onProgress: (p) => mounted ? setState(() => progress = p) : null);
      setState(() => photos = []);
      if (mounted) showToast(context, 'Factibilidad en proceso. La IA está analizando los documentos e investigando el mercado; tarda varios minutos.');
      await load();
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  Future<void> download(String reportId, String file) async {
    try {
      final url = await api.getFeasibilityDownload(reportId, file);
      if (mounted) await openUrl(context, url);
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  @override
  Widget build(BuildContext context) {
    final list = reports;
    return TabBody(
      onRefresh: load,
      children: [
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const CardHeader(
              title: 'Factibilidad comercial con IA',
              description:
                  'Con los documentos que envió el cliente, los datos del expediente y una investigación de mercado en portales, la IA elabora el reporte de factibilidad (Word, 27 secciones) y la presentación para el propietario (PowerPoint editable, 12 láminas).',
            ),
            if (!available)
              const Padding(
                padding: EdgeInsets.only(bottom: 12),
                child: Notice(tone: Tone.warning, text: 'La inteligencia artificial no está configurada en el servidor; por ahora no se puede generar la factibilidad.'),
              ),
            AppSelect<String>(
              label: '¿Para qué operación es la factibilidad?',
              value: operation,
              options: const [(value: 'SALE', label: 'Venta'), (value: 'RENT', label: 'Renta')],
              onChanged: (v) {
                if (v == operation) return;
                // Cambiar de operación cambia el sentido del precio (total de venta vs. renta mensual).
                setState(() {
                  operation = v;
                  price.text = v == 'SALE' ? authorizedPrice : '';
                });
              },
            ),
            const SizedBox(height: 10),
            AppTextField(
              label: operation == 'RENT' ? 'Renta mensual solicitada (MXN)' : 'Precio de venta solicitado (MXN)',
              controller: price,
              keyboardType: TextInputType.number,
              hint: operation == 'RENT' ? 'Ej. 25000' : 'Ej. 6900000',
              helper: operation == 'RENT' ? 'Renta por mes. Si no la sabes, la IA propone una.' : 'Se toma del precio autorizado si ya se capturó.',
            ),
            const SizedBox(height: 10),
            AppTextField(
              label: 'Notas del asesor (opcional)',
              maxLines: 5,
              maxLength: 4000,
              hint: 'Estado y remodelaciones, amenidades, motivo de venta, urgencia, si ya estuvo publicada…',
              onChanged: (v) => notes = v,
            ),
            const SizedBox(height: 10),
            MutedText('Fotografías del inmueble (opcional, hasta $_maxPhotos). La primera es la portada de la presentación.', size: 12),
            const SizedBox(height: 6),
            if (photos.isNotEmpty)
              Wrap(spacing: 6, runSpacing: 6, children: [
                for (final (i, p) in photos.indexed)
                  Stack(children: [
                    ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: Image.memory(_bytes(p.bytes), width: 84, height: 62, fit: BoxFit.cover),
                    ),
                    if (i == 0) const Positioned(left: 3, top: 3, child: AppBadge('Portada', tone: Tone.obsessed)),
                    Positioned(
                      right: 0,
                      top: 0,
                      child: InkWell(
                        onTap: () => setState(() => photos = [...photos]..removeAt(i)),
                        child: const CircleAvatar(radius: 10, backgroundColor: Colors.white, child: Icon(Icons.close, size: 13, color: Colors.black87)),
                      ),
                    ),
                  ]),
              ]),
            const SizedBox(height: 6),
            Align(
              alignment: Alignment.centerLeft,
              child: Btn('Agregar fotos', icon: Icons.add_photo_alternate_outlined, small: true, variant: BtnVariant.secondary, onPressed: photos.length >= _maxPhotos ? null : addPhotos),
            ),
            const SizedBox(height: 14),
            Btn(
              sending ? (progress != null && progress! < 100 ? 'Subiendo fotos $progress%' : 'Enviando…') : 'Generar factibilidad con IA',
              icon: Icons.auto_awesome_outlined,
              loading: sending,
              expand: true,
              onPressed: !available || sending || running ? null : submit,
            ),
            if (running) ...[const SizedBox(height: 6), const MutedText('Hay una factibilidad en proceso; espera a que termine para generar otra.', size: 12)],
            const SizedBox(height: 8),
            const MutedText('Estimación comercial orientativa elaborada con IA: no sustituye un avalúo. Revisa el reporte antes de presentarlo al propietario.', size: 11.5),
          ]),
        ),
        if (list == null)
          const MutedText('Cargando…')
        else if (list.isEmpty)
          const MutedText('Todavía no se ha generado ninguna factibilidad para este expediente.')
        else
          for (final r in list) _ReportCard(report: r, onDownload: download),
      ],
    );
  }
}

/// Image.memory necesita `Uint8List`; UploadFile guarda `List<int>`.
Uint8List _bytes(List<int> bytes) => bytes is Uint8List ? bytes : Uint8List.fromList(bytes);

class _ReportCard extends StatelessWidget {
  const _ReportCard({required this.report, required this.onDownload});
  final FeasibilityReport report;
  final Future<void> Function(String id, String file) onDownload;

  @override
  Widget build(BuildContext context) {
    final r = report;
    final s = r.summary;
    final cls = _classification[s?.classification];
    final c = context.c;
    final rent = r.operation == 'RENT';
    String money(int? v) => v == null ? '—' : (rent ? '${formatMoney(v)}/mes' : formatMoney(v));
    return AppCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Wrap(spacing: 6, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
          Text('Factibilidad v${r.versionNumber}', style: TextStyle(color: c.fg, fontWeight: FontWeight.w600)),
          AppBadge(rent ? 'Renta' : 'Venta', tone: Tone.obsessed),
          switch (r.status) {
            'READY' => const AppBadge('Lista', tone: Tone.success),
            'FAILED' => const AppBadge('Falló', tone: Tone.danger),
            _ => const AppBadge('En proceso', tone: Tone.info),
          },
          if (cls != null) AppBadge(cls.$1, tone: cls.$2),
        ]),
        const SizedBox(height: 4),
        MutedText('${formatDateTime(r.requestedAt)}${r.requestedByName != null ? ' · ${r.requestedByName}' : ''}', size: 12),
        const SizedBox(height: 10),
        if (r.inProgress)
          Notice(
            tone: Tone.info,
            text: r.status == 'ANALYZING'
                ? 'Etapa 1 de 2: analizando los documentos e investigando el mercado. Suele tardar entre 5 y 20 minutos; puedes salir de esta pantalla.'
                : 'Etapa 2 de 2: preparando la presentación para el propietario (comparables, mapa y precio recomendado).',
          ),
        if (r.status == 'FAILED') Notice(tone: Tone.danger, text: '${r.error ?? 'No se pudo completar la factibilidad.'} Puedes volver a generarla.'),
        if (r.status == 'READY') ...[
          Btn('Reporte de factibilidad (Word)', icon: Icons.download_outlined, variant: BtnVariant.dark, expand: true, onPressed: r.hasDocx ? () => onDownload(r.id, 'docx') : null),
          const SizedBox(height: 8),
          Btn('Presentación (PowerPoint)', icon: Icons.slideshow_outlined, expand: true, onPressed: r.hasPptx ? () => onDownload(r.id, 'pptx') : null),
          if (s != null) ...[
            const SizedBox(height: 12),
            FieldGrid(children: [
              FieldView('Expectativa del propietario', money(s.ownerPrice)),
              FieldView(rent ? 'Rango de renta sugerido' : 'Rango sugerido', s.rangeMin != null && s.rangeMax != null ? '${formatMoney(s.rangeMin)} – ${money(s.rangeMax)}' : '—'),
              FieldView(rent ? 'Renta mensual recomendada' : 'Precio recomendado de salida', money(s.recommendedPrice)),
              FieldView('Comparables', '${s.comparables.length}'),
            ]),
            if (s.executiveSummary != null) ...[const SizedBox(height: 10), Text(s.executiveSummary!, style: TextStyle(color: c.fg, fontSize: 13.5))],
            if (s.comparables.isNotEmpty) ...[
              const SizedBox(height: 10),
              Expandable(
                title: 'Comparables y sus enlaces (${s.comparables.length})',
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  for (final comp in s.comparables)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 6),
                      child: Wrap(spacing: 6, crossAxisAlignment: WrapCrossAlignment.center, children: [
                        Text(comp.id, style: TextStyle(color: c.fg, fontWeight: FontWeight.w600, fontSize: 13)),
                        MutedText(_groups[comp.group] ?? comp.group, size: 12),
                        Text('${comp.zone ?? '—'} · ${money(comp.price)}', style: TextStyle(color: c.fg, fontSize: 13)),
                        if (comp.url != null)
                          LinkText(comp.urlVerified ? 'Ver publicación' : 'Ver (no verificada)', icon: Icons.open_in_new, onTap: () => openUrl(context, comp.url!))
                        else
                          const MutedText('Sin liga', size: 12),
                      ]),
                    ),
                ]),
              ),
            ],
            if (s.pendingItems.isNotEmpty) ...[
              const SizedBox(height: 8),
              Expandable(title: 'Información pendiente o no verificable (${s.pendingItems.length})', child: Bullets(s.pendingItems)),
            ],
          ],
        ],
      ]),
    );
  }
}
