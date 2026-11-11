package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnabledIfEnvironmentVariable(named="AIDBRIDGE_TEST_DATABASE_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="AIDBRIDGE_TEST_DATABASE_USERNAME",matches=".+")
@EnabledIfEnvironmentVariable(named="AIDBRIDGE_TEST_DATABASE_PASSWORD",matches=".+")
class B04MigrationIT {
    String schema,url,user,password;
    @BeforeEach void isolate() {
        schema="b04_migration_"+UUID.randomUUID().toString().replace("-","");
        url=System.getenv("AIDBRIDGE_TEST_DATABASE_URL");user=System.getenv("AIDBRIDGE_TEST_DATABASE_USERNAME");
        password=System.getenv("AIDBRIDGE_TEST_DATABASE_PASSWORD");
        assertThat(url).isIn("jdbc:postgresql://localhost:5432/aidbridge_test","jdbc:postgresql://127.0.0.1:5432/aidbridge_test");
        assertThat(user).isEqualTo("aidbridge_app");
    }
    FluentConfiguration config() { return Flyway.configure().dataSource(url,user,password)
            .locations("classpath:db/migration").schemas(schema).defaultSchema(schema).createSchemas(true).cleanDisabled(true); }
    Connection connect() throws Exception {
        var connection=DriverManager.getConnection(url,user,password);connection.setSchema(schema);return connection;
    }
    @Test void cleanMigrationThroughV8HasAllTablesAndValidatedChecksums() throws Exception {
        var flyway=config().target("8").load(); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(8);flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
        try(var c=connect();var s=c.prepareStatement("select count(*) from information_schema.tables where table_schema=? and table_type='BASE TABLE' and table_name<>'flyway_schema_history'")) {
            s.setString(1,schema);try(var rows=s.executeQuery()){rows.next();assertThat(rows.getInt(1)).isEqualTo(28);}
        }
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
    @Test void upgradePreservesPrivateLegacyDataWithoutInventingConsentOrPublicSummary() throws Exception {
        config().target("5").load().migrate();UUID caseId=legacyCase("MEDIUM");
        var upgraded=config().target("6").load();assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);upgraded.validate();
        try(var c=connect();var s=c.prepareStatement("select priority,status,internal_title,title,public_description,public_summary,consent_record_id,version from aid_cases where id=?")) {
            s.setObject(1,caseId);try(var r=s.executeQuery()) {
                assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("NORMAL");
                assertThat(r.getString(2)).isEqualTo("CHANGES_REQUESTED");
                assertThat(r.getString(3)).isEqualTo("Legacy internal title");
                assertThat(r.getString(4)).isEqualTo("Legacy internal title");
                assertThat(r.getString(5)).isEqualTo("Legacy private marker");
                assertThat(r.getString(6)).isNull();assertThat(r.getObject(7)).isNull();assertThat(r.getLong(8)).isZero();
            }
        }
    }
    @Test void upgradeToV7NormalizesReviewDecisionAndPreservesHistory() throws Exception {
        config().target("5").load().migrate();
        UUID caseId = legacyCase("MEDIUM");
        config().target("6").load().migrate();
        UUID reviewerId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        OffsetDateTime reviewedAt = OffsetDateTime.parse("2026-11-05T10:00:00Z");
        String notes = "Provide a clearer handover photo";

        try (var connection = connect();
             var reviewer = connection.prepareStatement("insert into users(id,email,password_hash,full_name) values(?,?,'not-a-login-hash','Migration reviewer')");
             var review = connection.prepareStatement("insert into verifications(id,aid_case_id,verifier_id,decision,notes,verified_at) values(?,?,?,'NEEDS_INFO',?,?)")) {
            reviewer.setObject(1, reviewerId);
            reviewer.setString(2, reviewerId + "@example.com");
            reviewer.executeUpdate();
            review.setObject(1, reviewId);
            review.setObject(2, caseId);
            review.setObject(3, reviewerId);
            review.setString(4, notes);
            review.setObject(5, reviewedAt);
            review.executeUpdate();
        }

        var upgraded = config().target("7").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        upgraded.validate();
        try (var connection = connect();
             var select = connection.prepareStatement("select aid_case_id,verifier_id,decision,notes,verified_at from verifications where id=?");
             var restoreLegacyDecision = connection.prepareStatement("update verifications set decision='NEEDS_INFO' where id=?")) {
            select.setObject(1, reviewId);
            try (var row = select.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getObject("aid_case_id", UUID.class)).isEqualTo(caseId);
                assertThat(row.getObject("verifier_id", UUID.class)).isEqualTo(reviewerId);
                assertThat(row.getString("decision")).isEqualTo("CHANGES_REQUESTED");
                assertThat(row.getString("notes")).isEqualTo(notes);
                assertThat(row.getObject("verified_at", OffsetDateTime.class)).isEqualTo(reviewedAt);
            }
            restoreLegacyDecision.setObject(1, reviewId);
            assertThatThrownBy(restoreLegacyDecision::executeUpdate)
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23514"));
        }
    }
    @Test void upgradeToV8PreservesLegacyReviewAndLinksOnlyRevisionsOfItsCase() throws Exception {
        config().target("5").load().migrate();
        UUID caseId = legacyCase("MEDIUM");
        UUID otherCaseId = legacyCase("MEDIUM");
        config().target("7").load().migrate();
        UUID legacyReviewId = UUID.randomUUID();
        OffsetDateTime legacyReviewedAt = OffsetDateTime.parse("2026-11-05T10:00:00Z");
        try (var connection = connect();
             var review = connection.prepareStatement("""
                     INSERT INTO verifications (id, aid_case_id, verifier_id, decision, notes, verified_at)
                     SELECT ?, id, created_by, 'CHANGES_REQUESTED', 'Legacy request', ?
                     FROM aid_cases WHERE id = ?
                     """)) {
            review.setObject(1, legacyReviewId);
            review.setObject(2, legacyReviewedAt);
            review.setObject(3, caseId);
            assertThat(review.executeUpdate()).isEqualTo(1);
        }

        var upgraded = config().target("8").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        upgraded.validate();

        UUID newReviewId = UUID.randomUUID();
        try (var connection = connect();
             var legacy = connection.prepareStatement("""
                     SELECT reviewed_case_version, verified_at FROM verifications WHERE id = ?
                     """);
             var review = connection.prepareStatement("""
                     INSERT INTO verifications (id, aid_case_id, verifier_id, decision, reviewed_case_version)
                     SELECT ?, id, created_by, 'APPROVED', ? FROM aid_cases WHERE id = ?
                     """);
             var snapshot = connection.prepareStatement("""
                     SELECT r.snapshot_private ->> 'internalTitle' AS title
                     FROM verifications v
                     JOIN case_revisions r ON r.aid_case_id = v.aid_case_id
                         AND r.case_version = v.reviewed_case_version
                     WHERE v.id = ?
                     """)) {
            legacy.setObject(1, legacyReviewId);
            try (var row = legacy.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getObject("reviewed_case_version")).isNull();
                assertThat(row.getObject("verified_at", OffsetDateTime.class)).isEqualTo(legacyReviewedAt);
            }

            insertRevision(connection, caseId, 5, "Reviewed title");
            insertRevision(connection, otherCaseId, 9, "Another case title");
            review.setObject(1, newReviewId);
            review.setLong(2, 9);
            review.setObject(3, caseId);
            assertThatThrownBy(review::executeUpdate)
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23503"));

            review.setLong(2, 5);
            assertThat(review.executeUpdate()).isEqualTo(1);
            insertRevision(connection, caseId, 6, "Updated title");

            snapshot.setObject(1, newReviewId);
            try (var row = snapshot.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("title")).isEqualTo("Reviewed title");
            }
        }
    }

    @Test void upgradeToV9BackfillsPublicRefsAndWorkerColumns() throws Exception {
        config().target("5").load().migrate();
        UUID caseId = legacyCase("MEDIUM");
        config().target("8").load().migrate();
        UUID auditEventId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        try (var connection = connect();
             var audit = connection.prepareStatement("""
                     INSERT INTO audit_events (id, organization_id, aid_case_id, actor_user_id, entity_type, entity_id, event_type, event_key)
                     SELECT ?, organization_id, id, created_by, 'VERIFICATION', ?, 'CASE_VERIFIED', ?
                     FROM aid_cases WHERE id = ?
                     """);
             var job = connection.prepareStatement("""
                     INSERT INTO blockchain_jobs (id, audit_event_id) VALUES (?, ?)
                     """)) {
            UUID verificationKey = UUID.randomUUID();
            audit.setObject(1, auditEventId);
            audit.setObject(2, verificationKey);
            audit.setObject(3, verificationKey);
            audit.setObject(4, caseId);
            assertThat(audit.executeUpdate()).isEqualTo(1);
            job.setObject(1, jobId);
            job.setObject(2, auditEventId);
            assertThat(job.executeUpdate()).isEqualTo(1);
        }

        var upgraded = config().target("9").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        upgraded.validate();

        try (var connection = connect();
             var check = connection.prepareStatement("""
                     SELECT octet_length(o.public_ref) AS org_ref_len,
                            octet_length(c.public_ref) AS case_ref_len,
                            octet_length(j.logical_event_id) AS event_id_len,
                            j.chain_sequence,
                            j.schema_version::int AS schema_version
                     FROM blockchain_jobs j
                     JOIN audit_events a ON a.id = j.audit_event_id
                     JOIN aid_cases c ON c.id = a.aid_case_id
                     JOIN organizations o ON o.id = c.organization_id
                     WHERE j.id = ?
                     """);
             var preparedTx = connection.prepareStatement("""
                     INSERT INTO blockchain_transactions (blockchain_job_id, tx_hash, status, signer_address, nonce)
                     VALUES (?, ?, 'PREPARED', '0x000000000000000000000000000000000000beef', 0)
                     """)) {
            check.setObject(1, jobId);
            try (var row = check.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getInt("org_ref_len")).isEqualTo(32);
                assertThat(row.getInt("case_ref_len")).isEqualTo(32);
                assertThat(row.getInt("event_id_len")).isEqualTo(32);
                assertThat(row.getLong("chain_sequence")).isEqualTo(1L);
                assertThat(row.getInt("schema_version")).isEqualTo(1);
            }
            preparedTx.setObject(1, jobId);
            preparedTx.setString(2, "0x" + "1".repeat(64));
            assertThat(preparedTx.executeUpdate()).isEqualTo(1);
            preparedTx.setString(2, "0x" + "2".repeat(64));
            assertThatThrownBy(preparedTx::executeUpdate)
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("23505"));
        }
    }

    private void insertRevision(Connection connection, UUID caseId, long version, String title) throws SQLException {
        try (var insert = connection.prepareStatement("""
                INSERT INTO case_revisions (id, organization_id, aid_case_id, case_version, snapshot_private)
                SELECT ?, organization_id, id, ?, jsonb_build_object('internalTitle', CAST(? AS TEXT))
                FROM aid_cases WHERE id = ?
                """)) {
            insert.setObject(1, UUID.randomUUID());
            insert.setLong(2, version);
            insert.setString(3, title);
            insert.setObject(4, caseId);
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }
    }

    @ParameterizedTest @ValueSource(strings={"LOW","HIGH"})
    void refusesAmbiguousPriorityMappingWithoutModifyingExistingRows(String priority) throws Exception {
        config().target("5").load().migrate();UUID caseId=legacyCase(priority);
        assertThatThrownBy(()->config().load().migrate()).hasMessageContaining("Legacy case priorities require an explicit mapping");
        try(var c=connect();var s=c.prepareStatement("select priority from aid_cases where id=?")) {
            s.setObject(1,caseId);try(var r=s.executeQuery()){r.next();assertThat(r.getString(1)).isEqualTo(priority);}
        }
    }
    @Test void refusesEvidenceSharedAcrossCasesBeforeChangingSchema() throws Exception {
        config().target("5").load().migrate();UUID first=legacyCase("MEDIUM"),second=legacyCase("MEDIUM");
        try(var c=connect();var s=c.createStatement()) {
            s.executeUpdate("""
                INSERT INTO evidence_files(id,organization_id,uploaded_by,title)
                SELECT '20000000-0000-4000-8000-000000000001',organization_id,created_by,'Legacy fixture' FROM aid_cases WHERE id='%s'
                """.formatted(first));
            s.executeUpdate("""
                INSERT INTO evidence_versions(id,evidence_file_id,version_number,storage_key,sha256_hex,mime_type,size_bytes,uploaded_by)
                SELECT '20000000-0000-4000-8000-000000000002','20000000-0000-4000-8000-000000000001',1,'legacy',repeat('0',64),'text/plain',0,created_by FROM aid_cases WHERE id='%s'
                """.formatted(first));
            for(UUID id:new UUID[]{first,second}) s.executeUpdate("""
                INSERT INTO case_evidence_links(aid_case_id,evidence_version_id,linked_by)
                SELECT id,'20000000-0000-4000-8000-000000000002',created_by FROM aid_cases WHERE id='%s'
                """.formatted(id));
        }
        assertThatThrownBy(()->config().load().migrate()).hasMessageContaining("Legacy evidence ownership requires reconciliation");
    }
    UUID legacyCase(String priority) throws Exception {
        UUID org=UUID.randomUUID(),actor=UUID.randomUUID(),beneficiary=UUID.randomUUID(),id=UUID.randomUUID();
        try(var c=connect()) {
            try(var s=c.prepareStatement("insert into users(id,email,password_hash,full_name) values(?,?,?,'Migration actor')")){
                s.setObject(1,actor);s.setString(2,actor+"@example.com");s.setString(3,"not-a-login-hash");s.executeUpdate();}
            try(var s=c.prepareStatement("insert into organizations(id,name) values(?,'Migration organization')")){s.setObject(1,org);s.executeUpdate();}
            try(var s=c.prepareStatement("insert into beneficiaries(id,organization_id,reference_code,display_label,created_by) values(?,?,'legacy','Fictional household',?)")){
                s.setObject(1,beneficiary);s.setObject(2,org);s.setObject(3,actor);s.executeUpdate();}
            try(var s=c.prepareStatement("insert into aid_cases(id,case_code,organization_id,beneficiary_id,created_by,title,public_description,priority,status) values(?,?,?,?,?,'Legacy internal title','Legacy private marker',?,'NEEDS_INFO')")){
                s.setObject(1,id);s.setString(2,"CASE-"+id);s.setObject(3,org);s.setObject(4,beneficiary);s.setObject(5,actor);s.setString(6,priority);s.executeUpdate();}
        }
        return id;
    }
}
