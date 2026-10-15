/// Configuración de la app. Se puede cambiar al compilar sin tocar código:
///
///   flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
///
/// (10.0.2.2 es la computadora vista desde el emulador de Android.)
class AppConfig {
  /// Base de la API del backend (el mismo que usa la web).
  static const apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'https://genera-4ch0.onrender.com/api/v1',
  );

  /// Dominio de la web: las ligas del cliente (/carga) y de firma (/firma) se
  /// abren en el navegador, así que se arman con este dominio.
  static const webBaseUrl = String.fromEnvironment(
    'WEB_BASE_URL',
    defaultValue: 'https://genera-puce.vercel.app',
  );

  static const online21BaseUrl = 'https://plus.21onlinemx.com';
}
