-- Matriz de permisos detallada (retroalimentación 25/09, hallazgo 10): cada
-- acción sensible tiene su propio permiso y existe un perfil de Director.

INSERT INTO permission (code, description) VALUES
    ('EXPEDIENT_EDIT', 'Corregir datos del expediente, participantes y datos legales (queda en el historial)'),
    ('DOCUMENT_UPLOAD', 'Cargar documentos en nombre del cliente (p. ej. escaneo en oficina)'),
    ('DOCUMENT_QUALITY_OVERRIDE', 'Autorizar por excepción un documento ilegible o que no coincide con lo solicitado, con justificación'),
    ('DOCUMENT_MARK_NOT_APPLICABLE', 'Marcar un documento como "No aplica" con justificación, o volver a solicitarlo'),
    ('CONTRACT_SIGN', 'Firmar el contrato en representación de la intermediaria'),
    ('AUDIT_VIEW', 'Consultar la bitácora técnica y el historial de notificaciones');

-- El administrador conserva todos los permisos.
INSERT INTO role_permission (role_id, permission_code)
SELECT '00000000-0000-0000-0000-000000000001', code FROM permission
 WHERE code IN ('EXPEDIENT_EDIT', 'DOCUMENT_UPLOAD', 'DOCUMENT_QUALITY_OVERRIDE', 'DOCUMENT_MARK_NOT_APPLICABLE', 'CONTRACT_SIGN', 'AUDIT_VIEW');

INSERT INTO app_role (id, code, name, description, version, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000004', 'DIRECTOR', 'Director',
     'Supervisa todos los expedientes: aprueba documentos y excepciones, genera y firma contratos y decide sobre el inmueble. No administra usuarios ni roles.',
     0, now(), now());

INSERT INTO role_permission (role_id, permission_code)
SELECT '00000000-0000-0000-0000-000000000004', code FROM permission
 WHERE code NOT IN ('USER_MANAGE', 'ROLE_MANAGE');

-- Asesor: corrige sus expedientes y carga documentos en nombre del cliente.
INSERT INTO role_permission (role_id, permission_code) VALUES
    ('00000000-0000-0000-0000-000000000002', 'EXPEDIENT_EDIT'),
    ('00000000-0000-0000-0000-000000000002', 'DOCUMENT_UPLOAD');

-- Revisor documental: carga en oficina y marca documentos que no aplican.
INSERT INTO role_permission (role_id, permission_code) VALUES
    ('00000000-0000-0000-0000-000000000003', 'DOCUMENT_UPLOAD'),
    ('00000000-0000-0000-0000-000000000003', 'DOCUMENT_MARK_NOT_APPLICABLE');

-- Descripciones de roles alineadas con lo que realmente pueden hacer.
UPDATE app_role SET description =
    'Da seguimiento solo a los expedientes que creó: genera ligas, corrige datos, carga documentos y prepara el contrato. No acepta documentos ni decide el inmueble.'
 WHERE code = 'ADVISOR';
UPDATE app_role SET description =
    'Revisa todos los expedientes: acepta, devuelve o rechaza documentos y marca los que no aplican. No autoriza excepciones de calidad ni genera contratos.'
 WHERE code = 'DOCUMENT_REVIEWER';
