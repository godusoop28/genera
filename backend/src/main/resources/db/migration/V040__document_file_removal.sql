-- "Eliminar archivo" de un documento: la versión deja de ser vigente (el documento vuelve a
-- pendiente y se puede subir de nuevo), pero la fila y el archivo se conservan para auditoría.
ALTER TABLE document_version ADD COLUMN removed_at TIMESTAMPTZ;
ALTER TABLE document_version ADD COLUMN removed_by_user_id UUID;
