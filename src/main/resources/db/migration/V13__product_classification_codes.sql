ALTER TABLE electronic_document_item
    ADD COLUMN sunat_product_code varchar(8),
    ADD COLUMN gtin varchar(14),
    ADD COLUMN gtin_scheme_id varchar(14);

ALTER TABLE electronic_document_item
    ADD CONSTRAINT ck_document_item_sunat_product_code
        CHECK (sunat_product_code IS NULL OR sunat_product_code ~ '^[0-9]{8}$'),
    ADD CONSTRAINT ck_document_item_gtin
        CHECK (gtin IS NULL OR gtin ~ '^[0-9]{8,14}$'),
    ADD CONSTRAINT ck_document_item_gtin_scheme
        CHECK ((gtin IS NULL AND gtin_scheme_id IS NULL) OR (gtin IS NOT NULL AND gtin_scheme_id IS NOT NULL AND btrim(gtin_scheme_id) <> ''));

CREATE INDEX ix_document_item_sunat_product_code
    ON electronic_document_item (tenant_id, sunat_product_code)
    WHERE sunat_product_code IS NOT NULL;
