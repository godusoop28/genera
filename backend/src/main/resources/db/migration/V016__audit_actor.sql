-- Bitácora con actor explícito (retroalimentación 25/09, hallazgo 9): cada
-- movimiento registra quién lo hizo (cliente por su liga, usuario interno con
-- su nombre y rol, o el sistema), la acción y el documento afectado.

ALTER TABLE activity ADD COLUMN actor_type     VARCHAR(16);
ALTER TABLE activity ADD COLUMN actor_user_id  UUID;
ALTER TABLE activity ADD COLUMN actor_name     TEXT;
ALTER TABLE activity ADD COLUMN actor_role     VARCHAR(32);
ALTER TABLE activity ADD COLUMN action         VARCHAR(64);
ALTER TABLE activity ADD COLUMN document_id    UUID;
ALTER TABLE activity ADD COLUMN document_label TEXT;
ALTER TABLE activity ALTER COLUMN message TYPE VARCHAR(2000);

ALTER TABLE audit_event ADD COLUMN actor_type VARCHAR(16);
ALTER TABLE audit_event ADD COLUMN actor_name TEXT;
ALTER TABLE audit_event ADD COLUMN actor_role VARCHAR(32);
ALTER TABLE audit_event ALTER COLUMN summary TYPE VARCHAR(2000);

-- Las cargas históricas se atribuían siempre al cliente aunque las hiciera el
-- staff; se deja constancia de que el actor de esas filas no es confiable.
UPDATE activity SET actor_type = 'UNKNOWN' WHERE actor_type IS NULL;
