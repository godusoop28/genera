import 'package:flutter/foundation.dart';

import '../api/api.dart' as api;
import '../api/client.dart';
import '../api/models.dart';

/// Autenticación real contra el backend (JWT), como AuthProvider de la web.
class AuthState extends ChangeNotifier {
  AuthState() {
    ApiClient.instance.onSessionExpired = () {
      user = null;
      notifyListeners();
    };
  }

  MeResponse? user;
  bool isLoading = true;

  bool get isAuthenticated => user != null;

  /// Permisos del usuario actual. La UI solo oculta lo que no puede hacer; el backend vuelve a validar.
  bool can(String permission) {
    final p = user?.permissions ?? const [];
    return p.contains('*') || p.contains(permission);
  }

  bool get canSeeCrm => can('LEAD_VIEW_ALL') || can('LEAD_VIEW_OWN');

  Future<void> restore() async {
    try {
      final token = await ApiClient.instance.loadStoredAccessToken();
      if (token != null) user = await api.me();
    } catch (_) {
      await ApiClient.instance.clearTokens();
    } finally {
      isLoading = false;
      notifyListeners();
    }
  }

  Future<void> login(String email, String password) async {
    final auth = await api.login(email, password);
    await ApiClient.instance.storeTokens('${auth['accessToken']}', auth['refreshToken'] as String?);
    user = await api.me();
    notifyListeners();
  }

  Future<void> logout() async {
    final refreshToken = await ApiClient.instance.storedRefreshToken();
    await ApiClient.instance.clearTokens();
    user = null;
    notifyListeners();
    if (refreshToken != null) {
      try {
        await api.logout(refreshToken);
      } catch (_) {
        // El token ya pudo haber expirado: no bloquea el cierre local.
      }
    }
  }

  Future<void> refreshUser() async {
    user = await api.me();
    notifyListeners();
  }
}
