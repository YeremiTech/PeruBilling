-- Corrige la semántica legacy de Phase 8: 50 fue usado como cargo por ítem.
UPDATE document_item_allowance_charge
SET reason_code = '48'
WHERE reason_code = '50' AND charge_indicator = true;

ALTER TABLE document_item_allowance_charge
    DROP CONSTRAINT ck_item_adjustment_reason,
    DROP CONSTRAINT ck_item_adjustment_reason_indicator;

ALTER TABLE document_item_allowance_charge
    ADD CONSTRAINT ck_item_adjustment_reason
        CHECK (reason_code IN ('00', '01', '47', '48')),
    ADD CONSTRAINT ck_item_adjustment_reason_indicator CHECK (
        (charge_indicator = false AND reason_code IN ('00', '01')) OR
        (charge_indicator = true AND reason_code IN ('47', '48'))
    );

CREATE TABLE document_allowance_charge (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    document_id uuid NOT NULL,
    sequence_number integer NOT NULL,
    charge_indicator boolean NOT NULL,
    reason_code varchar(2) NOT NULL,
    factor numeric(9,6) NOT NULL,
    amount numeric(18,2) NOT NULL,
    base_amount numeric(18,2) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_document_adjustment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_document_adjustment_document_tenant
        FOREIGN KEY (document_id, tenant_id)
        REFERENCES electronic_document(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_document_adjustment_sequence UNIQUE (document_id, sequence_number),
    CONSTRAINT ck_document_adjustment_sequence CHECK (sequence_number > 0),
    CONSTRAINT ck_document_adjustment_reason
        CHECK (reason_code IN ('02', '03', '04', '05', '06', '49', '50', '51', '52', '53')),
    CONSTRAINT ck_document_adjustment_reason_indicator CHECK (
        (charge_indicator = false AND reason_code IN ('02', '03', '04', '05', '06')) OR
        (charge_indicator = true AND reason_code IN ('49', '50', '51', '52', '53'))
    ),
    CONSTRAINT ck_document_adjustment_factor CHECK (factor > 0 AND factor <= 1),
    CONSTRAINT ck_document_adjustment_amount CHECK (amount > 0 AND amount <= base_amount),
    CONSTRAINT ck_document_adjustment_base CHECK (base_amount > 0)
);

CREATE INDEX ix_document_adjustment_document
    ON document_allowance_charge (tenant_id, document_id, sequence_number);
