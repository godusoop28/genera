-- Control de Cambios #26: catálogo de inmuebles con categorías y subcategorías.
--
-- El administrador lo mantiene desde Configuración (agregar, editar o
-- desactivar; nunca se borra). Cada subcategoría define:
--   * case_type: tratamiento de documentos y contrato (expedientes.PropertyCaseType).
--     Solo lo habitacional usa el modelo de contrato registrado ante PROFECO;
--     lo demás queda como COMMERCIAL (el contrato avisa que requiere otro modelo).
--   * online21_type / online21_subtype: cómo se publica en 21Online.
--   * requires_detail: "Otro — especificar" pide describir el inmueble.

CREATE TABLE property_category (
    id         UUID PRIMARY KEY,
    name       VARCHAR(80) NOT NULL UNIQUE,
    sort_order INTEGER     NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE property_subtype (
    id               UUID PRIMARY KEY,
    category_id      UUID        NOT NULL REFERENCES property_category (id),
    name             VARCHAR(80) NOT NULL,
    sort_order       INTEGER     NOT NULL,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    requires_detail  BOOLEAN     NOT NULL DEFAULT FALSE,
    case_type        VARCHAR(24) NOT NULL,
    online21_type    VARCHAR(24) NOT NULL,
    online21_subtype VARCHAR(40) NOT NULL,
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    UNIQUE (category_id, name)
);

INSERT INTO property_category (id, name, sort_order, created_at, updated_at) VALUES
    ('00000000-0000-0000-0026-0000000000c1', 'Habitacional',           10, now(), now()),
    ('00000000-0000-0000-0026-0000000000c2', 'Edificios',              20, now(), now()),
    ('00000000-0000-0000-0026-0000000000c3', 'Comercial y oficinas',   30, now(), now()),
    ('00000000-0000-0000-0026-0000000000c4', 'Industrial y logística', 40, now(), now()),
    ('00000000-0000-0000-0026-0000000000c5', 'Terrenos',               50, now(), now()),
    ('00000000-0000-0000-0026-0000000000c6', 'Agropecuario',           60, now(), now()),
    ('00000000-0000-0000-0026-0000000000c7', 'Turístico y hospedaje',  70, now(), now()),
    ('00000000-0000-0000-0026-0000000000c8', 'Especiales',             80, now(), now());

INSERT INTO property_subtype (id, category_id, name, sort_order, requires_detail, case_type, online21_type, online21_subtype, created_at, updated_at) VALUES
    -- Habitacional
    ('00000000-0000-0000-0026-000000000101', '00000000-0000-0000-0026-0000000000c1', 'Casa',                      10, FALSE, 'HOUSING',          'habitacional', 'casa',                now(), now()),
    ('00000000-0000-0000-0026-000000000102', '00000000-0000-0000-0026-0000000000c1', 'Departamento',              20, FALSE, 'DEPARTMENT',       'habitacional', 'departamento',        now(), now()),
    ('00000000-0000-0000-0026-000000000103', '00000000-0000-0000-0026-0000000000c1', 'Casa en condominio',        30, FALSE, 'HOUSING',          'habitacional', 'casa-en-condominio',  now(), now()),
    ('00000000-0000-0000-0026-000000000104', '00000000-0000-0000-0026-0000000000c1', 'Dúplex',                    40, FALSE, 'HOUSING',          'habitacional', 'casa-duplex',         now(), now()),
    ('00000000-0000-0000-0026-000000000105', '00000000-0000-0000-0026-0000000000c1', 'Penthouse',                 50, FALSE, 'DEPARTMENT',       'habitacional', 'penthouse',           now(), now()),
    -- Edificios
    ('00000000-0000-0000-0026-000000000201', '00000000-0000-0000-0026-0000000000c2', 'Edificio habitacional',     10, FALSE, 'COMMERCIAL',       'habitacional', 'edificio',            now(), now()),
    ('00000000-0000-0000-0026-000000000202', '00000000-0000-0000-0026-0000000000c2', 'Edificio de oficinas',      20, FALSE, 'COMMERCIAL',       'comercial',    'edificio',            now(), now()),
    ('00000000-0000-0000-0026-000000000203', '00000000-0000-0000-0026-0000000000c2', 'Edificio comercial',        30, FALSE, 'COMMERCIAL',       'comercial',    'edificio',            now(), now()),
    ('00000000-0000-0000-0026-000000000204', '00000000-0000-0000-0026-0000000000c2', 'Edificio de uso mixto',     40, FALSE, 'COMMERCIAL',       'comercial',    'edificio',            now(), now()),
    -- Comercial y oficinas
    ('00000000-0000-0000-0026-000000000301', '00000000-0000-0000-0026-0000000000c3', 'Local comercial',           10, FALSE, 'COMMERCIAL',       'comercial',    'local',               now(), now()),
    ('00000000-0000-0000-0026-000000000302', '00000000-0000-0000-0026-0000000000c3', 'Oficina',                   20, FALSE, 'COMMERCIAL',       'comercial',    'oficinas',            now(), now()),
    ('00000000-0000-0000-0026-000000000303', '00000000-0000-0000-0026-0000000000c3', 'Consultorio',               30, FALSE, 'COMMERCIAL',       'comercial',    'oficinas',            now(), now()),
    ('00000000-0000-0000-0026-000000000304', '00000000-0000-0000-0026-0000000000c3', 'Plaza o centro comercial',  40, FALSE, 'COMMERCIAL',       'comercial',    'local',               now(), now()),
    -- Industrial y logística
    ('00000000-0000-0000-0026-000000000401', '00000000-0000-0000-0026-0000000000c4', 'Nave industrial',           10, FALSE, 'COMMERCIAL',       'comercial',    'nave',                now(), now()),
    ('00000000-0000-0000-0026-000000000402', '00000000-0000-0000-0026-0000000000c4', 'Bodega',                    20, FALSE, 'COMMERCIAL',       'comercial',    'bodega',              now(), now()),
    ('00000000-0000-0000-0026-000000000403', '00000000-0000-0000-0026-0000000000c4', 'Terreno industrial',        30, FALSE, 'COMMERCIAL',       'comercial',    'terreno',             now(), now()),
    ('00000000-0000-0000-0026-000000000404', '00000000-0000-0000-0026-0000000000c4', 'Parque industrial',         40, FALSE, 'COMMERCIAL',       'comercial',    'nave',                now(), now()),
    -- Terrenos
    ('00000000-0000-0000-0026-000000000501', '00000000-0000-0000-0026-0000000000c5', 'Terreno habitacional',      10, FALSE, 'RESIDENTIAL_LAND', 'habitacional', 'terreno',             now(), now()),
    ('00000000-0000-0000-0026-000000000502', '00000000-0000-0000-0026-0000000000c5', 'Terreno comercial',         20, FALSE, 'COMMERCIAL',       'comercial',    'terreno',             now(), now()),
    ('00000000-0000-0000-0026-000000000503', '00000000-0000-0000-0026-0000000000c5', 'Terreno de uso mixto',      30, FALSE, 'COMMERCIAL',       'comercial',    'terreno',             now(), now()),
    ('00000000-0000-0000-0026-000000000504', '00000000-0000-0000-0026-0000000000c5', 'Terreno rústico',           40, FALSE, 'COMMERCIAL',       'habitacional', 'terreno',             now(), now()),
    ('00000000-0000-0000-0026-000000000505', '00000000-0000-0000-0026-0000000000c5', 'Macrolote para desarrollo', 50, FALSE, 'COMMERCIAL',       'comercial',    'fraccionamiento',     now(), now()),
    -- Agropecuario
    ('00000000-0000-0000-0026-000000000601', '00000000-0000-0000-0026-0000000000c6', 'Rancho',                    10, FALSE, 'COMMERCIAL',       'habitacional', 'rancho',              now(), now()),
    ('00000000-0000-0000-0026-000000000602', '00000000-0000-0000-0026-0000000000c6', 'Finca',                     20, FALSE, 'COMMERCIAL',       'habitacional', 'quinta',              now(), now()),
    ('00000000-0000-0000-0026-000000000603', '00000000-0000-0000-0026-0000000000c6', 'Terreno agrícola',          30, FALSE, 'COMMERCIAL',       'comercial',    'inmueble-productivo', now(), now()),
    -- Turístico y hospedaje
    ('00000000-0000-0000-0026-000000000701', '00000000-0000-0000-0026-0000000000c7', 'Hotel',                     10, FALSE, 'COMMERCIAL',       'comercial',    'inmueble-productivo', now(), now()),
    ('00000000-0000-0000-0026-000000000702', '00000000-0000-0000-0026-0000000000c7', 'Hostal',                    20, FALSE, 'COMMERCIAL',       'comercial',    'inmueble-productivo', now(), now()),
    ('00000000-0000-0000-0026-000000000703', '00000000-0000-0000-0026-0000000000c7', 'Cabaña',                    30, FALSE, 'HOUSING',          'habitacional', 'cabana',              now(), now()),
    ('00000000-0000-0000-0026-000000000704', '00000000-0000-0000-0026-0000000000c7', 'Conjunto de cabañas',       40, FALSE, 'COMMERCIAL',       'comercial',    'inmueble-productivo', now(), now()),
    -- Especiales
    ('00000000-0000-0000-0026-000000000801', '00000000-0000-0000-0026-0000000000c8', 'Estacionamiento',           10, FALSE, 'COMMERCIAL',       'comercial',    'inmueble-productivo', now(), now()),
    ('00000000-0000-0000-0026-000000000802', '00000000-0000-0000-0026-0000000000c8', 'Inmueble de uso especial',  20, FALSE, 'COMMERCIAL',       'comercial',    'inmueble-productivo', now(), now()),
    ('00000000-0000-0000-0026-000000000803', '00000000-0000-0000-0026-0000000000c8', 'Otro — especificar',        30, TRUE,  'COMMERCIAL',       'comercial',    'inmueble-productivo', now(), now());

INSERT INTO permission (code, description) VALUES
    ('PROPERTY_CATALOG_MANAGE', 'Mantener el catálogo de tipos de inmueble (categorías y subcategorías)');

INSERT INTO role_permission (role_id, permission_code)
SELECT id, 'PROPERTY_CATALOG_MANAGE' FROM app_role WHERE code = 'ADMINISTRATOR';

-- Expedientes: categoría y subcategoría elegidas (con el nombre tal como estaba
-- al elegirlo) y el detalle de "Otro". property_case_type se sigue guardando:
-- es el tratamiento de documentos y contrato que viene de la subcategoría.
ALTER TABLE expediente
    ADD COLUMN property_category_id   UUID REFERENCES property_category (id),
    ADD COLUMN property_category_name VARCHAR(80),
    ADD COLUMN property_subtype_id    UUID REFERENCES property_subtype (id),
    ADD COLUMN property_subtype_name  VARCHAR(80),
    ADD COLUMN property_type_detail   VARCHAR(160);

-- Registros existentes: Casa y Departamento pasan tal cual a Habitacional.
UPDATE expediente
   SET property_category_id = '00000000-0000-0000-0026-0000000000c1', property_category_name = 'Habitacional',
       property_subtype_id = '00000000-0000-0000-0026-000000000101', property_subtype_name = 'Casa'
 WHERE property_case_type = 'HOUSING';
UPDATE expediente
   SET property_category_id = '00000000-0000-0000-0026-0000000000c1', property_category_name = 'Habitacional',
       property_subtype_id = '00000000-0000-0000-0026-000000000102', property_subtype_name = 'Departamento'
 WHERE property_case_type = 'DEPARTMENT';
-- Terreno y Comercial no dicen de qué tipo son: se conserva todo y quedan
-- "pendientes de clasificación" en su categoría, sin suponer la subcategoría.
UPDATE expediente
   SET property_category_id = '00000000-0000-0000-0026-0000000000c5', property_category_name = 'Terrenos'
 WHERE property_case_type = 'RESIDENTIAL_LAND';
UPDATE expediente
   SET property_category_id = '00000000-0000-0000-0026-0000000000c3', property_category_name = 'Comercial y oficinas'
 WHERE property_case_type = 'COMMERCIAL';

CREATE INDEX idx_expediente_property_subtype ON expediente (property_subtype_id);

-- Propiedades del CRM: la subcategoría del catálogo (su tipo/subtipo de
-- 21Online sale de ella). Las ya capturadas conservan su tipo de 21Online.
ALTER TABLE crm_property
    ADD COLUMN catalog_subtype_id   UUID REFERENCES property_subtype (id),
    ADD COLUMN property_type_detail VARCHAR(160);
