-- Petición del cliente 07/10: al dar de alta un cliente que ya tiene un expediente activo
-- el sistema lo avisa para no duplicarlo; abrir otro (p. ej. por una segunda o tercera
-- propiedad) requiere una autorización especial con justificación.
INSERT INTO permission (code, description) VALUES
    ('EXPEDIENT_DUPLICATE_AUTHORIZE',
     'Autorizar otro expediente para un cliente que ya tiene uno activo (p. ej. otra propiedad), con justificación');

INSERT INTO role_permission (role_id, permission_code) VALUES
    ('00000000-0000-0000-0000-000000000001', 'EXPEDIENT_DUPLICATE_AUTHORIZE'),
    ('00000000-0000-0000-0000-000000000004', 'EXPEDIENT_DUPLICATE_AUTHORIZE');
