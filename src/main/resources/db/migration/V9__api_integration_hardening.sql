ALTER TABLE electronic_document
    ADD COLUMN external_id varchar(100);

CREATE UNIQUE INDEX uq_document_external_id
    ON electronic_document(tenant_id, issuer_id, external_id)
    WHERE external_id IS NOT NULL;

CREATE INDEX idx_document_customer_number
    ON electronic_document(tenant_id, customer_document_number, issue_date DESC);

CREATE INDEX idx_document_type_date
    ON electronic_document(tenant_id, document_type, issue_date DESC);

ALTER TABLE api_key
    ADD COLUMN expires_at timestamptz;

CREATE INDEX idx_api_key_active_expiration
    ON api_key(tenant_id, expires_at)
    WHERE revoked_at IS NULL;
