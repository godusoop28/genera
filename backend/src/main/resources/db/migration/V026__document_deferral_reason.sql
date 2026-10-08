-- Petición del cliente 08/10: cuando el asesor deja un documento "para después" (una
-- excepción a la primera entrega) debe escribir la razón, para entender por qué no se pidió.
ALTER TABLE document ADD COLUMN deferral_reason TEXT;
