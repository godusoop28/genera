-- Ajustes autorizados de los controles de cambios #32, #34 y #36. Solo agrega
-- columnas opcionales: no borra ni cambia datos existentes.

-- CC#32: fecha de cierre del seguimiento de cierre que ya existe. Se llena al
-- completarse; los cierres ya completados toman la fecha de su última
-- actualización (Ciudad de México), que es cuando se completaron.
ALTER TABLE closing_case ADD COLUMN closed_on DATE;
UPDATE closing_case
   SET closed_on = (updated_at AT TIME ZONE 'America/Mexico_City')::date
 WHERE status = 'COMPLETED';

-- Copia en el expediente para mostrar "Cerrada" en los listados sin cruzar módulos.
ALTER TABLE expediente ADD COLUMN closing_completed_on DATE;
UPDATE expediente e
   SET closing_completed_on = c.closed_on
  FROM closing_case c
 WHERE c.expediente_id = e.id
   AND c.status = 'COMPLETED';
CREATE INDEX idx_expediente_closing_completed ON expediente (closing_completed_on) WHERE closing_completed_on IS NOT NULL;

-- CC#34: precio de venta sugerido por el cliente (informativo, en MXN). No
-- sustituye al precio autorizado del contrato.
ALTER TABLE manual_client_data ADD COLUMN suggested_price NUMERIC(14, 2);

-- CC#36: fecha de alta de la propiedad para ordenar por antigüedad. Las de
-- 21Online toman su fechaAlta en la siguiente importación del inventario; las
-- capturadas en Genera, su fecha de captura.
ALTER TABLE crm_property ADD COLUMN listed_at TIMESTAMPTZ;
UPDATE crm_property SET listed_at = created_at WHERE origin = 'GENERA';
CREATE INDEX idx_crm_property_listed ON crm_property (COALESCE(listed_at, created_at));
