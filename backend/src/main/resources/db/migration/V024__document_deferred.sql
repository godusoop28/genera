-- Petición del cliente 07/10: al preparar la liga el asesor elige qué documentos pide en
-- la primera entrega y cuáles puede subir el cliente después, cuando los tenga. Siguen
-- siendo obligatorios para aprobar la documentación y generar el contrato.
ALTER TABLE document ADD COLUMN deferred BOOLEAN NOT NULL DEFAULT FALSE;
