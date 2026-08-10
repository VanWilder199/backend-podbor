ALTER TABLE users
ADD CONSTRAINT users_phone_or_email_check
CHECK (phone IS NOT NULL OR email IS NOT NULL);