package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.*;
import com.aidbridge.casefile.service.EvidenceFixtureCatalog;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named="AIDBRIDGE_TEST_DATABASE_URL",matches=".+")
@EnabledIfEnvironmentVariable(named="AIDBRIDGE_TEST_DATABASE_USERNAME",matches=".+")
@EnabledIfEnvironmentVariable(named="AIDBRIDGE_TEST_DATABASE_PASSWORD",matches=".+")
class EvidenceAccessIT extends AbstractCaseHttpIT {
    @Test void fixtureCatalogIsPrivateAndOrganizationAllowlisted() throws Exception {
        var allowed=volunteer.browser().send("GET","/api/evidence-fixtures?organizationId="+ORG_A,null,null,false);
        status(allowed,200); assertThat(node(allowed).size()).isEqualTo(3);
        assertThat(allowed.body()).doesNotContain("AIDBRIDGE_PRIVATE_FIXTURE","storageKey","classpath","private-evidence");
        problem(other.browser().send("GET","/api/evidence-fixtures?organizationId="+ORG_A,null,null,false),404,"RESOURCE_NOT_FOUND");
        problem(admin.browser().send("GET","/api/evidence-fixtures?organizationId="+ORG_A,null,null,false),404,"RESOURCE_NOT_FOUND");
        problem(new Browser().send("GET","/api/evidence-fixtures?organizationId="+ORG_A,null,null,false),401,"UNAUTHENTICATED");
        var otherCatalog=other.browser().send("GET","/api/evidence-fixtures?organizationId="+ORG_B,null,null,false);
        status(otherCatalog,200); assertThat(node(otherCatalog).size()).isEqualTo(1);
    }

    @Test void downloadUsesActualBytesAttachmentNoStoreAndAccessAudit() throws Exception {
        var c=create(); var version=c.path("evidenceVersions").get(0);
        String path="/api/evidence-versions/"+version.path("id").asText()+"/content";
        var content=verifier.browser().send("GET",path,null,null,false);
        status(content,200);
        byte[] bytes=content.body().getBytes(StandardCharsets.UTF_8);
        assertThat(EvidenceFixtureCatalog.sha256(bytes)).isEqualTo(version.path("sha256").asText());
        assertThat(bytes.length).isEqualTo(version.path("byteSize").asLong());
        assertThat(content.headers().firstValue("Content-Disposition").orElse("")).startsWith("attachment;").contains("evidence-");
        assertThat(content.headers().firstValue("X-Content-Type-Options").orElse("")).isEqualTo("nosniff");
        assertThat(jdbc.queryForObject("select count(*) from audit_events where actor_user_id=? and event_type='EVIDENCE_CONTENT_READ'",Long.class,verifier.id())).isEqualTo(1);
        problem(other.browser().send("GET",path,null,null,false),404,"RESOURCE_NOT_FOUND");
        problem(admin.browser().send("GET",path,null,null,false),404,"RESOURCE_NOT_FOUND");
        problem(new Browser().send("GET",path,null,null,false),401,"UNAUTHENTICATED");
        assertThat(count("audit_events","actor_user_id",other.id())).isZero();
    }

    @Test void replacingEvidencePreservesPreviousBytesHashesLinksAndSnapshot() throws Exception {
        var c=create(); UUID id=UUID.fromString(c.path("id").asText());
        var old=c.path("evidenceVersions").get(0);
        String oldPath="/api/evidence-versions/"+old.path("id").asText()+"/content";
        var before=volunteer.browser().send("GET",oldPath,null,null,false); status(before,200);
        // Pick the other fixture regardless of ordering of randomly generated version IDs.
        String replacement=before.body().contains("PRIVATE_FIXTURE_V1")?"household-note-v2":"household-note-v1";
        var request=Map.of("expectedCaseVersion",0,"evidenceFileId",old.path("evidenceFileId").asText(),
                "supersedesVersionId",old.path("id").asText(),"fixtureKey",replacement);
        String key=UUID.randomUUID().toString();
        var created=volunteer.browser().send("POST","/api/cases/"+id+"/evidence-versions",request,key,true);
        status(created,201); assertThat(node(created).path("versionNo").asInt()).isEqualTo(2);
        assertThat(node(created).path("sha256").asText()).isNotEqualTo(old.path("sha256").asText());
        var replay=volunteer.browser().send("POST","/api/cases/"+id+"/evidence-versions",request,key,true);
        status(replay,201); assertThat(node(replay)).isEqualTo(node(created));
        var after=volunteer.browser().send("GET",oldPath,null,null,false); status(after,200);
        assertThat(after.body()).isEqualTo(before.body());
        assertThat(count("case_evidence_links","aid_case_id",id)).isEqualTo(3);
        var detail=volunteer.browser().send("GET","/api/cases/"+id,null,null,false); status(detail,200);
        assertThat(node(detail).path("version").asLong()).isEqualTo(1);
        assertThat(node(detail).path("evidenceVersions").size()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select snapshot_private::text from case_revisions where aid_case_id=? and case_version=0",String.class,id)).contains(old.path("id").asText());
    }

    @Test void pathsUrlsAndUnknownKeysCannotResolveOrCreateRows() throws Exception {
        for(String key:List.of("../application.properties","file:///C:/Windows/win.ini","https://example.com/private","unknown")) {
            var body=createBody(); body.put("evidenceFixtureKeys",List.of(key));
            problem(volunteer.browser().send("POST","/api/cases",body,UUID.randomUUID().toString(),true),404,"RESOURCE_NOT_FOUND");
        }
        assertThat(count("beneficiaries","created_by",volunteer.id())).isZero();
        var body=createBody(); body.put("organizationId",ORG_B); body.put("evidenceFixtureKeys",List.of("household-note-v1"));
        problem(other.browser().send("POST","/api/cases",body,UUID.randomUUID().toString(),true),404,"RESOURCE_NOT_FOUND");
    }

    @Test void evidenceMutationRejectsNonCreatorWrongOrganizationStaleAndReviewedState() throws Exception {
        var c=create(); String path="/api/cases/"+c.path("id").asText()+"/evidence-versions";
        var request=Map.of("expectedCaseVersion",0,"fixtureKey","household-note-v2");
        problem(verifier.browser().send("POST",path,request,UUID.randomUUID().toString(),true),403,"FORBIDDEN");
        problem(other.browser().send("POST",path,request,UUID.randomUUID().toString(),true),404,"RESOURCE_NOT_FOUND");
        problem(volunteer.browser().send("POST",path,Map.of("expectedCaseVersion",99,"fixtureKey","household-note-v2"),UUID.randomUUID().toString(),true),409,"VERSION_CONFLICT");
        jdbc.update("update aid_cases set status='VERIFIED' where id=?",UUID.fromString(c.path("id").asText()));
        problem(volunteer.browser().send("POST",path,request,UUID.randomUUID().toString(),true),409,"INVALID_STATE");
    }

    @Test void cannotSupersedeAnotherCaseEvenInSameOrganization() throws Exception {
        var a=create(); var b=create(); var version=a.path("evidenceVersions").get(0);
        var request=Map.of("expectedCaseVersion",0,"fixtureKey","household-note-v2",
                "evidenceFileId",version.path("evidenceFileId").asText(),"supersedesVersionId",version.path("id").asText());
        problem(volunteer.browser().send("POST","/api/cases/"+b.path("id").asText()+"/evidence-versions",request,UUID.randomUUID().toString(),true),404,"RESOURCE_NOT_FOUND");
        assertThat(count("case_evidence_links","aid_case_id",UUID.fromString(b.path("id").asText()))).isEqualTo(2);
    }

    @Test void oldSuccessorCannotBeReusedAndDatabaseRejectsVersionOverwrite() throws Exception {
        var c=create(); var version=c.path("evidenceVersions").get(0); UUID versionId=UUID.fromString(version.path("id").asText());
        var request=new LinkedHashMap<String,Object>();
        request.put("expectedCaseVersion",0);request.put("fixtureKey","household-note-v2");
        request.put("evidenceFileId",version.path("evidenceFileId").asText());request.put("supersedesVersionId",versionId);
        String path="/api/cases/"+c.path("id").asText()+"/evidence-versions";
        status(volunteer.browser().send("POST",path,request,UUID.randomUUID().toString(),true),201);
        request.put("expectedCaseVersion",1);
        problem(volunteer.browser().send("POST",path,request,UUID.randomUUID().toString(),true),409,"INVALID_STATE");
        assertThatThrownBy(()->jdbc.update("update evidence_versions set sha256_hex=repeat('0',64) where id=?",versionId))
                .hasMessageContaining("Immutable business records");
    }

    @Test void revokedMembershipAndPlatformRoleDoNotPermitContentOrMutationReplay() throws Exception {
        var c=create(); var old=c.path("evidenceVersions").get(0);
        var request=Map.of("expectedCaseVersion",0,"fixtureKey","household-note-v2");
        String path="/api/cases/"+c.path("id").asText()+"/evidence-versions",key=UUID.randomUUID().toString();
        status(volunteer.browser().send("POST",path,request,key,true),201);
        jdbc.update("update organization_memberships set status='DISABLED' where id=?",volunteer.membership());
        problem(volunteer.browser().send("POST",path,request,key,true),404,"RESOURCE_NOT_FOUND");
        problem(volunteer.browser().send("GET","/api/evidence-versions/"+old.path("id").asText()+"/content",null,null,false),404,"RESOURCE_NOT_FOUND");
    }
}
