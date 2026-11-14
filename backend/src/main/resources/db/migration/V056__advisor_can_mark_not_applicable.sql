-- Control de Cambios #46: en el alta, el asesor indica si un documento "no aplica"
-- (p. ej. recibo de luz de un terreno) con motivo obligatorio, en lugar de dejarlo
-- pendiente. Solo ve sus propios expedientes. Pendiente de confirmar con el cliente
-- y legal; el administrador puede quitarlo desde Roles y permisos.
INSERT INTO role_permission (role_id, permission_code)
SELECT '00000000-0000-0000-0000-000000000002', 'DOCUMENT_MARK_NOT_APPLICABLE'
 WHERE NOT EXISTS (
   SELECT 1 FROM role_permission
    WHERE role_id = '00000000-0000-0000-0000-000000000002' AND permission_code = 'DOCUMENT_MARK_NOT_APPLICABLE');
