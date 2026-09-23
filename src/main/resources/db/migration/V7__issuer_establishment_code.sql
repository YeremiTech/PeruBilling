ALTER TABLE issuer
    ADD COLUMN establishment_code varchar(4) NOT NULL DEFAULT '0000';

ALTER TABLE issuer
    ADD CONSTRAINT ck_issuer_establishment_code CHECK (establishment_code ~ '^[0-9]{4}$');
