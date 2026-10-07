-- Revisión cliente 07/10: el alta ya no pide domicilio (se lee de los documentos), y el
-- cliente veía el campo Inmueble vacío al confirmar su liga. El asesor captura una
-- referencia corta (p. ej. "Casa en Coto Austriaco, Zapopan") para que el cliente
-- reconozca su expediente antes de entregar datos; no sustituye al domicilio legal.
ALTER TABLE expediente ADD COLUMN property_reference VARCHAR(120);
