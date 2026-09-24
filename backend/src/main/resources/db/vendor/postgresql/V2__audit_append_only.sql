-- =====================================================================
-- PostgreSQL only: make the audit trail append only at database level.
-- Even an administrator with the application's credentials cannot edit
-- or delete audit rows; the hash chain makes any other tampering visible.
-- =====================================================================

create or replace function mrms_audit_is_append_only() returns trigger as $$
begin
    raise exception 'audit_entry is append only';
end;
$$ language plpgsql;

create trigger audit_entry_no_update
    before update or delete on audit_entry
    for each row execute function mrms_audit_is_append_only();

create trigger audit_entry_no_truncate
    before truncate on audit_entry
    for each statement execute function mrms_audit_is_append_only();
