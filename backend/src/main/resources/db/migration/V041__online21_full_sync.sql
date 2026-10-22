-- Sincronización completa con 21Online.
-- Propiedades: todo el inventario de la oficina se importa (created_by_user_id
-- queda NULL en las que trae el proceso programado), con sus atributos
-- adicionales (características, contrato y publicación, JSON con las claves de
-- 21Online) y las URLs de sus fotos (solo lectura: se suben en 21Online).
ALTER TABLE crm_property
    ALTER COLUMN created_by_user_id DROP NOT NULL,
    ADD COLUMN extra_attributes  TEXT,
    ADD COLUMN photo_urls        TEXT,
    -- caracteristicasJSON tal como lo trae 21Online: al editar amenidades se conservan sus demás campos.
    ADD COLUMN features_template TEXT;

-- Asesores: copia de su usuario de 21Online (21Online manda).
ALTER TABLE crm_advisor_profile
    ADD COLUMN online21_name         VARCHAR(200),
    ADD COLUMN online21_email        VARCHAR(200),
    ADD COLUMN online21_mobile_phone VARCHAR(40),
    ADD COLUMN online21_office_phone VARCHAR(40),
    ADD COLUMN online21_whatsapp     VARCHAR(40),
    ADD COLUMN online21_photo_url    VARCHAR(500),
    ADD COLUMN online21_position     VARCHAR(80),
    ADD COLUMN online21_profile_url  VARCHAR(500),
    ADD COLUMN online21_synced_at    TIMESTAMPTZ;
