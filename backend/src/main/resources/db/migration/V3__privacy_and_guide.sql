-- ---------------------------------------------------------------------
-- Onboarding: which privacy notice version the user acknowledged, and
-- when they finished the first login guide.
-- ---------------------------------------------------------------------
alter table user_account add column privacy_notice_version varchar(20);
alter table user_account add column privacy_accepted_at timestamp with time zone;
alter table user_account add column guide_seen_at timestamp with time zone;
