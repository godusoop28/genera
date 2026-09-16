-- Módulo expedientes: el núcleo del Módulo 1 (no una propiedad, ver AGENTS §8).

CREATE SEQUENCE expediente_folio_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE expediente (
    id                    UUID PRIMARY KEY,
    folio                 VARCHAR(32) NOT NULL UNIQUE,
    owner_display_name    TEXT NOT NULL,
    status                VARCHAR(32) NOT NULL,
    person_type           VARCHAR(16) NOT NULL,
    signer_character      VARCHAR(16) NOT NULL,
    accreditation_type    VARCHAR(24) NOT NULL,
    condominium_regime    BOOLEAN NOT NULL DEFAULT FALSE,
    property_case_type    VARCHAR(24) NOT NULL,
    declared_legal_status VARCHAR(24) NOT NULL,
    property_address      TEXT,
    all_required_documents_uploaded BOOLEAN NOT NULL DEFAULT FALSE,
    decision_reason       TEXT,
    decided_by_user_id    UUID,
    decided_at            TIMESTAMPTZ,
    created_by_user_id    UUID NOT NULL REFERENCES app_user (id),
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_expediente_status ON expediente (status);
CREATE INDEX idx_expediente_created_by ON expediente (created_by_user_id);

CREATE TABLE expediente_participant (
    id             UUID PRIMARY KEY,
    expediente_id  UUID NOT NULL REFERENCES expediente (id) ON DELETE CASCADE,
    role           VARCHAR(24) NOT NULL,
    full_name      TEXT NOT NULL,
    ordinal        INT NOT NULL
);

CREATE INDEX idx_expediente_participant_expediente ON expediente_participant (expediente_id);

CREATE TABLE manual_client_data (
    expediente_id                 UUID PRIMARY KEY REFERENCES expediente (id) ON DELETE CASCADE,
    civil_status                  VARCHAR(16),
    authorized_price              NUMERIC(14, 2),
    email                         TEXT,
    phone                         TEXT,
    notification_address          TEXT,
    visit_instructions            TEXT,
    marketing_data_authorized     BOOLEAN,
    receive_ads_authorized        BOOLEAN,
    additional_services_requested TEXT,
    bedrooms                      INT,
    bathrooms                     INT,
    parking_spots                 INT,
    conservation_status           TEXT,
    available_services            TEXT,
    relevant_features             TEXT,
    contract_signature_date       DATE
);
