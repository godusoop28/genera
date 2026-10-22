import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../api/models.dart';
import '../../labels.dart';
import '../../state/auth.dart';
import '../../ui/shell.dart';
import '../../ui/property_type_picker.dart';
import '../../ui/widgets.dart';

// Criterio de agrupación (definido en el backend): cada estatus pertenece a un
// solo grupo, así que pendientes + en revisión + completos = total.
const _groups = [(id: '', label: 'Todos'), (id: 'PENDING', label: 'Pendientes'), (id: 'IN_REVIEW', label: 'En revisión'), (id: 'COMPLETE', label: 'Completos')];

const _pageSize = 18;

String _operationText(ExpedienteResponse e) {
  final type = e.propertySubtypeName ?? (e.propertyCategoryName != null ? '${e.propertyCategoryName} (sin clasificar)' : label(propertyTypeLabels, e.propertyCaseType));
  return '$type en ${label(operationLabels, e.operationType).toLowerCase()}';
}

class ExpedientesScreen extends StatefulWidget {
  /// Control de Cambios #24/#26: los reportes abren el listado ya filtrado (?categoria=&subtipo=&operacion=&asesor=).
  const ExpedientesScreen({super.key, this.initialCategory, this.initialSubtype, this.initialOperation, this.initialAdvisor});
  final String? initialCategory, initialSubtype, initialOperation, initialAdvisor;

  @override
  State<ExpedientesScreen> createState() => _ExpedientesScreenState();
}

class _ExpedientesScreenState extends State<ExpedientesScreen> {
  String query = '';
  String group = '';
  late String categoryId = widget.initialCategory ?? '';
  late String subtypeId = widget.initialSubtype ?? '';
  late String operationType = operationLabels.containsKey(widget.initialOperation) ? widget.initialOperation! : '';
  late String advisorId = widget.initialAdvisor ?? '';
  List<AdvisorOption> advisors = [];
  List<ExpedienteOverviewItem>? items;
  ExpedienteOverview? last;
  int page = 0;
  ({String message, bool forbidden})? error;
  bool loading = false;
  bool loadingMore = false;
  String? moreError;
  Timer? _debounce;
  int _request = 0;

  /// Vista de tarjetas o de lista (se recuerda en el dispositivo, como en la web).
  bool listView = false;
  static const _viewKey = 'genera.expedientes.view';

  @override
  void initState() {
    super.initState();
    load();
    if (context.read<AuthState>().can('EXPEDIENT_VIEW_ALL')) {
      api.listAssignableAdvisors().then((list) {
        if (mounted) setState(() => advisors = list);
      }).catchError((_) {});
    }
    SharedPreferences.getInstance()
        .then((p) {
          if (mounted && p.getString(_viewKey) == 'list') setState(() => listView = true);
        })
        .catchError((_) {});
  }

  void toggleView() {
    setState(() => listView = !listView);
    SharedPreferences.getInstance().then((p) => p.setString(_viewKey, listView ? 'list' : 'grid')).catchError((_) => false);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }

  ({String message, bool forbidden}) _errorOf(Object err) {
    if (err is ApiError && err.status == 403) return (message: 'Tu usuario no tiene permiso para consultar expedientes.', forbidden: true);
    if (err is ApiError) return (message: 'El servidor respondió con un error (${err.status}). Intenta de nuevo en un momento.', forbidden: false);
    return (message: 'No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.', forbidden: false);
  }

  Future<void> load() async {
    final id = ++_request;
    setState(() => loading = true);
    try {
      final res = await api.getExpedienteOverview(q: query, group: group.isEmpty ? null : group, page: 0, size: _pageSize, categoryId: _portfolio.categoryId, subtypeId: _portfolio.subtypeId, operationType: _portfolio.operationType, advisorId: _portfolio.advisorId);
      if (!mounted || id != _request) return;
      setState(() {
        last = res;
        items = res.page.items;
        page = 0;
        error = null;
        moreError = null;
        loading = false;
      });
    } catch (err) {
      if (mounted && id == _request) {
        setState(() {
          error = _errorOf(err);
          loading = false;
        });
      }
    }
  }

  Future<void> loadMore() async {
    final id = _request;
    setState(() {
      loadingMore = true;
      moreError = null;
    });
    try {
      final res = await api.getExpedienteOverview(q: query, group: group.isEmpty ? null : group, page: page + 1, size: _pageSize, categoryId: _portfolio.categoryId, subtypeId: _portfolio.subtypeId, operationType: _portfolio.operationType, advisorId: _portfolio.advisorId);
      if (!mounted || id != _request) return;
      final seen = items!.map((i) => i.expediente.id).toSet();
      setState(() {
        items = [...items!, ...res.page.items.where((i) => !seen.contains(i.expediente.id))];
        last = res;
        page = page + 1;
      });
    } catch (_) {
      if (mounted) setState(() => moreError = 'No se pudieron cargar más expedientes. Intenta de nuevo.');
    } finally {
      if (mounted) setState(() => loadingMore = false);
    }
  }

  int? countFor(String id) {
    final l = last;
    if (l == null) return null;
    return switch (id) {
      '' => l.total,
      'PENDING' => l.pending,
      'IN_REVIEW' => l.inReview,
      _ => l.complete,
    };
  }

  ({String? categoryId, String? subtypeId, String? operationType, String? advisorId}) get _portfolio => (
        categoryId: categoryId.isEmpty ? null : categoryId,
        subtypeId: subtypeId.isEmpty ? null : subtypeId,
        operationType: operationType.isEmpty ? null : operationType,
        advisorId: advisorId.isEmpty ? null : advisorId,
      );

  bool get filtering =>
      query.trim().isNotEmpty || group.isNotEmpty || categoryId.isNotEmpty || subtypeId.isNotEmpty || operationType.isNotEmpty || advisorId.isNotEmpty;

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final canCreate = auth.can('EXPEDIENT_CREATE');
    String v(String id) => countFor(id)?.toString() ?? '—';
    return AppScaffold(
      title: 'Expedientes',
      route: '/expedientes',
      actions: [
        IconButton(tooltip: listView ? 'Ver como tarjetas' : 'Ver como lista', icon: Icon(listView ? Icons.grid_view_outlined : Icons.view_list_outlined), onPressed: toggleView),
      ],
      fab: canCreate
          ? FloatingActionButton.extended(
              backgroundColor: Brand.gold,
              foregroundColor: Brand.ink,
              onPressed: () => context.push('/expedientes/nuevo'),
              icon: const Icon(Icons.add),
              label: const Text('Nuevo expediente'),
            )
          : null,
      body: error?.forbidden == true
          ? PageBody(
              children: [StateMessage(kind: StateKind.restricted, title: 'Acceso restringido', description: error!.message)],
            )
          : PageBody(
              onRefresh: load,
              bottomPadding: 96,
              subtitle: 'Da seguimiento a cada operación, desde los documentos del cliente hasta la firma del contrato.',
              children: [
                StatGrid(
                  children: [
                    StatCard(icon: Icons.folder_open_outlined, value: v(''), label: 'Total', hint: 'Expedientes que puedes ver'),
                    StatCard(icon: Icons.schedule, tone: Tone.warning, value: v('PENDING'), label: 'Pendientes', hint: 'Esperan algo del cliente'),
                    StatCard(icon: Icons.refresh, tone: Tone.info, value: v('IN_REVIEW'), label: 'En revisión', hint: 'Documentos por revisar'),
                    StatCard(icon: Icons.check_circle_outline, tone: Tone.success, value: v('COMPLETE'), label: 'Completos', hint: 'Documentación aprobada'),
                  ],
                ),
                const SizedBox(height: 16),
                AppTextField(
                  hint: 'Buscar por cliente, folio, clave o domicilio',
                  value: query,
                  onChanged: (q) {
                    query = q;
                    _debounce?.cancel();
                    _debounce = Timer(const Duration(milliseconds: 300), load);
                  },
                ),
                const SizedBox(height: 12),
                FilterChips<String>(
                  value: group,
                  options: [for (final g in _groups) (id: g.id, label: g.label, count: countFor(g.id))],
                  onChanged: (g) {
                    setState(() => group = g);
                    load();
                  },
                ),
                const SizedBox(height: 12),
                PropertyTypeFilters(
                  categoryId: categoryId,
                  subtypeId: subtypeId,
                  onChanged: (category, subtype) {
                    setState(() {
                      categoryId = category;
                      subtypeId = subtype;
                    });
                    load();
                  },
                ),
                const SizedBox(height: 10),
                AppSelect<String>(
                  value: operationType,
                  options: optionsOf(operationLabels, empty: 'Venta y renta'),
                  onChanged: (v) {
                    setState(() => operationType = v);
                    load();
                  },
                ),
                if (auth.can('EXPEDIENT_VIEW_ALL')) ...[
                  const SizedBox(height: 10),
                  AppSelect<String>(
                    value: advisorId,
                    options: [
                      (value: '', label: 'Todos los asesores'),
                      (value: api.unassignedAdvisor, label: 'Sin asesor asignado'),
                      for (final a in advisors) (value: a.id, label: a.name),
                      if (advisorId.isNotEmpty && advisorId != api.unassignedAdvisor && !advisors.any((a) => a.id == advisorId))
                        (value: advisorId, label: 'Asesor seleccionado'),
                    ],
                    onChanged: (v) {
                      setState(() => advisorId = v);
                      load();
                    },
                  ),
                ],
                const SizedBox(height: 16),
                if (error != null && items == null)
                  LoadError(title: 'No se pudieron cargar los expedientes', message: error!.message, onRetry: load)
                else if (items == null)
                  const StateMessage(kind: StateKind.loading, title: 'Cargando expedientes…')
                else ...[
                  if (error != null) ...[
                    Notice(
                      tone: Tone.danger,
                      text: error!.message,
                      action: Btn('Reintentar', small: true, variant: BtnVariant.secondary, onPressed: load),
                    ),
                    const SizedBox(height: 12),
                  ],
                  AnimatedOpacity(
                    opacity: loading ? 0.6 : 1,
                    duration: const Duration(milliseconds: 150),
                    child: items!.isEmpty
                        ? (filtering
                              ? StateMessage(
                                  kind: StateKind.noResults,
                                  title: 'Sin resultados',
                                  description: query.trim().isNotEmpty ? 'Ningún expediente coincide con “${query.trim()}” en este filtro.' : 'No hay expedientes en este filtro.',
                                  action: Btn(
                                    'Quitar filtros',
                                    variant: BtnVariant.secondary,
                                    onPressed: () {
                                      setState(() {
                                        query = '';
                                        group = '';
                                        categoryId = '';
                                        subtypeId = '';
                                        operationType = '';
                                        advisorId = '';
                                      });
                                      load();
                                    },
                                  ),
                                )
                              : StateMessage(
                                  kind: StateKind.empty,
                                  title: 'Aún no hay expedientes',
                                  description: 'Cuando crees un expediente aparecerá aquí con su avance documental.',
                                  action: canCreate ? Btn('Nuevo expediente', icon: Icons.add, onPressed: () => context.push('/expedientes/nuevo')) : null,
                                ))
                        : listView
                        ? AppCard(
                            padding: EdgeInsets.zero,
                            child: Column(
                              children: [
                                for (var i = 0; i < items!.length; i++) ...[if (i > 0) Divider(height: 1, color: context.c.border), ExpedienteRow(item: items![i])],
                              ],
                            ),
                          )
                        : Column(children: spaced(items!.map((i) => ExpedienteCard(item: i)), 12)),
                  ),
                  if (items!.isNotEmpty) ...[
                    const SizedBox(height: 16),
                    MutedText(
                      'Mostrando ${items!.length} de ${last!.page.totalElements} expediente${last!.page.totalElements == 1 ? '' : 's'}${filtering ? ' que coinciden' : ''}',
                      size: 12,
                      center: true,
                    ),
                    if (moreError != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 8),
                        child: Text(
                          moreError!,
                          textAlign: TextAlign.center,
                          style: TextStyle(color: context.c.dangerText),
                        ),
                      ),
                    if (page + 1 < last!.page.totalPages)
                      Padding(
                        padding: const EdgeInsets.only(top: 12),
                        child: Center(
                          child: Btn(loadingMore ? 'Cargando…' : 'Cargar más expedientes', variant: BtnVariant.secondary, loading: loadingMore, onPressed: loadMore),
                        ),
                      ),
                  ],
                ],
              ],
            ),
    );
  }
}

class DocumentsProgressView extends StatelessWidget {
  const DocumentsProgressView({super.key, required this.progress});
  final DocumentProgress progress;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final p = progress;
    if (p.required == 0) return const MutedText('Sin documentos obligatorios registrados', size: 12);
    final pct = (p.accepted * 100 / p.required).round();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text.rich(
                TextSpan(
                  children: [
                    const TextSpan(text: 'Aprobados: '),
                    TextSpan(
                      text: '${p.accepted}',
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    TextSpan(text: ' de ${p.required}'),
                  ],
                ),
                style: TextStyle(fontSize: 13.5, color: c.fg),
              ),
            ),
            MutedText('$pct%', size: 12),
          ],
        ),
        const SizedBox(height: 6),
        ProgressLine(pct / 100, color: pct == 100 ? c.successText : Brand.gold),
        const SizedBox(height: 4),
        MutedText('Recibidos: ${p.received} de ${p.required} obligatorios', size: 12),
      ],
    );
  }
}

class ExpedienteCard extends StatelessWidget {
  const ExpedienteCard({super.key, required this.item});
  final ExpedienteOverviewItem item;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final e = item.expediente;
    return AppCard(
      onTap: () => context.push('/expedientes/${e.id}'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  e.propertyKey != null ? '${e.folio} · ${e.propertyKey}' : e.folio,
                  style: TextStyle(fontFamily: 'GeistMono', fontSize: 12, color: c.muted, fontWeight: FontWeight.w500),
                ),
              ),
              Flexible(child: AppBadge(label(backendStatusLabels, e.status), tone: backendStatusTone[e.status] ?? Tone.neutral)),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 42,
                height: 42,
                alignment: Alignment.center,
                decoration: BoxDecoration(color: c.appBg, shape: BoxShape.circle),
                child: Text(
                  initials(e.ownerDisplayName),
                  style: TextStyle(fontWeight: FontWeight.w600, color: c.fg),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      e.ownerDisplayName.isEmpty ? 'Sin nombre' : e.ownerDisplayName,
                      style: TextStyle(fontSize: 15.5, fontWeight: FontWeight.w600, color: c.fg),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        Icon(Icons.home_outlined, size: 14, color: c.muted),
                        const SizedBox(width: 4),
                        Expanded(child: MutedText(_operationText(e), size: 12.5)),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Divider(color: c.border),
          const SizedBox(height: 8),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(Icons.place_outlined, size: 16, color: c.muted),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  (e.propertyAddress?.isNotEmpty ?? false)
                      ? e.propertyAddress!
                      : ((e.propertyReference?.isNotEmpty ?? false) ? e.propertyReference! : 'Domicilio pendiente (se toma de los documentos)'),
                  style: TextStyle(fontSize: 13, color: c.muted),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(Icons.description_outlined, size: 16, color: c.muted),
              const SizedBox(width: 6),
              Expanded(child: DocumentsProgressView(progress: item.documents)),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Icon(Icons.calendar_today_outlined, size: 13, color: c.muted),
              const SizedBox(width: 5),
              Expanded(child: MutedText('Actualizado: ${_date(e.updatedAt)}', size: 12)),
              Text(
                'Ver expediente',
                style: TextStyle(color: c.goldText, fontWeight: FontWeight.w600, fontSize: 13.5),
              ),
              Icon(Icons.arrow_forward, size: 16, color: c.goldText),
            ],
          ),
        ],
      ),
    );
  }

  static String _date(String iso) {
    final d = DateTime.tryParse(iso)?.toLocal();
    return d == null ? iso : DateFormat('d MMM y', 'es_MX').format(d);
  }
}

/// Fila compacta de la vista de lista.
class ExpedienteRow extends StatelessWidget {
  const ExpedienteRow({super.key, required this.item});
  final ExpedienteOverviewItem item;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final e = item.expediente;
    final p = item.documents;
    final pct = p.required == 0 ? null : (p.accepted * 100 / p.required).round();
    return InkWell(
      onTap: () => context.push('/expedientes/${e.id}'),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 38,
              height: 38,
              alignment: Alignment.center,
              decoration: BoxDecoration(color: c.appBg, shape: BoxShape.circle),
              child: Text(
                initials(e.ownerDisplayName),
                style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: c.fg),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    e.ownerDisplayName.isEmpty ? 'Sin nombre' : e.ownerDisplayName,
                    style: TextStyle(fontWeight: FontWeight.w600, color: c.fg),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    e.propertyKey != null ? '${e.folio} · ${e.propertyKey}' : e.folio,
                    style: TextStyle(fontFamily: 'GeistMono', fontSize: 12, color: c.muted),
                  ),
                  Text(
                    (e.propertyAddress?.isNotEmpty ?? false)
                        ? e.propertyAddress!
                        : ((e.propertyReference?.isNotEmpty ?? false) ? e.propertyReference! : _operationText(e)),
                    style: TextStyle(fontSize: 12, color: c.muted),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 6),
                  Wrap(
                    spacing: 8,
                    runSpacing: 4,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      AppBadge(label(backendStatusLabels, e.status), tone: backendStatusTone[e.status] ?? Tone.neutral),
                      MutedText(pct == null ? 'Sin documentos obligatorios' : 'Aprobados ${p.accepted} de ${p.required} ($pct%)', size: 12),
                    ],
                  ),
                ],
              ),
            ),
            Icon(Icons.chevron_right, color: c.muted),
          ],
        ),
      ),
    );
  }
}
