ALTER TABLE digital_certificate
    DROP CONSTRAINT IF EXISTS digital_certificate_fingerprint_key;

ALTER TABLE digital_certificate
    ADD CONSTRAINT uq_certificate_issuer_fingerprint
    UNIQUE (tenant_id, issuer_id, fingerprint);
