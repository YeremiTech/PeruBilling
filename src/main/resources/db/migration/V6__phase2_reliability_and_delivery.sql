ALTER TABLE electronic_document
    ADD COLUMN delivery_status varchar(30) NOT NULL DEFAULT 'NOT_DELIVERED',
    ADD COLUMN granted_at timestamptz,
    ADD COLUMN granted_channel varchar(30),
    ADD COLUMN submission_unknown_since timestamptz,
    ADD COLUMN reconciliation_count integer NOT NULL DEFAULT 0;

CREATE INDEX idx_document_submission_unknown
    ON electronic_document(status, next_retry_at, processing_lease_until)
    WHERE status='SUBMISSION_UNKNOWN';

CREATE TABLE document_access_token (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    document_id uuid NOT NULL REFERENCES electronic_document(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    last_access_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_access_token_document_tenant
        FOREIGN KEY (document_id, tenant_id) REFERENCES electronic_document(id, tenant_id)
);
CREATE INDEX idx_access_token_document ON document_access_token(tenant_id, document_id, expires_at);

CREATE TABLE outbox_event (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    event_type varchar(100) NOT NULL,
    aggregate_type varchar(100) NOT NULL,
    aggregate_id uuid NOT NULL,
    payload text NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    processing_started_at timestamptz,
    published_at timestamptz,
    last_error varchar(2000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX idx_outbox_queue ON outbox_event(status, next_attempt_at, created_at);

ALTER TABLE webhook_delivery ADD COLUMN source_event_id uuid REFERENCES outbox_event(id);
CREATE UNIQUE INDEX uq_webhook_delivery_source_endpoint
    ON webhook_delivery(source_event_id, endpoint_id)
    WHERE source_event_id IS NOT NULL;
