CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone TEXT UNIQUE,
    email CITEXT UNIQUE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
    );

CREATE TABLE otp_codes (
    id BIGSERIAL PRIMARY KEY,
    channel TEXT NOT NULL,
    destination TEXT NOT NULL,
    code_hash TEXT NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()

);

CREATE TABLE otp_rate_limits (
    id BIGSERIAL PRIMARY KEY,
    channel TEXT NOT NULL,
    destination TEXT NOT NULL UNIQUE,
    send_count INT NOT NULL DEFAULT 0,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    blocked_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX refresh_tokens_hash_idx
ON refresh_tokens (token_hash)
WHERE revoked_at IS NULL;

CREATE TABLE admins (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
   email CITEXT NOT NULL UNIQUE,
   password_hash TEXT NOT NULL,
   totp_secret TEXT,
   created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);