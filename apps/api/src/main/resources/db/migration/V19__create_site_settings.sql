-- Site settings edited from the back office: one row, enforced by the check.
-- The starting values are written here so a fresh database already has them;
-- SiteSettingsService holds the same defaults for a database without the row.
CREATE TABLE site_settings (
    id            INTEGER PRIMARY KEY CHECK (id = 1),
    contact_email VARCHAR(255) NOT NULL,
    about_fr      VARCHAR(5000) NOT NULL,
    about_en      VARCHAR(5000) NOT NULL,
    modified_at   TIMESTAMP
);

INSERT INTO site_settings (id, contact_email, about_fr, about_en) VALUES (
    1,
    'contact@meetstudent.com',
    E'MeetStudent aide les futurs étudiants à choisir leur établissement au Sénégal.\n\nNous réunissons au même endroit les écoles, instituts et universités, leurs formations et les avis de celles et ceux qui y étudient, pour que chacun puisse comparer avant de décider.\n\nNotre conviction est simple : une orientation réussie commence par une information claire, accessible à tous et partagée par la communauté étudiante.',
    E'MeetStudent helps future students choose where to study in Senegal.\n\nWe bring schools, institutes and universities together in one place, along with their programs and the reviews of the people who study there, so that everyone can compare before deciding.\n\nOur belief is simple: good guidance starts with clear information, open to all and shared by the student community.'
);
