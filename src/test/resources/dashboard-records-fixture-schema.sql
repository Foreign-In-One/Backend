-- TEST ONLY. This is not a migration and must never be run against a deployed database.
-- PR #6 / #7 may not yet provide JPA entities in this branch. Supply their read columns
-- in the isolated H2 test DB. IF NOT EXISTS leaves real entity-created tables untouched.
CREATE TABLE IF NOT EXISTS tax_checks (
    tax_check_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    tax_year INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    analysis_summary TEXT,
    next_action TEXT,
    benefit_summary JSON,
    analyzed_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS exit_checks (
    exit_check_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    expected_exit_date DATE,
    readiness_score INT,
    status VARCHAR(30) NOT NULL,
    analysis_summary TEXT,
    next_action TEXT,
    analyzed_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
