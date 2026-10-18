-- When the current PENDING delivery state began. A row that stays PENDING for good (restart, dropped task) becomes
-- resendable once it is older than app.contact.mail.pending-timeout. NULL falls back to created_at.
ALTER TABLE contact_messages
    ADD COLUMN mail_pending_since timestamp with time zone;
