import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../api/models.dart';
import '../../config.dart';
import '../../labels.dart';
import '../../state/auth.dart';
import '../../ui/money_field.dart';
import '../../ui/property_type_picker.dart';
import '../../ui/shell.dart';
import '../../ui/widgets.dart';
import 'crm_ui.dart';

// Propiedades del CRM vinculadas con 21Online (frontend/src/app/propiedades).
// El inventario se consulta en vivo en 21Online; lo que se da de alta aquí se
// publica allá y su estado de envío se ve en "En Genera".

const _noKey =
    'La conexión con 21Online aún no está configurada (falta la llave ONLINE21_API_KEY en el servidor). Puedes capturar propiedades: quedan pendientes y se envían a 21Online en cuanto se configure.';

class PropertiesScreen extends StatefulWidget {
  const PropertiesScreen({super.key, this.initialTab});
  final String? initialTab;

  @override
  State<PropertiesScreen> createState() => _PropertiesScreenState();
}

class _PropertiesScreenState extends State<PropertiesScreen> {
  late String tab = widget.initialTab == 'capturadas' ? 'captured' : 'inventory';
  String query = '';
  String status = 'enPromocion';
  String operation = '';
  int page = 1;
  InventoryPage? inventory;
  String? inventoryError;
  CapturedOverview? captured;
  Timer? _debounce;
  int _request = 0;

  // CC#36: filtros de "En Genera"; se aplican en el servidor sobre todas las propiedades.
  String cQuery = '', cTag = '', cStatus = '', cOperation = '', minPrice = '', maxPrice = '', currency = 'MXN', sort = 'NEWEST';
  int cPage = 1;
  CapturedPage? capturedPage;
  String? capturedPageError;
  Timer? _capturedDebounce;
  int _capturedRequest = 0;
  bool importing = false;

  @override
  void initState() {
    super.initState();
    loadInventory();
    loadCaptured();
    loadCapturedPage();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _capturedDebounce?.cancel();
    super.dispose();
  }

  Future<void> loadInventory() async {
    final id = ++_request;
    try {
      final result = await api.listInventory(q: query, status: status, operation: operation, page: page);
      if (mounted && id == _request) {
        setState(() {
          inventory = result;
          inventoryError = null;
        });
      }
    } catch (err) {
      if (mounted && id == _request) setState(() => inventoryError = crmErrorText(err));
    }
  }

  // Solo para el conteo de la pestaña y los pendientes de envío; el listado sale de [loadCapturedPage].
  Future<void> loadCaptured() async {
    try {
      final result = await api.listCaptured();
      if (mounted) setState(() => captured = result);
    } catch (_) {}
  }

  /// Importe crudo sin el punto final que puede quedar mientras se escribe ("5000." → "5000").
  static String _amount(String raw) => raw.replaceAll(RegExp(r'\.$'), '');

  String? get priceError => minPrice.isNotEmpty && maxPrice.isNotEmpty && num.parse(_amount(minPrice)) > num.parse(_amount(maxPrice))
      ? 'El precio mínimo no puede ser mayor que el máximo.'
      : null;

  bool get usesPrice => minPrice.isNotEmpty || maxPrice.isNotEmpty || sort == 'PRICE_ASC' || sort == 'PRICE_DESC';

  Future<void> loadCapturedPage() async {
    final id = ++_capturedRequest;
    if (priceError != null) return;
    try {
      final result = await api.listCapturedPage(
        q: cQuery,
        tag: cTag.isEmpty ? null : cTag,
        status: cStatus.isEmpty ? null : cStatus,
        operation: cOperation.isEmpty ? null : cOperation,
        minPrice: minPrice.isEmpty ? null : _amount(minPrice),
        maxPrice: maxPrice.isEmpty ? null : _amount(maxPrice),
        currency: usesPrice ? currency : null,
        sort: sort,
        page: cPage,
        size: 20,
      );
      if (mounted && id == _capturedRequest) {
        setState(() {
          capturedPage = result;
          capturedPageError = null;
        });
      }
    } catch (err) {
      if (mounted && id == _capturedRequest) setState(() => capturedPageError = crmErrorText(err));
    }
  }

  /// Cambiar un filtro regresa a la primera página; texto y precios se aplican tras una pausa al escribir.
  void changeCaptured(VoidCallback patch, {bool immediate = true}) {
    setState(() {
      patch();
      cPage = 1;
    });
    _capturedDebounce?.cancel();
    if (immediate) {
      loadCapturedPage();
    } else {
      _capturedDebounce = Timer(const Duration(milliseconds: 400), loadCapturedPage);
    }
  }

  /// Trae ya todo el inventario de 21Online (el backend también lo hace solo cada pocos minutos).
  Future<void> importNow() async {
    setState(() => importing = true);
    try {
      final r = await api.importInventory();
      if (!mounted) return;
      final imported = (r['imported'] as num?)?.toInt() ?? 0, updated = (r['updated'] as num?)?.toInt() ?? 0, total = (r['total'] as num?)?.toInt() ?? 0;
      showToast(context, imported > 0 || updated > 0 ? '21Online: $imported nuevas y $updated actualizadas de $total.' : 'Todo al día: $total propiedades en 21Online.');
      loadCaptured();
      loadCapturedPage();
    } catch (err) {
      if (mounted) showToast(context, crmErrorText(err));
    } finally {
      if (mounted) setState(() => importing = false);
    }
  }

  Future<void> refresh() => Future.wait([loadInventory(), loadCaptured(), loadCapturedPage()]);

  void onQuery(String v) {
    query = v;
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 400), () {
      page = 1;
      loadInventory();
    });
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    if (!auth.can('PROPERTY_VIEW')) {
      return const Restricted(title: 'Propiedades', route: '/propiedades', description: 'Tu usuario no tiene permiso para consultar propiedades.');
    }
    final configured = captured?.configured ?? true;
    final pending = captured?.captured.where((p) => p.syncStatus != 'DONE').length ?? 0;
    return AppScaffold(
      title: 'Propiedades',
      route: '/propiedades',
      fab: auth.can('PROPERTY_MANAGE')
          ? FloatingActionButton.extended(
              backgroundColor: Brand.gold,
              foregroundColor: Brand.ink,
              onPressed: () async {
                await context.push('/propiedades/nueva');
                loadCaptured();
              },
              icon: const Icon(Icons.add),
              label: const Text('Nueva propiedad'),
            )
          : null,
      body: PageBody(
        onRefresh: refresh,
        bottomPadding: 96,
        subtitle: 'Inventario de la oficina en 21Online. Lo que des de alta aquí se publica también en 21Online.',
        children: [
          if (!configured) ...[const Notice(tone: Tone.warning, icon: Icons.warning_amber_outlined, text: _noKey), const SizedBox(height: 14)],
          FilterChips<String>(
            value: tab,
            options: [
              (id: 'inventory', label: 'Inventario 21Online', count: null),
              (id: 'captured', label: 'En Genera', count: captured?.captured.length),
            ],
            onChanged: (t) => setState(() => tab = t),
          ),
          if (pending > 0 && tab == 'inventory') ...[
            const SizedBox(height: 8),
            LinkText('${pending == 1 ? '1 propiedad capturada aún no está' : '$pending propiedades capturadas aún no están'} en 21Online. Revisar',
                onTap: () => setState(() => tab = 'captured')),
          ],
          const SizedBox(height: 14),
          if (tab == 'inventory') ..._inventory(configured) else ..._captured(auth.can('PROPERTY_MANAGE') && auth.can('LEAD_VIEW_ALL') && configured),
        ],
      ),
    );
  }

  List<Widget> _inventory(bool configured) {
    final data = inventory;
    if (!configured || (data != null && !data.configured)) {
      return const [
        StateMessage(kind: StateKind.empty, title: 'Sin conexión con 21Online', description: 'Cuando se configure la llave de 21Online aquí verás todo el inventario de la oficina.'),
      ];
    }
    return [
      // CC#36: la API de 21Online no filtra ni ordena por precio; el inventario completo con esos filtros está en "En Genera".
      Text.rich(
        TextSpan(style: TextStyle(fontSize: 13.5, color: context.c.muted), children: [
          const TextSpan(text: '¿Buscas por rango de precio u orden por precio o antigüedad? '),
          WidgetSpan(
            alignment: PlaceholderAlignment.baseline,
            baseline: TextBaseline.alphabetic,
            child: GestureDetector(
              onTap: () => setState(() => tab = 'captured'),
              child: Text('Usa la vista En Genera',
                  style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600, color: context.c.goldText, decoration: TextDecoration.underline, decorationColor: context.c.goldText)),
            ),
          ),
          const TextSpan(text: ': tiene todo el inventario de la oficina (se actualiza desde 21Online cada pocos minutos).'),
        ]),
      ),
      const SizedBox(height: 12),
      AppTextField(hint: 'Buscar por número de 21Online o colonia', onChanged: onQuery, value: query),
      const SizedBox(height: 10),
      AppSelect<String>(
        value: status,
        options: [
          (value: 'enPromocion', label: 'En promoción'),
          (value: '', label: 'Todos los estatus'),
          for (final s in ['suspendida', 'conIntencion', 'conContratoFirmado', 'cerrada', 'cancelada']) (value: s, label: listingStatusLabels[s]!),
        ],
        onChanged: (v) {
          setState(() {
            status = v;
            page = 1;
          });
          loadInventory();
        },
      ),
      const SizedBox(height: 10),
      AppSelect<String>(
        value: operation,
        options: optionsOf(listingOperationLabels, empty: 'Venta y renta'),
        onChanged: (v) {
          setState(() {
            operation = v;
            page = 1;
          });
          loadInventory();
        },
      ),
      const SizedBox(height: 14),
      if (inventoryError != null && data == null)
        LoadError(message: inventoryError!, onRetry: loadInventory)
      else if (data == null)
        const StateMessage(kind: StateKind.loading, title: 'Consultando 21Online…')
      else if (data.error != null)
        LoadError(message: data.error!, onRetry: loadInventory)
      else if (data.items.isEmpty)
        const StateMessage(kind: StateKind.noResults, title: 'Sin propiedades', description: 'Ninguna propiedad de 21Online coincide con estos filtros.')
      else ...[
        ...spaced(data.items.map((i) => _ListingCard(item: i)), 12),
        const SizedBox(height: 14),
        Row(children: [
          Expanded(child: MutedText('Página ${data.page}${data.totalItems != null ? ' · ${data.totalItems} propiedades' : ''}', size: 12.5)),
          IconButton(
            tooltip: 'Página anterior',
            onPressed: page <= 1
                ? null
                : () {
                    setState(() => page--);
                    loadInventory();
                  },
            icon: const Icon(Icons.chevron_left),
          ),
          IconButton(
            tooltip: 'Página siguiente',
            onPressed: !data.hasNext
                ? null
                : () {
                    setState(() => page++);
                    loadInventory();
                  },
            icon: const Icon(Icons.chevron_right),
          ),
        ]),
      ],
    ];
  }

  static const _sortOptions = [
    (value: 'NEWEST', label: 'Más recientes primero'),
    (value: 'OLDEST', label: 'Más antiguas primero'),
    (value: 'PRICE_ASC', label: 'Precio: de menor a mayor'),
    (value: 'PRICE_DESC', label: 'Precio: de mayor a menor'),
  ];

  /// CC#36: todo el inventario que hay en Genera (capturadas aquí e importadas de 21Online) con búsqueda,
  /// filtros de precio y un criterio de orden, aplicados en el servidor sobre el conjunto completo.
  List<Widget> _captured(bool canImport) {
    final data = capturedPage;
    final anyFilter = cQuery.trim().isNotEmpty || cTag.isNotEmpty || cStatus.isNotEmpty || cOperation.isNotEmpty || minPrice.isNotEmpty || maxPrice.isNotEmpty;
    return [
      if (canImport) ...[
        const MutedText('Todo el inventario de la oficina en 21Online se trae solo cada pocos minutos, con sus fotos y características.', size: 13),
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerLeft,
          child: Btn(importing ? 'Trayendo de 21Online…' : 'Traer de 21Online ahora',
              icon: Icons.refresh, variant: BtnVariant.secondary, small: true, loading: importing, onPressed: importing ? null : importNow),
        ),
        const SizedBox(height: 14),
      ],
      AppTextField(hint: 'Buscar por título, colonia, asesor, número o etiqueta', value: cQuery, onChanged: (v) => changeCaptured(() => cQuery = v, immediate: false)),
      const SizedBox(height: 10),
      AppSelect<String>(
        value: cStatus,
        options: [
          (value: '', label: 'Todos los estatus'),
          for (final s in ['enPromocion', 'suspendida', 'conIntencion', 'conContratoFirmado', 'cerrada', 'cancelada']) (value: s, label: listingStatusLabels[s]!),
        ],
        onChanged: (v) => changeCaptured(() => cStatus = v),
      ),
      const SizedBox(height: 10),
      AppSelect<String>(value: cOperation, options: optionsOf(listingOperationLabels, empty: 'Venta y renta'), onChanged: (v) => changeCaptured(() => cOperation = v)),
      if (data != null && data.tags.isNotEmpty) ...[
        const SizedBox(height: 10),
        AppSelect<String>(
          value: data.tags.contains(cTag) ? cTag : '',
          options: [(value: '', label: 'Todas las etiquetas'), for (final t in data.tags) (value: t, label: t)],
          onChanged: (v) => changeCaptured(() => cTag = v),
        ),
      ],
      const SizedBox(height: 12),
      Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Expanded(
          child: MoneyField(
            label: 'Precio mínimo',
            hint: 'Sin mínimo',
            currency: currency,
            value: minPrice,
            error: priceError,
            onChanged: (v) => changeCaptured(() => minPrice = v, immediate: false),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: MoneyField(label: 'Precio máximo', hint: 'Sin máximo', currency: currency, value: maxPrice, onChanged: (v) => changeCaptured(() => maxPrice = v, immediate: false)),
        ),
      ]),
      const SizedBox(height: 10),
      Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        SizedBox(
          width: 110,
          child: AppSelect<String>(
            label: 'Moneda',
            value: currency,
            options: const [(value: 'MXN', label: 'MXN'), (value: 'USD', label: 'USD')],
            onChanged: (v) => changeCaptured(() => currency = v),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(child: AppSelect<String>(label: 'Ordenar por', value: sort, options: _sortOptions, onChanged: (v) => changeCaptured(() => sort = v))),
      ]),
      if (usesPrice) ...[
        const SizedBox(height: 8),
        MutedText(
          'Los precios se comparan en $currency con el precio de la operación (venta o renta mensual); los límites incluyen el importe indicado. '
          'Las propiedades sin precio quedan fuera del rango y al final del orden por precio.',
          size: 12,
        ),
      ],
      const SizedBox(height: 14),
      if (priceError != null)
        StateMessage(kind: StateKind.noResults, title: 'Revisa el rango de precios', description: priceError)
      else if (capturedPageError != null && data == null)
        LoadError(message: capturedPageError!, onRetry: loadCapturedPage)
      else if (data == null)
        const StateMessage(kind: StateKind.loading, title: 'Cargando propiedades…')
      else if (data.totalItems == 0)
        anyFilter
            ? StateMessage(
                kind: StateKind.noResults,
                title: 'Sin resultados',
                description: 'Ninguna propiedad coincide con estos filtros.',
                action: Btn(
                  'Quitar filtros',
                  variant: BtnVariant.secondary,
                  onPressed: () => changeCaptured(() {
                    cQuery = '';
                    cTag = '';
                    cStatus = '';
                    cOperation = '';
                    minPrice = '';
                    maxPrice = '';
                  }),
                ),
              )
            : const StateMessage(
                kind: StateKind.empty,
                title: 'Aún no hay propiedades en Genera',
                description: 'Aquí aparecen las que des de alta en Genera y todo el inventario que se trae de 21Online.',
              )
      else ...[
        ...spaced(
          data.captured.map((p) => _CapturedCard(
                property: p,
                onTap: () async {
                  await context.push('/propiedades/${p.id}');
                  loadCaptured();
                  loadCapturedPage();
                },
              )),
          10,
        ),
        const SizedBox(height: 14),
        Row(children: [
          Expanded(
            child: MutedText(
              'Página ${data.page} de ${data.totalPages < 1 ? 1 : data.totalPages} · ${data.totalItems} ${data.totalItems == 1 ? 'propiedad' : 'propiedades'}',
              size: 12.5,
            ),
          ),
          IconButton(
            tooltip: 'Página anterior',
            onPressed: data.page <= 1
                ? null
                : () {
                    setState(() => cPage = data.page - 1);
                    loadCapturedPage();
                  },
            icon: const Icon(Icons.chevron_left),
          ),
          IconButton(
            tooltip: 'Página siguiente',
            onPressed: data.page >= data.totalPages
                ? null
                : () {
                    setState(() => cPage = data.page + 1);
                    loadCapturedPage();
                  },
            icon: const Icon(Icons.chevron_right),
          ),
        ]),
      ],
    ];
  }
}

class ListingStatusBadge extends StatelessWidget {
  const ListingStatusBadge(this.status, {super.key});
  final String? status;

  @override
  Widget build(BuildContext context) =>
      status == null ? const SizedBox.shrink() : AppBadge(label(listingStatusLabels, status), tone: listingStatusTones[status] ?? Tone.neutral);
}

class SyncBadge extends StatelessWidget {
  const SyncBadge(this.status, {super.key});
  final String status;

  @override
  Widget build(BuildContext context) => AppBadge(
        label(syncLabels, status),
        tone: syncTones[status] ?? Tone.neutral,
        icon: status == 'DONE' ? Icons.check_circle_outline : status == 'FAILED' ? Icons.warning_amber_outlined : Icons.schedule,
      );
}

/// Foto principal de 21Online (o un recuadro vacío si no tiene).
class _Photo extends StatelessWidget {
  const _Photo({required this.url});
  final String? url;
  static const height = 170.0;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final empty = Container(
      height: height,
      color: c.appBg,
      alignment: Alignment.center,
      child: Icon(Icons.hide_image_outlined, color: c.muted),
    );
    if (url == null) return empty;
    return Image.network(url!, height: height, width: double.infinity, fit: BoxFit.cover, errorBuilder: (_, _, _) => empty);
  }
}

class _ListingCard extends StatelessWidget {
  const _ListingCard({required this.item});
  final InventoryItem item;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final l = item.listing;
    final place = [l.neighborhood, l.municipality].whereType<String>().join(', ');
    final features = listingFeatures(bedrooms: l.bedrooms, bathrooms: l.bathrooms, builtM2: l.builtM2, landM2: l.landM2);
    return AppCard(
      padding: EdgeInsets.zero,
      onTap: () => context.push('/propiedades/21online/${l.id}'),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Stack(children: [
          _Photo(url: l.photoUrl),
          Positioned(
            left: 10,
            top: 10,
            child: Wrap(spacing: 6, children: [ListingStatusBadge(l.status), if (item.generaId != null) const AppBadge('Capturada en Genera', tone: Tone.gold)]),
          ),
        ]),
        Padding(
          padding: const EdgeInsets.all(14),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('${l.internalKey != null ? '${l.internalKey} · ' : ''}${l.id} · ${l.subtypeLabel} en ${l.operation == 'renta' ? 'renta' : 'venta'}',
                style: TextStyle(fontSize: 12, color: c.muted)),
            const SizedBox(height: 4),
            Text(l.title, style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15, color: c.fg), maxLines: 2, overflow: TextOverflow.ellipsis),
            const SizedBox(height: 4),
            Text(listingPrice(l.operation, l.salePrice, l.rentPrice, l.currency), style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600, color: c.goldText)),
            if (place.isNotEmpty) Text(place, style: TextStyle(fontSize: 13, color: c.muted), overflow: TextOverflow.ellipsis),
            if (features.isNotEmpty) ...[const SizedBox(height: 4), Text(features, style: TextStyle(fontSize: 12, color: c.muted))],
            const SizedBox(height: 10),
            AdvisorChip(advisor: item.advisor, emptyLabel: l.advisorOnline21Id != null ? 'Asesor sin vincular' : 'Sin asesor'),
          ]),
        ),
      ]),
    );
  }
}

class _CapturedCard extends StatelessWidget {
  const _CapturedCard({required this.property, required this.onTap});
  final CapturedProperty property;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final d = property.data;
    final photo = property.photos.isNotEmpty ? property.photos.first : null;
    final noPhoto = Container(width: 64, height: 48, color: c.appBg, alignment: Alignment.center, child: Icon(Icons.hide_image_outlined, size: 16, color: c.muted));
    return AppCard(
      onTap: onTap,
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: photo == null ? noPhoto : Image.network(photo, width: 64, height: 48, fit: BoxFit.cover, errorBuilder: (_, _, _) => noPhoto),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(d.headline, style: TextStyle(fontWeight: FontWeight.w600, fontSize: 15, color: c.fg), maxLines: 2, overflow: TextOverflow.ellipsis),
              Text(
                '${subtypeLabel(d.subtype)} · ${d.neighborhood}, ${d.municipality} · ${property.origin == 'ONLINE21' ? 'de 21Online' : 'capturada'} · alta ${timeAgo(property.listedAt ?? property.createdAt)}',
                style: TextStyle(fontSize: 12, color: c.muted),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ]),
          ),
        ]),
        if (property.tags.isNotEmpty) ...[
          const SizedBox(height: 6),
          Wrap(spacing: 4, runSpacing: 4, children: [for (final t in property.tags) AppBadge(t, tone: Tone.gold, icon: Icons.sell_outlined)]),
        ],
        const SizedBox(height: 6),
        Text(listingPrice(d.operation, d.salePrice, d.rentPrice, d.currency), style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: c.fg)),
        const SizedBox(height: 10),
        Wrap(spacing: 8, runSpacing: 6, crossAxisAlignment: WrapCrossAlignment.center, children: [
          SyncBadge(property.syncStatus),
          if (property.online21Id != null) MutedText('Núm. ${property.online21Id}', size: 12),
        ]),
        const SizedBox(height: 8),
        Row(children: [
          Expanded(child: AdvisorChip(advisor: property.advisor, emptyLabel: 'Sin asesor')),
          Text('Ver', style: TextStyle(color: c.goldText, fontWeight: FontWeight.w600)),
          Icon(Icons.arrow_forward, size: 16, color: c.goldText),
        ]),
      ]),
    );
  }
}

// ---------------------------------------------------------------------------
// Ficha de una propiedad de 21Online (solo consulta)
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Ficha de una propiedad capturada en Genera (estado de envío y edición)
// ---------------------------------------------------------------------------

class CapturedPropertyScreen extends StatefulWidget {
  const CapturedPropertyScreen({super.key, required this.id});
  final String id;

  @override
  State<CapturedPropertyScreen> createState() => _CapturedPropertyScreenState();
}

class _CapturedPropertyScreenState extends State<CapturedPropertyScreen> {
  CapturedProperty? data;
  String? error;
  bool busy = false;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    try {
      final result = await api.getCaptured(widget.id);
      if (mounted) {
        setState(() {
          data = result;
          error = null;
        });
      }
    } catch (err) {
      if (mounted) setState(() => error = crmErrorText(err));
    }
  }

  Future<void> retry() async {
    setState(() => busy = true);
    try {
      final updated = await api.retryPropertySync(widget.id);
      if (!mounted) return;
      setState(() => data = updated);
      showToast(context, updated.syncStatus == 'DONE' ? 'Propiedad enviada a 21Online' : '21Online aún no la aceptó');
    } catch (err) {
      if (mounted) showToast(context, crmErrorText(err));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = data;
    if (p == null) {
      return AppScaffold(
        title: 'Propiedad',
        body: PageBody(children: [
          if (error != null) LoadError(message: error!, onRetry: load) else const StateMessage(kind: StateKind.loading, title: 'Cargando propiedad…'),
        ]),
      );
    }
    final d = p.data;
    final address = [
      if (d.street != null) d.extNumber != null ? '${d.street} ${d.extNumber}' : d.street,
      d.neighborhood,
      d.municipality,
      d.state,
      d.postalCode,
    ].whereType<String>().join(', ');
    final features = listingFeatures(bedrooms: d.bedrooms, bathrooms: d.bathrooms, builtM2: d.builtM2, landM2: d.landM2);
    final c = context.c;
    return AppScaffold(
      title: d.headline,
      actions: [
        if (p.canEdit)
          IconButton(
            tooltip: 'Editar',
            icon: const Icon(Icons.edit_outlined),
            onPressed: () async {
              final saved = await Navigator.of(context).push<CapturedProperty>(MaterialPageRoute(builder: (_) => PropertyFormScreen(existing: p)));
              if (saved != null && mounted) setState(() => data = saved);
            },
          ),
      ],
      body: PageBody(
        onRefresh: load,
        children: [
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Wrap(spacing: 10, runSpacing: 6, crossAxisAlignment: WrapCrossAlignment.center, children: [
                SyncBadge(p.syncStatus),
                if (p.online21Id != null)
                  LinkText('Número ${p.online21Id} en 21Online',
                      trailingIcon: Icons.open_in_new, onTap: () => openUrl(context, '${AppConfig.online21BaseUrl}/propiedades/ver/${p.online21Id}')),
              ]),
              if (p.syncStatus == 'DONE' && p.syncedAt != null) ...[const SizedBox(height: 8), MutedText('Última sincronización: ${formatDateTime(p.syncedAt)}.')],
              if (p.syncError != null) ...[const SizedBox(height: 8), Text(p.syncError!, style: TextStyle(fontSize: 13.5, color: c.dangerText))],
              if (p.syncUncertain) ...[
                const SizedBox(height: 8),
                const Notice(
                  tone: Tone.warning,
                  icon: Icons.warning_amber_outlined,
                  text: 'Antes de reintentar, busca la propiedad en 21Online: pudo haberse guardado aunque no hubo respuesta, y reintentar la duplicaría.',
                ),
              ],
              if (p.advisor != null && !p.advisorLinked) ...[
                const SizedBox(height: 8),
                MutedText('${p.advisor!.name} no tiene vinculado su usuario de 21Online (CRM → Asesores), así que 21Online la registra a nombre de la llave de la API.'),
              ],
              if (p.canEdit && p.syncStatus != 'DONE') ...[
                const SizedBox(height: 12),
                Btn(busy ? 'Enviando…' : 'Reintentar envío', icon: Icons.refresh, variant: BtnVariant.secondary, loading: busy, onPressed: busy ? null : retry),
              ],
            ]),
          ),
          const SizedBox(height: 16),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              CardHeader(
                title: listingPrice(d.operation, d.salePrice, d.rentPrice, d.currency),
                description: '${p.propertyTypeLabel ?? '${subtypeLabel(d.subtype)} · ${label(listingTypeLabels, d.propertyType)}'} · ${label(listingOperationLabels, d.operation)}',
                action: ListingStatusBadge(d.status),
              ),
              if (features.isNotEmpty) MutedText(features),
              const SizedBox(height: 10),
              Text(d.description ?? 'Sin descripción.', style: TextStyle(fontSize: 14, color: d.description == null ? c.muted : c.fg, height: 1.45)),
            ]),
          ),
          const SizedBox(height: 16),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const CardHeader(title: 'Datos'),
              FieldView('Asesor', null, child: AdvisorChip(advisor: p.advisor, emptyLabel: 'Sin asesor')),
              const SizedBox(height: 14),
              FieldView('Ubicación', address),
              const SizedBox(height: 14),
              FieldGrid(children: [
                FieldView('En internet', '${d.publishOnline ? 'Sí' : 'No'}${d.hidePrice ? ' · precio oculto' : ''}'),
                if (d.contractType != null) FieldView('Contrato', label(contractLabels, d.contractType)),
                if (d.commission != null) FieldView('Comisión', '${numText(d.commission!)}%'),
                if (d.parking != null) FieldView('Estacionamientos', '${d.parking}'),
                if (d.levels != null) FieldView('Niveles', '${d.levels}'),
                if (d.age != null) FieldView('Antigüedad', '${d.age} años'),
                if (d.maintenanceFee != null) FieldView('Mantenimiento', formatPrice(d.maintenanceFee!)),
                FieldView('Capturó', '${p.createdBy?.name ?? '—'} · ${formatDateTime(p.createdAt)}'),
              ]),
            ]),
          ),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Alta y edición (se publica en 21Online al guardar)
// ---------------------------------------------------------------------------

class PropertyFormScreen extends StatefulWidget {
  const PropertyFormScreen({super.key, this.existing});
  final CapturedProperty? existing;

  @override
  State<PropertyFormScreen> createState() => _PropertyFormScreenState();
}

class _PropertyFormScreenState extends State<PropertyFormScreen> {
  late final PropertyData? d = widget.existing?.data;
  late String operation = d?.operation ?? 'venta';
  late String propertyType = d?.propertyType ?? 'habitacional';
  late String subtype = d?.subtype ?? 'casa';
  // Control de Cambios #26: el tipo se elige en el catálogo de inmuebles; el tipo/subtipo de 21Online salen de él.
  late String? catalogSubtypeId = d?.catalogSubtypeId;
  late String propertyTypeDetail = d?.propertyTypeDetail ?? '';
  List<CatalogCategory>? catalog;
  late String contractType = d?.contractType ?? 'exclusiva';
  late String status = d?.status ?? 'enPromocion';
  late String currency = d?.currency ?? 'MXN';
  late String state = d?.state ?? 'Morelos';
  late String advisorUserId = d?.advisorUserId ?? '';
  late bool hidePrice = d?.hidePrice ?? false;
  late bool hideStreet = d?.hideStreet ?? false;
  late bool hideNumber = d?.hideNumber ?? true;
  late bool publishOnline = d?.publishOnline ?? true;
  late final Map<String, String> text = {
    'headline': d?.headline ?? '',
    'description': d?.description ?? '',
    'price': _t(d == null ? null : (d!.operation == 'renta' ? d!.rentPrice : d!.salePrice)),
    'street': d?.street ?? '',
    'extNumber': d?.extNumber ?? '',
    'neighborhood': d?.neighborhood ?? '',
    'municipality': d?.municipality ?? '',
    'postalCode': d?.postalCode ?? '',
    'bedrooms': _t(d?.bedrooms),
    'bathrooms': _t(d?.bathrooms),
    'halfBathrooms': _t(d?.halfBathrooms),
    'parking': _t(d?.parking),
    'landM2': _t(d?.landM2),
    'builtM2': _t(d?.builtM2),
    'levels': _t(d?.levels),
    'age': _t(d?.age),
    'maintenanceFee': _t(d?.maintenanceFee),
    'commission': _t(d?.commission),
  };
  List<AdvisorRouting> advisors = [];
  bool saving = false;
  String? error;

  static String _t(num? v) => v == null ? '' : numText(v);

  bool get seesAll => context.read<AuthState>().can('LEAD_VIEW_ALL');

  @override
  void initState() {
    super.initState();
    api.listPropertyCatalog().then((c) {
      if (mounted) setState(() => catalog = c);
    }).catchError((Object _) {});
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (seesAll) api.listAdvisors().then((a) => mounted ? setState(() => advisors = a) : null).catchError((_) => null);
    });
  }

  num? _num(String key) {
    final cleaned = (text[key] ?? '').replaceAll(RegExp(r'[$,\s]'), '');
    return cleaned.isEmpty ? null : num.tryParse(cleaned);
  }

  String? _opt(String key) => (text[key] ?? '').trim().isEmpty ? null : text[key]!.trim();

  String? get typeProblem => propertyTypeError(catalog, catalogSubtypeId, propertyTypeDetail, currentId: d?.catalogSubtypeId);

  List<String> get missing => [
        if (typeProblem != null) 'tipo de inmueble',
        if ((text['headline'] ?? '').trim().isEmpty) 'encabezado',
        if ((_num('price') ?? 0) <= 0) 'precio',
        if ((text['neighborhood'] ?? '').trim().isEmpty) 'colonia',
        if ((text['municipality'] ?? '').trim().isEmpty) 'municipio',
      ];

  Json _payload() {
    final price = _num('price');
    return {
      'operation': operation,
      'propertyType': propertyType,
      'subtype': subtype,
      'catalogSubtypeId': catalogSubtypeId,
      'propertyTypeDetail': propertyTypeDetail.trim().isEmpty ? null : propertyTypeDetail.trim(),
      'contractType': contractType.isEmpty ? null : contractType,
      'status': status,
      'headline': text['headline']!.trim(),
      'description': _opt('description'),
      'salePrice': operation == 'venta' ? price : null,
      'rentPrice': operation == 'renta' ? price : null,
      'currency': currency,
      'hidePrice': hidePrice,
      'street': _opt('street'),
      'extNumber': _opt('extNumber'),
      'neighborhood': text['neighborhood']!.trim(),
      'municipality': text['municipality']!.trim(),
      'state': state,
      'postalCode': _opt('postalCode'),
      'hideStreet': hideStreet,
      'hideNumber': hideNumber,
      'bedrooms': _num('bedrooms')?.toInt(),
      'bathrooms': _num('bathrooms'),
      'halfBathrooms': _num('halfBathrooms')?.toInt(),
      'parking': _num('parking')?.toInt(),
      'landM2': _num('landM2'),
      'builtM2': _num('builtM2'),
      'levels': _num('levels')?.toInt(),
      'age': _num('age')?.toInt(),
      'maintenanceFee': _num('maintenanceFee'),
      'commission': _num('commission'),
      'publishOnline': publishOnline,
      'advisorUserId': advisorUserId.isEmpty ? null : advisorUserId,
    };
  }

  Future<void> save() async {
    setState(() {
      saving = true;
      error = null;
    });
    try {
      final existing = widget.existing;
      final saved = existing == null ? await api.createProperty(_payload()) : await api.updateProperty(existing.id, _payload());
      if (!mounted) return;
      if (existing == null) {
        context.pushReplacement('/propiedades/${saved.id}');
      } else {
        Navigator.of(context).pop(saved);
      }
    } catch (err) {
      if (mounted) {
        setState(() {
          saving = false;
          error = crmErrorText(err);
        });
      }
    }
  }

  Widget _field(String key, String labelText, {String? hint, TextInputType? keyboard, int maxLines = 1}) => AppTextField(
        label: labelText,
        hint: hint,
        value: text[key],
        keyboardType: keyboard,
        maxLines: maxLines,
        onChanged: (v) => setState(() => text[key] = v),
      );

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final editing = widget.existing != null;
    if (!auth.can('PROPERTY_MANAGE')) {
      return const Restricted(title: 'Nueva propiedad', route: '/propiedades', description: 'Tu usuario no puede dar de alta propiedades.');
    }
    const numeric = TextInputType.numberWithOptions(decimal: true);
    final isLand = subtype == 'terreno';
    final miss = missing;
    const gap = SizedBox(height: 12);
    return AppScaffold(
      title: editing ? 'Editar propiedad' : 'Nueva propiedad',
      body: PageBody(
        subtitle: editing ? 'Los cambios se envían a 21Online al guardar.' : 'Se guarda en Genera y se publica en 21Online en el mismo paso.',
        children: [
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const CardHeader(title: 'Operación y tipo', description: 'Se publica en 21Online con estos mismos datos.'),
              AppSelect<String>(label: 'Operación', value: operation, options: optionsOf(listingOperationLabels), onChanged: (v) => setState(() => operation = v)),
              gap,
              PropertyTypePicker(
                subtypeId: catalogSubtypeId,
                detail: propertyTypeDetail,
                currentLabel: widget.existing?.propertyTypeLabel,
                onChanged: (id, detail, picked) => setState(() {
                  catalogSubtypeId = id;
                  propertyTypeDetail = detail;
                  // Se publica en 21Online con el tipo y subtipo que corresponden a la subcategoría.
                  if (picked != null) {
                    propertyType = picked.online21Type;
                    subtype = picked.online21Subtype;
                  }
                }),
              ),
              if (catalogSubtypeId == null && d != null) ...[
                const SizedBox(height: 6),
                MutedText('Se capturó antes del catálogo como ${subtypeLabel(d!.subtype)} (${label(listingTypeLabels, d!.propertyType)}). Elige su tipo de inmueble.', size: 12.5),
              ],
              if (findSubtype(catalog, catalogSubtypeId) != null) ...[
                const SizedBox(height: 6),
                MutedText('En 21Online: ${subtypeLabel(subtype)} (${label(listingTypeLabels, propertyType)})', size: 12.5),
              ],
              gap,
              AppSelect<String>(
                label: 'Contrato con el propietario',
                value: contractType,
                options: optionsOf(contractLabels, empty: 'Sin especificar'),
                onChanged: (v) => setState(() => contractType = v),
              ),
              gap,
              AppSelect<String>(
                label: 'Estatus en 21Online',
                value: status,
                options: [for (final s in editableListingStatuses) (value: s, label: listingStatusLabels[s]!)],
                onChanged: (v) => setState(() => status = v),
              ),
              gap,
              if (seesAll)
                AppSelect<String>(
                  label: 'Asesor responsable',
                  value: advisorUserId,
                  options: [
                    (value: '', label: 'Sin asesor (a nombre de la llave de 21Online)'),
                    for (final a in advisors) (value: a.userId, label: a.online21UserId != null ? a.name : '${a.name} · sin usuario de 21Online'),
                  ],
                  onChanged: (v) => setState(() => advisorUserId = v),
                )
              else
                const MutedText('Quedará a tu nombre como asesor.'),
            ]),
          ),
          const SizedBox(height: 16),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const CardHeader(title: 'Anuncio y precio'),
              _field('headline', 'Encabezado del anuncio', hint: 'Ej. Casa con jardín y alberca en Burgos'),
              gap,
              _field('description', 'Descripción', maxLines: 5),
              gap,
              _field('price', operation == 'renta' ? 'Renta mensual' : 'Precio de venta', keyboard: numeric),
              gap,
              AppSelect<String>(
                label: 'Moneda',
                value: currency,
                options: const [(value: 'MXN', label: 'Pesos (MXN)'), (value: 'USD', label: 'Dólares (USD)')],
                onChanged: (v) => setState(() => currency = v),
              ),
              gap,
              _field('commission', 'Comisión (%)', keyboard: numeric),
              if (!isLand) ...[gap, _field('maintenanceFee', 'Cuota de mantenimiento', keyboard: numeric)],
              gap,
              ToggleRow(label: 'Publicar en internet', description: 'Portales y sitio de CENTURY 21 vía 21Online.', value: publishOnline, onChanged: (v) => setState(() => publishOnline = v)),
              ToggleRow(label: 'Ocultar precio en internet', value: hidePrice, onChanged: (v) => setState(() => hidePrice = v)),
            ]),
          ),
          const SizedBox(height: 16),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const CardHeader(title: 'Ubicación', description: 'Estado y municipio son obligatorios para 21Online.'),
              _field('street', 'Calle'),
              gap,
              _field('extNumber', 'Número'),
              gap,
              _field('neighborhood', 'Colonia'),
              gap,
              _field('municipality', 'Municipio'),
              gap,
              AppSelect<String>(label: 'Estado', value: state, options: [for (final s in mexicanStates) (value: s, label: s)], onChanged: (v) => setState(() => state = v)),
              gap,
              _field('postalCode', 'Código postal', keyboard: TextInputType.number),
              gap,
              ToggleRow(label: 'Ocultar la calle en internet', value: hideStreet, onChanged: (v) => setState(() => hideStreet = v)),
              ToggleRow(label: 'Ocultar el número en internet', value: hideNumber, onChanged: (v) => setState(() => hideNumber = v)),
            ]),
          ),
          const SizedBox(height: 16),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              const CardHeader(title: 'Características'),
              _field('landM2', 'Terreno (m²)', keyboard: numeric),
              if (!isLand) ...[
                gap,
                _field('builtM2', 'Construcción (m²)', keyboard: numeric),
                gap,
                _field('bedrooms', 'Recámaras', keyboard: TextInputType.number),
                gap,
                _field('bathrooms', 'Baños completos', keyboard: numeric),
                gap,
                _field('halfBathrooms', 'Medios baños', keyboard: TextInputType.number),
                gap,
                _field('parking', 'Estacionamientos', keyboard: TextInputType.number),
                gap,
                _field('levels', 'Niveles', keyboard: TextInputType.number),
                gap,
                _field('age', 'Antigüedad (años)', keyboard: TextInputType.number),
              ],
              const SizedBox(height: 12),
              const MutedText('Las fotos se suben en 21Online: su API no recibe archivos de imagen.', size: 12),
            ]),
          ),
          if (error != null) ...[const SizedBox(height: 16), Notice(tone: Tone.danger, text: error)],
          const SizedBox(height: 16),
          if (miss.isNotEmpty) ...[MutedText('Falta: ${miss.join(', ')}.'), const SizedBox(height: 8)],
          Btn(
            saving ? 'Guardando y enviando a 21Online…' : (editing && widget.existing!.online21Id != null ? 'Guardar y actualizar en 21Online' : 'Guardar y publicar en 21Online'),
            expand: true,
            loading: saving,
            onPressed: saving || miss.isNotEmpty ? null : save,
          ),
        ],
      ),
    );
  }
}
