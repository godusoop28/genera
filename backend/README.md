# Backend — CENTURY 21 Genera (Módulo 1 y CRM del Módulo 3)

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
| `notary` | Documentos por etapa (comercialización, envío inicial a notaría, cierre con oferta aceptada): paquetes enviados con la versión de cada documento y vigencia de los documentos de cierre |
| `leads` | CRM (Módulo 3): leads, etapas, actividades, ruteo de asesores (asesor de la propiedad o ruleta ponderada) y dashboard |
| `online21` | Consulta de solo lectura del inventario de 21Online (API REST con JWT) y registro opcional de prospectos |
| `assistant` | Asistente de WhatsApp con IA (misma lógica que el panel de Inova): WhatsApp Cloud API, OpenAI con herramientas acotadas, bandeja y simulador |
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
- El staff consulta los documentos en un visor (`DocumentViewer`): cada página
  llega como imagen JPEG con marca de agua de quien consulta, sin el archivo
  original. Descargar o imprimir el original requiere `DOCUMENT_DOWNLOAD`
  (solo administrador); la vista previa del correo a notaría tampoco trae
  ligas de descarga sin ese permiso.
- Además de los requisitos de la política, se pueden agregar documentos con
  nombre libre (tipo `OTHER`, `custom_name`), aprobar como excepción con nota
  un documento faltante (`DOCUMENT_EXCEPTION`: no bloquea la aprobación ni el
  contrato) y pedir una versión actualizada (`DOCUMENT_UPDATE_REQUEST`): la
  nueva carga es la versión siguiente y las anteriores nunca se borran.
- Controles de cambios 16/10: un documento se puede marcar "Pendiente" (nota y
  fecha estimada; no bloquea y al subirse queda entregado), los agregados a mano
  se pueden renombrar, el comprobante de domicilio registra cuál de los cinco
  aceptados es, y el contrato firmado fuera del sistema (DocuSign o escaneo,
  también histórico) se sube como documento `SIGNED_CONTRACT` y deja el
  expediente en `CONTRACT_SIGNED`. Descargar/imprimir en el visor: administrador
  y directivo.
- Autorizar una inconsistencia (`DataConflict`) requiere
  `INCONSISTENCY_AUTHORIZE` (administrador y directivo) y la contraseña del
  usuario; queda "Autorizada por" con fecha en la bitácora. El régimen de
  condominio se coteja contra escritura y predial (m² de terreno e indivisos) y
  la IA devuelve las opciones cuando una escritura trae varios valores
  (antecedentes) para que una persona elija.
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

## CRM y asistente de WhatsApp (Módulo 3)

Misma lógica que el panel de Inova (proyecto `inovapanel`,
`docs/CONVERSATIONAL_ASSISTANT.md`), en Java y con 21Online en lugar de
EasyBroker. **Las propiedades no se guardan ni se administran en Genera**
(eso es el Módulo 2): se consultan en 21Online.

```
Cliente (WhatsApp) → Meta → POST /api/v1/public/whatsapp/webhook (firma HMAC)
   → InboundService: guarda el mensaje y agenda el turno (ráfaga 6 s / máx. 25 s)
   → AssistantWorker (cada 2 s) → TurnProcessor → AssistantAgent (OpenAI Responses + herramientas)
        search_properties / get_property   → online21.PropertyCatalog (índice en memoria, ficha en vivo)
        request_commercial_handoff         → leads.LeadIntake (asesor de la propiedad o ruleta)
        request_management / request_human → pendiente para el equipo
   → respuesta en cola → OutboxSender → WhatsApp Cloud API
leads.LeadAssigned → AdvisorNoticeListener → aviso por WhatsApp al asesor
```

Reglas clave (iguales a Inova):

- **La IA propone; el backend decide.** El modelo no elige asesor ni escribe
  estados. Máximo 4 rondas y 6 herramientas por turno; una corrección si
  menciona una propiedad no verificada, afirma una asignación que no ocurrió
  o dice "ruleta".
- **Asignación:** propiedad identificada cuyo asesor en 21Online es usuario de
  Genera (`crm_advisor_profile.online21_user_id`) → directo a él. Si no,
  ruleta ponderada (leads de hoy / peso) entre quienes reciben leads, en su
  horario y sin rebasar su límite diario. Nadie disponible → el lead se guarda
  sin asesor y queda pendiente.
- **Un lead por teléfono en 30 días:** si ya tiene asesor, la nueva solicitud
  se anexa como seguimiento (no se reasigna). Idempotencia por
  `wa:<conversación>:s<sesión>`.
- **Sesión nueva** tras 6 h sin mensajes: lo anterior pasa a contexto y la IA
  no contesta pedidos viejos.
- **Abandono:** con interés inmobiliario y 30 min sin respuesta, se canaliza
  solo (nunca proveedores ni gerencia).
- **Fallas de OpenAI:** reintento a 10 s, 30 s y 90 s; luego respuesta fija
  una vez, IA en pausa para ese contacto y pendiente en el panel.
- Responder como equipo pausa la IA en esa conversación; al reanudarla no
  contesta lo que llegó durante la pausa.

Variables (ver `.env.example`): `ASSISTANT_MODE` (OFF/TEST_ONLY/ON; el
simulador del panel funciona siempre que haya `AI_API_KEY`),
`ASSISTANT_MODEL` (vacío = `AI_MODEL`), `ONLINE21_API_KEY` (Mi Perfil → API
Keys en 21Online), `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN`,
`WHATSAPP_VERIFY_TOKEN`, `WHATSAPP_APP_SECRET`, `WHATSAPP_ADVISOR_TEMPLATE`.

Pendiente de validar con credenciales reales: el formato exacto de la
respuesta de `GET /api/propiedades` de 21Online (el mapeo acepta JSON-LD y
arreglo plano), qué acepta `POST /api/prospectos`
(`ONLINE21_PUSH_PROSPECTS=false` por defecto) y el comportamiento del modelo
de `AI_MODEL` con la Responses API (herramientas + JSON Schema estricto).

### Calendario de turnos, guardias y rondas (V028)

El calendario (`crm_shift`, `crm_shift_skip`, `crm_absence`) es la **única**
fuente de disponibilidad para asignar; se calcula en el servidor en
America/Mexico_City y se lee en cada asignación (un cambio aplica de inmediato,
sin reiniciar). El horario simple anterior se conservó como
`crm_advisor_profile.legacy_shift` y esos asesores quedaron con
`calendar_needs_setup = true` (no se inventaron días).

- Turnos de una fecha o semanales (días, desde, hasta opcional), varios bloques
  por día, nocturnos (fin ≤ inicio = termina al día siguiente), jornada o
  guardia, y si participan en la asignación automática. Sin traslapes del mismo
  asesor; varios asesores sí pueden coincidir.
- Editar/eliminar: "solo este turno" omite la fecha de la serie y crea un turno
  puntual; "toda la serie" corta la serie el día anterior y crea otra. Lo pasado
  no se modifica. Todo queda en `crm_calendar_audit`.
- Ausencias (vacaciones, permiso, descanso, no disponible) prevalecen sobre los
  turnos. "Recibe leads / en pausa" sigue aplicando.
- Ronda persistente (`round_last_at`, `round_current_weight`): EQUAL (le toca a
  quien recibió hace más tiempo) o WEIGHTED (smooth weighted round robin). Se
  decide con los perfiles bloqueados (`lockAll`), así que dos asignaciones
  simultáneas no duplican ni rebasan límites. El límite diario usa la fecha de
  Ciudad de México.
- Política `crm_assignment_settings.owner_off_shift_policy`: RESPECT_OWNER
  (predeterminado: el lead queda `PENDING_OWNER`, reservado para el asesor de la
  propiedad hasta su próximo turno) o ROUTE_TO_GUARD (lo atiende un disponible,
  prefiere guardias, conservando `property_advisor_user_id`). Dueño no
  vinculado, desactivado, en pausa o sin turnos en 21 días → ronda de respaldo
  con el motivo visible.
- Sin nadie disponible: `PENDING_SCHEDULE` con `next_shift_at`;
  `PendingAssignmentWorker` (cada minuto, `FOR UPDATE SKIP LOCKED`) los atiende
  por antigüedad al empezar un turno. El estado de asignación es independiente
  de la etapa comercial. Un lead asignado no cambia de asesor al terminar el turno.
- Cada decisión queda en `lead_assignment_log` (motivo, regla, turno, modo de
  ronda, espera). La asignación manual a alguien no disponible exige motivo.
- Permisos: `CALENDAR_VIEW_ALL`/`CALENDAR_MANAGE` (administración y dirección),
  `CALENDAR_VIEW_OWN` (asesor), `CALENDAR_EDIT_OWN` (explícito, nadie lo tiene
  por defecto). La política la cambia `ADVISOR_ROUTING_MANAGE`.

Probado con pruebas unitarias y de servicio con repositorios simulados
(`ScheduleTest`, `AssignmentEngineTest`, `AssignmentScenariosTest`,
`LeadIntakeContinuityTest`). El bloqueo de filas, la migración V028 y el
procesamiento de pendientes contra Postgres real se verifican en el despliegue.
