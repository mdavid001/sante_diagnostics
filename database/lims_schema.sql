DROP TABLE IF EXISTS result_files        CASCADE;
DROP TABLE IF EXISTS results             CASCADE;
DROP TABLE IF EXISTS samples             CASCADE;
DROP TABLE IF EXISTS test_requests       CASCADE;
DROP TABLE IF EXISTS verification_tokens CASCADE;
DROP TABLE IF EXISTS audit_log           CASCADE;
DROP TABLE IF EXISTS tests               CASCADE;
DROP TABLE IF EXISTS lab_settings        CASCADE;
DROP TABLE IF EXISTS users               CASCADE;

DROP TYPE IF EXISTS user_role        CASCADE;
DROP TYPE IF EXISTS result_format    CASCADE;
DROP TYPE IF EXISTS payment_status   CASCADE;
DROP TYPE IF EXISTS request_status   CASCADE;
DROP TYPE IF EXISTS sample_status    CASCADE;
DROP TYPE IF EXISTS result_status    CASCADE;
DROP TYPE IF EXISTS result_file_type CASCADE;
DROP TYPE IF EXISTS token_type       CASCADE;

CREATE TYPE user_role        AS ENUM ('super_admin', 'lab_attendant', 'customer');
CREATE TYPE result_format     AS ENUM ('numeric', 'text', 'pdf', 'image');
CREATE TYPE payment_status    AS ENUM ('unpaid', 'paid');
CREATE TYPE request_status    AS ENUM ('submitted', 'in_progress', 'completed', 'cancelled');
CREATE TYPE sample_status     AS ENUM ('awaiting_collection', 'collected', 'processing', 'processed');
CREATE TYPE result_status     AS ENUM ('pending', 'verified', 'rejected');
CREATE TYPE result_file_type  AS ENUM ('pdf', 'image');
CREATE TYPE token_type        AS ENUM ('email_verification', 'password_reset');

CREATE TABLE users (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name            VARCHAR(80)  NOT NULL,
    last_name             VARCHAR(80)  NOT NULL,
    email                 VARCHAR(255) NOT NULL UNIQUE,
    password              VARCHAR(60)  NOT NULL,
    role                  user_role    NOT NULL,
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by            BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tests (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name              VARCHAR(150)  NOT NULL,
    description       TEXT,
    price             NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    turnaround_hours  INT           NOT NULL CHECK (turnaround_hours > 0),
    result_format     result_format NOT NULL,
    is_active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by        BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE test_requests (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id        BIGINT         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    test_id            BIGINT         NOT NULL REFERENCES tests(id) ON DELETE RESTRICT,
    price_at_order     NUMERIC(12,2)  NOT NULL CHECK (price_at_order >= 0),
    request_status     request_status NOT NULL DEFAULT 'submitted',
    payment_status     payment_status NOT NULL DEFAULT 'unpaid',
    paid_by            BIGINT         REFERENCES users(id) ON DELETE SET NULL,
    paid_at            TIMESTAMPTZ,
    expected_ready_at  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE samples (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    test_request_id        BIGINT        NOT NULL UNIQUE REFERENCES test_requests(id) ON DELETE CASCADE,
    status                 sample_status NOT NULL DEFAULT 'awaiting_collection',
    collected_by           BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    collected_at           TIMESTAMPTZ,
    processing_started_at  TIMESTAMPTZ,
    processed_at           TIMESTAMPTZ,
    notes                  TEXT,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE results (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    test_request_id  BIGINT        NOT NULL UNIQUE REFERENCES test_requests(id) ON DELETE CASCADE,
    value_numeric    NUMERIC(14,4),
    value_text       TEXT,
    status           result_status NOT NULL DEFAULT 'pending',
    uploaded_by      BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    uploaded_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    verified_by      BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    verified_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE result_files (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    result_id          BIGINT           NOT NULL REFERENCES results(id) ON DELETE CASCADE,
    file_type          result_file_type NOT NULL,
    file_path          TEXT             NOT NULL,
    original_filename  VARCHAR(255),
    uploaded_at        TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(50),
    entity_id      BIGINT,
    details        JSONB,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE verification_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    token_type  token_type   NOT NULL DEFAULT 'email_verification',
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE lab_settings (
    id                   INT          PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    lab_name             VARCHAR(150) NOT NULL,
    bank_name            VARCHAR(150) NOT NULL,
    bank_account_name    VARCHAR(150) NOT NULL,
    bank_account_number  VARCHAR(30)  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at         BEFORE UPDATE ON users         FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tests_updated_at         BEFORE UPDATE ON tests         FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_requests_updated_at      BEFORE UPDATE ON test_requests FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_samples_updated_at       BEFORE UPDATE ON samples       FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_results_updated_at       BEFORE UPDATE ON results       FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE OR REPLACE FUNCTION prevent_audit_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is immutable: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_modification();

CREATE INDEX idx_users_role            ON users(role);
CREATE INDEX idx_requests_customer     ON test_requests(customer_id);
CREATE INDEX idx_requests_payment      ON test_requests(payment_status);
CREATE INDEX idx_requests_status       ON test_requests(request_status);
CREATE INDEX idx_results_status        ON results(status);
CREATE INDEX idx_result_files_result   ON result_files(result_id);
CREATE INDEX idx_audit_actor           ON audit_log(actor_user_id);
CREATE INDEX idx_audit_created_at      ON audit_log(created_at);
CREATE INDEX idx_tokens_user           ON verification_tokens(user_id);

INSERT INTO lab_settings (id, lab_name, bank_name, bank_account_name, bank_account_number)
VALUES (1, 'Sante Diagnostics Ltd', 'First Bank of Nigeria', 'Sante Diagnostics Ltd', '0123456789');

-- Bootstrap Super Admin account.
-- Email:    admin@sante.test
-- Password: admin123
-- must_change_password = TRUE so the first login forces a fresh password.
INSERT INTO users (first_name, last_name, email, password, role, email_verified, must_change_password)
VALUES (
    'System',
    'Administrator',
    'admin@sante.test',
    '$2a$12$m5AbwNRv11uBlibo1zh8ue7JFrBYrrMncmC8EDLN2XvLTVI8qQhpy',
    'super_admin',
    TRUE,
    TRUE
);

