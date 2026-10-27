import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../labels.dart';
import '../state/auth.dart';
import 'widgets.dart';

/// Menú lateral (Sidebar de la web): cada entrada solo se muestra a quien tiene el permiso.
class AppDrawer extends StatelessWidget {
  const AppDrawer({super.key, required this.current});
  final String current;

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final user = auth.user;
    final items = <({String href, String label, IconData icon, bool visible})>[
      (href: '/expedientes', label: 'Expedientes', icon: Icons.folder_copy_outlined, visible: true),
      (href: '/expedientes/nuevo', label: 'Nuevo expediente', icon: Icons.note_add_outlined, visible: auth.can('EXPEDIENT_CREATE')),
      // Control de Cambios #24: cartera por tipo de propiedad, operación y asesor.
      (href: '/reportes', label: 'Reportes', icon: Icons.bar_chart_outlined, visible: auth.can('EXPEDIENT_VIEW_ALL') || auth.can('EXPEDIENT_VIEW_OWN')),
      // Control de Cambios #27 (adenda): claves en uso y migración de las propiedades existentes.
      (href: '/claves', label: 'Claves de inmuebles', icon: Icons.key_outlined, visible: auth.can('EXPEDIENT_KEY_ASSIGN')),
      (href: '/usuarios', label: 'Usuarios y permisos', icon: Icons.group_outlined, visible: auth.can('USER_MANAGE')),
    ];
    final crm = <({String href, String label, IconData icon, bool visible})>[
      (href: '/dashboard', label: 'Dashboard', icon: Icons.dashboard_outlined, visible: true),
      (href: '/leads', label: 'Leads', icon: Icons.person_search_outlined, visible: true),
      // Control de Cambios #30: citas y actividades de seguimiento de los leads.
      (href: '/agenda', label: 'Agenda', icon: Icons.event_available_outlined, visible: true),
      (href: '/propiedades', label: 'Propiedades', icon: Icons.house_outlined, visible: auth.can('PROPERTY_VIEW')),
      (href: '/calendario', label: 'Calendario', icon: Icons.calendar_month_outlined, visible: auth.can('CALENDAR_VIEW_ALL') || auth.can('CALENDAR_VIEW_OWN')),
      (href: '/conversaciones', label: 'Conversaciones', icon: Icons.forum_outlined, visible: auth.can('CONVERSATION_VIEW')),
      (href: '/asesores', label: 'Asesores', icon: Icons.groups_2_outlined, visible: auth.can('ADVISOR_ROUTING_MANAGE')),
    ];

    Widget sectionLabel(String text) => Padding(
          padding: const EdgeInsets.fromLTRB(28, 18, 16, 6),
          child: Text(text.toUpperCase(), style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, letterSpacing: 1, color: Colors.white.withValues(alpha: 0.4))),
        );

    Widget link(({String href, String label, IconData icon, bool visible}) item) {
      final active = item.href == '/expedientes' ? current == '/expedientes' || (current.startsWith('/expedientes/') && current != '/expedientes/nuevo') : current.startsWith(item.href);
      return Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 2),
        child: Material(
          color: active ? Brand.gold.withValues(alpha: 0.15) : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
          child: InkWell(
            borderRadius: BorderRadius.circular(12),
            onTap: () {
              Navigator.of(context).pop();
              context.go(item.href);
            },
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              child: Row(children: [
                Icon(item.icon, size: 20, color: active ? Brand.gold : Colors.white.withValues(alpha: 0.75)),
                const SizedBox(width: 12),
                Text(item.label, style: TextStyle(fontSize: 14.5, fontWeight: FontWeight.w500, color: active ? Brand.gold : Colors.white.withValues(alpha: 0.85))),
              ]),
            ),
          ),
        ),
      );
    }

    return Drawer(
      backgroundColor: Brand.ink,
      child: SafeArea(
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 20, 24, 12),
            child: Align(alignment: Alignment.centerLeft, child: Image.asset('assets/brand/century21-genera-horizontal-nav.png', height: 36)),
          ),
          if (user != null)
            InkWell(
              onTap: () {
                Navigator.of(context).pop();
                context.go('/configuracion');
              },
              child: Padding(
                padding: const EdgeInsets.fromLTRB(24, 8, 16, 8),
                child: Row(children: [
                  Avatar(user.name, size: 38),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(user.name, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600), overflow: TextOverflow.ellipsis),
                      Text(roleLabels[user.role] ?? user.role, style: TextStyle(color: Colors.white.withValues(alpha: 0.55), fontSize: 12)),
                    ]),
                  ),
                  Icon(Icons.settings_outlined, size: 18, color: Colors.white.withValues(alpha: 0.6)),
                ]),
              ),
            ),
          Expanded(
            child: ListView(children: [
              if (auth.canSeeCrm) sectionLabel('Expedientes'),
              ...items.where((i) => i.visible).map(link),
              if (auth.canSeeCrm) ...[sectionLabel('CRM'), ...crm.where((i) => i.visible).map(link)],
            ]),
          ),
          Divider(color: Colors.white.withValues(alpha: 0.1), height: 1),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
            child: InkWell(
              borderRadius: BorderRadius.circular(12),
              onTap: () {
                Navigator.of(context).pop();
                auth.logout();
              },
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                child: Row(children: [
                  Icon(Icons.logout, size: 20, color: Colors.white.withValues(alpha: 0.75)),
                  const SizedBox(width: 12),
                  Text('Cerrar sesión', style: TextStyle(color: Colors.white.withValues(alpha: 0.85), fontWeight: FontWeight.w500)),
                ]),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(28, 4, 16, 16),
            child: Text('Cada Oficina es de Propiedad y Operación Independiente.',
                style: TextStyle(fontSize: 11, color: Colors.white.withValues(alpha: 0.5))),
          ),
        ]),
      ),
    );
  }
}

/// Pantalla con barra superior. En las pantallas principales muestra el menú;
/// en las de detalle, el botón de regresar.
class AppScaffold extends StatelessWidget {
  const AppScaffold({super.key, required this.title, required this.body, this.route, this.actions, this.fab, this.bottom, this.bottomBar});

  final String title;
  final Widget body;

  /// Ruta principal a la que pertenece (muestra el menú). null = pantalla de detalle.
  final String? route;
  final List<Widget>? actions;
  final Widget? fab;
  final PreferredSizeWidget? bottom;
  final Widget? bottomBar;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(title, overflow: TextOverflow.ellipsis),
        leading: route == null ? BackButton(onPressed: () => context.canPop() ? context.pop() : context.go('/expedientes')) : null,
        actions: [
          ...?actions,
          // Como en el encabezado de la web: iniciales del usuario con acceso a Configuración.
          if (route != null && route != '/configuracion' && context.watch<AuthState>().user != null)
            Padding(
              padding: const EdgeInsets.only(right: 8, left: 4),
              child: InkWell(
                customBorder: const CircleBorder(),
                onTap: () => context.go('/configuracion'),
                child: Tooltip(message: 'Mi perfil y configuración', child: Avatar(context.watch<AuthState>().user!.name, size: 34)),
              ),
            ),
        ],
        bottom: bottom,
      ),
      drawer: route != null ? AppDrawer(current: route!) : null,
      body: SafeArea(top: false, child: body),
      floatingActionButton: fab,
      bottomNavigationBar: bottomBar,
    );
  }
}

/// Mensaje para pantallas sin permiso.
class Restricted extends StatelessWidget {
  const Restricted({super.key, required this.title, required this.route, required this.description});
  final String title;
  final String route;
  final String description;

  @override
  Widget build(BuildContext context) => AppScaffold(
        title: title,
        route: route,
        body: PageBody(children: [StateMessage(kind: StateKind.restricted, title: 'Acceso restringido', description: description)]),
      );
}
