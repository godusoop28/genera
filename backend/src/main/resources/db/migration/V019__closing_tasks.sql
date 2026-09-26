-- Seguimiento posterior a la firma (retroalimentación 25/09, hallazgo 17):
-- al firmarse el contrato se abren tareas internas que se completan solas
-- cuando ocurre el evento correspondiente o las marca el staff.
CREATE TABLE closing_task (
    id                UUID PRIMARY KEY,
    closing_case_id   UUID NOT NULL REFERENCES closing_case (id) ON DELETE CASCADE,
    code              VARCHAR(48) NOT NULL,
    title             TEXT NOT NULL,
    due_date          DATE,
    done              BOOLEAN NOT NULL DEFAULT FALSE,
    done_at           TIMESTAMPTZ,
    done_by_user_id   UUID REFERENCES app_user (id),
    done_note         TEXT,
    created_at        TIMESTAMPTZ NOT NULL,
    UNIQUE (closing_case_id, code)
);

ALTER TABLE closing_case ADD COLUMN signed_contract_id UUID;
