import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../api/models.dart';
import '../../config.dart';
import '../../labels.dart';
import '../../ui/shell.dart';
import '../../ui/widgets.dart';
import 'tabs/consistency_tab.dart';
import 'tabs/contract_data_tab.dart';
import 'tabs/contract_tab.dart';
import 'tabs/documents_tab.dart';
import 'tabs/follow_up_tab.dart';
import 'tabs/history_tab.dart';
import 'tabs/participants_tab.dart';
import 'tabs/summary_tab.dart';

const _tabs = ['Resumen', 'Participantes', 'Documentos', 'Consistencia', 'Datos del contrato', 'Contrato y firmas', 'Seguimiento', 'Bitácora'];

/// El backend arma las ligas con su propia configuración; el token es siempre
/// el último segmento, así que se reconstruyen con el dominio de la web.
String frontendLink(String backendUrl, String route) {
  final token = backendUrl.split(RegExp(r'[?#]')).first.replaceAll(RegExp(r'/+$'), '').split('/').last;
  if (token.isEmpty) return backendUrl;
  return '${AppConfig.webBaseUrl}/$route/$token';
}

/// Lo que comparten todas las pestañas del expediente.
class ExpedienteCtx {
  ExpedienteCtx({
    required this.expediente,
    required this.participants,
    required this.reload,
    required this.freshPublicLink,
    required this.setFreshPublicLink,
    required this.freshSigningLinks,
    required this.setFreshSigningLinks,
  });

  final ExpedienteResponse expediente;
  final List<Participant> participants;

  /// Vuelve a cargar el expediente (estatus, participantes) después de una acción.
  final Future<void> Function() reload;

  /// Ligas recién generadas (solo se muestran una vez); viven aquí para sobrevivir a reload().
  final String? freshPublicLink;
  final ValueChanged<String?> setFreshPublicLink;
  final List<SigningLink> freshSigningLinks;
  final ValueChanged<List<SigningLink>> setFreshSigningLinks;
}

class ExpedienteDetailScreen extends StatefulWidget {
  const ExpedienteDetailScreen({super.key, required this.id});
  final String id;

  @override
  State<ExpedienteDetailScreen> createState() => _ExpedienteDetailScreenState();
}

class _ExpedienteDetailScreenState extends State<ExpedienteDetailScreen> with TickerProviderStateMixin {
  late final tabs = TabController(length: _tabs.length, vsync: this);
  ExpedienteResponse? expediente;
  List<Participant> participants = [];
  bool notFound = false;
  String? loadError;
  bool refreshing = false;
  int version = 0;
  String? freshPublicLink;
  List<SigningLink> freshSigningLinks = [];

  String get _tabKey => 'expediente:${widget.id}:tab';

  @override
  void initState() {
    super.initState();
    // Al volver al expediente se regresa a la pestaña en la que se estaba trabajando.
    SharedPreferences.getInstance().then((p) {
      final saved = p.getInt(_tabKey);
      if (saved != null && saved < _tabs.length && mounted) tabs.index = saved;
    }).catchError((_) {});
    tabs.addListener(() {
      if (!tabs.indexIsChanging) SharedPreferences.getInstance().then((p) => p.setInt(_tabKey, tabs.index)).catchError((_) => false);
    });
    reload();
  }

  @override
  void dispose() {
    tabs.dispose();
    super.dispose();
  }

  Future<void> reload() async {
    try {
      final results = await Future.wait([api.getExpediente(widget.id), api.getParticipants(widget.id)]);
      if (!mounted) return;
      setState(() {
        expediente = results[0] as ExpedienteResponse;
        participants = results[1] as List<Participant>;
        loadError = null;
        version++;
      });
    } catch (err) {
      if (!mounted) return;
      if (err is ApiError && err.status == 404) {
        setState(() => notFound = true);
      } else if (expediente != null) {
        // Ya hay datos en pantalla: un error temporal de red no debe sacar al usuario.
        showToast(context, 'No se pudo actualizar la información (revisa tu conexión). Lo que tienes en pantalla se conserva.');
      } else {
        setState(() => loadError = 'No se pudo cargar el expediente. Revisa tu conexión e intenta de nuevo.');
      }
    }
  }

  Future<void> refresh() async {
    setState(() => refreshing = true);
    await reload();
    if (mounted) {
      setState(() => refreshing = false);
      showToast(context, 'Información actualizada.');
    }
  }

  @override
  Widget build(BuildContext context) {
    if (notFound) {
      return const AppScaffold(
        title: 'Expediente no encontrado',
        body: PageBody(children: [MutedText('Este expediente no existe o no tienes permiso para consultarlo.')]),
      );
    }
    final e = expediente;
    if (e == null) {
      return AppScaffold(
        title: 'Expediente',
        body: PageBody(children: [
          loadError != null
              ? LoadError(
                  message: loadError!,
                  onRetry: () {
                    setState(() => loadError = null);
                    reload();
                  })
              : const StateMessage(kind: StateKind.loading, title: 'Cargando expediente…'),
        ]),
      );
    }
    final ctx = ExpedienteCtx(
      expediente: e,
      participants: participants,
      reload: reload,
      freshPublicLink: freshPublicLink,
      setFreshPublicLink: (v) => setState(() => freshPublicLink = v),
      freshSigningLinks: freshSigningLinks,
      setFreshSigningLinks: (v) => setState(() => freshSigningLinks = v),
    );
    final c = context.c;
    final next = backendStatusNextStep[e.status];
    return AppScaffold(
      title: e.ownerDisplayName.isEmpty ? 'Expediente' : e.ownerDisplayName,
      actions: [
        IconButton(
          tooltip: 'Actualizar',
          onPressed: refreshing ? null : refresh,
          icon: refreshing ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.refresh),
        ),
      ],
      body: NestedScrollView(
        headerSliverBuilder: (context, _) => [
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 4),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                if (e.propertyAddress != null) ...[MutedText(e.propertyAddress!), const SizedBox(height: 10)],
                Wrap(spacing: 8, runSpacing: 6, crossAxisAlignment: WrapCrossAlignment.center, children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(color: c.card, borderRadius: BorderRadius.circular(8), border: Border.all(color: c.border)),
                    child: Text(e.folio, style: TextStyle(fontFamily: 'GeistMono', fontSize: 12, color: c.fg.withValues(alpha: 0.75))),
                  ),
                  AppBadge(label(backendStatusLabels, e.status), tone: backendStatusTone[e.status] ?? Tone.neutral),
                  if (!e.correctable) const AppBadge('Datos bloqueados (contrato firmado o expediente decidido)'),
                ]),
                if (next != null) ...[
                  const SizedBox(height: 12),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(color: c.card, borderRadius: BorderRadius.circular(12), border: Border.all(color: c.border)),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      FieldView('Qué significa', next.meaning, upper: true),
                      const SizedBox(height: 8),
                      FieldView('Siguiente paso', null,
                          upper: true,
                          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Icon(Icons.arrow_forward, size: 16, color: c.goldText),
                            const SizedBox(width: 4),
                            Expanded(child: Text(next.next, style: TextStyle(fontSize: 14, color: c.fg))),
                          ])),
                      const SizedBox(height: 8),
                      FieldView('Quién lo hace', next.who, upper: true),
                    ]),
                  ),
                ],
              ]),
            ),
          ),
          SliverPersistentHeader(
            pinned: true,
            delegate: _TabsHeader(TabBar(controller: tabs, isScrollable: true, tabs: [for (final t in _tabs) Tab(text: t)]), c.appBg),
          ),
        ],
        body: TabBarView(
          controller: tabs,
          children: [
            SummaryTab(key: ValueKey('s$version'), ctx: ctx),
            ParticipantsTab(key: ValueKey('p$version'), ctx: ctx),
            DocumentsTab(key: ValueKey('d$version'), ctx: ctx),
            ConsistencyTab(key: ValueKey('c$version'), ctx: ctx),
            ContractDataTab(key: ValueKey('cd$version'), ctx: ctx),
            ContractTab(key: ValueKey('ct$version'), ctx: ctx),
            FollowUpTab(key: ValueKey('f$version'), ctx: ctx),
            HistoryTab(key: ValueKey('h$version'), ctx: ctx),
          ],
        ),
      ),
    );
  }
}

class _TabsHeader extends SliverPersistentHeaderDelegate {
  _TabsHeader(this.tabBar, this.background);
  final TabBar tabBar;
  final Color background;

  @override
  double get minExtent => tabBar.preferredSize.height;
  @override
  double get maxExtent => tabBar.preferredSize.height;

  @override
  Widget build(BuildContext context, double shrinkOffset, bool overlapsContent) => Container(color: background, child: tabBar);

  @override
  bool shouldRebuild(covariant _TabsHeader oldDelegate) => oldDelegate.tabBar != tabBar || oldDelegate.background != background;
}

/// Contenido de una pestaña: lista desplazable con su propio "jalar para actualizar".
class TabBody extends StatelessWidget {
  const TabBody({super.key, required this.children, this.onRefresh});
  final List<Widget> children;
  final Future<void> Function()? onRefresh;

  @override
  Widget build(BuildContext context) => PageBody(onRefresh: onRefresh, children: spaced(children));
}
