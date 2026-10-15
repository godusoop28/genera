import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api.dart' as api;
import '../api/client.dart';
import '../state/auth.dart';
import '../state/settings.dart';
import '../ui/shell.dart';
import '../ui/widgets.dart';

const _advisorEvents = [
  'El cliente cargó todos sus documentos obligatorios',
  'El cliente cargó la corrección de un documento devuelto',
  'Un archivo no parece ser el documento solicitado o no es legible',
  'La documentación quedó aprobada',
  'El contrato quedó firmado por todas las partes',
  'El inmueble fue aceptado o rechazado',
];

const _clientEvents = [
  'Un documento fue devuelto o rechazado, con el motivo y qué corregir',
  'Una foto no se pudo leer y debe tomarse de nuevo',
  'Su documentación quedó completa',
  'Su liga personal para firmar el contrato',
  'El contrato quedó firmado',
];

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsState>();
    final c = context.c;
    return AppScaffold(
      title: 'Configuración',
      route: '/configuracion',
      body: PageBody(
        subtitle: 'Preferencias de apariencia y de la cuenta. La apariencia se guarda en este dispositivo.',
        children: spaced([
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const CardHeader(title: 'Tema', description: 'Elige la apariencia de la aplicación.'),
              for (final o in themeOptions) ...[
                ChoiceTile(selected: settings.theme == o.id, onTap: () => settings.setTheme(o.id), title: o.label, subtitle: o.description),
                const SizedBox(height: 8),
              ],
            ]),
          ),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const CardHeader(title: 'Tamaño de fuente', description: 'Ajusta el tamaño del texto y la interfaz en todo el sistema.'),
              Row(children: [
                for (final o in fontSizeOptions)
                  Expanded(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 3),
                      child: Material(
                        color: settings.fontSize == o.id ? Brand.gold.withValues(alpha: 0.15) : c.card,
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12), side: BorderSide(color: settings.fontSize == o.id ? Brand.gold : c.border, width: settings.fontSize == o.id ? 2 : 1)),
                        child: InkWell(
                          borderRadius: BorderRadius.circular(12),
                          onTap: () => settings.setFontSize(o.id),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            child: Column(children: [
                              Text('Aa', textScaler: TextScaler.noScaling, style: TextStyle(fontSize: 16 * o.scale, fontWeight: FontWeight.w600, color: c.fg)),
                              const SizedBox(height: 4),
                              Text(o.label, textScaler: TextScaler.noScaling, textAlign: TextAlign.center, style: TextStyle(fontSize: 11, color: c.muted)),
                            ]),
                          ),
                        ),
                      ),
                    ),
                  ),
              ]),
            ]),
          ),
          const _AccountCard(),
          AppCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const CardHeader(
                title: 'Notificaciones por correo',
                description:
                    'El sistema envía correos automáticamente cuando ocurre alguno de estos eventos. Cada envío, y cualquier error de entrega, queda registrado en la Bitácora del expediente.',
              ),
              Text('Al asesor del expediente', style: TextStyle(fontWeight: FontWeight.w500, color: c.fg)),
              const Bullets(_advisorEvents),
              const SizedBox(height: 12),
              Text('Al cliente', style: TextStyle(fontWeight: FontWeight.w500, color: c.fg)),
              const Bullets(_clientEvents),
            ]),
          ),
        ]),
      ),
    );
  }
}

class _AccountCard extends StatefulWidget {
  const _AccountCard();

  @override
  State<_AccountCard> createState() => _AccountCardState();
}

class _AccountCardState extends State<_AccountCard> {
  late final auth = context.read<AuthState>();
  late String name = auth.user?.name ?? '';
  late String email = auth.user?.email ?? '';
  bool saving = false;

  Future<void> save() async {
    final user = auth.user;
    if (user == null || name.trim().isEmpty || email.trim().isEmpty) return;
    setState(() => saving = true);
    try {
      await api.updateUser(user.id, name.trim(), email.trim(), user.role);
      await auth.refreshUser();
      if (mounted) showToast(context, 'Datos de la cuenta actualizados.');
    } catch (err) {
      if (mounted) showToast(context, err is ApiError ? 'No se pudo guardar (${err.status}): ${err.message}' : 'Error de conexión.');
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AppCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const CardHeader(title: 'Cuenta', description: 'Datos del personal interno con la sesión activa.'),
        AppTextField(label: 'Nombre completo', value: name, onChanged: (v) => setState(() => name = v)),
        const SizedBox(height: 12),
        AppTextField(label: 'Correo electrónico', value: email, keyboardType: TextInputType.emailAddress, hint: 'nombre@c21genera.com', onChanged: (v) => setState(() => email = v)),
        const SizedBox(height: 12),
        AppTextField(label: 'Rol', value: auth.user?.role ?? '', readOnly: true, enabled: false),
        const SizedBox(height: 16),
        Align(
          alignment: Alignment.centerRight,
          child: Btn('Guardar cambios', loading: saving, onPressed: name.trim().isEmpty || email.trim().isEmpty ? null : save),
        ),
      ]),
    );
  }
}
