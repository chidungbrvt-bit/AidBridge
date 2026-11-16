package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

/** Runs V16 against isolated temporary V15-shaped facts; never changes application facts. */
@Transactional
class DeliveryRefMigrationIT extends AbstractAuditTrailHttpIT {
    @Override
    @org.junit.jupiter.api.BeforeEach
    void actors() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("aidbridge_test");
        assertThat(jdbc.queryForObject("select current_schema()", String.class)).isEqualTo(SCHEMA);
    }

    private void fixture(String ref, boolean duplicate, boolean wrongContext) {
        jdbc.execute("CREATE TEMP TABLE aid_cases(id uuid,organization_id uuid) ON COMMIT DROP");
        jdbc.execute("CREATE TEMP TABLE deliveries(id uuid,aid_case_id uuid,outcome varchar) ON COMMIT DROP");
        jdbc.execute("CREATE TEMP TABLE audit_events(entity_type varchar,entity_id uuid,event_type varchar,event_key uuid,aid_case_id uuid,organization_id uuid,payload_private jsonb) ON COMMIT DROP");
        jdbc.execute("CREATE TRIGGER deliveries_immutable BEFORE UPDATE OR DELETE ON deliveries FOR EACH ROW EXECUTE FUNCTION reject_immutable_fact_change()");
        UUID id=UUID.randomUUID(),caseId=UUID.randomUUID(),org=UUID.randomUUID();
        jdbc.update("INSERT INTO aid_cases VALUES (?,?)",caseId,org);
        jdbc.update("INSERT INTO deliveries VALUES (?,?,'DELIVERED')",id,caseId);
        jdbc.update("INSERT INTO deliveries VALUES (?,?,'FAILED')",UUID.randomUUID(),caseId);
        String sql="INSERT INTO audit_events VALUES ('DELIVERY',?,'DELIVERY_RECORDED',?,?,?,CAST(? AS jsonb))";
        jdbc.update(sql,id,id,caseId,wrongContext?UUID.randomUUID():org,"{\"deliveryRef\":\""+ref+"\"}");
        if(duplicate)jdbc.update(sql,id,id,caseId,org,"{\"deliveryRef\":\""+ref+"\"}");
    }
    private void migrate() throws Exception {
        String sql=new ClassPathResource("db/migration/V16__add_delivery_ref.sql").getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);
    }
    @Test void copiesHistoricalRefLeavesFailedNullAndRestoresTrigger() throws Exception {
        fixture("0x"+"ab".repeat(32),false,false);migrate();
        assertThat(jdbc.queryForObject("SELECT encode(delivery_ref,'hex') FROM deliveries WHERE outcome='DELIVERED'",String.class)).isEqualTo("ab".repeat(32));
        assertThat(jdbc.queryForObject("SELECT delivery_ref IS NULL FROM deliveries WHERE outcome='FAILED'",Boolean.class)).isTrue();
        assertThatThrownBy(()->jdbc.update("UPDATE deliveries SET delivery_ref=delivery_ref")).hasMessageContaining("Immutable business records");
    }
    @ParameterizedTest @ValueSource(strings={"zz","0x0","0X"})
    void rejectsMalformedHistoricalRef(String prefix) {
        fixture(prefix+"ab".repeat(32),false,false);
        assertThatThrownBy(this::migrate).hasMessageContaining("Invalid or ambiguous historical deliveryRef");
    }
    @Test void rejectsAmbiguousAudit() {
        fixture("0x"+"ab".repeat(32),true,false);
        assertThatThrownBy(this::migrate).hasMessageContaining("Invalid or ambiguous historical deliveryRef");
    }
    @Test void rejectsWrongOrganization() {
        fixture("0x"+"ab".repeat(32),false,true);
        assertThatThrownBy(this::migrate).hasMessageContaining("Invalid or ambiguous historical deliveryRef");
    }

    @Test void emptyV15SchemaUpgrades() throws Exception {
        fixture("0x"+"ab".repeat(32),false,false);
        jdbc.execute("TRUNCATE deliveries, audit_events, aid_cases");migrate();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deliveries",Long.class)).isZero();
    }
    @Test void missingAuditFailsClosed() {
        fixture("0x"+"ab".repeat(32),false,false);jdbc.execute("TRUNCATE audit_events");
        assertThatThrownBy(this::migrate).hasMessageContaining("Invalid or ambiguous historical deliveryRef");
    }
    @Test void duplicateRefsAcrossDeliveriesFailClosed() {
        fixture("0x"+"ab".repeat(32),false,false);
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO deliveries SELECT ?,aid_case_id,'DELIVERED' FROM deliveries LIMIT 1",id);
        jdbc.update("INSERT INTO audit_events SELECT entity_type,?,event_type,?,aid_case_id,organization_id,payload_private FROM audit_events LIMIT 1",id,id);
        assertThatThrownBy(this::migrate).hasMessageContaining("Duplicate historical deliveryRef");
    }
}
