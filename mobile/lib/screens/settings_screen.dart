import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/api.dart' as api;
import '../api/client.dart';
import '../labels.dart';
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
      title: 'Mi perfil y configuración',
      route: '/configuracion',
      body: PageBody(
        subtitle: 'Tu cuenta, tu contraseña y las preferencias de apariencia (estas se guardan en este dispositivo).',
        children: spaced([
          const _AccountCard(),
          const _ChangePasswordCard(),
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

/// Control de Cambios #25: "Mi perfil". Los datos de la cuenta y el rol los administra el
/// administrador (Usuarios y permisos); aquí solo se consultan.
class _AccountCard extends StatelessWidget {
  const _AccountCard();

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthState>();
    final user = auth.user;
    return AppCard(
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const CardHeader(title: 'Mi perfil', description: 'Datos de la cuenta con la sesión activa.'),
        FieldGrid(children: [
          FieldView('Nombre', user?.name ?? '—', upper: true),
          FieldView('Correo de acceso', user?.email ?? '—', upper: true),
          FieldView('Rol', user == null ? '—' : label(roleLabels, user.role), upper: true),
        ]),
        const SizedBox(height: 12),
        MutedText(
          auth.can('USER_MANAGE')
              ? 'Tu nombre, correo y rol se cambian desde Usuarios y permisos (tu propio rol solo lo puede cambiar otro administrador).'
              : 'Para cambiar tu nombre, correo o rol, pídeselo al administrador.',
          size: 13,
        ),
      ]),
    );
  }
}

/// Control de Cambios #25: cada usuario cambia su contraseña escribiendo la actual. Se cierran sus
/// demás sesiones y este dispositivo continúa con una sesión nueva.
class _ChangePasswordCard extends StatefulWidget {
  const _ChangePasswordCard();

  @override
  State<_ChangePasswordCard> createState() => _ChangePasswordCardState();
}

class _ChangePasswordCardState extends State<_ChangePasswordCard> {
  static const _minLength = 10;
  var current = '';
  var password = '';
  var confirm = '';
  var submitted = false;
  var saving = false;
  String? error;
  int _formKey = 0;

  String? get problem {
    if (current.isEmpty) return 'Escribe tu contraseña actual.';
    if (password.length < _minLength) return 'Usa al menos $_minLength caracteres. Una frase corta es fácil de recordar y difícil de adivinar.';
    if (password.trim().isEmpty) return 'La contraseña no puede ser solo espacios.';
    final email = context.read<AuthState>().user?.email ?? '';
    final local = email.split('@').first.toLowerCase();
    if (local.length >= 4 && password.toLowerCase().contains(local)) return 'La contraseña no debe contener tu correo.';
    if (password != confirm) return 'Las dos contraseñas no coinciden.';
    return null;
  }

  Future<void> save() async {
    setState(() => submitted = true);
    if (problem != null) return;
    setState(() {
      saving = true;
      error = null;
    });
    try {
      final auth = await api.changeOwnPassword(current, password);
      await ApiClient.instance.storeTokens('${auth['accessToken']}', auth['refreshToken'] as String?);
      if (!mounted) return;
      setState(() {
        current = '';
        password = '';
        confirm = '';
        submitted = false;
        _formKey++;
      });
      showToast(context, 'Contraseña cambiada. Se cerraron tus sesiones en otros dispositivos.');
    } catch (err) {
      setState(() => error = errorText(err));
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AppCard(
      child: Column(key: ValueKey(_formKey), crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const CardHeader(title: 'Cambiar contraseña', description: 'Al cambiarla se cierran tus sesiones abiertas en otros dispositivos.'),
        AppTextField(label: 'Contraseña actual', obscure: true, autofillHints: const [AutofillHints.password], onChanged: (v) => setState(() => current = v)),
        const SizedBox(height: 12),
        AppTextField(
          label: 'Nueva contraseña',
          obscure: true,
          maxLength: 128,
          autofillHints: const [AutofillHints.newPassword],
          helper: 'Mínimo $_minLength caracteres. No necesita símbolos; evita tu nombre o datos obvios.',
          onChanged: (v) => setState(() => password = v),
        ),
        const SizedBox(height: 12),
        AppTextField(
          label: 'Confirma la contraseña',
          obscure: true,
          maxLength: 128,
          error: submitted ? problem : null,
          onChanged: (v) => setState(() => confirm = v),
        ),
        if (error != null) ...[const SizedBox(height: 12), Notice(tone: Tone.danger, text: error)],
        const SizedBox(height: 8),
        const MutedText('¿No recuerdas la actual? Pide al administrador un enlace para restablecerla.', size: 12.5),
        const SizedBox(height: 12),
        Align(alignment: Alignment.centerRight, child: Btn('Cambiar contraseña', loading: saving, onPressed: save)),
      ]),
    );
  }
}
