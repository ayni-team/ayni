CREATE TABLE audit_logs (
                            id UUID PRIMARY KEY,
                            tenant_id VARCHAR(50) NOT NULL,
                            user_id VARCHAR(255),
                            action VARCHAR(100) NOT NULL,
                            entity_type VARCHAR(100) NOT NULL,
                            entity_id VARCHAR(255) NOT NULL,
                            details TEXT,
                            created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_logs_tenant_created ON audit_logs (tenant_id, created_at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);