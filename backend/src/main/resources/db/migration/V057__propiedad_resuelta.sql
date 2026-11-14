-- Control de Cambios #42: apartado «Propiedad Resuelta», separado de los
-- expedientes de CENTURY 21 Genera y de los archivos de prueba. Privado por
-- defecto: solo quien tiene RESOLVED_PROPERTY_ADMIN (director general /
-- superusuario) lo ve completo; los demás solo los proyectos que se les
-- autoricen expresamente, con permisos separados para ver, descargar y subir.

CREATE TABLE resolved_project (
    id                         UUID PRIMARY KEY,
    name                       VARCHAR(160) NOT NULL,
    description                TEXT,
    location                   VARCHAR(300),
    estimated_investment       NUMERIC(16, 2),
    estimated_value            NUMERIC(16, 2),
    -- PENDING (pendiente de aprobación) / APPROVED (aprobado para comercialización)
    approval_status            VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    approved_by_user_id        UUID,
    approved_by_name           VARCHAR(160),
    approved_at                TIMESTAMPTZ,
    approval_note              TEXT,
    -- NOT_INCORPORATED / INCORPORATED (pasó al flujo de comercialización de C21 Genera)
    commercialization_status   VARCHAR(20) NOT NULL DEFAULT 'NOT_INCORPORATED',
    incorporated_by_name       VARCHAR(160),
    incorporated_at            TIMESTAMPTZ,
    incorporation_note         TEXT,
    linked_reference           VARCHAR(160),
    created_by_user_id         UUID,
    created_by_name            VARCHAR(160),
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,
    archived_at                TIMESTAMPTZ
);

CREATE TABLE resolved_document (
    id                   UUID PRIMARY KEY,
    project_id           UUID NOT NULL REFERENCES resolved_project (id) ON DELETE CASCADE,
    name                 VARCHAR(200) NOT NULL,
    category             VARCHAR(80),
    storage_key          TEXT NOT NULL,
    content_type         VARCHAR(120) NOT NULL,
    size_bytes           BIGINT NOT NULL,
    sha256               VARCHAR(64),
    original_filename    VARCHAR(255),
    uploaded_by_user_id  UUID,
    uploaded_by_name     VARCHAR(160),
    uploaded_at          TIMESTAMPTZ NOT NULL,
    removed_at           TIMESTAMPTZ,
    removed_by_name      VARCHAR(160)
);
CREATE INDEX idx_resolved_document_project ON resolved_document (project_id, uploaded_at DESC) WHERE removed_at IS NULL;

-- Acceso por proyecto. Ver no implica descargar ni subir.
CREATE TABLE resolved_access_grant (
    id                  UUID PRIMARY KEY,
    project_id          UUID NOT NULL REFERENCES resolved_project (id) ON DELETE CASCADE,
    user_id             UUID NOT NULL,
    user_name           VARCHAR(160),
    can_download        BOOLEAN NOT NULL DEFAULT FALSE,
    can_upload          BOOLEAN NOT NULL DEFAULT FALSE,
    granted_by_name     VARCHAR(160),
    granted_at          TIMESTAMPTZ NOT NULL,
    revoked_by_name     VARCHAR(160),
    revoked_at          TIMESTAMPTZ
);
CREATE INDEX idx_resolved_grant_user ON resolved_access_grant (user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_resolved_grant_project ON resolved_access_grant (project_id);

-- Bitácora del apartado (accesos otorgados/retirados, aprobación, incorporación, archivos).
CREATE TABLE resolved_activity (
    id            UUID PRIMARY KEY,
    project_id    UUID REFERENCES resolved_project (id) ON DELETE CASCADE,
    action        VARCHAR(40) NOT NULL,
    detail        TEXT,
    actor_user_id UUID,
    actor_name    VARCHAR(160),
    occurred_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_resolved_activity_project ON resolved_activity (project_id, occurred_at DESC);

INSERT INTO permission (code, description) VALUES
    ('RESOLVED_PROPERTY_ADMIN', 'Propiedad Resuelta: ver todo el apartado, aprobar proyectos y otorgar o retirar accesos (director general)');

-- Solo el perfil de administrador (Jorge). Los demás usuarios no ven el apartado
-- salvo que se les autorice un proyecto específico.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'RESOLVED_PROPERTY_ADMIN' FROM app_role r WHERE r.code = 'ADMINISTRATOR';
