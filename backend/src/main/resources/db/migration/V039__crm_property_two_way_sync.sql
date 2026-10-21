-- Sincronización en ambos sentidos con 21Online.
-- origin: GENERA (se capturó aquí) u ONLINE21 (se dio de alta en 21Online y se
-- vinculó al editarla o darla de baja desde Genera).
-- synced_draft: lo último que se vio o se mandó a 21Online (JSON). Al editar
-- solo se mandan los campos que cambiaron respecto de esto, para no pisar lo
-- que se editó allá en otros campos.
-- remote_modified_at: fechaModificacion de 21Online la última vez que se trajo;
-- el proceso programado trae los cambios hechos allá cuando es más reciente.

ALTER TABLE crm_property
    ADD COLUMN origin             VARCHAR(16) NOT NULL DEFAULT 'GENERA',
    ADD COLUMN synced_draft       TEXT,
    ADD COLUMN remote_modified_at TIMESTAMPTZ;
