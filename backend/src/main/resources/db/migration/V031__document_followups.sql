-- Controles de cambios #13, #14 y #18 (16/10).

-- #13: qué comprobante de domicilio se entregó (recibo de luz, estado de cuenta bancario,
-- Telmex, telefonía o internet), para identificarlo en el expediente.
ALTER TABLE document ADD COLUMN address_proof_kind VARCHAR(24);

-- #14: el asesor marca un documento como "Pendiente" (se entrega después, con nota y fecha
-- estimada opcionales). No bloquea el avance; al subirse el archivo deja de estar pendiente.
ALTER TABLE document ADD COLUMN pending_note TEXT;
ALTER TABLE document ADD COLUMN pending_due_date DATE;
ALTER TABLE document ADD COLUMN pending_by_user_id UUID;
ALTER TABLE document ADD COLUMN pending_at TIMESTAMPTZ;

-- #18: el nombre de un documento agregado a mano (custom_name) ahora se puede corregir; no
-- requiere columnas nuevas, el cambio queda en la bitácora.
