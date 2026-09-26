-- Retroalimentación del cliente (25/09): copropietarios ilimitados con datos
-- individuales, diferenciación persona física / moral / apoderado, datos
-- legales que alimentan el contrato y correcciones controladas con historial.

-- "REPRESENTANTE_LEGAL" (19 caracteres) no cabía en VARCHAR(16).
ALTER TABLE expediente ALTER COLUMN signer_character TYPE VARCHAR(24);

-- Datos legales estructurados (sociedad, representación, escritura/contrato
-- privado, condominio, publicidad...). Se guardan como JSON versionado por
-- la aplicación: varían según el tipo de persona y de acreditación.
ALTER TABLE expediente ADD COLUMN legal_details_json TEXT;

-- Datos individuales de cada propietario/copropietario/representante.
ALTER TABLE expediente_participant ADD COLUMN nationality        TEXT;
ALTER TABLE expediente_participant ADD COLUMN id_document_type   VARCHAR(24);
ALTER TABLE expediente_participant ADD COLUMN id_document_number TEXT;
ALTER TABLE expediente_participant ADD COLUMN id_document_issuer TEXT;
ALTER TABLE expediente_participant ADD COLUMN birth_date         DATE;
ALTER TABLE expediente_participant ADD COLUMN civil_status       VARCHAR(16);
ALTER TABLE expediente_participant ADD COLUMN marital_regime     VARCHAR(24);
ALTER TABLE expediente_participant ADD COLUMN rfc                VARCHAR(13);
ALTER TABLE expediente_participant ADD COLUMN curp               VARCHAR(18);
ALTER TABLE expediente_participant ADD COLUMN email              TEXT;
ALTER TABLE expediente_participant ADD COLUMN phone              TEXT;
ALTER TABLE expediente_participant ADD COLUMN address            TEXT;

-- El estado civil que antes se capturaba a nivel expediente corresponde al
-- propietario principal: se copia para no perder el dato ya capturado.
UPDATE expediente_participant p
   SET civil_status = m.civil_status
  FROM manual_client_data m
 WHERE m.expediente_id = p.expediente_id
   AND p.ordinal = 1
   AND m.civil_status IS NOT NULL;

-- Historial de correcciones: quién cambió qué, cuándo, de qué valor a qué
-- valor y por qué. Nunca se borra ni se edita.
CREATE TABLE expediente_change (
    id                 UUID PRIMARY KEY,
    expediente_id      UUID NOT NULL REFERENCES expediente (id) ON DELETE CASCADE,
    changed_at         TIMESTAMPTZ NOT NULL,
    actor_type         VARCHAR(16) NOT NULL,
    actor_user_id      UUID,
    actor_name         TEXT,
    actor_role         VARCHAR(32),
    section            VARCHAR(48) NOT NULL,
    field              VARCHAR(96) NOT NULL,
    old_value          TEXT,
    new_value          TEXT,
    reason             TEXT
);

CREATE INDEX idx_expediente_change_expediente ON expediente_change (expediente_id, changed_at);

-- Anexo A del contrato: superficies del inmueble.
ALTER TABLE manual_client_data ADD COLUMN land_area_m2  NUMERIC(12, 2);
ALTER TABLE manual_client_data ADD COLUMN built_area_m2 NUMERIC(12, 2);
