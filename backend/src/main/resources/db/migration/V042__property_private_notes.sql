-- Notas internas de la propiedad: solo se ven en Genera, nunca se mandan a 21Online.
ALTER TABLE crm_property
    ADD COLUMN private_notes TEXT,
    -- Etiquetas libres (JSON: ["dueño flexible", "urge vender"]).
    ADD COLUMN tags          TEXT;
