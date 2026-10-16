-- Controles de cambios #16 y #21 (16/10).

-- #21: textos estándar del contrato que el administrador actualiza sin liberar código.
CREATE TABLE contract_setting (
    setting_key        VARCHAR(64) PRIMARY KEY,
    setting_value      TEXT        NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    updated_by_user_id UUID,
    updated_by_name    VARCHAR(160)
);

INSERT INTO contract_setting (setting_key, setting_value, updated_at) VALUES (
    'VISIT_INSTRUCTIONS',
    'Las visitas al inmueble deberán programarse previamente con el propietario o la persona autorizada, con un mínimo de 24 horas de anticipación, sujetas a disponibilidad y confirmación. Todas las visitas serán coordinadas por Century 21 Genera y realizadas en compañía de un asesor inmobiliario autorizado. No se permitirá el acceso al inmueble sin previa autorización. Para toda visita, se solicitará al interesado que envíe previamente una copia de su identificación oficial.',
    now());

INSERT INTO permission (code, description) VALUES
    ('SETTINGS_MANAGE', 'Actualizar los textos estándar del contrato (p. ej. instrucciones para visitas)');

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'SETTINGS_MANAGE' FROM app_role r WHERE r.code = 'ADMINISTRATOR';

-- #16: en el visor, el administrador y los directivos de C21 Genera pueden descargar e imprimir;
-- el asesor solo consulta.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'DOCUMENT_DOWNLOAD' FROM app_role r
 WHERE r.code = 'DIRECTOR'
   AND NOT EXISTS (SELECT 1 FROM role_permission rp WHERE rp.role_id = r.id AND rp.permission_code = 'DOCUMENT_DOWNLOAD');
