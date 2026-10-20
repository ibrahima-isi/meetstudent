-- Approval status of Expert (teacher) accounts. NULL for every other role.
-- Experts registered before this migration keep the rights they already had: they become APPROVED.
ALTER TABLE users
    ADD COLUMN expert_status varchar(10),
    ADD COLUMN expert_status_reason varchar(255),
    ADD COLUMN expert_decision_date timestamp;

ALTER TABLE users
    ADD CONSTRAINT chk_users_expert_status CHECK (expert_status IN ('PENDING', 'APPROVED', 'REJECTED'));

UPDATE users
SET expert_status = 'APPROVED'
WHERE role_id IN (SELECT id FROM roles WHERE name = 'ROLE_EXPERT');
