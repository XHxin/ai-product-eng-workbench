CREATE TABLE requirements (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, revision BIGINT NOT NULL DEFAULT 0,
 title VARCHAR(200) NOT NULL, raw_text TEXT NOT NULL,
 source_type VARCHAR(40) NOT NULL, source_ref VARCHAR(1000) NOT NULL,
 idempotency_key VARCHAR(200) NOT NULL UNIQUE, normalized_text TEXT,
 requirement_type VARCHAR(255), priority VARCHAR(255), status VARCHAR(255),
 iteration_id BIGINT, created_at TIMESTAMP(6)
);
CREATE TABLE iterations (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, revision BIGINT NOT NULL DEFAULT 0,
 name VARCHAR(255), start_date VARCHAR(255), end_date VARCHAR(255), status VARCHAR(255)
);
CREATE TABLE workflow_records (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, revision BIGINT NOT NULL DEFAULT 0,
 category VARCHAR(40) NOT NULL, requirement_id BIGINT, iteration_id BIGINT, parent_id BIGINT,
 version_number INTEGER NOT NULL DEFAULT 0, status VARCHAR(60) NOT NULL,
 payload LONGTEXT NOT NULL, actor VARCHAR(200), created_at TIMESTAMP(6),
 FOREIGN KEY(requirement_id) REFERENCES requirements(id),
 FOREIGN KEY(iteration_id) REFERENCES iterations(id)
);
CREATE INDEX idx_workflow_category ON workflow_records(category,id);
CREATE INDEX idx_workflow_requirement ON workflow_records(requirement_id,category);
CREATE INDEX idx_requirements_iteration ON requirements(iteration_id);
