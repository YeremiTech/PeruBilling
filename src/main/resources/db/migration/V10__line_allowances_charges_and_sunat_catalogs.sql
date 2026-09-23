ALTER TABLE electronic_document
    ADD COLUMN allowance_total_amount numeric(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN charge_total_amount numeric(18,2) NOT NULL DEFAULT 0;

ALTER TABLE electronic_document
    ADD CONSTRAINT ck_document_allowance_total_nonnegative CHECK (allowance_total_amount >= 0),
    ADD CONSTRAINT ck_document_charge_total_nonnegative CHECK (charge_total_amount >= 0);

ALTER TABLE electronic_document_item
    ADD COLUMN line_gross_amount numeric(18,2),
    ADD COLUMN line_allowance_amount numeric(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN line_charge_amount numeric(18,2) NOT NULL DEFAULT 0;

UPDATE electronic_document_item
SET line_gross_amount = line_base_amount
WHERE line_gross_amount IS NULL;

ALTER TABLE electronic_document_item
    ALTER COLUMN line_gross_amount SET NOT NULL,
    ALTER COLUMN line_gross_amount SET DEFAULT 0,
    ADD CONSTRAINT ck_item_line_gross_nonnegative CHECK (line_gross_amount >= 0),
    ADD CONSTRAINT ck_item_line_allowance_nonnegative CHECK (line_allowance_amount >= 0),
    ADD CONSTRAINT ck_item_line_allowance_not_above_gross CHECK (line_allowance_amount <= line_gross_amount),
    ADD CONSTRAINT ck_item_line_charge_nonnegative CHECK (line_charge_amount >= 0),
    ADD CONSTRAINT uq_document_item_id_tenant UNIQUE (id, tenant_id),
    ADD CONSTRAINT uq_document_item_document_tenant_line UNIQUE (id, document_id, tenant_id, line_number);

CREATE TABLE document_item_allowance_charge (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    document_id uuid NOT NULL,
    document_item_id uuid NOT NULL,
    line_number integer NOT NULL,
    sequence_number integer NOT NULL,
    charge_indicator boolean NOT NULL,
    reason_code varchar(2) NOT NULL,
    factor numeric(9,6) NOT NULL,
    amount numeric(18,2) NOT NULL,
    base_amount numeric(18,2) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_item_adjustment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_item_adjustment_document_tenant
        FOREIGN KEY (document_id, tenant_id)
        REFERENCES electronic_document(id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_item_adjustment_item_document_tenant_line
        FOREIGN KEY (document_item_id, document_id, tenant_id, line_number)
        REFERENCES electronic_document_item(id, document_id, tenant_id, line_number) ON DELETE CASCADE,
    CONSTRAINT uq_item_adjustment_sequence UNIQUE (document_item_id, sequence_number),
    CONSTRAINT ck_item_adjustment_line_number CHECK (line_number > 0),
    CONSTRAINT ck_item_adjustment_sequence CHECK (sequence_number > 0),
    CONSTRAINT ck_item_adjustment_reason CHECK (reason_code IN ('00', '01', '02', '50', '51', '52', '53')),
    CONSTRAINT ck_item_adjustment_reason_indicator CHECK (
        (charge_indicator = false AND reason_code IN ('00', '01', '02')) OR
        (charge_indicator = true AND reason_code IN ('50', '51', '52', '53'))
    ),
    CONSTRAINT ck_item_adjustment_factor CHECK (factor > 0 AND factor <= 1),
    CONSTRAINT ck_item_adjustment_amount CHECK (amount > 0 AND amount <= base_amount),
    CONSTRAINT ck_item_adjustment_base CHECK (base_amount > 0)
);

CREATE INDEX ix_item_adjustment_document
    ON document_item_allowance_charge (tenant_id, document_id, line_number, sequence_number);

CREATE INDEX ix_item_adjustment_item
    ON document_item_allowance_charge (tenant_id, document_item_id);
