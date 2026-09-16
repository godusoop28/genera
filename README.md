# CENTURY 21 Genera — Prototipo de gestión documental (Módulo 1)

Prototipo visual navegable de gestión documental y contratos para CENTURY 21
Genera, construido en Next.js (App Router) + React + TypeScript + Tailwind CSS.

## Qué es este prototipo

Una demo interactiva del **Módulo 1** del sistema: recepción y control
documental, revisión, cumplimiento y cierre de venta. Todo el comportamiento
que en producción requeriría backend, IA, firma electrónica certificada o
integraciones externas está **simulado** con estado de React, Context y
`localStorage`.

No hay:

- Backend ni base de datos real
- Autenticación real (login, roles y permisos son simulados)
- OCR ni extracción de datos con IA real
- Firma electrónica certificada
- Envío real de correos
- Generación real de PDF/DOCX del contrato
- Conexión con CRM, propiedades, leads o campañas (Módulos 2 y 3)

## Qué incluye el Módulo 1

- Gestión de expedientes documentales y su ciclo de vida
- Configuración dinámica de requisitos documentales (propietarios, apoderado,
  régimen de condominio, tipo de inmueble)
- Portal público sin cuenta para que el cliente reciba y suba documentos vía
  liga (aviso de privacidad, datos complementarios, documentos, confirmación)
- Validación automática simulada de fotografías, separada de la aprobación
  humana (aceptar / devolver / rechazar documento)
- Banco documental por categorías y línea de tiempo de actividad
- Referencia del contrato real de intermediación (registrado ante PROFECO
  7/002193-2026) con cálculos de comisión, IVA, pena convencional y vigencia
- Control documental de cumplimiento, recepción firmada y decisión de
  aceptar/rechazar el inmueble (con *handoff* visual al Módulo 2)
- Envío simulado de documentos por correo
- Cierre de venta documental
- Gestión de usuarios internos, roles y matriz de permisos (los propietarios
  **no** tienen cuenta ni aparecen aquí)
- Configuración personal: tema (claro, oscuro, dorado, alto contraste),
  tamaño de fuente, datos de cuenta (incluido el correo), notificaciones y
  restablecer los datos de la demostración

## Rutas principales

| Ruta | Descripción |
| --- | --- |
| `/login` | Acceso de personal interno (sin autenticación real) |
| `/expedientes` | Listado de expedientes con filtros |
| `/expedientes/nuevo` | Configuración de un expediente y generación de liga |
| `/expedientes/[id]` | Detalle del expediente (Resumen, Documentos, Información, Contrato, Cumplimiento, Cierre de venta) |
| `/expedientes/demo` | Alias de `/expedientes/[id]` con datos de ejemplo (`id="demo"`) |
| `/carga/[linkId]` | Portal público del cliente (sin cuenta), según la liga del expediente |
| `/carga/demo-expediente` | Portal público del expediente de ejemplo |
| `/usuarios` | Usuarios internos y matriz de roles y permisos |
| `/configuracion` | Tema, tamaño de fuente, cuenta, notificaciones y reinicio de datos de demo (accesible desde el ícono de engrane del encabezado) |

## Cómo probar el flujo demo

1. Entra en `/login` y pulsa **Entrar**.
2. En `/expedientes/nuevo`, captura un propietario, configura el caso (número
   de propietarios, apoderado, condominio, etc.) y genera la liga del cliente.
3. Abre el portal del cliente desde el modal de la liga y completa el flujo:
   aviso de privacidad → datos complementarios → documentos → confirmación.
4. Regresa al expediente interno y revisa los documentos recibidos: acepta,
   devuelve o rechaza cada uno.
5. Firma la recepción documental y toma la decisión de aceptar o rechazar el
   inmueble desde la pestaña **Cumplimiento**.
6. Explora `/usuarios` para ver la gestión de usuarios internos y la matriz
   de roles y permisos.

## Desarrollo

```bash
npm install
npm run dev
```

Abre [http://localhost:3000](http://localhost:3000).

```bash
npm run lint
npm run build
```

## Fuentes de marca y legales

Los assets de marca (`/public/brand`) y los documentos legales de referencia
(`/public/legal`) provienen de los archivos de marca y contrato
proporcionados por CENTURY 21 Genera. El texto del contrato y del aviso de
privacidad que se muestra en el prototipo es una referencia fiel de esos
documentos fuente; no se modifican sus cláusulas ni se generan versiones
oficiales.
