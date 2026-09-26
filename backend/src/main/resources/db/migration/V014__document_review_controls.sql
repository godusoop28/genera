-- Controles de revisión documental (retroalimentación 25/09, hallazgos 2, 4, 7, 8, 9).

-- "No aplica" con justificación y usuario responsable.
ALTER TABLE document ADD COLUMN not_applicable_justification TEXT;
ALTER TABLE document ADD COLUMN not_applicable_by_user_id    UUID REFERENCES app_user (id);
ALTER TABLE document ADD COLUMN not_applicable_at            TIMESTAMPTZ;

-- Última decisión de revisión, para mostrarle al cliente qué corregir y por qué.
ALTER TABLE document ADD COLUMN last_review_decision    VARCHAR(16);
ALTER TABLE document ADD COLUMN last_review_reason_code VARCHAR(32);
ALTER TABLE document ADD COLUMN last_review_comment     TEXT;
ALTER TABLE document ADD COLUMN last_reviewed_at        TIMESTAMPTZ;

-- Quién cargó cada versión (cliente por su liga o usuario interno).
ALTER TABLE document_version ADD COLUMN uploaded_by_user_id UUID REFERENCES app_user (id);
ALTER TABLE document_version ADD COLUMN uploaded_by_name    TEXT;

-- Resultado de la revisión de contenido con IA: ¿corresponde al documento
-- solicitado?, ¿es legible? NULL = no se pudo determinar.
ALTER TABLE document_version ADD COLUMN ai_type_matches     BOOLEAN;
ALTER TABLE document_version ADD COLUMN ai_legible          BOOLEAN;
ALTER TABLE document_version ADD COLUMN ai_detected_kind    TEXT;
ALTER TABLE document_version ADD COLUMN ai_observations     TEXT;
ALTER TABLE document_version ADD COLUMN ai_assessed_at      TIMESTAMPTZ;

-- Aceptación por excepción: justificación obligatoria y qué alertas se
-- pasaron por alto (el usuario responsable ya está en reviewed_by).
ALTER TABLE document_review ADD COLUMN override_justification TEXT;
ALTER TABLE document_review ADD COLUMN overridden_issues      TEXT;
