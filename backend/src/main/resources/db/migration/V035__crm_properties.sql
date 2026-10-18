-- Gestión de propiedades en el CRM, vinculada con 21Online.
-- El inventario sigue viviendo en 21Online (se consulta en vivo). Aquí solo se
-- guardan las propiedades que se capturan EN Genera, para mandarlas a 21Online
-- (POST /api/propiedades), reintentar si 21Online no respondió y poder
-- editarlas después. online21_id queda lleno en cuanto 21Online la acepta.

INSERT INTO permission (code, description) VALUES
    ('PROPERTY_VIEW', 'Consultar el inventario de propiedades de 21Online en el CRM'),
    ('PROPERTY_MANAGE', 'Dar de alta y editar propiedades (se publican en 21Online)');

-- Administrador, Director y Asesor: el asesor capta y da de alta sus propiedades.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
  FROM app_role r
  CROSS JOIN permission p
 WHERE r.code IN ('ADMINISTRATOR', 'DIRECTOR', 'ADVISOR')
   AND p.code IN ('PROPERTY_VIEW', 'PROPERTY_MANAGE');

CREATE TABLE crm_property (
    id                  UUID PRIMARY KEY,
    online21_id         BIGINT UNIQUE,
    sync_status         VARCHAR(16)   NOT NULL,
    sync_error          VARCHAR(500),
    sync_uncertain      BOOLEAN       NOT NULL DEFAULT FALSE,
    synced_at           TIMESTAMPTZ,
    operation           VARCHAR(8)    NOT NULL,
    property_type       VARCHAR(24)   NOT NULL,
    subtype             VARCHAR(40)   NOT NULL,
    contract_type       VARCHAR(16),
    status              VARCHAR(24)   NOT NULL,
    headline            VARCHAR(200)  NOT NULL,
    description         TEXT,
    sale_price          NUMERIC(14, 2),
    rent_price          NUMERIC(14, 2),
    currency            VARCHAR(3)    NOT NULL,
    hide_price          BOOLEAN       NOT NULL DEFAULT FALSE,
    street              VARCHAR(200),
    ext_number          VARCHAR(40),
    neighborhood        VARCHAR(160)  NOT NULL,
    municipality        VARCHAR(160)  NOT NULL,
    state               VARCHAR(80)   NOT NULL,
    postal_code         VARCHAR(10),
    hide_street         BOOLEAN       NOT NULL DEFAULT FALSE,
    hide_number         BOOLEAN       NOT NULL DEFAULT TRUE,
    bedrooms            INTEGER,
    bathrooms           NUMERIC(4, 1),
    half_bathrooms      INTEGER,
    parking             INTEGER,
    land_m2             NUMERIC(12, 2),
    built_m2            NUMERIC(12, 2),
    levels              INTEGER,
    age                 INTEGER,
    maintenance_fee     NUMERIC(12, 2),
    commission          NUMERIC(6, 2),
    publish_online      BOOLEAN       NOT NULL DEFAULT TRUE,
    advisor_user_id     UUID,
    created_by_user_id  UUID          NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_crm_property_sync ON crm_property (sync_status);
CREATE INDEX idx_crm_property_advisor ON crm_property (advisor_user_id);
