// Recorre todas las pantallas con datos simulados (sin backend) en tamaño de
// celular: falla si alguna pantalla lanza un error o se desborda.
//
//   flutter test                                   # solo verificar
//   flutter test --dart-define=SCREENSHOTS=true --update-goldens   # además guarda capturas en test/goldens/

import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:genera_mobile/api/client.dart';
import 'package:genera_mobile/main.dart';
import 'package:genera_mobile/screens/expedientes/expedientes_screen.dart';
import 'package:genera_mobile/state/auth.dart';
import 'package:genera_mobile/state/settings.dart';
import 'package:genera_mobile/ui/theme.dart';
import 'package:go_router/go_router.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'fake_backend.dart';

const _screenshots = bool.fromEnvironment('SCREENSHOTS');

Future<void> _loadFonts() async {
  final sdk = Platform.environment['FLUTTER_ROOT'] ?? '${Platform.environment['USERPROFILE']}/flutter_sdk';
  final dir = '$sdk/bin/cache/artifacts/material_fonts';
  Future<void> load(String family, List<String> files) async {
    final loader = FontLoader(family);
    for (final f in files) {
      final file = File('$dir/$f');
      if (file.existsSync()) loader.addFont(Future.value(ByteData.sublistView(file.readAsBytesSync())));
    }
    await loader.load();
  }

  await load('Roboto', ['roboto-regular.ttf', 'roboto-medium.ttf', 'roboto-bold.ttf']);
  await load('MaterialIcons', ['materialicons-regular.otf']);
  Future<void> app(String family, List<String> files) async {
    final loader = FontLoader(family);
    for (final f in files) {
      loader.addFont(Future.value(ByteData.sublistView(File('assets/fonts/$f').readAsBytesSync())));
    }
    await loader.load();
  }

  await app('Geist', ['Geist-Regular.ttf', 'Geist-Medium.ttf', 'Geist-SemiBold.ttf', 'Geist-Bold.ttf']);
  await app('GeistMono', ['GeistMono-Regular.ttf', 'GeistMono-Medium.ttf']);
}

/// Abre una ruta y deja que carguen los datos simulados.
Future<void> _open(WidgetTester tester, String route, {ThemeId theme = ThemeId.light, FontSizeId fontSize = FontSizeId.md}) async {
  tester.view.physicalSize = const Size(1170, 2532);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  final auth = AuthState();
  await auth.restore();
  final settings = SettingsState()
    ..theme = theme
    ..fontSize = fontSize;
  await tester.pumpWidget(GeneraApp(auth: auth, settings: settings));
  await tester.pump();
  GoRouter.of(tester.element(find.byType(Navigator).first)).go(route);
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 300));
  }
}

Future<void> _close(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox());
  await tester.pump(const Duration(seconds: 10));
}

void main() {
  setUpAll(() async {
    await initializeDateFormatting('es_MX');
    // Fuentes reales siempre: así los desbordes se miden igual que en el celular.
    await _loadFonts();
  });

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    sentRequests.clear();
    FlutterSecureStorage.setMockInitialValues({'c21genera-access-token': 'token', 'c21genera-refresh-token': 'refresh'});
    ApiClient.instance.httpClient = MockClient((req) async {
      final sent = req is http.Request && req.body.isNotEmpty ? jsonDecode(req.body) : null;
      final (status, body) = fakeBackend(req.method, req.url, sent);
      return http.Response.bytes(utf8.encode(body == null ? '' : jsonEncode(body)), status, headers: {'content-type': 'application/json'});
    });
  });

  const routes = {
    'expedientes': '/expedientes',
    'expediente_nuevo': '/expedientes/nuevo',
    'expediente_resumen': '/expedientes/exp-1',
    'dashboard': '/dashboard',
    'leads': '/leads',
    'lead_detalle': '/leads/lead-1',
    'calendario': '/calendario',
    'conversaciones': '/conversaciones',
    'conversacion': '/conversaciones/conv-1',
    'asesores': '/asesores',
    'usuarios': '/usuarios',
    'configuracion': '/configuracion',
  };

  for (final entry in routes.entries) {
    testWidgets('pantalla ${entry.key}', (tester) async {
      tester.view.physicalSize = const Size(1170, 2532);
      tester.view.devicePixelRatio = 3;
      addTearDown(tester.view.reset);

      final auth = AuthState();
      await auth.restore();
      final settings = SettingsState();
      await tester.pumpWidget(GeneraApp(auth: auth, settings: settings));
      await tester.pump();
      GoRouter.of(tester.element(find.byType(Navigator).first)).go(entry.value);
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(milliseconds: 300));
      }
      expect(tester.takeException(), isNull);
      if (_screenshots) await expectLater(find.byType(GeneraApp), matchesGoldenFile('goldens/${entry.key}.png'));
      await tester.pumpWidget(const SizedBox());
      await tester.pump(const Duration(seconds: 10));
    });
  }

  testWidgets('pestañas del expediente', (tester) async {
    tester.view.physicalSize = const Size(1170, 2532);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final auth = AuthState();
    await auth.restore();
    await tester.pumpWidget(GeneraApp(auth: auth, settings: SettingsState()));
    await tester.pump();
    GoRouter.of(tester.element(find.byType(Navigator).first)).go('/expedientes/exp-1');
    for (var i = 0; i < 6; i++) {
      await tester.pump(const Duration(milliseconds: 300));
    }
    const tabs = ['Participantes', 'Documentos', 'Consistencia', 'Datos del contrato', 'Contrato y firmas', 'Seguimiento', 'Bitácora'];
    for (final t in tabs) {
      await tester.tap(find.text(t));
      for (var i = 0; i < 5; i++) {
        await tester.pump(const Duration(milliseconds: 300));
      }
      expect(tester.takeException(), isNull, reason: t);
      if (_screenshots) {
        await expectLater(find.byType(GeneraApp), matchesGoldenFile('goldens/expediente_${t.toLowerCase().replaceAll(' ', '_')}.png'));
      }
    }
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 10));
  });

  // Cada tema con las pantallas más representativas.
  for (final theme in ThemeId.values) {
    for (final route in ['/expedientes', '/expedientes/exp-1', '/dashboard', '/leads/lead-1', '/calendario', '/configuracion']) {
      testWidgets('tema ${theme.name} $route', (tester) async {
        await _open(tester, route, theme: theme);
        expect(tester.takeException(), isNull);
        if (_screenshots) {
          await expectLater(find.byType(GeneraApp), matchesGoldenFile('goldens/tema_${theme.name}${route.replaceAll('/', '_')}.png'));
        }
        await _close(tester);
      });
    }
  }

  // Letra "Extra grande": ninguna pantalla debe desbordarse.
  for (final route in [...routes.values, '/expedientes/exp-1']) {
    testWidgets('letra extra grande $route', (tester) async {
      await _open(tester, route, fontSize: FontSizeId.xl);
      final error = tester.takeException();
      if (_screenshots) await expectLater(find.byType(GeneraApp), matchesGoldenFile('goldens/letra_xl${route.replaceAll('/', '_')}.png'));
      expect(error, isNull);
      await _close(tester);
    });
  }

  testWidgets('login', (tester) async {
    FlutterSecureStorage.setMockInitialValues({});
    tester.view.physicalSize = const Size(1170, 2532);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final auth = AuthState();
    await auth.restore();
    await tester.pumpWidget(GeneraApp(auth: auth, settings: SettingsState()));
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.text('Acceso de personal interno'), findsOneWidget);
    if (_screenshots) await expectLater(find.byType(GeneraApp), matchesGoldenFile('goldens/login.png'));
  });

  // ---------------------------------------------------------------------------
  // Flujos: tocar botones y verificar lo que se manda al backend.
  // ---------------------------------------------------------------------------

  Future<void> settle(WidgetTester tester, [int n = 6]) async {
    for (var i = 0; i < n; i++) {
      await tester.pump(const Duration(milliseconds: 300));
    }
  }

  testWidgets('flujo: login con contraseña equivocada y luego correcta', (tester) async {
    FlutterSecureStorage.setMockInitialValues({});
    tester.view.physicalSize = const Size(1170, 2532);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final auth = AuthState();
    await auth.restore();
    await tester.pumpWidget(GeneraApp(auth: auth, settings: SettingsState()));
    await settle(tester, 2);
    final fields = find.byType(TextField);
    await tester.enterText(fields.at(0), 'admin@demo.local');
    await tester.enterText(fields.at(1), 'mala');
    await tester.tap(find.text('Entrar'));
    await settle(tester, 3);
    expect(find.text('Correo o contraseña incorrectos.'), findsOneWidget);
    await tester.enterText(fields.at(1), 'correcta');
    await tester.tap(find.text('Entrar'));
    await settle(tester);
    expect(find.text('María Fernanda López Gutiérrez'), findsWidgets, reason: 'debe entrar a Expedientes');
    await _close(tester);
  });

  testWidgets('flujo: menú lateral lleva a Leads', (tester) async {
    await _open(tester, '/expedientes');
    await tester.tap(find.byIcon(Icons.menu));
    await settle(tester, 2);
    expect(find.text('CRM'), findsOneWidget);
    await tester.tap(find.text('Leads').last);
    await settle(tester);
    expect(find.text('Roberto Castillo Núñez'), findsWidgets);
    await _close(tester);
  });

  testWidgets('flujo: vista de lista en Expedientes', (tester) async {
    await _open(tester, '/expedientes');
    await tester.tap(find.byTooltip('Ver como lista'));
    await settle(tester, 2);
    expect(find.byType(ExpedienteRow), findsNWidgets(2));
    await _close(tester);
  });

  testWidgets('flujo: aceptar un documento', (tester) async {
    await _open(tester, '/expedientes/exp-1');
    await tester.tap(find.text('Documentos'));
    await settle(tester);
    await Scrollable.ensureVisible(tester.element(find.text('Aceptar').first), alignment: 0.5);
    await settle(tester, 1);
    await tester.tap(find.text('Aceptar').first);
    await settle(tester);
    expect(sentRequests.any((r) => r.method == 'POST' && r.path == '/internal/documents/d-1/accept'), isTrue);
    await _close(tester);
  });

  testWidgets('flujo: registrar un lead', (tester) async {
    await _open(tester, '/leads');
    await tester.tap(find.text('Registrar lead'));
    await settle(tester, 3);
    // El primer campo dentro de la hoja "Registrar lead" (no el buscador de atrás).
    await tester.enterText(find.descendant(of: find.byType(BottomSheet), matching: find.byType(TextField)).first, 'Cliente Nuevo');
    await settle(tester, 1);
    await tester.tap(find.text('Registrar'));
    await settle(tester);
    final req = sentRequests.firstWhere((r) => r.path == '/internal/leads');
    expect((req.body as Map)['name'], 'Cliente Nuevo');
    expect((req.body as Map)['autoAssign'], isTrue);
    await _close(tester);
  });

  testWidgets('flujo: agregar un horario en el calendario', (tester) async {
    await _open(tester, '/calendario');
    await tester.tap(find.text('Agregar horario'));
    await settle(tester, 3);
    await tester.tap(find.text('Guardar'));
    await settle(tester);
    final req = sentRequests.firstWhere((r) => r.path == '/internal/crm/calendar/shifts');
    final body = req.body as Map;
    expect(body['recurrence'], 'WEEKLY');
    expect(body['days'], [1, 2, 3, 4, 5]);
    expect(body['startTime'], '09:00');
    await _close(tester);
  });

  testWidgets('flujo: crear un expediente en 3 pasos', (tester) async {
    await _open(tester, '/expedientes/nuevo');
    await tester.enterText(find.byType(TextField).first, 'Juan Pérez López');
    await tester.tap(find.text('Continuar'));
    await settle(tester);
    await tester.enterText(find.byType(TextField).first, 'Casa en Vista Hermosa');
    await tester.tap(find.text('Continuar'));
    await settle(tester);
    expect(find.text('Revisa antes de crear'), findsOneWidget);
    await tester.tap(find.text('Crear expediente'));
    await settle(tester, 8);
    final req = sentRequests.firstWhere((r) => r.path == '/internal/expedientes');
    final body = req.body as Map;
    expect(body['propertyReference'], 'Casa en Vista Hermosa');
    expect((body['participants'] as List).first, {'role': 'OWNER', 'fullName': 'Juan Pérez López'});
    expect(sentRequests.any((r) => r.method == 'DELETE' && r.path == '/internal/drafts/nuevo-expediente'), isTrue, reason: 'borra el borrador');
    await _close(tester);
  });

  testWidgets('flujo: responder una conversación', (tester) async {
    await _open(tester, '/conversaciones/conv-1');
    await tester.enterText(find.byType(TextField).last, 'Hola, soy del equipo');
    await settle(tester, 1);
    await tester.tap(find.byIcon(Icons.send));
    await settle(tester, 3);
    final req = sentRequests.firstWhere((r) => r.path == '/internal/conversations/conv-1/reply');
    expect((req.body as Map)['text'], 'Hola, soy del equipo');
    await _close(tester);
  });

  testWidgets('flujo: cambiar el tema a oscuro', (tester) async {
    await _open(tester, '/configuracion');
    await tester.tap(find.text('Oscuro'));
    await settle(tester, 2);
    final app = tester.widget<MaterialApp>(find.byType(MaterialApp));
    expect(app.theme!.scaffoldBackgroundColor, AppColors.dark.appBg);
    await _close(tester);
  });
}
