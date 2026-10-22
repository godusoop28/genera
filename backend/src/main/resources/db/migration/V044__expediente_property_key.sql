-- Control de Cambios #27 (y su adenda): clave estructurada del inmueble.
--
-- Formato: tipo de inmueble + operación (V venta / R renta) + iniciales del
-- asesor + consecutivo de dos dígitos, p. ej. CVEA03 = Casa, Venta, asesor
-- E. A., tercera de esa combinación. Se genera sola al dar de alta; las
-- propiedades que ya existían la reciben a mano (administrador o directivo)
-- respetando la clave que se capture.
--
-- property_key_prefix es la combinación (todo menos el consecutivo) y
-- property_key_number el consecutivo: el siguiente automático es el más alto
-- registrado para esa combinación + 1. La unicidad de property_key impide
-- asignar la misma clave a dos expedientes.

ALTER TABLE property_subtype ADD COLUMN key_code VARCHAR(3);

UPDATE property_subtype SET key_code = c.code
  FROM (VALUES
    ('00000000-0000-0000-0026-000000000101'::uuid, 'C'),
    ('00000000-0000-0000-0026-000000000102'::uuid, 'D'),
    ('00000000-0000-0000-0026-000000000103'::uuid, 'CC'),
    ('00000000-0000-0000-0026-000000000104'::uuid, 'DX'),
    ('00000000-0000-0000-0026-000000000105'::uuid, 'PH'),
    ('00000000-0000-0000-0026-000000000201'::uuid, 'EH'),
    ('00000000-0000-0000-0026-000000000202'::uuid, 'EO'),
    ('00000000-0000-0000-0026-000000000203'::uuid, 'EC'),
    ('00000000-0000-0000-0026-000000000204'::uuid, 'EM'),
    ('00000000-0000-0000-0026-000000000301'::uuid, 'L'),
    ('00000000-0000-0000-0026-000000000302'::uuid, 'O'),
    ('00000000-0000-0000-0026-000000000303'::uuid, 'CO'),
    ('00000000-0000-0000-0026-000000000304'::uuid, 'PC'),
    ('00000000-0000-0000-0026-000000000401'::uuid, 'N'),
    ('00000000-0000-0000-0026-000000000402'::uuid, 'B'),
    ('00000000-0000-0000-0026-000000000403'::uuid, 'TI'),
    ('00000000-0000-0000-0026-000000000404'::uuid, 'PI'),
    ('00000000-0000-0000-0026-000000000501'::uuid, 'T'),
    ('00000000-0000-0000-0026-000000000502'::uuid, 'TC'),
    ('00000000-0000-0000-0026-000000000503'::uuid, 'TM'),
    ('00000000-0000-0000-0026-000000000504'::uuid, 'TR'),
    ('00000000-0000-0000-0026-000000000505'::uuid, 'ML'),
    ('00000000-0000-0000-0026-000000000601'::uuid, 'RA'),
    ('00000000-0000-0000-0026-000000000602'::uuid, 'F'),
    ('00000000-0000-0000-0026-000000000603'::uuid, 'TA'),
    ('00000000-0000-0000-0026-000000000701'::uuid, 'H'),
    ('00000000-0000-0000-0026-000000000702'::uuid, 'HS'),
    ('00000000-0000-0000-0026-000000000703'::uuid, 'CB'),
    ('00000000-0000-0000-0026-000000000704'::uuid, 'CJ'),
    ('00000000-0000-0000-0026-000000000801'::uuid, 'ES'),
    ('00000000-0000-0000-0026-000000000802'::uuid, 'IE'),
    ('00000000-0000-0000-0026-000000000803'::uuid, 'X')
  ) AS c (id, code)
 WHERE property_subtype.id = c.id;

ALTER TABLE expediente
    ADD COLUMN property_key        VARCHAR(24),
    ADD COLUMN property_key_prefix VARCHAR(20),
    ADD COLUMN property_key_number INTEGER;

CREATE UNIQUE INDEX uq_expediente_property_key ON expediente (property_key);
CREATE INDEX idx_expediente_property_key_prefix ON expediente (property_key_prefix, property_key_number);

INSERT INTO permission (code, description) VALUES
    ('EXPEDIENT_KEY_ASSIGN', 'Asignar o corregir a mano la clave estructurada del inmueble y consultar las claves en uso');

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'EXPEDIENT_KEY_ASSIGN' FROM app_role r WHERE r.code IN ('ADMINISTRATOR', 'DIRECTOR');
