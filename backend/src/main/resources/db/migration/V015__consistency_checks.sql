-- Prueba de consistencia entre documentos (retroalimentación 25/09, hallazgo 15).
-- La clave del hallazgo ahora identifica el tipo de comparación y, cuando
-- aplica, el participante y el documento (no cabía en 64 caracteres).
ALTER TABLE data_conflict ALTER COLUMN field_name TYPE VARCHAR(160);

-- Un conflicto se puede cerrar solo cuando los datos vuelven a coincidir
-- (p. ej. tras corregir un dato o cargar el documento correcto).
ALTER TABLE data_conflict ADD COLUMN resolved_automatically BOOLEAN NOT NULL DEFAULT FALSE;
