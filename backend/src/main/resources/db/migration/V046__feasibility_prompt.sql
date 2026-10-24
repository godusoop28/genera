-- Prompts maestros de la factibilidad editables desde Configuración.
--
-- Cada guardado crea una versión nueva (nunca se sobrescribe): la vigente es la de número
-- más alto por prompt. Sin versiones, el sistema usa el texto original que entregó el
-- cliente (resources/feasibility/*.md), que cuenta como versión 0.

CREATE TABLE feasibility_prompt_version (
    id                  UUID PRIMARY KEY,
    prompt_key          VARCHAR(32) NOT NULL,
    version_number      INT NOT NULL,
    content             TEXT NOT NULL,
    note                VARCHAR(200),
    created_at          TIMESTAMPTZ NOT NULL,
    created_by_user_id  UUID,
    created_by_name     VARCHAR(160),
    UNIQUE (prompt_key, version_number)
);

-- Qué versión de cada prompt usó cada factibilidad (trazabilidad cuando se mejoren).
ALTER TABLE feasibility_report ADD COLUMN analyst_prompt_version INT;
ALTER TABLE feasibility_report ADD COLUMN presentation_prompt_version INT;
