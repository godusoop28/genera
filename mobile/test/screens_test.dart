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
import 'package:genera_mobile/state/auth.dart';
import 'package:genera_mobile/state/settings.dart';
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
}

void main() {
  setUpAll(() async {
    await initializeDateFormatting('es_MX');
    if (_screenshots) await _loadFonts();
  });

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    FlutterSecureStorage.setMockInitialValues({'c21genera-access-token': 'token', 'c21genera-refresh-token': 'refresh'});
    ApiClient.instance.httpClient = MockClient((req) async {
      final (status, body) = fakeBackend(req.method, req.url);
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
}
