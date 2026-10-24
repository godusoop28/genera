import 'dart:async';
import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart' show MediaType;

import '../config.dart';

/// Error de la API con el formato ProblemDetail del backend
/// ({ title, status, code, detail, traceId }).
class ApiError implements Exception {
  ApiError(this.message, this.status, [this.code]);

  final String message;
  final int status;
  final String? code;

  @override
  String toString() => message;
}

/// Sin conexión con el servidor (equivalente al TypeError de fetch en la web).
class NetworkError implements Exception {
  NetworkError([this.message = 'No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.']);

  final String message;

  @override
  String toString() => message;
}

/// Archivo a subir (de la cámara, la galería o el selector de archivos).
class UploadFile {
  UploadFile({required this.name, required this.bytes, required this.contentType});

  final String name;
  final List<int> bytes;
  final String contentType;
}

/// Cliente HTTP con el mismo comportamiento que src/lib/api/client.ts de la
/// web: Bearer token, un solo refresh a la vez ante un 401 y repetición de la
/// petición una sola vez. Los tokens se guardan cifrados en el dispositivo.
class ApiClient {
  ApiClient._();

  static final ApiClient instance = ApiClient._();

  static const _accessKey = 'c21genera-access-token';
  static const _refreshKey = 'c21genera-refresh-token';
  static const _timeout = Duration(seconds: 90);

  final _storage = const FlutterSecureStorage();
  /// Se puede reemplazar en pruebas (MockClient de package:http/testing.dart).
  http.Client httpClient = http.Client();

  String? _accessToken;
  Future<String?>? _refreshing;

  /// AuthProvider lo registra para vaciar la sesión cuando el refresh ya no sirve.
  void Function()? onSessionExpired;

  Future<String?> loadStoredAccessToken() async {
    _accessToken = await _storage.read(key: _accessKey);
    return _accessToken;
  }

  Future<String?> storedRefreshToken() => _storage.read(key: _refreshKey);

  Future<void> storeTokens(String accessToken, [String? refreshToken]) async {
    _accessToken = accessToken;
    await _storage.write(key: _accessKey, value: accessToken);
    if (refreshToken != null) await _storage.write(key: _refreshKey, value: refreshToken);
  }

  Future<void> clearTokens() async {
    _accessToken = null;
    await _storage.delete(key: _accessKey);
    await _storage.delete(key: _refreshKey);
  }

  Uri _uri(String path) => Uri.parse('${AppConfig.apiBaseUrl}$path');

  Future<String?> _refreshAccessToken() {
    return _refreshing ??= () async {
      try {
        final refreshToken = await _storage.read(key: _refreshKey);
        if (refreshToken == null) return null;
        final response = await httpClient
            .post(_uri('/auth/refresh'), headers: {'Content-Type': 'application/json'}, body: jsonEncode({'refreshToken': refreshToken}))
            .timeout(_timeout);
        if (response.statusCode < 200 || response.statusCode >= 300) return null;
        final data = jsonDecode(response.body) as Map<String, dynamic>?;
        final token = data?['accessToken'] as String?;
        if (token == null) return null;
        await storeTokens(token, data?['refreshToken'] as String?);
        return token;
      } catch (_) {
        return null;
      }
    }()
        .then((token) async {
      if (token == null) {
        await clearTokens();
        onSessionExpired?.call();
      }
      return token;
    }).whenComplete(() => _refreshing = null);
  }

  /// Ejecuta la petición y, si responde 401, renueva el token y la repite UNA vez.
  Future<http.Response> _withAuth(String path, Future<http.Response> Function(String? token) send) async {
    final token = _accessToken;
    http.Response response;
    try {
      response = await send(token).timeout(_timeout);
    } on TimeoutException {
      throw NetworkError('El servidor tardó demasiado en responder. Intenta de nuevo en unos segundos.');
    } on ApiError {
      rethrow;
    } catch (_) {
      throw NetworkError();
    }
    if (response.statusCode != 401 || path.startsWith('/auth/')) return response;
    final newToken = _accessToken != null && _accessToken != token ? _accessToken : await _refreshAccessToken();
    if (newToken == null) return response;
    try {
      return await send(newToken).timeout(_timeout);
    } catch (_) {
      throw NetworkError();
    }
  }

  Map<String, String> _headers(String? token, {bool json = true}) => {
        if (json) 'Content-Type': 'application/json',
        'Accept': 'application/json',
        if (token != null) 'Authorization': 'Bearer $token',
      };

  dynamic _decode(http.Response response) {
    if (response.statusCode == 204 || response.body.isEmpty) {
      if (response.statusCode >= 200 && response.statusCode < 300) return null;
    }
    dynamic data;
    try {
      data = response.body.isEmpty ? null : jsonDecode(utf8.decode(response.bodyBytes));
    } catch (_) {
      data = null;
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      final map = data is Map ? data : const {};
      throw ApiError(
        (map['detail'] ?? map['title'] ?? response.reasonPhrase ?? 'Error ${response.statusCode}').toString(),
        response.statusCode,
        map['code'] as String?,
      );
    }
    return data;
  }

  Future<dynamic> request(String method, String path, {Object? body}) async {
    final response = await _withAuth(path, (token) {
      final req = http.Request(method, _uri(path))..headers.addAll(_headers(token));
      if (body != null) req.body = jsonEncode(body);
      return httpClient.send(req).then(http.Response.fromStream);
    });
    return _decode(response);
  }

  Future<dynamic> get(String path) => request('GET', path);

  /// GET autenticado que devuelve el cuerpo binario (p. ej. una página del visor de documentos).
  Future<List<int>> getBytes(String path) async {
    final response = await _withAuth(path, (token) {
      final req = http.Request('GET', _uri(path))..headers.addAll({if (token != null) 'Authorization': 'Bearer $token'});
      return httpClient.send(req).then(http.Response.fromStream);
    });
    if (response.statusCode < 200 || response.statusCode >= 300) {
      _decode(response);
    }
    return response.bodyBytes;
  }
  Future<dynamic> post(String path, [Object? body]) => request('POST', path, body: body);
  Future<dynamic> put(String path, [Object? body]) => request('PUT', path, body: body);
  Future<dynamic> patch(String path, [Object? body]) => request('PATCH', path, body: body);
  Future<dynamic> delete(String path) => request('DELETE', path);

  /// POST multipart (nunca JSON). onProgress recibe de 0 a 100.
  /// fields: campos de texto adicionales del formulario.
  Future<dynamic> upload(String path, String fieldName, List<UploadFile> files, {void Function(int percent)? onProgress, Map<String, String>? fields}) async {
    final response = await _withAuth(path, (token) {
      final req = _ProgressMultipartRequest('POST', _uri(path), onProgress)..headers.addAll(_headers(token, json: false));
      if (fields != null) req.fields.addAll(fields);
      for (final f in files) {
        final parts = f.contentType.split('/');
        req.files.add(http.MultipartFile.fromBytes(fieldName, f.bytes, filename: f.name, contentType: MediaType(parts[0], parts.length > 1 ? parts[1] : 'octet-stream')));
      }
      return httpClient.send(req).then(http.Response.fromStream);
    });
    return _decode(response);
  }
}


class _ProgressMultipartRequest extends http.MultipartRequest {
  _ProgressMultipartRequest(super.method, super.url, this.onProgress);

  final void Function(int percent)? onProgress;

  @override
  http.ByteStream finalize() {
    final stream = super.finalize();
    final callback = onProgress;
    if (callback == null) return stream;
    final total = contentLength;
    var sent = 0;
    return http.ByteStream(stream.transform(StreamTransformer.fromHandlers(handleData: (data, sink) {
      sent += data.length;
      if (total > 0) callback((sent * 100 / total).round().clamp(0, 100));
      sink.add(data);
    })));
  }
}

/// Mensaje para el usuario (equivale a errorText de src/lib/errors.ts).
String errorText(Object err, [String fallback = 'No se pudo completar la acción. Revisa tu conexión e intenta de nuevo.']) {
  if (err is ApiError) {
    if (err.status == 403) return 'No tienes permiso para esta acción.';
    return err.message.isNotEmpty ? err.message : fallback;
  }
  if (err is NetworkError) return err.message;
  return fallback;
}

/// Mensajes del CRM (equivale a errorText de CrmUi.tsx).
String crmErrorText(Object err) {
  if (err is ApiError) {
    if (err.status == 403) return 'Tu usuario no tiene permiso para esta acción.';
    if ((err.status == 400 || err.status == 409 || err.status == 422) && err.message.isNotEmpty) return err.message;
    if (err.status == 404) return 'No se encontró. Puede que se haya eliminado.';
    return 'El servidor respondió con un error (${err.status}). Intenta de nuevo en un momento.';
  }
  return 'No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.';
}
