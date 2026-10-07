CREATE TABLE IF NOT EXISTS visitors (
 id VARCHAR(36) PRIMARY KEY, token_hash VARCHAR(64) NOT NULL UNIQUE,
 created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS languages (
 code VARCHAR(60) PRIMARY KEY, name VARCHAR(120) NOT NULL, native_name VARCHAR(120) NOT NULL,
 green_label VARCHAR(120) NOT NULL, red_label VARCHAR(120) NOT NULL,
 starter_template VARCHAR(400) NOT NULL, text_direction VARCHAR(3) NOT NULL,
 notes VARCHAR(1000) NOT NULL, owner_id VARCHAR(36), revision INTEGER DEFAULT 1 NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES visitors(id)
);
CREATE TABLE IF NOT EXISTS submissions (
 id VARCHAR(36) PRIMARY KEY, visitor_id VARCHAR(36) NOT NULL, request_key VARCHAR(36) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL, examples_viewed BOOLEAN NOT NULL, others_viewed BOOLEAN NOT NULL,
 consent_version VARCHAR(20) NOT NULL, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
 UNIQUE(visitor_id,request_key), FOREIGN KEY(visitor_id) REFERENCES visitors(id)
);
CREATE TABLE IF NOT EXISTS expressions (
 id VARCHAR(36) PRIMARY KEY, submission_id VARCHAR(36) NOT NULL, scene_id VARCHAR(80) NOT NULL,
 scene_version VARCHAR(20) NOT NULL, language_code VARCHAR(60) NOT NULL,
 annotated_text VARCHAR(2000) NOT NULL, plain_text VARCHAR(2000) NOT NULL,
 translation VARCHAR(1000) NOT NULL, gloss VARCHAR(1000) NOT NULL, dialect VARCHAR(120) NOT NULL,
 reading_type VARCHAR(20) NOT NULL, proficiency VARCHAR(20) NOT NULL, implicit_roles VARCHAR(4) NOT NULL,
 is_example BOOLEAN NOT NULL DEFAULT FALSE, visibility VARCHAR(10) NOT NULL DEFAULT 'VISIBLE',
 created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
 FOREIGN KEY(submission_id) REFERENCES submissions(id), FOREIGN KEY(language_code) REFERENCES languages(code)
);
CREATE INDEX IF NOT EXISTS idx_expression_lookup ON expressions(scene_id,language_code,visibility,is_example);
CREATE TABLE IF NOT EXISTS ratings (
 expression_id VARCHAR(36) NOT NULL, visitor_id VARCHAR(36) NOT NULL, weight INTEGER NOT NULL CHECK(weight BETWEEN 0 AND 100),
 updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
 PRIMARY KEY(expression_id,visitor_id), FOREIGN KEY(expression_id) REFERENCES expressions(id), FOREIGN KEY(visitor_id) REFERENCES visitors(id)
);
CREATE TABLE IF NOT EXISTS reports (
 expression_id VARCHAR(36) NOT NULL, visitor_id VARCHAR(36) NOT NULL, reason VARCHAR(1000) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
 PRIMARY KEY(expression_id,visitor_id), FOREIGN KEY(expression_id) REFERENCES expressions(id), FOREIGN KEY(visitor_id) REFERENCES visitors(id)
);
