CREATE TABLE artifact_blob (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    path varchar(1200) NOT NULL UNIQUE,
    content bytea NOT NULL,
    content_length bigint NOT NULL,
    sha256 varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT fk_artifact_blob_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT ck_artifact_blob_length CHECK (content_length >= 0),
    CONSTRAINT ck_artifact_blob_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_artifact_blob_tenant_owner
    ON artifact_blob (tenant_id, owner_id);
