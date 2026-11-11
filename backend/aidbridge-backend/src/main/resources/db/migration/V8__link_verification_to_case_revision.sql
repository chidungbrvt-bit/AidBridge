-- Historical reviews have no recorded case version.
ALTER TABLE verifications
    ADD COLUMN reviewed_case_version BIGINT,
    ADD CONSTRAINT fk_verification_reviewed_case_revision
        FOREIGN KEY (aid_case_id, reviewed_case_version)
        REFERENCES case_revisions (aid_case_id, case_version);
