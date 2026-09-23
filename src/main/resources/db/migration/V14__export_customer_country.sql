ALTER TABLE electronic_document
    ADD COLUMN IF NOT EXISTS customer_country_code varchar(2);

ALTER TABLE electronic_document
    ADD CONSTRAINT ck_electronic_document_customer_country_code
    CHECK (customer_country_code IS NULL OR customer_country_code ~ '^[A-Z]{2}$');

CREATE INDEX IF NOT EXISTS ix_electronic_document_operation_type
    ON electronic_document (tenant_id, operation_type, issue_date DESC);
