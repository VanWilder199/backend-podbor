ALTER TABLE reports ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE TABLE moderation_log (
        id BIGSERIAL PRIMARY KEY,
        report_id UUID NOT NULL REFERENCES reports(id),
        admin_id UUID NOT NULL REFERENCES admins(id),
        action TEXT NOT NULL,
        reason TEXT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

