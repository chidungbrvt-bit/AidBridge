package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import com.aidbridge.audit.service.LocalAuditService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@EnabledIfEnvironmentVariable(named="AIDBRIDGE_TEST_DATABASE_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="AIDBRIDGE_TEST_DATABASE_USERNAME",matches=".+")
@EnabledIfEnvironmentVariable(named="AIDBRIDGE_TEST_DATABASE_PASSWORD",matches=".+")
@ExtendWith(OutputCaptureExtension.class)
class CaseApiIT extends AbstractCaseHttpIT {
    @MockitoSpyBean LocalAuditService audit;

    @Test void privateRequestValuesAndCredentialsAreNotWrittenToLogs(CapturedOutput output) throws Exception {
        String marker="PRIVATE_CASE_"+UUID.randomUUID();var body=createBody();body.put("internalTitle",marker);
        status(volunteer.browser().send("POST","/api/cases",body,UUID.randomUUID().toString(),true),201);
        assertThat(output.getAll()).doesNotContain(marker,PASSWORD,passwordHash,"AIDBRIDGE_PRIVATE_FIXTURE");
    }

    @Test void volunteerCreatesAndReadsTwoNeedsWhileOtherOrganizationAndPlatformAdminCannot() throws Exception {
        var body=createBody(); String key=UUID.randomUUID().toString();
        var response=volunteer.browser().send("POST","/api/cases",body,key,true);
        status(response,201); var created=node(response); String id=created.path("id").asText();
        assertThat(response.headers().firstValue("Location").orElseThrow()).isEqualTo("/api/cases/"+id);
        assertThat(created.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(created.path("needs").size()).isEqualTo(2);
        assertThat(created.path("evidenceVersions").size()).isEqualTo(2);
        assertThat(created.path("version").asLong()).isZero();
        assertThat(created.path("creator").path("id").asText()).isEqualTo(volunteer.id().toString());
        status(volunteer.browser().send("GET","/api/cases/"+id,null,null,false),200);
        status(verifier.browser().send("GET","/api/cases/"+id,null,null,false),200);
        problem(other.browser().send("GET","/api/cases/"+id,null,null,false),404,"RESOURCE_NOT_FOUND");
        problem(admin.browser().send("GET","/api/cases/"+id,null,null,false),404,"RESOURCE_NOT_FOUND");
        var list=volunteer.browser().send("GET","/api/cases?organizationId="+ORG_A+"&status=SUBMITTED",null,null,false);
        status(list,200); assertThat(node(list).path("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(list.body()).doesNotContain("beneficiaryAlias","consent","evidenceVersions");
        problem(other.browser().send("GET","/api/cases?organizationId="+ORG_A,null,null,false),404,"RESOURCE_NOT_FOUND");
        UUID caseId=UUID.fromString(id);
        assertThat(count("audit_events","aid_case_id",caseId)).isEqualTo(1);
        assertThat(count("case_revisions","aid_case_id",caseId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from blockchain_jobs j join audit_events a on a.id=j.audit_event_id where a.aid_case_id=?",Long.class,caseId)).isZero();
    }

    @Test void consentAndEvidenceAreRequiredWithoutAnyBusinessRows() throws Exception {
        for(String field:List.of("consent","evidenceFixtureKeys")) {
            var body=createBody(); body.remove(field);
            problem(volunteer.browser().send("POST","/api/cases",body,UUID.randomUUID().toString(),true),400,"VALIDATION_ERROR");
        }
        assertThat(count("aid_cases","created_by",volunteer.id())).isZero();
        assertThat(count("consent_records","recorded_by",volunteer.id())).isZero();
        assertThat(count("idempotency_records","actor_id",volunteer.id())).isZero();
    }

    @ParameterizedTest @ValueSource(ints={0,-1,1_000_001})
    void invalidQuantityIsRejected(int quantity) throws Exception {
        var body=createBody(); body.put("needs",List.of(Map.of("name","Food","unit","PACKAGE","quantity",quantity)));
        problem(volunteer.browser().send("POST","/api/cases",body,UUID.randomUUID().toString(),true),400,"VALIDATION_ERROR");
        assertThat(count("aid_cases","created_by",volunteer.id())).isZero();
    }

    @Test void duplicatesAreRejectedAfterCaseAndUnitNormalization() throws Exception {
        var body=createBody(); body.put("needs",List.of(Map.of("name","Food","unit","KIT","quantity",1),
                Map.of("name"," food ","unit","kit","quantity",2)));
        problem(volunteer.browser().send("POST","/api/cases",body,UUID.randomUUID().toString(),true),400,"VALIDATION_ERROR");
    }

    @Test void nonVolunteerCannotCreateAndAnotherCreatorCannotEdit() throws Exception {
        problem(verifier.browser().send("POST","/api/cases",createBody(),UUID.randomUUID().toString(),true),403,"FORBIDDEN");
        var created=create(); var colleague=actor(ORG_A,"VOLUNTEER",false);
        problem(colleague.browser().send("PATCH","/api/cases/"+created.path("id").asText(),
                Map.of("expectedVersion",0,"internalTitle","Unowned edit"),UUID.randomUUID().toString(),true),403,"FORBIDDEN");
    }

    @Test void replayReturnsOriginalStatusBodyLocationAndRejectsChangedPayload() throws Exception {
        var body=createBody(); String key=UUID.randomUUID().toString();
        var first=volunteer.browser().send("POST","/api/cases",body,key,true);
        var retry=volunteer.browser().send("POST","/api/cases",body,key,true);
        status(first,201); status(retry,201);
        assertThat(node(retry)).isEqualTo(node(first));
        assertThat(retry.headers().firstValue("Idempotent-Replayed").orElse("")).isEqualTo("true");
        assertThat(retry.headers().firstValue("Location")).isEqualTo(first.headers().firstValue("Location"));
        body.put("internalTitle","  Private case title  ");
        status(volunteer.browser().send("POST","/api/cases",body,key,true),201);
        body.put("internalTitle","Different request");
        problem(volunteer.browser().send("POST","/api/cases",body,key,true),409,"IDEMPOTENCY_CONFLICT");
        assertThat(count("aid_cases","created_by",volunteer.id())).isEqualTo(1);
    }

    @Test void revokedMembershipIsRecheckedBeforeReplayAndPrivateReads() throws Exception {
        var body=createBody(); String key=UUID.randomUUID().toString();
        var created=volunteer.browser().send("POST","/api/cases",body,key,true); status(created,201);
        jdbc.update("update organization_memberships set status='DISABLED' where id=?",volunteer.membership());
        problem(volunteer.browser().send("POST","/api/cases",body,key,true),403,"FORBIDDEN");
        problem(volunteer.browser().send("GET","/api/cases/"+node(created).path("id").asText(),null,null,false),404,"RESOURCE_NOT_FOUND");
    }

    @Test void roleRevocationCannotReplayAnEarlierSuccessfulCreate() throws Exception {
        var body=createBody();String key=UUID.randomUUID().toString();
        status(volunteer.browser().send("POST","/api/cases",body,key,true),201);
        jdbc.update("delete from membership_roles where membership_id=?",volunteer.membership());
        problem(volunteer.browser().send("POST","/api/cases",body,key,true),403,"FORBIDDEN");
        assertThat(count("aid_cases","created_by",volunteer.id())).isEqualTo(1);
    }

    @Test void idempotencyKeyCannotBeReusedInAnotherOrganizationByTheSameActor() throws Exception {
        var body=createBody();String key=UUID.randomUUID().toString();
        status(volunteer.browser().send("POST","/api/cases",body,key,true),201);
        UUID membership=UUID.randomUUID();
        jdbc.update("insert into organization_memberships(id,organization_id,user_id) values(?,?,?)",membership,ORG_B,volunteer.id());
        jdbc.update("insert into membership_roles(membership_id,role) values(?,'VOLUNTEER')",membership);
        body.put("organizationId",ORG_B);body.put("evidenceFixtureKeys",List.of("consent-note-v1"));
        problem(volunteer.browser().send("POST","/api/cases",body,key,true),409,"IDEMPOTENCY_CONFLICT");
        assertThat(count("aid_cases","created_by",volunteer.id())).isEqualTo(1);
    }

    @Test void suspendedOrganizationAndDisabledAccountLoseAccess() throws Exception {
        var c=create(); String path="/api/cases/"+c.path("id").asText();
        jdbc.update("update organizations set status='SUSPENDED' where id=?",ORG_A);
        problem(volunteer.browser().send("GET",path,null,null,false),404,"RESOURCE_NOT_FOUND");
        jdbc.update("update organizations set status='APPROVED' where id=?",ORG_A);
        jdbc.update("update users set status='DISABLED' where id=?",volunteer.id());
        problem(volunteer.browser().send("GET",path,null,null,false),401,"INVALID_CREDENTIALS");
    }

    @Test void patchRetainsPreviousNeedsAndRevisionWhileStaleAndReviewedWritesFail() throws Exception {
        var c=create(); String id=c.path("id").asText(),path="/api/cases/"+id;
        String snapshot=jdbc.queryForObject("select snapshot_private::text from case_revisions where aid_case_id=? and case_version=0",String.class,UUID.fromString(id));
        var body=Map.of("expectedVersion",0,"needs",List.of(Map.of("name","Blanket","unit","PIECE","quantity",3)));
        String key=UUID.randomUUID().toString();
        var response=volunteer.browser().send("PATCH",path,body,key,true); status(response,200);
        assertThat(node(response).path("version").asLong()).isEqualTo(1);
        assertThat(node(response).path("needs").size()).isEqualTo(1);
        assertThat(count("case_need_items","aid_case_id",UUID.fromString(id))).isEqualTo(3);
        assertThat(jdbc.queryForObject("select snapshot_private::text from case_revisions where aid_case_id=? and case_version=0",String.class,UUID.fromString(id))).isEqualTo(snapshot);
        status(volunteer.browser().send("PATCH",path,body,key,true),200);
        problem(volunteer.browser().send("PATCH",path,body,UUID.randomUUID().toString(),true),409,"VERSION_CONFLICT");
        jdbc.update("update aid_cases set status='VERIFIED' where id=?",UUID.fromString(id));
        problem(volunteer.browser().send("PATCH",path,Map.of("expectedVersion",1,"internalTitle","Reviewed edit"),UUID.randomUUID().toString(),true),409,"INVALID_STATE");
    }

    @Test void resubmitRequiresChangesRequestedAndKeepsPriorRevision() throws Exception {
        var c=create(); UUID id=UUID.fromString(c.path("id").asText()); String path="/api/cases/"+id+"/resubmit";
        problem(volunteer.browser().send("POST",path,Map.of("expectedVersion",0),UUID.randomUUID().toString(),true),409,"INVALID_STATE");
        // B05 is not implemented; this SQL fixture represents its future request-changes result.
        jdbc.update("update aid_cases set status='CHANGES_REQUESTED',version=version+1 where id=?",id);
        jdbc.update("insert into verifications(aid_case_id,verifier_id,decision) values(?,?,'CHANGES_REQUESTED')",id,verifier.id());
        problem(volunteer.browser().send("POST",path,Map.of("expectedVersion",1),UUID.randomUUID().toString(),true),409,"INVALID_STATE");
        status(volunteer.browser().send("PATCH","/api/cases/"+id,Map.of("expectedVersion",1,"internalTitle","Clarified request"),UUID.randomUUID().toString(),true),200);
        var response=volunteer.browser().send("POST",path,Map.of("expectedVersion",2),UUID.randomUUID().toString(),true);
        status(response,200); assertThat(node(response).path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(node(response).path("version").asLong()).isEqualTo(3);
        assertThat(count("case_revisions","aid_case_id",id)).isEqualTo(3);
    }

    @Test void missingEvidenceBlocksResubmitAndPublicSummaryRequiresSeparateConsent() throws Exception {
        var body=createBody(); body.put("publicSummary","An unconsented public story");
        problem(volunteer.browser().send("POST","/api/cases",body,UUID.randomUUID().toString(),true),400,"VALIDATION_ERROR");
        var c=create(); UUID id=UUID.fromString(c.path("id").asText());
        jdbc.update("update aid_cases set status='CHANGES_REQUESTED' where id=?",id);
        jdbc.update("update case_evidence_links set retired_at_version=1 where aid_case_id=?",id);
        problem(volunteer.browser().send("POST","/api/cases/"+id+"/resubmit",Map.of("expectedVersion",0),UUID.randomUUID().toString(),true),400,"VALIDATION_ERROR");
    }

    @Test void missingSessionCsrfKeyAndMalformedInputAreSafe() throws Exception {
        var anonymous=new Browser();
        problem(anonymous.send("GET","/api/cases?organizationId="+ORG_A,null,null,false),401,"UNAUTHENTICATED");
        problem(volunteer.browser().send("POST","/api/cases",createBody(),UUID.randomUUID().toString(),false),403,"CSRF_INVALID");
        String token=volunteer.browser().token; volunteer.browser().token="invalid";
        problem(volunteer.browser().send("POST","/api/cases",createBody(),UUID.randomUUID().toString(),true),403,"CSRF_INVALID");
        volunteer.browser().token=token;
        problem(volunteer.browser().send("POST","/api/cases",createBody(),null,true),400,"VALIDATION_ERROR");
        problem(volunteer.browser().send("POST","/api/cases","{bad-json",UUID.randomUUID().toString(),true),400,"VALIDATION_ERROR");
        problem(volunteer.browser().send("GET","/api/cases?organizationId="+ORG_A+"&status=UNKNOWN",null,null,false),400,"VALIDATION_ERROR");
    }

    @Test void auditFailureRollsBackBeneficiaryConsentCaseNeedsEvidenceAndIdempotency() throws Exception {
        LocalAuditService auditSpy = AopTestUtils.getUltimateTargetObject(audit);
        doThrow(new IllegalStateException("Synthetic audit persistence failure"))
                .when(auditSpy).append(eq(ORG_A),any(),eq(volunteer.id()),any(),eq("CASE_CREATED"),any());
        problem(volunteer.browser().send("POST","/api/cases",createBody(),UUID.randomUUID().toString(),true),500,"INTERNAL_SERVER_ERROR");
        for(var tableColumn:Map.of("beneficiaries","created_by","consent_records","recorded_by",
                "aid_cases","created_by","evidence_files","uploaded_by","audit_events","actor_user_id","idempotency_records","actor_id").entrySet())
            assertThat(count(tableColumn.getKey(),tableColumn.getValue(),volunteer.id())).isZero();
    }

    @Test void simultaneousEditsAcceptExactlyOneExpectedVersion() throws Exception {
        var c=create(); UUID id=UUID.fromString(c.path("id").asText());
        var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results=new ArrayList<>();
            for(int i=0;i<2;i++) { int number=i;
                results.add(executor.submit(()->{start.await();return volunteer.browser().send("PATCH","/api/cases/"+id,
                        Map.of("expectedVersion",0,"internalTitle","Concurrent title "+number),UUID.randomUUID().toString(),true).statusCode();})); }
            start.countDown();
            assertThat(List.of(results.get(0).get(30,TimeUnit.SECONDS),results.get(1).get(30,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200,409);
        }
        assertThat(count("case_revisions","aid_case_id",id)).isEqualTo(2);
    }

    @Test void concurrentSameKeyCreatesExactlyOneCaseAndAudit() throws Exception {
        var body=createBody(); String key=UUID.randomUUID().toString(); var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->{start.await();return volunteer.browser().send("POST","/api/cases",body,key,true);});
            var b=executor.submit(()->{start.await();return volunteer.browser().send("POST","/api/cases",body,key,true);});
            start.countDown(); var first=a.get(30,TimeUnit.SECONDS); var second=b.get(30,TimeUnit.SECONDS);
            status(first,201); status(second,201); assertThat(node(first)).isEqualTo(node(second));
        }
        assertThat(count("aid_cases","created_by",volunteer.id())).isEqualTo(1);
        assertThat(count("audit_events","actor_user_id",volunteer.id())).isEqualTo(1);
    }
}
