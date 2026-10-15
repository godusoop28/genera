import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';

import 'router.dart';
import 'state/auth.dart';
import 'state/settings.dart';
import 'ui/theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('es_MX');
  final settings = SettingsState();
  await settings.load();
  final auth = AuthState();
  auth.restore();
  runApp(GeneraApp(auth: auth, settings: settings));
}

class GeneraApp extends StatefulWidget {
  const GeneraApp({super.key, required this.auth, required this.settings});
  final AuthState auth;
  final SettingsState settings;

  @override
  State<GeneraApp> createState() => _GeneraAppState();
}

class _GeneraAppState extends State<GeneraApp> {
  late final router = buildRouter(widget.auth);

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: widget.auth),
        ChangeNotifierProvider.value(value: widget.settings),
      ],
      child: Consumer<SettingsState>(
        builder: (context, settings, _) => MaterialApp.router(
          title: 'CENTURY 21 Genera',
          debugShowCheckedModeBanner: false,
          theme: buildTheme(settings.theme),
          routerConfig: router,
          locale: const Locale('es', 'MX'),
          supportedLocales: const [Locale('es', 'MX'), Locale('es')],
          localizationsDelegates: const [GlobalMaterialLocalizations.delegate, GlobalWidgetsLocalizations.delegate, GlobalCupertinoLocalizations.delegate],
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(settings.textScale)),
            child: child!,
          ),
        ),
      ),
    );
  }
}
