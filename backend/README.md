# Backend — CENTURY 21 Genera, Módulo 1

Monolito modular en Spring Boot 4.1.1 / Java 21, organizado con Spring
Modulith. Base de paquete: `com.c21genera`.

## Módulos

| Módulo | Responsabilidad |
|---|---|
| `identity` | Usuarios, roles, permisos, JWT (access+refresh), login interno |
| `expedientes` | Dominio central: `Expediente`, máquina de estados, participantes, requisitos documentales |
| `publicaccess` | Ligas públicas con token opaco hasheado (clientes NUNCA tienen cuenta) |
| `privacy` | Aviso de privacidad, plantillas legales versionadas (SHA-256), consentimiento |
| `documents` | Documento lógico / versión física / páginas / revisión, storage S3-compatible |
| `documentprocessing` | Normalización de imágenes (EXIF), verificación de calidad, ensamblado de PDF |
| `extraction` | Extracción de campos por IA (opcional), observaciones "detectado vs. confirmado", conflictos entre documentos |
| `contracts` | Cálculos (comisión/IVA/pena/exclusividad), generación DOCX/PDF, snapshot inmutable |
| `compliance` | Checklist de "Control documental de cumplimiento" (no es garantía legal) |
| `notifications` | Envío de correo vía outbox transaccional (nunca síncrono en el request) |
| `audit` | Rastro técnico (`AuditEvent`) separado de la línea de tiempo amigable (`Activity`) |
| `closing` | Seguimiento genérico de cierre tras la aceptación del inmueble |
| `shared` | Módulo abierto: eventos de integración, tipos de valor compartidos, seguridad de archivos, JPA base |

Los módulos se comunican solo por API pública (interfaces en el paquete raíz
de cada módulo, p. ej. `documents.DocumentsApi`) o por eventos de
integración (`shared.events.*`, escuchados con `@ApplicationModuleListener`).
Nunca por acceso directo a repositorios/entidades de otro módulo. Esto se
verifica automáticamente en `ModularityTests`
(`ApplicationModules.of(...).verify()`), que debe pasar siempre.

`Expediente` (no `Property`) es el agregado central: `Property` no existe
todavía — `acceptProperty`/`rejectProperty` solo publican un evento
(`PropertyAccepted`/`PropertyRejected`) como punto de integración futuro para
el Módulo 2.

## Cómo correrlo localmente

```bash
# 1) Infra (Postgres, MinIO, Mailpit) desde la raíz del repo
docker compose up -d

# 2) Variables de entorno
cp backend/.env.example backend/.env
# edítalo si es necesario; los valores por defecto ya calzan con compose.yml

# 3) Backend
cd backend
./mvnw spring-boot:run
```

- API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Correos capturados (Mailpit): http://localhost:8025
- Consola MinIO: http://localhost:9001 (usuario/clave: ver `compose.yml`)

Para tener un usuario administrador de prueba, define `DEV_ADMIN_PASSWORD`
en `.env` antes de arrancar (perfil `local` únicamente; `DevAdminSeeder` no
hace nada si la variable está vacía, y nunca corre en `prod`).

## Seguridad

- Dos superficies HTTP totalmente separadas: `/api/v1/public/**` (clientes,
  sin Spring Security, autorización vía token opaco de la liga) y
  `/api/v1/internal/**` (staff, JWT Bearer obligatorio).
- Permisos con nombre (16, ver `identity.domain.PermissionCode`) verificados
  con `@PreAuthorize("hasAuthority('...')")`, nunca `if (role == ADMIN)`.
- Contraseñas con BCrypt; JWT HS256 autoemitido (`JWT_SECRET`, mínimo 32
  bytes, sin valor por defecto fuera de `local`/`test`).
- Los clientes NUNCA tienen cuenta ni contraseña: usan un
  `PublicAccessToken` de un solo uso lógico (hasheado con SHA-256, nunca
  almacenado en claro), generable/revocable/regenerable.
- CORS restringido a `FRONTEND_URL`; nunca `*` en producción.
- Cada endpoint público valida que el token resuelva al expediente
  correcto antes de cualquier operación (protección IDOR: nunca confía en un
  ID crudo de la URL).

## Documentos y almacenamiento

- `Document` (lógico) vs. `DocumentVersion` (archivo físico, nunca se
  sobrescribe: reemplazar crea una versión nueva) vs. `DocumentPage`
  (fotos ordenadas dentro de una versión).
- Los archivos NUNCA se guardan en Postgres: van a un bucket S3-compatible
  siempre privado (MinIO local / S3 o R2 en producción), con claves de
  almacenamiento generadas internamente (no confían en el nombre del
  archivo del cliente) y solo URLs firmadas temporales para descarga.
- El MIME real se detecta con Apache Tika (nunca se confía en el
  `Content-Type` del navegador).
- Pipeline asíncrono (`documentprocessing`, sin Kafka/RabbitMQ: una tabla
  `background_job` con `SELECT ... FOR UPDATE SKIP LOCKED`, diseñada para
  poder migrar a un broker externo después sin cambiar el dominio):
  normalización de orientación EXIF → verificación de calidad determinística
  → ensamblado de PDF (PDFBox/POI).

## IA / extracción de datos

- Deshabilitada por defecto (`AI_ENABLED=false`): se usa
  `StubDocumentIntelligenceProvider`, que nunca llama a un servicio externo
  (ni en tests ni en CI).
- Si se habilita, `OpenAiStructuredExtractionProvider` llama al proveedor
  configurado vía `RestClient` simple (no el SDK oficial, por una decisión
  deliberada de no depender de nombres de clases generadas sin poder
  compilar contra el jar real en este entorno) y falla rápido en el arranque
  si falta `AI_API_KEY`/`AI_MODEL`.
- El documento es tratado como contenido NO confiable: el prompt de sistema
  instruye explícitamente a ignorar cualquier instrucción embebida en el
  documento. Nunca se registran en logs el contenido de la imagen ni el
  texto extraído.
- Cada llamada tiene tiempo máximo (`AI_TIMEOUT`, 120 s por defecto) y
  reintentos ante errores temporales (5xx, 429, red); después el job se
  reintenta con espera. Si la IA nunca responde, la versión queda marcada
  "sin revisión automática" (`ai_check_failed`) y solo se acepta con
  autorización de excepción; mientras la revisión sigue en curso (hasta 20
  minutos después de la carga) no se puede aceptar.
- En escrituras, contratos privados, actas, poderes y régimen de condominio
  se envían hasta 12 páginas (los datos suelen estar en páginas interiores).
- `GET /internal/expedientes/{id}/extracted-fields` junta lo detectado en
  todos los documentos vigentes; el frontend lo usa para prellenar los datos
  del contrato y de cada participante (el usuario revisa y guarda).
- Los valores "detectados" por IA nunca sobreescriben el dato canónico: se
  guardan aparte (`ExtractedFieldObservation`) hasta que un humano los
  confirma. Los conflictos entre documentos (`DataConflict`) se reportan,
  nunca se resuelven automáticamente.

## Contrato y privacidad

- El contrato real es el "Contrato de prestación de servicios de
  intermediación..." (registro PROFECO 7/002193-2026), no una compraventa.
  Cada generación (`ContractGeneration`) es un snapshot inmutable: si el
  expediente cambia después, los contratos ya generados no cambian.
- Todos los cálculos monetarios usan `BigDecimal` (comisión 5%, IVA 16% de
  la comisión, pena convencional 100% de la comisión, exclusividad 180 días
  naturales, ventana de revocación 5 días hábiles informativa).
- Las plantillas legales (`LegalTemplate`) versionan cada documento legal
  con su SHA-256, para poder demostrar exactamente qué versión aceptó cada
  cliente. Ningún texto legal se inventa ni se corrige: cualquier
  discrepancia entre documentos fuente queda marcada en el código con
  `// TODO: Confirmar dato canónico con CENTURY 21 Genera antes de
  producción`, nunca mostrada al usuario final.

## Base de datos

PostgreSQL únicamente, migraciones con Flyway (`V001`...`V011`, secuenciales
y nunca reescritas una vez confirmadas). `ddl-auto=validate` en todos los
perfiles (nunca `update`). Bloqueo optimista (`@Version`) en entidades
importantes (`Expediente`, `Document`, `ContractGeneration`, `ClosingCase`).

## Pruebas

```bash
cd backend
./mvnw clean verify
```

- `ModularityTests` verifica los límites entre módulos (Spring Modulith) y
  debe pasar siempre; es la prueba más importante de este backend.
- Pruebas unitarias puras (máquina de estados, política de requisitos,
  cálculos de contrato, validador cruzado de documentos, generador de
  plantillas DOCX, normalizador de imágenes, analizador de calidad) no
  requieren base de datos ni red.
- **Limitación conocida de este entorno**: no hay Docker disponible aquí, así
  que las pruebas basadas en Testcontainers (el test de contexto por
  defecto `C21generaBackendApplicationTests`, que levanta un Postgres real)
  solo se pudieron verificar en compilación, no en ejecución. Con Docker
  disponible deberían pasar sin cambios.
- Nunca se llama a IA real ni a un servidor SMTP real en pruebas/CI: se usa
  siempre `StubDocumentIntelligenceProvider` y (cuando aplique) un
  `NotificationSender` de prueba.

## Lo que es un mock/stub deliberado en el Módulo 1

- `StubDocumentIntelligenceProvider`: sin IA real por defecto.
- Firma de consentimiento de privacidad: evidencia simulada (imagen +
  metadatos), explícitamente NO una firma electrónica certificada.
- `ContractDocxBuilder`: genera una vista previa de prototipo ("VISTA PREVIA
  DE PROTOTIPO"), no un documento con validez legal automática.
- `LibreOfficePdfConverter`: deshabilitado por defecto
  (`PDF_CONVERSION_ENABLED=false`); el DOCX siempre se genera igual.
- `Property` (Módulo 2): no existe como entidad; solo el punto de
  integración por evento.
