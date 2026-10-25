CREATE TABLE IF NOT EXISTS audit_logs (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 action VARCHAR(100) NOT NULL,
 actor VARCHAR(100) NOT NULL,
 resource VARCHAR(100) NOT NULL,
 details LONGTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at);
CREATE TABLE IF NOT EXISTS integration_configurations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 provider VARCHAR(60) NOT NULL UNIQUE,
 endpoint VARCHAR(1000) NOT NULL,
 encrypted_secret LONGTEXT NOT NULL,
 model VARCHAR(200),
 updated_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE IF NOT EXISTS release_records (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 iteration_id BIGINT NOT NULL,
 version VARCHAR(100) NOT NULL,
 reminder_status VARCHAR(40) NOT NULL,
 confirmation_status VARCHAR(40) NOT NULL,
 release_status VARCHAR(40) NOT NULL,
 writeback_status VARCHAR(40) NOT NULL,
 notify_status VARCHAR(40) NOT NULL,
 notes VARCHAR(10000),
 confirmed_by VARCHAR(100),
 last_error VARCHAR(1000),
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_release_iteration FOREIGN KEY(iteration_id) REFERENCES iterations(id),
 CONSTRAINT uk_release_iteration_version UNIQUE(iteration_id, version)
);
CREATE INDEX idx_release_iteration ON release_records(iteration_id);
