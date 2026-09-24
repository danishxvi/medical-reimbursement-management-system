-- H2 (local development only): no trigger language is available, so the
-- append only rule is enforced by the application and verified by the hash
-- chain. Kept as a no op so both databases share the same version history.
select 1;
