import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';

import '../api/api.dart' as api;
import '../api/client.dart';
import '../api/models.dart';
import '../state/auth.dart';
import '../ui/shell.dart';
import '../ui/widgets.dart';

String _lastActivityLabel(String? iso) {
  if (iso == null) return 'Sin actividad';
  final date = DateTime.tryParse(iso)?.toLocal();
  if (date == null) return 'Sin actividad';
  final now = DateTime.now();
  final yesterday = now.subtract(const Duration(days: 1));
  bool same(DateTime a, DateTime b) => a.year == b.year && a.month == b.month && a.day == b.day;
  if (same(date, now)) return 'Hoy, ${DateFormat('HH:mm').format(date)}';
  if (same(date, yesterday)) return 'Ayer, ${DateFormat('HH:mm').format(date)}';
  return DateFormat('d MMM y', 'es_MX').format(date);
}

class UsersScreen extends StatefulWidget {
  const UsersScreen({super.key});

  @override
  State<UsersScreen> createState() => _UsersScreenState();
}

class _UsersScreenState extends State<UsersScreen> with SingleTickerProviderStateMixin {
  late final tabs = TabController(length: 2, vsync: this);
  List<UserResponse>? users;
  int totalUsers = 0;
  ({bool forbidden, String message})? loadError;
  List<RoleResponse> roles = [];
  List<PermissionResponse> permissions = [];
  String? rolesError;
  bool rolesLoaded = false;
  String query = '';
  String roleFilter = '';
  String? busyUserId;

  @override
  void initState() {
    super.initState();
    loadUsers();
    loadRoles();
  }

  Future<void> loadUsers() async {
    try {
      final page = await api.listUsers(0, 100);
      setState(() {
        users = page.items;
        totalUsers = page.totalElements;
        loadError = null;
      });
    } catch (err) {
      setState(() => loadError = err is ApiError && err.status == 403
          ? (forbidden: true, message: 'Tu sesión no tiene permiso para administrar usuarios.')
          : (
              forbidden: false,
              message: err is ApiError
                  ? 'El servidor respondió con un error (${err.status}). Puede ser algo temporal; intenta de nuevo.'
                  : 'No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.'
            ));
    }
  }

  Future<void> loadRoles() async {
    try {
      final results = await Future.wait([api.listRoles(), api.listPermissions()]);
      setState(() {
        roles = results[0] as List<RoleResponse>;
        permissions = results[1] as List<PermissionResponse>;
        rolesLoaded = true;
        rolesError = null;
      });
    } catch (err) {
      setState(() => rolesError = err is ApiError && err.status == 403
          ? 'Tu sesión no tiene permiso para consultar los roles.'
          : 'No se pudieron cargar los roles y permisos. Intenta de nuevo.');
    }
  }

  Future<void> toggleStatus(UserResponse user) async {
    if (busyUserId != null) return;
    setState(() => busyUserId = user.id);
    try {
      if (user.status == 'ACTIVE') {
        await api.deactivateUser(user.id);
        if (mounted) showToast(context, '${user.name} quedó desactivado.');
      } else {
        await api.activateUser(user.id);
        if (mounted) showToast(context, '${user.name} quedó activado.');
      }
      await loadUsers();
    } catch (err) {
      if (mounted) showToast(context, err is ApiError ? 'No se pudo actualizar: ${err.message}' : 'No se pudo conectar con el servidor.');
    } finally {
      if (mounted) setState(() => busyUserId = null);
    }
  }

  Future<void> openForm([UserResponse? editing]) async {
    var name = editing?.name ?? '';
    var email = editing?.email ?? '';
    var password = '';
    var roleCode = editing?.roleCode ?? 'ADVISOR';
    String? formError;
    var saving = false;
    await showAppSheet(
      context,
      title: editing == null ? 'Crear usuario' : 'Editar usuario',
      builder: (ctx, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        AppTextField(label: 'Nombre completo', value: name, onChanged: (v) => name = v),
        const SizedBox(height: 12),
        AppTextField(label: 'Correo', value: email, keyboardType: TextInputType.emailAddress, onChanged: (v) => email = v),
        if (editing == null) ...[
          const SizedBox(height: 12),
          AppTextField(
            label: 'Contraseña temporal',
            obscure: true,
            helper: 'Mínimo 8 caracteres. Compártela con la persona por un medio seguro.',
            onChanged: (v) => password = v,
          ),
        ],
        const SizedBox(height: 12),
        AppSelect<String>(
          label: 'Rol',
          value: roleCode,
          options: [for (final r in roles) (value: r.code, label: r.name)],
          onChanged: (v) => setSheet(() => roleCode = v),
        ),
        if (formError != null) ...[const SizedBox(height: 12), Notice(tone: Tone.danger, text: formError)],
      ]),
      footer: (ctx, setSheet) => [
        Btn('Cancelar', variant: BtnVariant.secondary, onPressed: () => Navigator.pop(ctx)),
        Btn(
          editing == null ? (saving ? 'Creando…' : 'Crear') : (saving ? 'Guardando…' : 'Guardar'),
          loading: saving,
          onPressed: () async {
            if (editing == null && (name.trim().isEmpty || email.trim().isEmpty || password.length < 8)) {
              setSheet(() => formError = 'Completa nombre, correo y una contraseña temporal de al menos 8 caracteres.');
              return;
            }
            if (editing != null && (name.trim().isEmpty || email.trim().isEmpty)) {
              setSheet(() => formError = 'El nombre y el correo son obligatorios.');
              return;
            }
            setSheet(() {
              saving = true;
              formError = null;
            });
            try {
              if (editing == null) {
                await api.createUser(name.trim(), email.trim(), password, roleCode);
              } else {
                await api.updateUser(editing.id, name.trim(), email.trim(), roleCode);
              }
              if (ctx.mounted) Navigator.pop(ctx);
              if (mounted) showToast(context, editing == null ? 'Usuario creado.' : 'Usuario actualizado.');
              loadUsers();
            } catch (err) {
              setSheet(() {
                saving = false;
                formError = err is ApiError
                    ? '${editing == null ? 'No se pudo crear el usuario' : 'No se pudo actualizar'}: ${err.message}'
                    : 'No se pudo conectar con el servidor. Lo capturado sigue aquí.';
              });
            }
          },
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    if (!auth.can('USER_MANAGE')) {
      return const Restricted(
        title: 'Usuarios y permisos',
        route: '/usuarios',
        description: 'Solo un administrador puede ver y gestionar los usuarios y sus permisos. Si necesitas acceso, pídeselo a un administrador.',
      );
    }
    return AppScaffold(
      title: 'Usuarios y permisos',
      route: '/usuarios',
      bottom: TabBar(controller: tabs, isScrollable: true, tabs: const [Tab(text: 'Usuarios'), Tab(text: 'Roles y permisos')]),
      fab: FloatingActionButton.extended(
        backgroundColor: Brand.gold,
        foregroundColor: Brand.ink,
        onPressed: () {
          tabs.animateTo(0);
          openForm();
        },
        icon: const Icon(Icons.add),
        label: const Text('Crear usuario'),
      ),
      body: TabBarView(controller: tabs, children: [_usersTab(auth), _rolesTab()]),
    );
  }

  Widget _usersTab(AuthState auth) {
    final c = context.c;
    if (users == null && loadError != null) {
      return PageBody(children: [
        loadError!.forbidden
            ? StateMessage(kind: StateKind.restricted, title: 'Acceso restringido', description: loadError!.message)
            : LoadError(title: 'No se pudo cargar la lista de usuarios', message: loadError!.message, onRetry: loadUsers),
      ]);
    }
    if (users == null) return const PageBody(children: [StateMessage(kind: StateKind.loading, title: 'Cargando usuarios…')]);
    final q = query.trim().toLowerCase();
    final filtered = users!.where((u) => (roleFilter.isEmpty || u.roleCode == roleFilter) && (q.isEmpty || u.name.toLowerCase().contains(q) || u.email.toLowerCase().contains(q))).toList();
    final allLoaded = users!.length >= totalUsers;
    final active = users!.where((u) => u.status == 'ACTIVE').length;
    return PageBody(
      onRefresh: loadUsers,
      bottomPadding: 96,
      subtitle: 'Organiza al personal interno y controla sus accesos. Los propietarios no tienen cuenta ni aparecen aquí.',
      children: [
        StatGrid(children: [
          StatCard(icon: Icons.group_outlined, value: '$totalUsers', label: totalUsers == 1 ? 'usuario' : 'usuarios'),
          StatCard(
            icon: Icons.check_circle_outline,
            tone: Tone.success,
            value: allLoaded ? '$active' : '—',
            label: allLoaded ? (active == 1 ? 'activo' : 'activos') : 'activos (solo los primeros 100)',
          ),
        ]),
        const SizedBox(height: 16),
        AppTextField(hint: 'Buscar por nombre o correo', onChanged: (v) => setState(() => query = v)),
        const SizedBox(height: 10),
        AppSelect<String>(
          value: roleFilter,
          options: [(value: '', label: 'Todos los roles'), for (final r in roles) (value: r.code, label: r.name)],
          onChanged: (v) => setState(() => roleFilter = v),
        ),
        const SizedBox(height: 16),
        if (loadError != null) ...[Notice(tone: Tone.danger, text: loadError!.message, action: Btn('Reintentar', small: true, variant: BtnVariant.secondary, onPressed: loadUsers)), const SizedBox(height: 12)],
        if (filtered.isEmpty)
          StateMessage(
            kind: users!.isEmpty ? StateKind.empty : StateKind.noResults,
            title: users!.isEmpty ? 'Aún no hay usuarios' : 'Sin resultados',
            description: users!.isEmpty ? 'Crea el primer usuario con el botón “Crear usuario”.' : 'Ningún usuario coincide con la búsqueda o el rol seleccionado.',
            action: users!.isNotEmpty
                ? Btn('Quitar filtros', variant: BtnVariant.secondary, onPressed: () => setState(() {
                      query = '';
                      roleFilter = '';
                    }))
                : null,
          )
        else
          ...spaced(filtered.map((u) => AppCard(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Avatar(u.name),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text.rich(TextSpan(children: [
                          TextSpan(text: u.name, style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
                          if (u.id == auth.user?.id) TextSpan(text: '  (tú)', style: TextStyle(fontSize: 12, color: c.muted)),
                        ])),
                        Text(u.email, style: TextStyle(fontSize: 12.5, color: c.muted)),
                      ]),
                    ),
                    u.status == 'ACTIVE'
                        ? const AppBadge('Activo', tone: Tone.success, icon: Icons.check_circle_outline)
                        : const AppBadge('Inactivo', icon: Icons.block),
                  ]),
                  const SizedBox(height: 12),
                  FieldGrid(children: [FieldView('Rol', u.roleName), FieldView('Última actividad', _lastActivityLabel(u.lastActivityAt))]),
                  const SizedBox(height: 12),
                  Wrap(spacing: 8, children: [
                    Btn('Editar', icon: Icons.edit_outlined, small: true, variant: BtnVariant.ghost, onPressed: () => openForm(u)),
                    Btn(
                      busyUserId == u.id ? 'Guardando…' : (u.status == 'ACTIVE' ? 'Desactivar' : 'Activar'),
                      small: true,
                      variant: u.status == 'ACTIVE' ? BtnVariant.danger : BtnVariant.secondary,
                      onPressed: busyUserId == u.id ? null : () => toggleStatus(u),
                    ),
                  ]),
                ]),
              )), 10),
        const SizedBox(height: 16),
        AppCard(
          child: Row(children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(color: Brand.gold.withValues(alpha: 0.2), shape: BoxShape.circle),
              child: Icon(Icons.verified_user_outlined, color: c.goldText),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Accesos por rol', style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
                const MutedText('Cada usuario tiene los permisos de su rol. Consulta qué puede hacer cada uno.', size: 13),
                LinkText('Ver roles y permisos', trailingIcon: Icons.arrow_forward, onTap: () => tabs.animateTo(1)),
              ]),
            ),
          ]),
        ),
      ],
    );
  }

  Widget _rolesTab() {
    final c = context.c;
    if (rolesError != null) {
      return PageBody(children: [
        LoadError(
            title: 'No se pudieron cargar los roles',
            message: rolesError!,
            onRetry: () {
              setState(() => rolesError = null);
              loadRoles();
            }),
      ]);
    }
    if (!rolesLoaded) return const PageBody(children: [StateMessage(kind: StateKind.loading, title: 'Cargando roles…')]);
    return PageBody(
      onRefresh: loadRoles,
      bottomPadding: 96,
      children: [
        ...spaced(roles.map((r) => AppCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(r.name, style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
                const SizedBox(height: 4),
                MutedText(r.description, size: 13),
                const SizedBox(height: 6),
                MutedText('${r.permissions.length} permiso${r.permissions.length == 1 ? '' : 's'}', size: 12),
              ]),
            )), 10),
        const SizedBox(height: 20),
        const SectionTitle('Permisos incluidos en cada rol'),
        ...permissions.map((p) => Container(
              margin: const EdgeInsets.only(bottom: 8),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: c.card, borderRadius: BorderRadius.circular(12), border: Border.all(color: c.border)),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(p.description, style: TextStyle(color: c.fg, fontSize: 14)),
                const SizedBox(height: 8),
                Wrap(spacing: 6, runSpacing: 6, children: [
                  for (final r in roles)
                    r.permissions.contains(p.code) ? AppBadge(r.name, tone: Tone.success, icon: Icons.check) : AppBadge(r.name, icon: Icons.remove),
                ]),
              ]),
            )),
      ],
    );
  }
}
