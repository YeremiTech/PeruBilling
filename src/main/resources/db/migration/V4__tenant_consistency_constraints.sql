ALTER TABLE issuer ADD CONSTRAINT uq_issuer_id_tenant UNIQUE(id, tenant_id);
ALTER TABLE daily_summary ADD CONSTRAINT uq_daily_summary_id_tenant UNIQUE(id, tenant_id);
ALTER TABLE electronic_document ADD CONSTRAINT uq_document_id_tenant UNIQUE(id, tenant_id);
ALTER TABLE webhook_endpoint ADD CONSTRAINT uq_webhook_endpoint_id_tenant UNIQUE(id, tenant_id);

ALTER TABLE digital_certificate
    ADD CONSTRAINT fk_certificate_issuer_tenant
    FOREIGN KEY (issuer_id, tenant_id) REFERENCES issuer(id, tenant_id);

ALTER TABLE document_series
    ADD CONSTRAINT fk_series_issuer_tenant
    FOREIGN KEY (issuer_id, tenant_id) REFERENCES issuer(id, tenant_id);

ALTER TABLE daily_summary
    ADD CONSTRAINT fk_summary_issuer_tenant
    FOREIGN KEY (issuer_id, tenant_id) REFERENCES issuer(id, tenant_id);

ALTER TABLE electronic_document
    ADD CONSTRAINT fk_document_issuer_tenant
    FOREIGN KEY (issuer_id, tenant_id) REFERENCES issuer(id, tenant_id);

ALTER TABLE electronic_document
    ADD CONSTRAINT fk_document_summary_tenant
    FOREIGN KEY (daily_summary_id, tenant_id) REFERENCES daily_summary(id, tenant_id);

ALTER TABLE electronic_document_item
    ADD CONSTRAINT fk_item_document_tenant
    FOREIGN KEY (document_id, tenant_id) REFERENCES electronic_document(id, tenant_id);

ALTER TABLE payment_installment
    ADD CONSTRAINT fk_installment_document_tenant
    FOREIGN KEY (document_id, tenant_id) REFERENCES electronic_document(id, tenant_id);

ALTER TABLE document_status_history
    ADD CONSTRAINT fk_history_document_tenant
    FOREIGN KEY (document_id, tenant_id) REFERENCES electronic_document(id, tenant_id);

ALTER TABLE submission_attempt
    ADD CONSTRAINT fk_attempt_document_tenant
    FOREIGN KEY (document_id, tenant_id) REFERENCES electronic_document(id, tenant_id);

ALTER TABLE idempotency_record
    ADD CONSTRAINT fk_idempotency_document_tenant
    FOREIGN KEY (resource_id, tenant_id) REFERENCES electronic_document(id, tenant_id);

ALTER TABLE webhook_delivery
    ADD CONSTRAINT fk_delivery_endpoint_tenant
    FOREIGN KEY (endpoint_id, tenant_id) REFERENCES webhook_endpoint(id, tenant_id);

ALTER TABLE voiding_batch
    ADD CONSTRAINT fk_voiding_issuer_tenant
    FOREIGN KEY (issuer_id, tenant_id) REFERENCES issuer(id, tenant_id);

ALTER TABLE voiding_batch
    ADD CONSTRAINT fk_voiding_document_tenant
    FOREIGN KEY (document_id, tenant_id) REFERENCES electronic_document(id, tenant_id);
