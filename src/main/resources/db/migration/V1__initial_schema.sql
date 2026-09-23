CREATE TABLE tenant (
    id uuid PRIMARY KEY,
    name varchar(150) NOT NULL,
    slug varchar(100) NOT NULL UNIQUE,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE user_account (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    email varchar(255) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    role varchar(20) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    last_login_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX idx_user_tenant ON user_account(tenant_id);

CREATE TABLE api_key (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    name varchar(120) NOT NULL,
    prefix varchar(24) NOT NULL UNIQUE,
    secret_hash varchar(64) NOT NULL UNIQUE,
    scopes varchar(500) NOT NULL,
    last_used_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX idx_api_key_tenant ON api_key(tenant_id);

CREATE TABLE issuer (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    ruc varchar(11) NOT NULL,
    business_name varchar(250) NOT NULL,
    trade_name varchar(250),
    address varchar(250) NOT NULL,
    ubigeo varchar(6) NOT NULL,
    department varchar(100),
    province varchar(100),
    district varchar(100),
    sunat_environment varchar(20) NOT NULL,
    sol_user varchar(100),
    sol_password_encrypted varchar(1000),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_issuer_tenant_ruc UNIQUE(tenant_id, ruc)
);
CREATE INDEX idx_issuer_tenant ON issuer(tenant_id);

CREATE TABLE digital_certificate (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    issuer_id uuid NOT NULL REFERENCES issuer(id),
    certificate_alias varchar(200),
    encrypted_pfx bytea NOT NULL,
    password_encrypted varchar(1000) NOT NULL,
    fingerprint varchar(128) NOT NULL UNIQUE,
    subject_dn varchar(1000) NOT NULL,
    serial_number varchar(200) NOT NULL,
    valid_from timestamptz NOT NULL,
    valid_until timestamptz NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX idx_certificate_issuer_active ON digital_certificate(tenant_id, issuer_id, active, valid_until);

CREATE TABLE document_series (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    issuer_id uuid NOT NULL REFERENCES issuer(id),
    document_type varchar(30) NOT NULL,
    series varchar(4) NOT NULL,
    current_value bigint NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_document_series UNIQUE(tenant_id, issuer_id, document_type, series),
    CONSTRAINT ck_document_series_value CHECK(current_value >= 0)
);
CREATE INDEX idx_series_issuer ON document_series(tenant_id, issuer_id);

CREATE TABLE daily_summary (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    issuer_id uuid NOT NULL REFERENCES issuer(id),
    reference_date date NOT NULL,
    sequence_number bigint NOT NULL,
    identifier varchar(40) NOT NULL,
    status varchar(30) NOT NULL,
    ticket varchar(200),
    attempt_count integer NOT NULL DEFAULT 0,
    next_retry_at timestamptz,
    xml_path varchar(1000),
    signed_xml_path varchar(1000),
    cdr_path varchar(1000),
    response_code varchar(50),
    response_message varchar(2000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_daily_summary_sequence UNIQUE(tenant_id, issuer_id, reference_date, sequence_number),
    CONSTRAINT uq_daily_summary_identifier UNIQUE(tenant_id, issuer_id, identifier)
);
CREATE INDEX idx_daily_summary_queue ON daily_summary(status, next_retry_at, created_at);

CREATE TABLE electronic_document (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    issuer_id uuid NOT NULL REFERENCES issuer(id),
    document_type varchar(30) NOT NULL,
    series varchar(4) NOT NULL,
    correlativo bigint NOT NULL,
    full_number varchar(30) NOT NULL,
    issue_date date NOT NULL,
    operation_type varchar(4) NOT NULL DEFAULT '0101',
    currency varchar(3) NOT NULL DEFAULT 'PEN',
    customer_document_type varchar(2) NOT NULL,
    customer_document_number varchar(20) NOT NULL,
    customer_name varchar(300) NOT NULL,
    customer_address varchar(300),
    customer_email varchar(255),
    reference_document_type varchar(2),
    reference_document_number varchar(30),
    reason_code varchar(4),
    reason_text varchar(500),
    taxable_amount numeric(18,2) NOT NULL DEFAULT 0,
    exonerated_amount numeric(18,2) NOT NULL DEFAULT 0,
    unaffected_amount numeric(18,2) NOT NULL DEFAULT 0,
    export_amount numeric(18,2) NOT NULL DEFAULT 0,
    free_amount numeric(18,2) NOT NULL DEFAULT 0,
    igv_amount numeric(18,2) NOT NULL DEFAULT 0,
    icbper_amount numeric(18,2) NOT NULL DEFAULT 0,
    total_amount numeric(18,2) NOT NULL DEFAULT 0,
    status varchar(30) NOT NULL,
    processing_started_at timestamptz,
    attempt_count integer NOT NULL DEFAULT 0,
    next_retry_at timestamptz,
    last_error_code varchar(100),
    last_error_message varchar(2000),
    xml_path varchar(1000),
    signed_xml_path varchar(1000),
    pdf_path varchar(1000),
    cdr_path varchar(1000),
    xml_hash varchar(64),
    cdr_code varchar(20),
    cdr_description varchar(2000),
    accepted_at timestamptz,
    daily_summary_id uuid REFERENCES daily_summary(id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_document_number UNIQUE(tenant_id, issuer_id, document_type, series, correlativo),
    CONSTRAINT uq_document_full_number UNIQUE(tenant_id, issuer_id, document_type, full_number),
    CONSTRAINT ck_document_correlativo CHECK(correlativo > 0),
    CONSTRAINT ck_document_total CHECK(total_amount >= 0)
);
CREATE INDEX idx_document_tenant_created ON electronic_document(tenant_id, created_at DESC);
CREATE INDEX idx_document_queue ON electronic_document(status, next_retry_at, created_at);
CREATE INDEX idx_document_issuer_date ON electronic_document(tenant_id, issuer_id, issue_date, status);
CREATE INDEX idx_document_summary ON electronic_document(daily_summary_id);

CREATE TABLE electronic_document_item (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    document_id uuid NOT NULL REFERENCES electronic_document(id) ON DELETE CASCADE,
    line_number integer NOT NULL,
    sku varchar(100),
    description varchar(500) NOT NULL,
    unit_code varchar(5) NOT NULL,
    quantity numeric(18,6) NOT NULL,
    unit_value numeric(18,6) NOT NULL,
    unit_price numeric(18,6) NOT NULL,
    tax_affectation_code varchar(2) NOT NULL,
    igv_rate numeric(7,4) NOT NULL,
    line_base_amount numeric(18,2) NOT NULL,
    line_igv_amount numeric(18,2) NOT NULL,
    icbper_per_unit numeric(18,4) NOT NULL DEFAULT 0,
    line_icbper_amount numeric(18,2) NOT NULL DEFAULT 0,
    line_total_amount numeric(18,2) NOT NULL,
    free_operation boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_document_line UNIQUE(document_id, line_number),
    CONSTRAINT ck_item_quantity CHECK(quantity > 0)
);
CREATE INDEX idx_document_item_document ON electronic_document_item(document_id, line_number);

CREATE TABLE document_status_history (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    document_id uuid NOT NULL REFERENCES electronic_document(id) ON DELETE CASCADE,
    status varchar(30) NOT NULL,
    code varchar(100),
    message varchar(2000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX idx_status_history_document ON document_status_history(tenant_id, document_id, created_at);

CREATE TABLE submission_attempt (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    document_id uuid NOT NULL REFERENCES electronic_document(id) ON DELETE CASCADE,
    attempt_number integer NOT NULL,
    started_at timestamptz NOT NULL,
    finished_at timestamptz,
    success boolean NOT NULL DEFAULT false,
    http_status integer,
    response_code varchar(100),
    response_message varchar(2000),
    duration_ms bigint,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_submission_attempt UNIQUE(document_id, attempt_number)
);
CREATE INDEX idx_submission_attempt_document ON submission_attempt(tenant_id, document_id, attempt_number);

CREATE TABLE idempotency_record (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    idempotency_key varchar(150) NOT NULL,
    request_hash varchar(64) NOT NULL,
    resource_id uuid NOT NULL REFERENCES electronic_document(id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_idempotency_key UNIQUE(tenant_id, idempotency_key)
);
CREATE INDEX idx_idempotency_resource ON idempotency_record(resource_id);

CREATE TABLE webhook_endpoint (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    url varchar(500) NOT NULL,
    secret_encrypted varchar(1000) NOT NULL,
    event_types varchar(1000) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX idx_webhook_endpoint_tenant ON webhook_endpoint(tenant_id, active);

CREATE TABLE webhook_delivery (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant(id),
    endpoint_id uuid NOT NULL REFERENCES webhook_endpoint(id),
    event_id uuid NOT NULL UNIQUE,
    event_type varchar(100) NOT NULL,
    resource_id uuid NOT NULL,
    payload text NOT NULL,
    status varchar(20) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    last_http_status integer,
    last_error varchar(2000),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX idx_webhook_delivery_queue ON webhook_delivery(status, next_attempt_at, created_at);

CREATE TABLE audit_event (
    id uuid PRIMARY KEY,
    tenant_id uuid REFERENCES tenant(id),
    actor varchar(255),
    http_method varchar(10),
    request_path varchar(500) NOT NULL,
    response_status integer NOT NULL,
    request_id varchar(100),
    remote_address varchar(100),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX idx_audit_tenant_created ON audit_event(tenant_id, created_at DESC);
