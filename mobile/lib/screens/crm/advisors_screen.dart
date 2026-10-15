import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../api/api.dart' as api;
import '../../api/client.dart';
import '../../api/models.dart';
import '../../labels.dart';
import '../../state/auth.dart';
import '../../ui/shell.dart';
import '../../ui/widgets.dart';

class AdvisorsScreen extends StatefulWidget {
  const AdvisorsScreen({super.key});

  @override
  State<AdvisorsScreen> createState() => _AdvisorsScreenState();
}

class _AdvisorsScreenState extends State<AdvisorsScreen> {
  List<AdvisorRouting>? advisors;
  AssignmentSettings? settings;
  String? error;
  bool savingSettings = false;

  @override
  void initState() {
    super.initState();
    load();
  }

  Future<void> load() async {
    api.getAssignmentSettings().then((s) => mounted ? setState(() => settings = s) : null).catchError((_) => null);
    try {
      final list = await api.listAdvisors();
      if (mounted) {
        setState(() {
          advisors = list;
          error = null;
        });
      }
    } catch (err) {
      if (mounted) setState(() => error = crmErrorText(err));
    }
  }

  Future<bool> save(AdvisorRouting row, Map<String, Object?> patch) async {
    final input = {
      'receivesLeads': row.receivesLeads,
      'weight': row.weight,
      'dailyLimit': row.dailyLimit,
      'online21UserId': row.online21UserId,
      'whatsappPhone': row.whatsappPhone,
      ...patch,
    };
    try {
      await api.updateAdvisor(row.userId, input);
      // Se recarga la lista para conservar los contadores de leads.
      final list = await api.listAdvisors();
      if (mounted) setState(() => advisors = list);
      return true;
    } catch (err) {
      if (mounted) showToast(context, crmErrorText(err));
      return false;
    }
  }

  Future<void> saveSettings(String roundMode, String policy) async {
    setState(() => savingSettings = true);
    try {
      final s = await api.updateAssignmentSettings(roundMode, policy);
      if (mounted) {
        setState(() => settings = s);
        showToast(context, 'Política de asignación guardada');
      }
    } catch (err) {
      if (mounted) showToast(context, crmErrorText(err));
    } finally {
      if (mounted) setState(() => savingSettings = false);
    }
  }

  Future<void> edit(AdvisorRouting a) async {
    var weight = '${a.weight}';
    var dailyLimit = a.dailyLimit?.toString() ?? '';
    var online21 = a.online21UserId?.toString() ?? '';
    var phone = a.whatsappPhone ?? '';
    var saving = false;
    await showAppSheet(
      context,
      title: 'Configurar a ${a.name}',
      builder: (ctx, setSheet) {
        final w = int.tryParse(weight);
        final valid = w != null && w >= 0 && w <= 100;
        return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          AppTextField(
            label: 'Peso en la ronda ponderada (0–100)',
            value: weight,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            helper: 'Con peso 10 recibe el doble que con peso 5.',
            error: valid ? null : 'El peso debe ser un número entero de 0 a 100.',
            onChanged: (v) => setSheet(() => weight = v),
          ),
          const SizedBox(height: 12),
          AppTextField(
            label: 'Límite diario',
            value: dailyLimit,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            helper: 'Por día de Ciudad de México. Vacío = sin límite.',
            onChanged: (v) => dailyLimit = v,
          ),
          const SizedBox(height: 12),
          AppTextField(
            label: 'Usuario en 21Online',
            value: online21,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            helper: 'Id numérico del asesor en 21Online: así se le priorizan los leads de sus propiedades.',
            onChanged: (v) => online21 = v,
          ),
          const SizedBox(height: 12),
          AppTextField(
            label: 'WhatsApp para avisos',
            value: phone,
            keyboardType: TextInputType.phone,
            hint: '+52 777 123 4567',
            helper: 'Recibe aquí el aviso de cada lead asignado.',
            onChanged: (v) => phone = v,
          ),
          const SizedBox(height: 12),
          InkWell(
            onTap: () {
              Navigator.pop(ctx);
              context.push('/calendario?asesor=${a.userId}');
            },
            child: Text.rich(TextSpan(children: [
              TextSpan(text: 'Los horarios se configuran en el ', style: TextStyle(color: ctx.c.muted)),
              TextSpan(text: 'calendario', style: TextStyle(color: ctx.c.goldText, fontWeight: FontWeight.w600)),
              TextSpan(text: '.', style: TextStyle(color: ctx.c.muted)),
            ])),
          ),
        ]);
      },
      footer: (ctx, setSheet) {
        final w = int.tryParse(weight);
        final valid = w != null && w >= 0 && w <= 100;
        return [
          Btn('Cancelar', variant: BtnVariant.secondary, onPressed: () => Navigator.pop(ctx)),
          Btn(saving ? 'Guardando…' : 'Guardar', loading: saving, onPressed: !valid
              ? null
              : () async {
                  setSheet(() => saving = true);
                  final ok = await save(a, {
                    'weight': w,
                    'dailyLimit': dailyLimit.isEmpty ? null : int.parse(dailyLimit),
                    'online21UserId': online21.isEmpty ? null : int.parse(online21),
                    'whatsappPhone': phone.trim().isEmpty ? null : phone.trim(),
                  });
                  if (ok && ctx.mounted) {
                    Navigator.pop(ctx);
                    if (mounted) showToast(context, 'Configuración guardada');
                  } else {
                    setSheet(() => saving = false);
                  }
                }),
        ];
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final canManage = auth.can('ADVISOR_ROUTING_MANAGE');
    if (!canManage) return const Restricted(title: 'Asesores', route: '/asesores', description: 'Tu usuario no tiene permiso para configurar a los asesores.');
    final list = advisors;
    final activeWeight = (list ?? []).where((a) => a.receivesLeads && a.weight > 0).fold<int>(0, (s, a) => s + a.weight);
    final equalMode = settings?.roundMode != 'WEIGHTED';
    return AppScaffold(
      title: 'Asesores',
      route: '/asesores',
      actions: [IconButton(tooltip: 'Calendario', icon: const Icon(Icons.calendar_month_outlined), onPressed: () => context.push('/calendario'))],
      body: PageBody(
        onRefresh: load,
        subtitle: 'Quién recibe leads, su estado según el calendario, su cupo y su participación en la ronda.',
        children: [
          if (settings != null) ...[_SettingsCard(settings: settings!, canManage: canManage, saving: savingSettings, onSave: saveSettings), const SizedBox(height: 16)],
          if (error != null && list == null)
            LoadError(message: error!, onRetry: load)
          else if (list == null)
            const StateMessage(kind: StateKind.loading, title: 'Cargando asesores…')
          else if (list.isEmpty)
            StateMessage(
              kind: StateKind.empty,
              title: 'No hay asesores',
              description: 'Da de alta a los asesores en Usuarios y permisos; después configura aquí quién recibe leads y en el Calendario sus turnos.',
              action: LinkText('Ir a usuarios', onTap: () => context.go('/usuarios')),
            )
          else
            ...spaced(list.map((a) => _AdvisorCard(
                  advisor: a,
                  equalMode: equalMode,
                  share: a.receivesLeads && activeWeight > 0 ? (a.weight * 100 / activeWeight).round() : 0,
                  onToggle: (v) => save(a, {'receivesLeads': v}),
                  onEdit: () => edit(a),
                )), 10),
        ],
      ),
    );
  }
}

class _AdvisorCard extends StatelessWidget {
  const _AdvisorCard({required this.advisor, required this.equalMode, required this.share, required this.onToggle, required this.onEdit});
  final AdvisorRouting advisor;
  final bool equalMode;
  final int share;
  final ValueChanged<bool> onToggle;
  final VoidCallback onEdit;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final a = advisor;
    return AppCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Avatar(a.name),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(a.name, style: TextStyle(fontWeight: FontWeight.w600, color: a.receivesLeads ? c.fg : c.muted), overflow: TextOverflow.ellipsis),
              Wrap(spacing: 8, children: [
                MutedText(label(roleLabels, a.roleCode), size: 12),
                a.online21UserId != null
                    ? MutedText('21Online #${a.online21UserId}', size: 12)
                    : Text('Sin usuario de 21Online', style: TextStyle(fontSize: 12, color: c.warningText)),
              ]),
            ]),
          ),
          IconButton(
            tooltip: 'Ver su calendario',
            icon: const Icon(Icons.calendar_month_outlined, size: 20),
            onPressed: () => context.push('/calendario?asesor=${a.userId}'),
          ),
          IconButton(tooltip: 'Configurar a ${a.name}', icon: const Icon(Icons.edit_outlined, size: 20), onPressed: onEdit),
        ]),
        const SizedBox(height: 10),
        Wrap(spacing: 6, runSpacing: 4, children: [
          AppBadge(label(advisorStatusLabels, a.status), tone: advisorStatusTones[a.status] ?? Tone.neutral),
          if (a.currentShiftKind == 'GUARD') const AppBadge('Guardia', tone: Tone.info),
          if (a.needsCalendar) const AppBadge('Completar calendario', tone: Tone.danger),
        ]),
        const SizedBox(height: 4),
        MutedText(
          '${a.currentShiftEnd != null ? 'En turno hasta ${hhmm(a.currentShiftEnd!)}' : (a.nextShift != null ? 'Próximo turno: ${formatLocal(a.nextShift!)}' : a.statusDetail)}'
          '${a.needsCalendar && a.legacyShift != null ? ' · horario anterior ${a.legacyShift} (sin días definidos)' : ''}',
          size: 12,
        ),
        const SizedBox(height: 10),
        FieldGrid(children: [
          FieldView('Hoy', a.dailyLimit != null ? '${a.assignedToday} de ${a.dailyLimit}' : '${a.assignedToday} · sin límite'),
          FieldView('Ronda', equalMode ? (a.receivesLeads ? 'Participa (equitativa)' : 'No participa') : 'Peso ${a.weight}${a.receivesLeads ? ' · ≈$share%' : ''}'),
        ]),
        const SizedBox(height: 10),
        Wrap(spacing: 6, runSpacing: 4, children: [
          InkWell(onTap: () => context.push('/leads?asesor=${a.userId}'), child: AppBadge('${a.openLeads} en seguimiento', tone: Tone.info)),
          if (a.wonLeads > 0) AppBadge('${a.wonLeads} ganados', tone: Tone.success),
        ]),
        const SizedBox(height: 4),
        ToggleRow(label: a.receivesLeads ? 'Recibe leads' : 'En pausa', value: a.receivesLeads, onChanged: onToggle),
      ]),
    );
  }
}

class _SettingsCard extends StatelessWidget {
  const _SettingsCard({required this.settings, required this.canManage, required this.saving, required this.onSave});
  final AssignmentSettings settings;
  final bool canManage;
  final bool saving;
  final void Function(String roundMode, String policy) onSave;

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    final s = settings;
    Widget rule(IconData icon, String title, String text) => Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(color: Brand.gold.withValues(alpha: 0.2), shape: BoxShape.circle),
              child: Icon(icon, size: 18, color: c.goldText),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(title, style: TextStyle(fontWeight: FontWeight.w600, color: c.fg, fontSize: 13.5)),
                MutedText(text, size: 13),
              ]),
            ),
          ]),
        );
    return AppCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const CardHeader(title: '¿Cómo se asigna un lead?', description: businessTimezoneLabel),
        if (s.noUpcomingCoverage)
          Padding(
            padding: const EdgeInsets.only(bottom: 14),
            child: Notice(
              tone: Tone.danger,
              icon: Icons.warning_amber_rounded,
              text: 'No hay ningún asesor disponible ahora ni turnos próximos en el calendario: los leads nuevos quedarán pendientes sin fecha. Configura turnos.',
            ),
          )
        else if (s.pendingLeads > 0)
          Padding(
            padding: const EdgeInsets.only(bottom: 14),
            child: Notice(
              tone: Tone.warning,
              icon: Icons.warning_amber_rounded,
              text:
                  '${s.pendingLeads} lead${s.pendingLeads == 1 ? '' : 's'} en espera de turno${s.nextCoverage != null ? '; el próximo turno disponible empieza ${formatLocal(s.nextCoverage!)}.' : '.'}',
              action: Btn('Ver', small: true, variant: BtnVariant.secondary, onPressed: () => context.push('/leads?grupo=PENDING')),
            ),
          ),
        rule(Icons.home_outlined, 'Lead de una propiedad con asesor vinculado',
            'Tiene prioridad el asesor de la propiedad en 21Online. Un cliente que ya tiene asesor lo conserva.'),
        rule(Icons.shuffle, 'Sin propiedad o sin asesor vinculado',
            'Ronda entre quienes están en un turno habilitado, sin ausencia, sin pausa y bajo su límite. Sin nadie disponible: “Pendiente por horario”.'),
        const SizedBox(height: 4),
        Text('Ronda', style: TextStyle(fontWeight: FontWeight.w500, color: c.fg)),
        const SizedBox(height: 8),
        ChoiceTile(
          selected: s.roundMode == 'EQUAL',
          enabled: canManage && !saving,
          onTap: () => onSave('EQUAL', s.ownerOffShiftPolicy),
          title: 'Equitativa',
          subtitle: 'Le toca a quien recibió hace más tiempo.',
        ),
        const SizedBox(height: 8),
        ChoiceTile(
          selected: s.roundMode == 'WEIGHTED',
          enabled: canManage && !saving,
          onTap: () => onSave('WEIGHTED', s.ownerOffShiftPolicy),
          title: 'Ponderada',
          subtitle: 'Reparte en proporción al peso de cada asesor.',
        ),
        const SizedBox(height: 16),
        Text('Si el asesor de la propiedad está fuera de horario', style: TextStyle(fontWeight: FontWeight.w500, color: c.fg)),
        const SizedBox(height: 8),
        ChoiceTile(
          selected: s.ownerOffShiftPolicy == 'RESPECT_OWNER',
          enabled: canManage && !saving,
          onTap: () => onSave(s.roundMode, 'RESPECT_OWNER'),
          title: 'Respetar al asesor de la propiedad (predeterminado)',
          subtitle: 'Queda reservado para él y pendiente de su próximo turno.',
        ),
        const SizedBox(height: 8),
        ChoiceTile(
          selected: s.ownerOffShiftPolicy == 'ROUTE_TO_GUARD',
          enabled: canManage && !saving,
          onTap: () => onSave(s.roundMode, 'ROUTE_TO_GUARD'),
          title: 'Canalizar a un asesor de guardia',
          subtitle: 'Lo atiende alguien disponible (prefiere guardias); se conserva la referencia del asesor de la propiedad.',
        ),
        if (s.updatedByName != null) ...[const SizedBox(height: 10), MutedText('Último cambio por ${s.updatedByName}.', size: 12)],
      ]),
    );
  }
}
