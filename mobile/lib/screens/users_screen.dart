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

String _dateTime(String iso) {
  final date = DateTime.tryParse(iso)?.toLocal();
  return date == null ? iso : DateFormat("d MMM y, HH:mm", 'es_MX').format(date);
}

const _statusFilters = [
  (value: '', label: 'Cuentas vigentes'),
  (value: 'ACTIVE', label: 'Activas'),
  (value: 'PENDING', label: 'Pendientes de contraseña'),
  (value: 'INACTIVE', label: 'Desactivadas'),
  (value: 'OFFBOARDED', label: 'Dadas de baja'),
];

const _auditLabels = {
  'UserCREATED': 'Alta',
  'UserUPDATED': 'Edición',
  'UserROLE_CHANGED': 'Cambio de rol',
  'UserDEACTIVATED': 'Desactivación',
  'UserACTIVATED': 'Reactivación',
  'UserOFFBOARDED': 'Baja',
  'UserINVITE_LINK_ISSUED': 'Enlace de alta',
  'UserRESET_LINK_ISSUED': 'Restablecimiento',
  'UserPASSWORD_SET': 'Contraseña definida',
  'UserPASSWORD_CHANGED': 'Cambio de contraseña',
  'UserLOCKED': 'Bloqueo',
  'UserUNLOCKED': 'Desbloqueo',
};

/// Control de Cambios #25 (como /usuarios en la web): el administrador da de alta (la persona define
/// su contraseña con un enlace), edita, genera enlaces de restablecimiento, desbloquea, desactiva o
/// da de baja. Nunca ve ni escribe la contraseña de nadie.
class UsersScreen extends StatefulWidget {
  const UsersScreen({super.key});

  @override
  State<UsersScreen> createState() => _UsersScreenState();
}

class _UsersScreenState extends State<UsersScreen> with SingleTickerProviderStateMixin {
  late final bool canAudit = context.read<AuthState>().can('AUDIT_VIEW');
  late final tabs = TabController(length: canAudit ? 3 : 2, vsync: this);
  List<UserResponse>? users;
  int totalUsers = 0;
  ({bool forbidden, String message})? loadError;
  List<RoleResponse> roles = [];
  List<PermissionResponse> permissions = [];
  String? rolesError;
  bool rolesLoaded = false;
  String query = '';
  String roleFilter = '';
  String statusFilter = '';
  String? busyUserId;
  List<AuditEvent>? audit;
  String? auditError;

  @override
  void initState() {
    super.initState();
    loadUsers();
    loadRoles();
    if (canAudit) loadAudit();
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

  Future<void> loadAudit() async {
    try {
      final page = await api.listAccountEvents(0, 100);
      setState(() {
        audit = page.items;
        auditError = null;
      });
    } catch (err) {
      setState(() => auditError = errorText(err));
    }
  }

  /// Ejecuta una acción sobre una cuenta con su estado de "guardando" y el aviso correspondiente.
  Future<void> act(UserResponse user, Future<void> Function() action, String success) async {
    if (busyUserId != null) return;
    setState(() => busyUserId = user.id);
    try {
      await action();
      if (mounted) showToast(context, success);
      await loadUsers();
      if (canAudit) loadAudit();
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    } finally {
      if (mounted) setState(() => busyUserId = null);
    }
  }

  Future<void> openForm([UserResponse? editing]) async {
    final me = context.read<AuthState>().user;
    final isMe = editing != null && editing.id == me?.id;
    var name = editing?.name ?? '';
    var email = editing?.email ?? '';
    var roleCode = editing?.roleCode ?? 'ADVISOR';
    String? formError;
    var saving = false;
    await showAppSheet(
      context,
      title: editing == null ? 'Crear usuario' : 'Editar usuario',
      builder: (ctx, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        AppTextField(label: 'Nombre completo', value: name, onChanged: (v) => name = v),
        const SizedBox(height: 12),
        AppTextField(
          label: 'Correo',
          value: email,
          keyboardType: TextInputType.emailAddress,
          helper: editing == null ? 'Con este correo entrará al sistema.' : null,
          onChanged: (v) => email = v,
        ),
        const SizedBox(height: 12),
        AppSelect<String>(
          label: 'Rol',
          value: roleCode,
          enabled: !isMe,
          options: [for (final r in roles) (value: r.code, label: r.name)],
          onChanged: (v) => setSheet(() => roleCode = v),
        ),
        if (isMe) ...[const SizedBox(height: 6), const MutedText('No puedes cambiar tu propio rol; pídeselo a otro administrador.', size: 12.5)],
        if (editing == null) ...[
          const SizedBox(height: 12),
          const Notice(
            tone: Tone.info,
            text: 'No necesitas inventarle una contraseña: al crear la cuenta obtendrás un enlace para enviárselo y que la persona defina la suya.',
          ),
        ],
        if (formError != null) ...[const SizedBox(height: 12), Notice(tone: Tone.danger, text: formError)],
      ]),
      footer: (ctx, setSheet) => [
        Btn('Cancelar', variant: BtnVariant.secondary, onPressed: () => Navigator.pop(ctx)),
        Btn(
          editing == null ? (saving ? 'Creando…' : 'Crear y obtener enlace') : (saving ? 'Guardando…' : 'Guardar'),
          loading: saving,
          onPressed: () async {
            if (name.trim().isEmpty || email.trim().isEmpty) {
              setSheet(() => formError = 'El nombre y el correo son obligatorios.');
              return;
            }
            setSheet(() {
              saving = true;
              formError = null;
            });
            try {
              if (editing == null) {
                final created = await api.createUser(name.trim(), email.trim(), roleCode);
                if (ctx.mounted) Navigator.pop(ctx);
                loadUsers();
                if (mounted) await showLink(created.user.name, created.user.email, created.link);
              } else {
                await api.updateUser(editing.id, name.trim(), email.trim(), roleCode);
                if (ctx.mounted) Navigator.pop(ctx);
                if (mounted) showToast(context, roleCode != editing.roleCode ? 'Usuario actualizado. El nuevo rol aplica de inmediato.' : 'Usuario actualizado.');
                loadUsers();
              }
              if (canAudit) loadAudit();
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

  /// El enlace solo se muestra una vez: el administrador lo copia o lo comparte (WhatsApp, etc.).
  Future<void> showLink(String name, String email, SetupLink link) async {
    final expires = _dateTime(link.expiresAt);
    final first = name.split(' ').first;
    final message = link.invite
        ? 'Hola $first, ya tienes cuenta en el sistema de CENTURY 21 Genera. Entra a este enlace para crear tu contraseña (vence el $expires): ${link.url}'
        : 'Hola $first, este es tu enlace para definir una contraseña nueva en el sistema de CENTURY 21 Genera (vence el $expires y solo funciona una vez): ${link.url}';
    await showAppSheet(
      context,
      title: link.invite ? 'Cuenta creada' : 'Enlace de restablecimiento',
      builder: (ctx, setSheet) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        MutedText('Envía este enlace a $name ($email) para que ${link.invite ? 'cree' : 'defina'} su contraseña. Vence el $expires y solo funciona una vez.'),
        const SizedBox(height: 12),
        SelectableText(link.url, style: TextStyle(fontSize: 13, color: ctx.c.fg)),
        const SizedBox(height: 12),
        Wrap(spacing: 8, runSpacing: 8, children: [
          Btn('Copiar enlace', icon: Icons.copy, variant: BtnVariant.secondary, onPressed: () => copyText(ctx, link.url, 'Enlace copiado.')),
          Btn('Enviar por WhatsApp', icon: Icons.chat_outlined, onPressed: () => openUrl(ctx, 'https://wa.me/?text=${Uri.encodeComponent(message)}')),
        ]),
        const SizedBox(height: 12),
        Notice(
          tone: Tone.warning,
          text: 'Por seguridad, este enlace no se vuelve a mostrar. Si se pierde o vence, genera uno nuevo con “${link.invite ? 'Nuevo enlace de alta' : 'Restablecer contraseña'}”.',
        ),
      ]),
      footer: (ctx, setSheet) => [Btn('Listo', onPressed: () => Navigator.pop(ctx))],
    );
  }

  Future<void> passwordLink(UserResponse user) async {
    final pending = user.status == 'PENDING';
    final ok = await confirmDialog(
      context,
      title: pending ? 'Nuevo enlace de alta' : 'Restablecer contraseña',
      message:
          'Se generará un enlace de un solo uso para que ${user.name} defina una contraseña nueva. Tú no verás ni escribirás su contraseña. Cualquier enlace anterior deja de funcionar${pending ? '' : '; su contraseña actual sigue valiendo hasta que use el enlace'} y al usarlo se cierran sus sesiones.',
      confirmLabel: 'Generar enlace',
    );
    if (!ok || !mounted) return;
    try {
      final link = await api.issuePasswordLink(user.id);
      loadUsers();
      if (canAudit) loadAudit();
      if (mounted) await showLink(user.name, user.email, link);
    } catch (err) {
      if (mounted) showToast(context, errorText(err));
    }
  }

  /// Antes de desactivar o dar de baja se muestra lo que la persona tiene a su cargo. Para la baja es
  /// obligatorio reasignarlo primero (el servidor también lo exige).
  Future<void> deactivateOrOffboard(UserResponse user, {required bool offboard}) async {
    List<WorkloadItem>? workload;
    try {
      workload = await api.getUserWorkload(user.id);
    } catch (_) {}
    if (!mounted) return;
    final hasWork = (workload?.isNotEmpty ?? false);
    final workText = hasWork ? 'Tiene a su cargo: ${workload!.map((w) => '${w.count} ${w.label.toLowerCase()}').join(', ')}.' : null;
    if (!offboard) {
      final ok = await confirmDialog(
        context,
        title: 'Desactivar cuenta',
        message:
            '${user.name} dejará de poder entrar de inmediato y se cerrarán sus sesiones. Puedes reactivarla después; su historial se conserva.${workText == null ? '' : '\n\n$workText Seguirán asignados a esta persona; considera reasignarlos.'}',
        confirmLabel: 'Desactivar',
        danger: true,
      );
      if (ok) await act(user, () => api.deactivateUser(user.id), '${user.name} quedó desactivado; ya no puede entrar.');
      return;
    }
    if (hasWork) {
      await confirmDialog(
        context,
        title: 'Primero reasigna',
        message: '$workText Reasígnalos a otra persona (desde cada expediente o lead) antes de darle de baja.',
        confirmLabel: 'Entendido',
      );
      return;
    }
    final reason = await askReason(
      context,
      title: 'Dar de baja',
      description:
          'La baja es definitiva: ${user.name} ya no podrá entrar ni reactivarse. No se borra nada: su nombre se conserva en el historial y en los expedientes.',
      label: 'Motivo de la baja',
      placeholder: 'Ej. Dejó de colaborar con la oficina.',
      danger: true,
      confirmLabel: 'Dar de baja definitivamente',
    );
    if (reason != null) await act(user, () => api.offboardUser(user.id, reason), '${user.name} quedó dado de baja.');
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    if (!auth.can('USER_MANAGE')) {
      return const Restricted(
        title: 'Usuarios y permisos',
        route: '/usuarios',
        description:
            'Solo un administrador puede ver y gestionar los usuarios y sus permisos. Si necesitas acceso, pídeselo a un administrador. Tu contraseña la cambias desde “Mi perfil”.',
      );
    }
    return AppScaffold(
      title: 'Usuarios y permisos',
      route: '/usuarios',
      bottom: TabBar(
        controller: tabs,
        isScrollable: true,
        tabs: [const Tab(text: 'Usuarios'), const Tab(text: 'Roles y permisos'), if (canAudit) const Tab(text: 'Bitácora')],
      ),
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
      body: TabBarView(controller: tabs, children: [_usersTab(auth), _rolesTab(), if (canAudit) _auditTab()]),
    );
  }

  Widget _statusBadge(UserResponse u) => switch (u.status) {
        'ACTIVE' => const AppBadge('Activa', tone: Tone.success, icon: Icons.check_circle_outline),
        'PENDING' => const AppBadge('Pendiente de contraseña', tone: Tone.warning, icon: Icons.schedule),
        'OFFBOARDED' => const AppBadge('Dada de baja', tone: Tone.danger, icon: Icons.person_off_outlined),
        _ => const AppBadge('Desactivada', icon: Icons.block),
      };

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
    final filtered = users!
        .where((u) =>
            (statusFilter.isEmpty ? u.status != 'OFFBOARDED' : u.status == statusFilter) &&
            (roleFilter.isEmpty || u.roleCode == roleFilter) &&
            (q.isEmpty || u.name.toLowerCase().contains(q) || u.email.toLowerCase().contains(q)))
        .toList();
    final allLoaded = users!.length >= totalUsers;
    final active = users!.where((u) => u.status == 'ACTIVE').length;
    final pending = users!.where((u) => u.status == 'PENDING').length;
    return PageBody(
      onRefresh: loadUsers,
      bottomPadding: 96,
      subtitle: 'Organiza al personal interno y controla sus accesos. Los propietarios no tienen cuenta ni aparecen aquí.',
      children: [
        StatGrid(children: [
          StatCard(icon: Icons.group_outlined, value: '$totalUsers', label: totalUsers == 1 ? 'cuenta' : 'cuentas'),
          StatCard(
            icon: Icons.check_circle_outline,
            tone: Tone.success,
            value: allLoaded ? '$active' : '—',
            label: allLoaded ? (active == 1 ? 'activa' : 'activas') : 'activas (solo las primeras 100)',
          ),
          StatCard(icon: Icons.schedule, tone: Tone.warning, value: allLoaded ? '$pending' : '—', label: 'pendientes de contraseña'),
        ]),
        const SizedBox(height: 16),
        AppTextField(hint: 'Buscar por nombre o correo', onChanged: (v) => setState(() => query = v)),
        const SizedBox(height: 10),
        AppSelect<String>(value: statusFilter, options: _statusFilters, onChanged: (v) => setState(() => statusFilter = v)),
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
            description: users!.isEmpty ? 'Crea el primer usuario con el botón “Crear usuario”.' : 'Ninguna cuenta coincide con la búsqueda o los filtros.',
            action: users!.isNotEmpty
                ? Btn('Quitar filtros', variant: BtnVariant.secondary, onPressed: () => setState(() {
                      query = '';
                      roleFilter = '';
                      statusFilter = '';
                    }))
                : null,
          )
        else
          ...spaced(filtered.map((u) {
            final isMe = u.id == auth.user?.id;
            final busy = busyUserId == u.id;
            return AppCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Avatar(u.name),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text.rich(TextSpan(children: [
                        TextSpan(text: u.name, style: TextStyle(fontWeight: FontWeight.w600, color: c.fg)),
                        if (isMe) TextSpan(text: '  (tú)', style: TextStyle(fontSize: 12, color: c.muted)),
                      ])),
                      Text(u.email, style: TextStyle(fontSize: 12.5, color: c.muted)),
                    ]),
                  ),
                ]),
                const SizedBox(height: 10),
                Wrap(spacing: 6, runSpacing: 6, children: [
                  _statusBadge(u),
                  if (u.locked) const AppBadge('Bloqueada por intentos fallidos', tone: Tone.danger, icon: Icons.lock_outline),
                ]),
                const SizedBox(height: 12),
                FieldGrid(children: [FieldView('Rol', u.roleName), FieldView('Última actividad', _lastActivityLabel(u.lastActivityAt))]),
                const SizedBox(height: 12),
                if (u.status == 'OFFBOARDED')
                  const MutedText('Sin acciones: la baja es definitiva.', size: 12.5)
                else
                  Wrap(spacing: 8, runSpacing: 8, children: [
                    Btn('Editar', icon: Icons.edit_outlined, small: true, variant: BtnVariant.ghost, onPressed: busy ? null : () => openForm(u)),
                    if (u.status != 'INACTIVE' && !isMe)
                      Btn(u.status == 'PENDING' ? 'Nuevo enlace de alta' : 'Restablecer contraseña',
                          icon: Icons.key_outlined, small: true, variant: BtnVariant.ghost, onPressed: busy ? null : () => passwordLink(u)),
                    if (u.locked)
                      Btn('Desbloquear', icon: Icons.lock_open, small: true, variant: BtnVariant.secondary,
                          onPressed: busy ? null : () => act(u, () => api.unlockUser(u.id), 'La cuenta de ${u.name} quedó desbloqueada.')),
                    if (!isMe)
                      u.status == 'INACTIVE'
                          ? Btn(busy ? 'Guardando…' : 'Reactivar', small: true, variant: BtnVariant.secondary,
                              onPressed: busy ? null : () => act(u, () => api.activateUser(u.id), '${u.name} quedó reactivado.'))
                          : Btn(busy ? 'Guardando…' : 'Desactivar', small: true, variant: BtnVariant.danger,
                              onPressed: busy ? null : () => deactivateOrOffboard(u, offboard: false)),
                    if (!isMe)
                      Btn('Dar de baja', icon: Icons.person_remove_outlined, small: true, variant: BtnVariant.ghost,
                          onPressed: busy ? null : () => deactivateOrOffboard(u, offboard: true)),
                  ]),
              ]),
            );
          }), 10),
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
                const MutedText('Cada usuario tiene los permisos de su rol. Consulta qué puede hacer cada uno y quién administra las cuentas.', size: 13),
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
        const AppCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            CardHeader(title: 'Administración de cuentas', description: 'Quién puede hacer cada acción sobre las cuentas.'),
            Bullets([
              'Alta, edición, cambio de rol, desactivación, baja y desbloqueo: solo el Administrador (nunca sobre su propia cuenta).',
              'Cambiar mi contraseña: cada usuario, desde Mi perfil (pide la actual).',
              'Olvidé mi contraseña: el Administrador genera un enlace de un solo uso (24 h) y se lo envía por WhatsApp.',
              'Nadie conoce la contraseña de otra persona; nunca se muestra ni se envía en texto.',
              'Mínimo 10 caracteres; tras 5 intentos fallidos la cuenta se bloquea 15 minutos.',
              'Cambiar o restablecer la contraseña cierra las demás sesiones; desactivar o dar de baja corta el acceso de inmediato.',
              'Una baja no borra nada: el nombre se conserva en el historial y en los expedientes. Antes hay que reasignar expedientes y leads.',
            ]),
          ]),
        ),
        const SizedBox(height: 16),
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

  /// Control de Cambios #25: quién hizo qué sobre qué cuenta y cuándo. Nunca contiene contraseñas.
  Widget _auditTab() {
    final c = context.c;
    if (audit == null && auditError != null) {
      return PageBody(children: [LoadError(title: 'No se pudo cargar la bitácora', message: auditError!, onRetry: loadAudit)]);
    }
    if (audit == null) return const PageBody(children: [StateMessage(kind: StateKind.loading, title: 'Cargando bitácora…')]);
    if (audit!.isEmpty) {
      return const PageBody(children: [
        StateMessage(kind: StateKind.empty, title: 'Sin movimientos', description: 'Aquí aparecerán las altas, cambios de rol, bajas y restablecimientos de contraseña.'),
      ]);
    }
    return PageBody(
      onRefresh: loadAudit,
      bottomPadding: 96,
      subtitle: 'Altas, cambios de rol y estado, restablecimientos y bloqueos. No se guarda ninguna contraseña.',
      children: spaced(
        audit!.map((e) => AppCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Expanded(child: Text(_auditLabels[e.eventType] ?? e.eventType, style: TextStyle(fontWeight: FontWeight.w600, color: c.goldText))),
                  MutedText(_dateTime(e.occurredAt), size: 12),
                ]),
                const SizedBox(height: 4),
                Text(e.summary, style: TextStyle(color: c.fg, fontSize: 14)),
                const SizedBox(height: 4),
                MutedText('Por: ${e.actorType == 'SYSTEM' ? 'el sistema (automático)' : (e.actorName ?? '—')}', size: 12),
              ]),
            )),
        8,
      ),
    );
  }
}
