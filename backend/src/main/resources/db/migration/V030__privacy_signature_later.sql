-- Control de Cambios #2 (15/10): el aviso de privacidad ya no bloquea el alta. Se firma en la
-- liga (digital) o en papel en la oficina (física: el escaneo se guarda como documento del
-- expediente). Los consentimientos anteriores son todos digitales.
ALTER TABLE privacy_consent ADD COLUMN signature_method VARCHAR(16) NOT NULL DEFAULT 'DIGITAL';
ALTER TABLE privacy_consent ADD COLUMN recorded_by_user_id UUID;
ALTER TABLE privacy_consent ADD COLUMN signed_document_id UUID;
