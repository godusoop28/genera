-- La revisión automática con IA no respondió después de todos los reintentos:
-- el contenido del archivo quedó sin verificar y no se puede aceptar sin
-- una autorización de excepción (nunca se interpreta como aprobado).
ALTER TABLE document_version ADD COLUMN ai_check_failed BOOLEAN NOT NULL DEFAULT FALSE;
