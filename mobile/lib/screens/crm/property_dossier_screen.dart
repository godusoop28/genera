import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:share_plus/share_plus.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../api/models.dart';
import '../../config.dart';
import '../../labels.dart';
import '../../ui/shell.dart';
import '../../ui/widgets.dart';
import 'crm_ui.dart';

/// Ficha de una propiedad de 21Online, como su pantalla "Ver propiedad" (y como
/// frontend/src/app/propiedades/21online/[online21Id]): datos generales, portales con su liga,
/// clientes relacionados, seguimientos, resumen con gráficas y la ficha técnica en PDF.
class Online21PropertyScreen extends StatefulWidget {
  const Online21PropertyScreen({super.key, required this.online21Id});
  final String online21Id;

  @override
  State<Online21PropertyScreen> createState() => _Online21PropertyScreenState();
}

class _Online21PropertyScreenState extends State<Online21PropertyScreen> with SingleTickerProviderStateMixin {
  late final tabs = TabController(length: 5, vsync: this);
  PropertyDetails? details;
  String? generaId;
  String? error;
  List<PortalListing>? portals;
  ({bool contactsVisible, List<RelatedClient> clients})? clients;
  List<FollowUp>? followUps;
  String? tabError;
  bool sharing = false;

  @override
  void initState() {
    super.initState();
    load();
    loadTabs();
  }

  Future<void> load() async {
    try {
      final result = await api.getPropertyDetails(widget.online21Id);
      if (mounted) {
        setState(() {
          details = result;
          error = null;
        });
      }
      final item = await api.getInventoryItem(widget.online21Id);
      if (mounted) setState(() => generaId = item.generaId);
    } catch (err) {
      if (mounted && details == null) setState(() => error = crmErrorText(err));
    }
  }

  Future<void> loadTabs() async {
    try {
      final results = await Future.wait([
        api.getPropertyPortals(widget.online21Id),
        api.getPropertyClients(widget.online21Id),
        api.getPropertyFollowUps(widget.online21Id),
      ]);
      if (!mounted) return;
      setState(() {
        portals = results[0] as List<PortalListing>;
        clients = results[1] as ({bool contactsVisible, List<RelatedClient> clients});
        followUps = results[2] as List<FollowUp>;
        tabError = null;
      });
    } catch (err) {
      if (mounted) setState(() => tabError = crmErrorText(err));
    }
  }

  Future<void> refresh() async {
    await Future.wait([load(), loadTabs()]);
  }

  /// Descarga la ficha técnica y abre el menú para compartirla o guardarla.
  Future<void> shareSheet() async {
    final variant = await showAppSheet<String>(
      context,
      title: 'Descargar ficha técnica',
      builder: (ctx, _) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        for (final v in const [
          (id: 'ADVISOR', label: 'Con datos del asesor', description: 'Para el cliente: incluye teléfono y correo del asesor.'),
          (id: 'NO_CONTACT', label: 'Sin datos de contacto', description: 'Para compartir con otras inmobiliarias.'),
          (id: 'NO_LOGO', label: 'Sin logo', description: 'Neutra: sin logotipo ni datos de contacto.'),
        ]) ...[
          ChoiceTile(selected: false, onTap: () => Navigator.of(ctx).pop(v.id), title: v.label, subtitle: v.description, icon: Icons.picture_as_pdf_outlined),
          const SizedBox(height: 8),
        ],
      ]),
    );
    if (variant == null || !mounted) return;
    setState(() => sharing = true);
    try {
      final bytes = await api.getPropertySheet(widget.online21Id, variant);
      final suffix = switch (variant) { 'NO_CONTACT' => '-sin-contacto', 'NO_LOGO' => '-sin-logo', _ => '' };
      final name = 'ficha-${widget.online21Id}$suffix.pdf';
      await SharePlus.instance.share(ShareParams(files: [XFile.fromData(Uint8List.fromList(bytes), name: name, mimeType: 'application/pdf')], fileNameOverrides: [name]));
    } catch (err) {
      if (mounted) showToast(context, errorText(err, 'No se pudo generar la ficha técnica.'));
    } finally {
      if (mounted) setState(() => sharing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final d = details;
    if (d == null) {
      return AppScaffold(
        title: 'Propiedad',
        body: PageBody(children: [
          if (error != null) LoadError(message: error!, onRetry: load) else const StateMessage(kind: StateKind.loading, title: 'Consultando 21Online…'),
        ]),
      );
    }
    return AppScaffold(
      title: d.listing.title,
      bottom: TabBar(
        controller: tabs,
        isScrollable: true,
        tabs: const [Tab(text: 'Datos'), Tab(text: 'Portales'), Tab(text: 'Clientes'), Tab(text: 'Seguimientos'), Tab(text: 'Resumen')],
      ),
      body: TabBarView(controller: tabs, children: [_general(d), _portals(d), _clients(), _followUps(), _summary(d)]),
    );
  }

  Widget _tabState(Widget Function() ready, bool loaded) {
    if (!loaded && tabError != null) return PageBody(children: [LoadError(message: tabError!, onRetry: loadTabs)]);
    if (!loaded) return const PageBody(children: [StateMessage(kind: StateKind.loading, title: 'Consultando 21Online…')]);
    return ready();
  }

  // --- Datos generales ----------------------------------------------------------

  Widget _general(PropertyDetails d) {
    final c = context.c;
    final l = d.listing;
    final photos = d.photos.isNotEmpty ? d.photos : [?l.photoUrl];
    final sections = <String, List<DossierField>>{};
    for (final f in d.fields) {
      sections.putIfAbsent(f.section, () => []).add(f);
    }
    return PageBody(
      onRefresh: refresh,
      actions: [
        Btn(sharing ? 'Generando…' : 'Ficha técnica', icon: Icons.picture_as_pdf_outlined, small: true, loading: sharing, onPressed: shareSheet),
        if (d.publicUrl != null)
          Btn('Compartir', icon: Icons.share_outlined, small: true, variant: BtnVariant.secondary,
              onPressed: () => SharePlus.instance.share(ShareParams(text: '${l.title} · ${listingPrice(l.operation, l.salePrice, l.rentPrice, l.currency)}\n${d.publicUrl}'))),
        if (generaId != null) Btn('Editar en Genera', icon: Icons.edit_outlined, small: true, variant: BtnVariant.secondary, onPressed: () => context.push('/propiedades/$generaId')),
        Btn('Abrir en 21Online', icon: Icons.open_in_new, small: true, variant: BtnVariant.secondary,
            onPressed: () => openUrl(context, '${AppConfig.online21BaseUrl}/propiedades/ver/${l.id}')),
      ],
      children: [
        if (photos.isEmpty)
          Container(height: 200, decoration: BoxDecoration(color: c.appBg, borderRadius: BorderRadius.circular(14)), alignment: Alignment.center, child: Icon(Icons.hide_image_outlined, color: c.muted))
        else
          SizedBox(
            height: 220,
            child: PageView.builder(
              itemCount: photos.length,
              itemBuilder: (_, i) => Padding(
                padding: const EdgeInsets.symmetric(horizontal: 2),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(14),
                  child: Image.network(photos[i], fit: BoxFit.cover, errorBuilder: (_, _, _) => Container(color: c.appBg)),
                ),
              ),
            ),
          ),
        if (photos.length > 1) ...[const SizedBox(height: 6), MutedText('${photos.length} fotos · desliza para verlas', size: 12, center: true)],
        const SizedBox(height: 16),
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            CardHeader(
              title: listingPrice(l.operation, l.salePrice, l.rentPrice, l.currency),
              description: '${l.subtypeLabel} en ${l.operation == 'renta' ? 'renta' : 'venta'} · ${l.key}',
              action: _StatusBadge(l.status),
            ),
            Text(l.description ?? 'Sin descripción.', style: TextStyle(fontSize: 14, color: l.description == null ? c.muted : c.fg, height: 1.45)),
          ]),
        ),
        const SizedBox(height: 16),
        AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const CardHeader(title: 'Asesor'),
            if (d.advisor?.name != null) ...[
              Text(d.advisor!.name!, style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
              if (d.advisor!.mobile != null) LinkText('Cel. ${d.advisor!.mobile}', icon: Icons.phone_outlined, onTap: () => openUrl(context, 'tel:${d.advisor!.mobile}')),
              if (d.advisor!.email != null) LinkText(d.advisor!.email!, icon: Icons.mail_outline, onTap: () => openUrl(context, 'mailto:${d.advisor!.email}')),
            ] else
              const MutedText('Sin asesor asignado en 21Online.'),
            const SizedBox(height: 10),
            FieldView('En Genera', null, child: AdvisorChip(advisor: d.generaAdvisor, emptyLabel: l.advisorOnline21Id != null ? 'Usuario ${l.advisorOnline21Id} sin vincular' : 'Sin asesor')),
            if (d.latitude != null && d.longitude != null) ...[
              const SizedBox(height: 10),
              LinkText('Ver en el mapa', icon: Icons.place_outlined, onTap: () => openUrl(context, 'https://www.google.com/maps?q=${d.latitude},${d.longitude}')),
            ],
            if (d.videoUrl != null) LinkText('Video', icon: Icons.play_circle_outline, onTap: () => openUrl(context, d.videoUrl!)),
          ]),
        ),
        for (final entry in sections.entries) ...[
          const SizedBox(height: 16),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              CardHeader(title: entry.key),
              FieldGrid(children: [for (final f in entry.value) FieldView(f.label, f.value)]),
            ]),
          ),
        ],
        if (d.floorPlans.isNotEmpty) ...[
          const SizedBox(height: 16),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const CardHeader(title: 'Planos'),
              for (final url in d.floorPlans)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: InkWell(
                    onTap: () => openUrl(context, url),
                    child: ClipRRect(borderRadius: BorderRadius.circular(10), child: Image.network(url, height: 160, width: double.infinity, fit: BoxFit.cover)),
                  ),
                ),
            ]),
          ),
        ],
      ],
    );
  }

  // --- Portales ------------------------------------------------------------------

  Widget _portals(PropertyDetails d) => _tabState(() {
        final c = context.c;
        final list = portals!;
        return PageBody(onRefresh: refresh, children: [
          if (d.publicUrl != null) ...[
            AppCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Sitio de CENTURY 21', style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
                LinkText('Ver publicación', trailingIcon: Icons.open_in_new, onTap: () => openUrl(context, d.publicUrl!)),
              ]),
            ),
            const SizedBox(height: 12),
          ],
          if (list.isEmpty)
            const StateMessage(kind: StateKind.empty, title: 'Sin portales asignados', description: 'En 21Online esta propiedad no tiene portales asignados.')
          else
            ...spaced(list.map((p) => AppCard(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [
                      Expanded(child: Text(p.portal, style: TextStyle(fontWeight: FontWeight.w600, color: c.fg))),
                      AppBadge(p.withError ? 'Con error' : (p.active ? 'Activo' : 'Inactivo'), tone: p.withError ? Tone.danger : (p.active ? Tone.success : Tone.neutral)),
                    ]),
                    if (p.status != null) MutedText(p.status!, size: 12.5),
                    if (p.reference != null) MutedText(p.reference!, size: 12.5),
                    if (p.withError && p.error != null) ...[const SizedBox(height: 6), Notice(tone: Tone.danger, text: p.error)],
                    const SizedBox(height: 6),
                    if (p.url != null)
                      LinkText('Ver en ${p.portal}', trailingIcon: Icons.open_in_new, onTap: () => openUrl(context, p.url!))
                    else
                      const MutedText('Sin liga en 21Online', size: 12.5),
                  ]),
                )), 10),
          const SizedBox(height: 12),
          const MutedText('La liga aparece cuando 21Online la tiene registrada. Si un portal no la muestra, ábrelo desde 21Online → Ver portales.', size: 12.5),
        ]);
      }, portals != null);

  // --- Clientes relacionados -------------------------------------------------------

  Widget _clients() => _tabState(() {
        final c = context.c;
        final data = clients!;
        return PageBody(onRefresh: refresh, children: [
          if (!data.contactsVisible) ...[
            const Notice(tone: Tone.info, text: 'Los datos de contacto solo los ve el asesor de la propiedad o quien ve todos los leads.'),
            const SizedBox(height: 12),
          ],
          if (data.clients.isEmpty)
            const StateMessage(kind: StateKind.empty, title: 'Sin clientes relacionados', description: 'Nadie ha mostrado interés en esta propiedad en 21Online.')
          else
            ...spaced(data.clients.map((client) => AppCard(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(data.contactsVisible ? (client.name ?? 'Sin nombre') : 'Cliente #${client.id ?? ''}', style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
                    if (client.phone != null) LinkText(client.phone!, icon: Icons.phone_outlined, onTap: () => openUrl(context, 'tel:${client.phone}')),
                    if (client.email != null) MutedText(client.email!, size: 12.5),
                    const SizedBox(height: 6),
                    Wrap(spacing: 6, runSpacing: 6, children: [
                      if (client.source != null) AppBadge(client.source!, tone: Tone.gold),
                      if (client.status != null) AppBadge(client.status!),
                    ]),
                    if (client.message != null) ...[const SizedBox(height: 6), MutedText('“${client.message}”', size: 12.5)],
                    if (client.createdAt != null) ...[const SizedBox(height: 4), MutedText(formatDateTime(client.createdAt), size: 12)],
                  ]),
                )), 10),
        ]);
      }, clients != null);

  // --- Seguimientos -------------------------------------------------------------------

  Widget _followUps() => _tabState(() {
        final c = context.c;
        final list = followUps!;
        return PageBody(onRefresh: refresh, children: [
          if (list.isEmpty)
            const StateMessage(kind: StateKind.empty, title: 'Sin seguimientos', description: 'En 21Online no hay seguimientos registrados para esta propiedad.')
          else
            ...spaced(list.map((f) => AppCard(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('${_capitalize(f.type) ?? 'Seguimiento'}${f.subtype != null ? ' · ${f.subtype}' : ''}', style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
                    if (f.client != null) MutedText('Cliente: ${f.client}', size: 13),
                    if (f.advisor != null) MutedText('Asesor: ${f.advisor}', size: 13),
                    if (f.createdAt != null) MutedText(formatDateTime(f.createdAt), size: 12),
                  ]),
                )), 10),
        ]);
      }, followUps != null);

  // --- Resumen ------------------------------------------------------------------------

  Widget _summary(PropertyDetails d) => _tabState(() {
        final list = clients!.clients;
        final now = DateTime.now();
        final last30 = list.where((x) => x.createdAt != null && now.difference(DateTime.parse(x.createdAt!)).inDays <= 30).length;
        final visits = followUps!.where((f) => RegExp('visita|cita', caseSensitive: false).hasMatch('${f.type ?? ''} ${f.subtype ?? ''}')).length;
        final activePortals = portals!.where((p) => p.active && !p.withError).length;
        return PageBody(onRefresh: refresh, children: [
          StatGrid(children: [
            StatCard(icon: Icons.group_outlined, value: '${list.length}', label: 'Clientes interesados'),
            StatCard(icon: Icons.person_add_alt, value: '$last30', label: 'Últimos 30 días'),
            StatCard(icon: Icons.place_outlined, value: '$visits', label: 'Visitas y citas', hint: '${followUps!.length} seguimientos'),
            StatCard(icon: Icons.public, value: '$activePortals', label: 'Portales activos', hint: '${portals!.length} asignados'),
          ]),
          const SizedBox(height: 16),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const CardHeader(title: 'Clientes interesados por mes', description: 'Últimos 12 meses.'),
              _MonthColumns(dates: list.map((x) => x.createdAt).toList()),
            ]),
          ),
          const SizedBox(height: 16),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const CardHeader(title: '¿Por dónde llegaron?', description: 'Medio de origen de cada cliente.'),
              _Bars(values: list.map((x) => x.source ?? 'Sin medio').toList(), empty: 'Sin clientes todavía.'),
            ]),
          ),
          const SizedBox(height: 16),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const CardHeader(title: 'Seguimientos por tipo'),
              _Bars(values: followUps!.map((f) => _capitalize(f.type) ?? 'Sin tipo').toList(), empty: 'Sin seguimientos todavía.'),
            ]),
          ),
          const SizedBox(height: 12),
          const MutedText('Las vistas de cada anuncio dentro de los portales no las comparte la API de 21Online; para eso abre el resumen en 21Online.', size: 12.5),
        ]);
      }, clients != null && followUps != null && portals != null);
}

String? _capitalize(String? s) => s == null || s.isEmpty ? null : '${s[0].toUpperCase()}${s.substring(1).replaceAll('_', ' ')}';

class _StatusBadge extends StatelessWidget {
  const _StatusBadge(this.status);
  final String? status;

  @override
  Widget build(BuildContext context) => AppBadge(label(listingStatusLabels, status), tone: Tone.gold);
}

/// Columnas de una sola serie por mes (últimos 12): toca una columna para ver su valor.
class _MonthColumns extends StatefulWidget {
  const _MonthColumns({required this.dates});
  final List<String?> dates;

  @override
  State<_MonthColumns> createState() => _MonthColumnsState();
}

class _MonthColumnsState extends State<_MonthColumns> {
  int? active;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final now = DateTime.now();
    final months = [for (var i = 11; i >= 0; i--) DateTime(now.year, now.month - i)];
    final counts = List<int>.filled(12, 0);
    for (final iso in widget.dates) {
      final d = iso == null ? null : DateTime.tryParse(iso)?.toLocal();
      if (d == null) continue;
      final index = months.indexWhere((m) => m.year == d.year && m.month == d.month);
      if (index >= 0) counts[index]++;
    }
    final max = math.max(1, counts.reduce(math.max));
    final format = DateFormat('MMM', 'es_MX');
    const height = 120.0;
    final selected = active;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      SizedBox(
        height: 18,
        child: selected == null
            ? const MutedText('Toca una columna para ver su valor', size: 12)
            : Text('${DateFormat('MMMM y', 'es_MX').format(months[selected])}: ${counts[selected]} cliente${counts[selected] == 1 ? '' : 's'}',
                style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w600, color: c.fg)),
      ),
      const SizedBox(height: 6),
      SizedBox(
        height: height,
        child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
          for (var i = 0; i < 12; i++)
            Expanded(
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => setState(() => active = active == i ? null : i),
                child: Semantics(
                  label: '${DateFormat('MMMM', 'es_MX').format(months[i])}: ${counts[i]} clientes',
                  child: Align(
                    alignment: Alignment.bottomCenter,
                    child: Container(
                      width: 18,
                      height: counts[i] == 0 ? 0 : math.max(2, counts[i] / max * height),
                      margin: const EdgeInsets.symmetric(horizontal: 1),
                      decoration: BoxDecoration(
                        color: Brand.gold.withValues(alpha: selected == null || selected == i ? 1 : 0.5),
                        borderRadius: const BorderRadius.vertical(top: Radius.circular(4)),
                      ),
                    ),
                  ),
                ),
              ),
            ),
        ]),
      ),
      Divider(height: 1, color: c.border),
      const SizedBox(height: 4),
      Row(children: [
        for (var i = 0; i < 12; i++)
          Expanded(child: Text(i.isEven ? format.format(months[i]) : '', textAlign: TextAlign.center, style: TextStyle(fontSize: 10, color: c.muted))),
      ]),
    ]);
  }
}

/// Barras horizontales de una sola serie, de mayor a menor, con el valor en la punta.
class _Bars extends StatelessWidget {
  const _Bars({required this.values, required this.empty});
  final List<String> values;
  final String empty;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    if (values.isEmpty) return Padding(padding: const EdgeInsets.symmetric(vertical: 12), child: MutedText(empty, center: true));
    final counts = <String, int>{};
    for (final v in values) {
      counts[v] = (counts[v] ?? 0) + 1;
    }
    final sorted = counts.entries.toList()..sort((a, b) => b.value.compareTo(a.value));
    final max = sorted.first.value;
    return Column(children: [
      for (final e in sorted)
        Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: Row(children: [
            SizedBox(width: 110, child: Text(e.key, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 13, color: c.fg))),
            const SizedBox(width: 8),
            Expanded(
              child: LayoutBuilder(
                builder: (_, box) => Row(children: [
                  Container(
                    width: math.max(3, (box.maxWidth - 32) * e.value / max),
                    height: 14,
                    decoration: const BoxDecoration(color: Brand.gold, borderRadius: BorderRadius.horizontal(right: Radius.circular(4))),
                  ),
                  const SizedBox(width: 6),
                  Text('${e.value}', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: c.fg)),
                ]),
              ),
            ),
          ]),
        ),
    ]);
  }
}
