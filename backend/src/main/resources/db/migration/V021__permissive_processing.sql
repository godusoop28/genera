-- Recepción permisiva (revisión del cliente 01/10): aceptar y extraer todo lo
-- posible; rechazar solo lo que de verdad no se puede procesar.

-- Calidad de la imagen en tres niveles: ACCEPTED, ACCEPTED_WITH_WARNINGS o
-- UNREADABLE. Las advertencias (foto oscura, resolución baja, algo borrosa)
-- se muestran al revisor pero nunca detienen el PDF ni la extracción.
ALTER TABLE document_version ADD COLUMN quality_level    VARCHAR(32);
ALTER TABLE document_version ADD COLUMN quality_warnings TEXT;

-- Advertencias de la revisión con IA (p. ej. "solo se ve el frente de la
-- credencial") y cuántas páginas se analizaron de cuántas tiene el archivo.
ALTER TABLE document_version ADD COLUMN ai_warnings       TEXT;
ALTER TABLE document_version ADD COLUMN ai_pages_analyzed INT;
ALTER TABLE document_version ADD COLUMN ai_pages_total    INT;
-- Cuántos campos del esquema del tipo se encontraron: permite distinguir
-- "extracción completa" de "extracción parcial" (que también es válida).
ALTER TABLE document_version ADD COLUMN ai_fields_expected INT;
ALTER TABLE document_version ADD COLUMN ai_fields_found    INT;

-- Página del documento donde la IA encontró cada dato (NULL si no lo indicó).
-- Los datos útiles fuera del esquema del tipo se guardan con prefijo "extra.".
ALTER TABLE extracted_field_observation ADD COLUMN source_page INT;
ALTER TABLE extracted_field_observation ALTER COLUMN field_name TYPE VARCHAR(160);
CREATE INDEX idx_extracted_field_version ON extracted_field_observation (document_version_id);

-- Severidad de cada posible inconsistencia: INFO, WARNING o CRITICAL. Solo
-- CRITICAL (evidencia fuerte de otra persona u otro inmueble) bloquea el envío
-- del contrato a firma; el resto es ayuda para el revisor.
ALTER TABLE data_conflict ADD COLUMN severity VARCHAR(16) NOT NULL DEFAULT 'WARNING';

-- Borradores de formularios guardados en el servidor (autoguardado): sobreviven
-- a recargar la página, cambiar de equipo o una caída temporal de red.
-- owner_key: "user:<uuid>" para el staff o "link:<expediente>" para el cliente.
CREATE TABLE form_draft (
    id          UUID PRIMARY KEY,
    owner_key   VARCHAR(80)  NOT NULL,
    form_key    VARCHAR(160) NOT NULL,
    payload     TEXT         NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_form_draft UNIQUE (owner_key, form_key)
);
