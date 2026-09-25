-- ---------------------------------------------------------------------
-- Documents: a standard name given at upload (the name the user chose is
-- kept only for reference) and the result of the virus scan.
-- ---------------------------------------------------------------------
alter table stored_document add column standard_name varchar(200);
update stored_document set standard_name = original_name where standard_name is null;
alter table stored_document alter column standard_name set not null;

-- CLEAN: scanned, nothing found. NOT_SCANNED: scanning disabled (development only).
-- Infected files are refused and never stored, so there is no INFECTED state.
alter table stored_document add column scan_status varchar(20) default 'NOT_SCANNED' not null;
alter table stored_document add column scan_engine varchar(80);
alter table stored_document add column scanned_at timestamp with time zone;

create index idx_document_owner_category on stored_document (owner_user_id, category, uploaded_at);
