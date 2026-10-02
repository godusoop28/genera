-- E2E 02/10: la sección de la bitácora de cambios es "Participante: <nombre completo>";
-- con 48 caracteres no cabía un nombre de más de 34 (p. ej. "INMUEBLES DEMO DEL SUR, S.A.
-- DE C.V.") y editar a ese participante respondía error 500.
ALTER TABLE expediente_change ALTER COLUMN section TYPE VARCHAR(200);
