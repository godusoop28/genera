-- Módulo identity: personal interno, roles y permisos.
-- Los clientes/propietarios NUNCA tienen fila aquí (ver AGENTS §13).

CREATE TABLE app_role (
    id          UUID PRIMARY KEY,
    code        VARCHAR(32) NOT NULL UNIQUE,
    name        TEXT NOT NULL,
    description TEXT NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE permission (
    code        VARCHAR(64) PRIMARY KEY,
    description TEXT NOT NULL
);

CREATE TABLE role_permission (
    role_id         UUID NOT NULL REFERENCES app_role (id) ON DELETE CASCADE,
    permission_code VARCHAR(64) NOT NULL REFERENCES permission (code) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_code)
);

CREATE TABLE app_user (
    id               UUID PRIMARY KEY,
    name             TEXT NOT NULL,
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    TEXT NOT NULL,
    role_id          UUID NOT NULL REFERENCES app_role (id),
    status           VARCHAR(16) NOT NULL,
    last_activity_at TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_app_user_role ON app_user (role_id);

CREATE TABLE refresh_token (
    id                    UUID PRIMARY KEY,
    user_id               UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash            VARCHAR(64) NOT NULL UNIQUE,
    created_at            TIMESTAMPTZ NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    revoked_at            TIMESTAMPTZ,
    replaced_by_token_id  UUID
);

CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);

-- Catálogo de permisos (ver identity.domain.PermissionCode). Es dato de
-- referencia, no dato de demo: debe existir en todos los ambientes.
INSERT INTO permission (code, description) VALUES
    ('EXPEDIENT_CREATE', 'Crear expediente'),
    ('EXPEDIENT_VIEW_OWN', 'Consultar expedientes propios'),
    ('EXPEDIENT_VIEW_ALL', 'Consultar todos los expedientes'),
    ('PUBLIC_LINK_GENERATE', 'Generar liga de cliente'),
    ('DOCUMENT_REVIEW', 'Revisar documentos'),
    ('DOCUMENT_ACCEPT', 'Aceptar documentos'),
    ('DOCUMENT_RETURN', 'Devolver documentos'),
    ('DOCUMENT_REJECT', 'Rechazar documentos'),
    ('EXTRACTED_DATA_EDIT', 'Editar datos extraídos'),
    ('CONTRACT_PREPARE', 'Preparar contrato'),
    ('CONTRACT_GENERATE', 'Generar contrato'),
    ('DOCUMENT_EMAIL_SEND', 'Enviar documentos por correo'),
    ('RECEPTION_SIGN', 'Firmar recepción documental'),
    ('PROPERTY_DECIDE', 'Aceptar/rechazar inmueble'),
    ('USER_MANAGE', 'Gestionar usuarios'),
    ('ROLE_MANAGE', 'Gestionar roles y permisos');

-- Roles base (ver AGENTS §16). UUID fijos para que sean predecibles en tests.
INSERT INTO app_role (id, code, name, description, version, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMINISTRATOR', 'Administrador / Representante legal',
     'Control total del sistema. Único rol que puede aceptar o rechazar un inmueble y gestionar usuarios y permisos.',
     0, now(), now()),
    ('00000000-0000-0000-0000-000000000002', 'ADVISOR', 'Asesor',
     'Da seguimiento a sus propios expedientes, genera ligas y prepara el contrato.', 0, now(), now()),
    ('00000000-0000-0000-0000-000000000003', 'DOCUMENT_REVIEWER', 'Revisor documental',
     'Revisa, acepta, devuelve o rechaza documentos recibidos de los clientes.', 0, now(), now());

INSERT INTO role_permission (role_id, permission_code)
SELECT '00000000-0000-0000-0000-000000000001', code FROM permission;

INSERT INTO role_permission (role_id, permission_code) VALUES
    ('00000000-0000-0000-0000-000000000002', 'EXPEDIENT_CREATE'),
    ('00000000-0000-0000-0000-000000000002', 'EXPEDIENT_VIEW_OWN'),
    ('00000000-0000-0000-0000-000000000002', 'PUBLIC_LINK_GENERATE'),
    ('00000000-0000-0000-0000-000000000002', 'DOCUMENT_REVIEW'),
    ('00000000-0000-0000-0000-000000000002', 'EXTRACTED_DATA_EDIT'),
    ('00000000-0000-0000-0000-000000000002', 'CONTRACT_PREPARE'),
    ('00000000-0000-0000-0000-000000000002', 'DOCUMENT_EMAIL_SEND'),

    ('00000000-0000-0000-0000-000000000003', 'EXPEDIENT_VIEW_OWN'),
    ('00000000-0000-0000-0000-000000000003', 'EXPEDIENT_VIEW_ALL'),
    ('00000000-0000-0000-0000-000000000003', 'DOCUMENT_REVIEW'),
    ('00000000-0000-0000-0000-000000000003', 'DOCUMENT_ACCEPT'),
    ('00000000-0000-0000-0000-000000000003', 'DOCUMENT_RETURN'),
    ('00000000-0000-0000-0000-000000000003', 'DOCUMENT_REJECT'),
    ('00000000-0000-0000-0000-000000000003', 'EXTRACTED_DATA_EDIT');
