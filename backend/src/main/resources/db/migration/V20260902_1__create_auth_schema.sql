CREATE TABLE app_user (
    user_id UUID PRIMARY KEY,
    display_name VARCHAR(50) NOT NULL,
    language VARCHAR(2) NOT NULL,
    age_verified_at TIMESTAMPTZ,
    age_gate_policy_version VARCHAR(50),
    personalization_mode VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE user_consent (
    consent_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    consent_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    policy_version VARCHAR(50) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_user_consent_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT uk_user_consent_policy UNIQUE (user_id, consent_type, policy_version)
);

CREATE TABLE local_credential (
    local_credential_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_local_credential_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT uk_local_credential_user UNIQUE (user_id),
    CONSTRAINT uk_local_credential_email UNIQUE (email)
);

CREATE TABLE auth_identity (
    identity_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    provider_email VARCHAR(254),
    linked_at TIMESTAMPTZ NOT NULL,
    unlinked_at TIMESTAMPTZ,
    CONSTRAINT fk_auth_identity_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT uk_auth_identity_provider_subject UNIQUE (provider, provider_subject)
);

CREATE TABLE auth_session (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_family_id UUID NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    device_id VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE,
    CONSTRAINT uk_auth_session_refresh_hash UNIQUE (refresh_token_hash)
);

CREATE INDEX idx_auth_session_user_expires ON auth_session (user_id, expires_at);
CREATE INDEX idx_auth_session_family ON auth_session (token_family_id);

CREATE TABLE auth_refresh_token (
    refresh_token_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_auth_refresh_token_session FOREIGN KEY (session_id) REFERENCES auth_session (session_id) ON DELETE CASCADE,
    CONSTRAINT uk_auth_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_auth_refresh_token_session ON auth_refresh_token (session_id);

CREATE TABLE auth_one_time_token (
    auth_token_id UUID PRIMARY KEY,
    local_credential_id UUID NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_auth_one_time_token_credential FOREIGN KEY (local_credential_id)
        REFERENCES local_credential (local_credential_id) ON DELETE CASCADE,
    CONSTRAINT uk_auth_one_time_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_auth_one_time_token_credential_purpose
    ON auth_one_time_token (local_credential_id, purpose, created_at DESC);

CREATE TABLE oauth_challenge (
    challenge_id UUID PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    state_hash VARCHAR(64) NOT NULL,
    nonce_hash VARCHAR(64) NOT NULL,
    redirect_uri VARCHAR(500) NOT NULL,
    device_id VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_oauth_challenge_state UNIQUE (state_hash)
);

CREATE INDEX idx_oauth_challenge_expires ON oauth_challenge (expires_at);
