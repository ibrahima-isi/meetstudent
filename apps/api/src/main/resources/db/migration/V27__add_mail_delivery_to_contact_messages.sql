-- Delivery state of the team mail of a contact message (see ContactEmailListener).
-- Rows that already exist were mailed (or not) before tracking existed: their status is UNKNOWN.
-- New rows are inserted by the application with PENDING and updated once the delivery run has finished.
ALTER TABLE contact_messages
    ADD COLUMN mail_status varchar(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN mail_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN mail_sent_at timestamp with time zone;
