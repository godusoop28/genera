import 'package:go_router/go_router.dart';

import 'screens/calendar/calendar_screen.dart';
import 'screens/crm/advisors_screen.dart';
import 'screens/crm/conversations_screen.dart';
import 'screens/crm/dashboard_screen.dart';
import 'screens/crm/lead_detail_screen.dart';
import 'screens/crm/leads_screen.dart';
import 'screens/crm/properties_screen.dart';
import 'screens/crm/property_dossier_screen.dart';
import 'screens/expedientes/expediente_detail_screen.dart';
import 'screens/expedientes/expedientes_screen.dart';
import 'screens/expedientes/new_expediente_screen.dart';
import 'screens/expedientes/reports_screen.dart';
import 'screens/login_screen.dart';
import 'screens/settings_screen.dart';
import 'screens/splash_screen.dart';
import 'screens/users_screen.dart';
import 'state/auth.dart';

/// Mismas rutas que la web (src/app/**). Sin sesión todo lleva a /login.
GoRouter buildRouter(AuthState auth) => GoRouter(
      initialLocation: '/expedientes',
      refreshListenable: auth,
      redirect: (context, state) {
        final loc = state.matchedLocation;
        if (auth.isLoading) return loc == '/splash' ? null : '/splash';
        if (!auth.isAuthenticated) return loc == '/login' ? null : '/login';
        if (loc == '/login' || loc == '/splash') return '/expedientes';
        return null;
      },
      routes: [
        GoRoute(path: '/splash', builder: (_, _) => const SplashScreen()),
        GoRoute(path: '/login', builder: (_, _) => const LoginScreen()),
        GoRoute(
          path: '/expedientes',
          builder: (_, s) => ExpedientesScreen(
            initialCategory: s.uri.queryParameters['categoria'],
            initialSubtype: s.uri.queryParameters['subtipo'],
            initialOperation: s.uri.queryParameters['operacion'],
            initialAdvisor: s.uri.queryParameters['asesor'],
          ),
        ),
        GoRoute(path: '/expedientes/nuevo', builder: (_, _) => const NewExpedienteScreen()),
        GoRoute(path: '/expedientes/:id', builder: (_, s) => ExpedienteDetailScreen(id: s.pathParameters['id']!)),
        GoRoute(path: '/reportes', builder: (_, _) => const ReportsScreen()),
        GoRoute(path: '/usuarios', builder: (_, _) => const UsersScreen()),
        GoRoute(path: '/configuracion', builder: (_, _) => const SettingsScreen()),
        GoRoute(path: '/dashboard', builder: (_, _) => const DashboardScreen()),
        GoRoute(
          path: '/leads',
          builder: (_, s) => LeadsScreen(
            initialGroup: s.uri.queryParameters['grupo'],
            initialAdvisor: s.uri.queryParameters['asesor'],
            initialSource: s.uri.queryParameters['fuente'],
          ),
        ),
        GoRoute(path: '/leads/:id', builder: (_, s) => LeadDetailScreen(id: s.pathParameters['id']!)),
        GoRoute(path: '/propiedades', builder: (_, s) => PropertiesScreen(initialTab: s.uri.queryParameters['vista'])),
        GoRoute(path: '/propiedades/nueva', builder: (_, _) => const PropertyFormScreen()),
        GoRoute(path: '/propiedades/21online/:online21Id', builder: (_, s) => Online21PropertyScreen(online21Id: s.pathParameters['online21Id']!)),
        GoRoute(path: '/propiedades/:id', builder: (_, s) => CapturedPropertyScreen(id: s.pathParameters['id']!)),
        GoRoute(path: '/calendario', builder: (_, s) => CalendarScreen(initialAdvisor: s.uri.queryParameters['asesor'])),
        GoRoute(path: '/conversaciones', builder: (_, _) => const ConversationsScreen()),
        GoRoute(path: '/conversaciones/:id', builder: (_, s) => ConversationScreen(id: s.pathParameters['id']!)),
        GoRoute(path: '/asesores', builder: (_, _) => const AdvisorsScreen()),
      ],
    );
