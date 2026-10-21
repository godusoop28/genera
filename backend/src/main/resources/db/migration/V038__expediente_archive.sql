-- Archivar expedientes: el expediente sale del listado de trabajo y de los
-- totales (sigue consultable en "Archivados" y se puede desarchivar). No borra
-- nada ni cambia su estatus; mientras está archivado la liga del cliente no
-- acepta cargas.

ALTER TABLE expediente
    ADD COLUMN archived_at          TIMESTAMPTZ,
    ADD COLUMN archived_by_user_id  UUID,
    ADD COLUMN archive_reason       VARCHAR(500);

CREATE INDEX idx_expediente_archived ON expediente (archived_at);

INSERT INTO permission (code, description) VALUES
    ('EXPEDIENT_ARCHIVE', 'Archivar y desarchivar expedientes (queda en el historial)');

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'EXPEDIENT_ARCHIVE' FROM app_role r WHERE r.code IN ('ADMINISTRATOR', 'DIRECTOR');
