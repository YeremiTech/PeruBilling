ALTER TABLE electronic_document
    ADD COLUMN ivap_taxable_amount numeric(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN ivap_amount numeric(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN free_tax_amount numeric(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN payment_method varchar(20),
    ADD COLUMN pending_amount numeric(18,2),
    ADD COLUMN summary_condition_code varchar(1) NOT NULL DEFAULT '1',
    ADD COLUMN processing_lease_until timestamptz;

CREATE TABLE payment_installment (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    document_id uuid NOT NULL REFERENCES electronic_document(id) ON DELETE CASCADE,
    installment_number integer NOT NULL,
    due_date date NOT NULL,
    amount numeric(18,2) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_payment_installment UNIQUE(document_id, installment_number),
    CONSTRAINT ck_payment_installment_number CHECK(installment_number > 0),
    CONSTRAINT ck_payment_installment_amount CHECK(amount > 0)
);
CREATE INDEX idx_payment_installment_document ON payment_installment(tenant_id, document_id, installment_number);

ALTER TABLE user_account
    ADD COLUMN failed_login_count integer NOT NULL DEFAULT 0,
    ADD COLUMN locked_until timestamptz;

ALTER TABLE webhook_delivery
    ADD COLUMN sending_started_at timestamptz;

CREATE INDEX idx_document_processing_lease ON electronic_document(status, processing_lease_until);
