-- Optional social links shown as buttons on the public Contact page.
-- Nullable with no default: NULL means "not set", and the button is hidden.
ALTER TABLE site_settings
    ADD COLUMN whatsapp_url  VARCHAR(255),
    ADD COLUMN telegram_url  VARCHAR(255),
    ADD COLUMN discord_url   VARCHAR(255),
    ADD COLUMN facebook_url  VARCHAR(255),
    ADD COLUMN twitter_url   VARCHAR(255),
    ADD COLUMN instagram_url VARCHAR(255),
    ADD COLUMN tiktok_url    VARCHAR(255);
