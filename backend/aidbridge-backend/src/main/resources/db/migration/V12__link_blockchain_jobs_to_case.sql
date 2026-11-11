-- B07 / D52 (per D49 C01-O02): bind every case milestone job to its case so chain_sequence
-- is unique per case. Assignment happens under the case row lock; this key is the safety net.
ALTER TABLE blockchain_jobs
    ADD COLUMN aid_case_id UUID REFERENCES aid_cases(id);

-- Backfill from the immutable audit event. Before B07 only CASE_VERIFIED jobs exist (sequence 1).
UPDATE blockchain_jobs j
SET aid_case_id = a.aid_case_id
FROM audit_events a
WHERE a.id = j.audit_event_id;

-- Nullable on purpose: future organization-level jobs (registerOrganization) have no case.
-- PostgreSQL treats NULLs as distinct, so those rows are not constrained by this key.
ALTER TABLE blockchain_jobs
    ADD CONSTRAINT uq_blockchain_job_case_sequence UNIQUE (aid_case_id, chain_sequence);
