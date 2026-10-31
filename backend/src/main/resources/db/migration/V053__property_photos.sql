-- Control de Cambios #33: fotografías del inmueble que suben el cliente (desde
-- su liga, en el mismo flujo de documentos) y el asesor (desde el expediente).
-- Van separadas de los documentos legales. Subirlas no las publica: el asesor
-- elige cuáles se usan en el portal, la principal y el orden.
CREATE TABLE property_photo (
    id                   UUID PRIMARY KEY,
    expediente_id        UUID NOT NULL REFERENCES expediente (id) ON DELETE CASCADE,
    storage_key          TEXT NOT NULL,
    content_type         VARCHAR(40) NOT NULL,
    size_bytes           BIGINT NOT NULL,
    sha256               VARCHAR(64),
    original_filename    VARCHAR(255),
    uploaded_by_kind     VARCHAR(16) NOT NULL,
    uploaded_by_user_id  UUID,
    uploaded_by_name     VARCHAR(160),
    uploaded_at          TIMESTAMPTZ NOT NULL,
    selected_for_portal  BOOLEAN NOT NULL DEFAULT FALSE,
    main_photo           BOOLEAN NOT NULL DEFAULT FALSE,
    portal_order         INTEGER,
    selection_updated_by VARCHAR(160),
    selection_updated_at TIMESTAMPTZ,
    removed_at           TIMESTAMPTZ,
    removed_by_name      VARCHAR(160)
);
CREATE INDEX idx_property_photo_expediente ON property_photo (expediente_id, uploaded_at) WHERE removed_at IS NULL;
