import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/client.dart';
import '../state/auth.dart';
import '../ui/widgets.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  String email = '';
  String password = '';
  String? error;
  bool submitting = false;

  Future<void> submit() async {
    if (email.trim().isEmpty || password.isEmpty) {
      setState(() => error = 'Escribe tu correo y tu contraseña.');
      return;
    }
    setState(() {
      error = null;
      submitting = true;
    });
    try {
      await context.read<AuthState>().login(email.trim(), password);
    } catch (err) {
      setState(() {
        if (err is ApiError && (err.status == 401 || err.status == 400)) {
          error = 'Correo o contraseña incorrectos.';
        } else if (err is NetworkError) {
          error = 'No se pudo conectar con el servidor. Intenta de nuevo en unos segundos.';
        } else {
          error = 'Ocurrió un error inesperado al iniciar sesión.';
        }
      });
    } finally {
      if (mounted) setState(() => submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = context.c;
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 32),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: AutofillGroup(
                child: Column(children: [
                  const BrandLogo(),
                  const SizedBox(height: 28),
                  AppCard(
                    padding: const EdgeInsets.all(24),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                      Center(
                        child: Container(
                          width: 56,
                          height: 56,
                          decoration: BoxDecoration(color: Brand.gold.withValues(alpha: 0.15), borderRadius: BorderRadius.circular(16)),
                          child: Icon(Icons.lock_person_outlined, color: c.goldText, size: 28),
                        ),
                      ),
                      const SizedBox(height: 14),
                      Text('Acceso de personal interno', textAlign: TextAlign.center, style: TextStyle(fontSize: 19, fontWeight: FontWeight.w600, color: c.fg)),
                      const SizedBox(height: 6),
                      const MutedText(
                        'Este acceso es exclusivo para personal autorizado de CENTURY 21 Genera. Los propietarios no requieren cuenta: reciben una liga directa a su expediente.',
                        center: true,
                      ),
                      const SizedBox(height: 22),
                      AppTextField(
                        label: 'Correo electrónico',
                        hint: 'admin@demo.local',
                        keyboardType: TextInputType.emailAddress,
                        autofillHints: const [AutofillHints.email],
                        textInputAction: TextInputAction.next,
                        onChanged: (v) => email = v,
                      ),
                      const SizedBox(height: 14),
                      AppTextField(
                        label: 'Contraseña',
                        hint: '••••••••',
                        obscure: true,
                        autofillHints: const [AutofillHints.password],
                        textInputAction: TextInputAction.done,
                        onChanged: (v) => password = v,
                        onSubmitted: (_) => submit(),
                      ),
                      if (error != null) ...[const SizedBox(height: 12), Text(error!, style: TextStyle(color: c.dangerText, fontSize: 13.5))],
                      const SizedBox(height: 20),
                      Btn(submitting ? 'Entrando...' : 'Entrar', large: true, expand: true, loading: submitting, onPressed: submit),
                    ]),
                  ),
                  const SizedBox(height: 20),
                  const MutedText('Cada Oficina es de Propiedad y Operación Independiente.', size: 11.5, center: true),
                ]),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
