-- Control de Cambios #24 (17/10): asesor asignado por expediente y tipo de operación
-- (venta/renta) para filtrar reportes y dashboards por tipo de propiedad, operación y asesor.

ALTER TABLE expediente ADD COLUMN assigned_advisor_id UUID;
ALTER TABLE expediente ADD COLUMN assigned_advisor_name VARCHAR(160);
ALTER TABLE expediente ADD COLUMN operation_type VARCHAR(16) NOT NULL DEFAULT 'SALE';

-- Los expedientes que ya existen quedan asignados a quien los dio de alta: así el asesor
-- conserva los que ya veía; el administrador o directivo puede reasignarlos.
UPDATE expediente e
   SET assigned_advisor_id = e.created_by_user_id,
       assigned_advisor_name = u.name
  FROM app_user u
 WHERE u.id = e.created_by_user_id;

CREATE INDEX idx_expediente_assigned_advisor ON expediente (assigned_advisor_id);
CREATE INDEX idx_expediente_type_operation ON expediente (property_case_type, operation_type);

INSERT INTO permission (code, description) VALUES
    ('EXPEDIENT_ADVISOR_ASSIGN', 'Cambiar el asesor asignado a un expediente (queda en el historial)');

INSERT INTO role_permission (role_id, permission_code)
SELECT r.id, 'EXPEDIENT_ADVISOR_ASSIGN' FROM app_role r WHERE r.code IN ('ADMINISTRATOR', 'DIRECTOR');
