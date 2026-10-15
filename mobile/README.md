# Genera — app móvil (Flutter)

Versión para celular (Android e iOS) del sistema interno de CENTURY 21 Genera.
Usa **el mismo backend** que la web (`/backend`) y replica sus pantallas para el
personal interno:

- **Expedientes**: lista con avance documental, alta en 3 pasos (con borrador
  autoguardado en el servidor y aviso de cliente duplicado) y detalle con las 8
  pestañas de la web: Resumen (liga del cliente, recepción, decisión), Participantes,
  Documentos (revisar, aceptar, devolver, subir con la cámara, datos leídos por IA),
  Consistencia, Datos del contrato, Contrato y firmas (firma de la intermediaria
  con el dedo), Seguimiento y Bitácora.
- **CRM**: Dashboard, Leads (lista y tablero), detalle del lead, Conversaciones de
  WhatsApp (se actualizan solas), Calendario de asesores (mes, semana, por asesor,
  lista y día) y Asesores.
- **Usuarios y permisos** y **Configuración** (tema claro/oscuro/dorado/alto
  contraste y tamaño de letra).

El portal del cliente (`/carga`) y la página de firma (`/firma`) siguen siendo
web: la app genera las ligas y permite copiarlas o compartirlas.

## Comandos

```bash
cd mobile
flutter pub get
flutter run                      # en un emulador o celular conectado
flutter build apk --release      # APK para Android
flutter test                     # recorre todas las pantallas con datos simulados
```

Por defecto apunta al backend de Render y a la web de Vercel. Para otro backend:

```bash
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1 --dart-define=WEB_BASE_URL=http://10.0.2.2:3000
```

(`10.0.2.2` es la computadora vista desde el emulador de Android.)

## Estructura

- `lib/api/` — cliente HTTP (JWT con renovación automática, igual que la web),
  modelos y todos los endpoints.
- `lib/labels.dart` — etiquetas en español de todos los códigos del backend.
- `lib/ui/` — tema de marca, componentes (tarjetas, insignias, hojas, etc.),
  menú lateral, selector de archivos/cámara y panel de firma.
- `lib/screens/` — una carpeta por módulo, con la misma organización que
  `frontend/src/app`.
- `test/` — pruebas de todas las pantallas contra un backend simulado.

Al cambiar una pantalla de la web, conviene revisar su equivalente aquí.
