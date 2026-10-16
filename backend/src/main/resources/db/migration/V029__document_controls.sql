-- Controles de cambios del cliente (15/10).

-- #1: documentos adicionales que no están en el listado, con el nombre que les dé el usuario.
-- Se guardan como cualquier otro documento (tipo OTHER) y se muestran con este nombre.
ALTER TABLE document ADD COLUMN custom_name VARCHAR(120);

-- #5: el asesor aprueba como excepción, con nota, un documento que falta: no bloquea la
-- aprobación ni el contrato, y el cliente puede entregarlo después.
ALTER TABLE document ADD COLUMN exception_note TEXT;
ALTER TABLE document ADD COLUMN exception_by_user_id UUID;
ALTER TABLE document ADD COLUMN exception_at TIMESTAMPTZ;

-- #10: al acercarse el cierre se pide una versión actualizada (constancia fiscal,
-- comprobante de domicilio...). La versión anterior se conserva siempre.
ALTER TABLE document ADD COLUMN update_request_note TEXT;
ALTER TABLE document ADD COLUMN update_requested_at TIMESTAMPTZ;

INSERT INTO permission (code, description) VALUES
    ('DOCUMENT_DOWNLOAD', 'Descargar o imprimir los documentos del cliente (los demás solo los consultan en el visor)'),
    ('DOCUMENT_EXCEPTION', 'Aprobar como excepción, con nota, un documento que el cliente entregará después'),
    ('DOCUMENT_UPDATE_REQUEST', 'Pedir al cliente una versión actualizada de un documento ya entregado');

-- #6: descargar e imprimir es un permiso especial solo del administrador.
INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'DOCUMENT_DOWNLOAD' FROM app_role r WHERE r.code = 'ADMINISTRATOR';

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, p.code
  FROM app_role r
  CROSS JOIN permission p
 WHERE r.code IN ('ADMINISTRATOR', 'DIRECTOR', 'ADVISOR', 'DOCUMENT_REVIEWER')
   AND p.code IN ('DOCUMENT_EXCEPTION', 'DOCUMENT_UPDATE_REQUEST');
