package com.aidbridge.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import com.aidbridge.AidbridgeBackendApplication;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes=AidbridgeBackendApplication.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","spring.flyway.target=latest",
        "spring.flyway.clean-disabled=true","spring.flyway.create-schemas=true","server.servlet.session.cookie.secure=false",
        "logging.level.org.hibernate.SQL=OFF","logging.level.org.hibernate.orm.jdbc.bind=OFF"})
@ActiveProfiles("test")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@org.junit.jupiter.api.parallel.ResourceLock("aidbridge-b04-test-schema")
abstract class AbstractCaseHttpIT {
    static final UUID ORG_A=UUID.fromString("10000000-0000-4000-8000-000000000001");
    static final UUID ORG_B=UUID.fromString("10000000-0000-4000-8000-000000000002");
    static final String SCHEMA="b04_it_"+UUID.randomUUID().toString().replace("-","");
    static final String PASSWORD="B04-test-"+UUID.randomUUID();
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.aidbridge.exception.GlobalExceptionHandler diagnosticErrors;

    @BeforeEach void diagnoseFixtureFailure() {
        org.mockito.Mockito.doAnswer(invocation -> {
            Throwable cause = invocation.getArgument(0);
            while (cause != null) {
                System.err.println("TEST_FIXTURE_CAUSE " + cause.getClass().getName());
                if (cause instanceof org.postgresql.util.PSQLException pg) {
                    var detail = pg.getServerErrorMessage();
                    System.err.println("TEST_FIXTURE_SQLSTATE " + pg.getSQLState()
                            + (detail == null ? "" : " " + detail.getTable() + " " + detail.getColumn()
                                    + " " + detail.getConstraint()));
                }
                for (var frame : cause.getStackTrace()) {
                    if (frame.getClassName().startsWith("com.aidbridge.")) {
                        System.err.println("TEST_FIXTURE_FRAME " + frame);
                    }
                }
                cause = cause.getCause();
            }
            return invocation.callRealMethod();
        }).when(diagnosticErrors).handleUnexpectedException(
                org.mockito.ArgumentMatchers.any(Exception.class), org.mockito.ArgumentMatchers.any());
    }
    @Autowired PasswordEncoder encoder;
    @LocalServerPort int port;
    String passwordHash;
    Actor volunteer, other, verifier, admin;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        String url=System.getenv("AIDBRIDGE_TEST_DATABASE_URL");
        if (!("jdbc:postgresql://localhost:5432/aidbridge_test".equals(url)
                || "jdbc:postgresql://127.0.0.1:5432/aidbridge_test".equals(url))
                || !"aidbridge_app".equals(System.getenv("AIDBRIDGE_TEST_DATABASE_USERNAME")))
            throw new IllegalStateException("B04 tests require localhost:5432/aidbridge_test and aidbridge_app");
        r.add("spring.datasource.url",()->url+"?currentSchema="+SCHEMA);
        r.add("spring.datasource.username",()->"aidbridge_app");
        r.add("spring.datasource.password",()->System.getenv("AIDBRIDGE_TEST_DATABASE_PASSWORD"));
        r.add("spring.flyway.url",()->url+"?currentSchema="+SCHEMA);
        r.add("spring.flyway.user",()->"aidbridge_app");
        r.add("spring.flyway.password",()->System.getenv("AIDBRIDGE_TEST_DATABASE_PASSWORD"));
        r.add("spring.flyway.schemas",()->SCHEMA);
        r.add("spring.flyway.default-schema",()->SCHEMA);
        r.add("spring.jpa.properties.hibernate.default_schema",()->SCHEMA);
        r.add("aidbridge.evidence.organization-fixtures["+ORG_A+"]",()->"household-note-v1,household-note-v2,consent-note-v1");
        r.add("aidbridge.evidence.organization-fixtures["+ORG_B+"]",()->"consent-note-v1");
    }
    @BeforeAll void encodePassword() { passwordHash=encoder.encode(PASSWORD); }
    @BeforeEach void actors() throws Exception {
        assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("aidbridge_test");
        assertThat(jdbc.queryForObject("select current_schema()",String.class)).isEqualTo(SCHEMA);
        jdbc.update("INSERT INTO organizations(id,name,status) VALUES (?,'B04 Organization A','APPROVED'),(?,'B04 Organization B','APPROVED') ON CONFLICT(id) DO UPDATE SET status='APPROVED'",ORG_A,ORG_B);
        volunteer=actor(ORG_A,"VOLUNTEER",false);
        other=actor(ORG_B,"VOLUNTEER",false);
        verifier=actor(ORG_A,"VERIFIER",false);
        admin=actor(null,null,true);
    }
    Actor actor(UUID org,String role,boolean platformAdmin) throws Exception {
        UUID id=UUID.randomUUID(), membership=UUID.randomUUID();
        String email="b04-"+id+"@example.com";
        jdbc.update("INSERT INTO users(id,full_name,email,password_hash,platform_role) VALUES (?,'B04 Test Staff',?,?,?)",
                id,email,passwordHash,platformAdmin?"PLATFORM_ADMIN":"USER");
        if(org!=null) {
            jdbc.update("INSERT INTO organization_memberships(id,organization_id,user_id) VALUES(?,?,?)",membership,org,id);
            jdbc.update("INSERT INTO membership_roles(membership_id,role) VALUES(?,?)",membership,role);
        }
        Browser browser=new Browser();
        browser.csrf();
        status(browser.send("POST","/api/auth/login",Map.of("email",email,"password",PASSWORD),null,true),200);
        browser.csrf();
        return new Actor(id,membership,browser);
    }
    record Actor(UUID id,UUID membership,Browser browser) { }
    Map<String,Object> createBody() {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("organizationId",ORG_A); body.put("beneficiaryAlias","Demo Household A");
        body.put("approximateArea","Simulated district"); body.put("internalTitle","Private case title");
        body.put("priority","NORMAL");
        body.put("consent",Map.of("basis","CONSENT","scope","Aid case processing",
                "capturedAt",Instant.now().minusSeconds(60).toString(),"publicSummaryAllowed",false));
        body.put("needs",List.of(Map.of("name","Food","unit","PACKAGE","quantity",2),Map.of("name","Hygiene","unit","KIT","quantity",2)));
        body.put("evidenceFixtureKeys",List.of("household-note-v1","consent-note-v1"));
        return body;
    }
    JsonNode create() throws Exception {
        var response=volunteer.browser().send("POST","/api/cases",createBody(),UUID.randomUUID().toString(),true);
        status(response,201); return node(response);
    }
    JsonNode node(HttpResponse<String> response) { return json.readTree(response.body()); }
    void status(HttpResponse<String> response,int expected) {
        assertThat(response.statusCode()).as("HTTP status").isEqualTo(expected);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(response.body()).doesNotContain(PASSWORD,passwordHash,"passwordHash","privateDataEncrypted","storageKey","private-evidence/");
    }
    void problem(HttpResponse<String> response,int expected,String code) {
        status(response,expected);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        var body=node(response);
        assertThat(body.path("code").asText()).isEqualTo(code);
        assertThat(body.path("traceId").asText()).isNotBlank();
        assertThat(body.path("instance").asText()).startsWith("/api/");
        assertThat(response.body()).doesNotContain("stackTrace","org.postgresql","AIDBRIDGE_PRIVATE_FIXTURE");
    }
    long count(String table,String column,UUID id) {
        // Table/column are test constants, never HTTP input.
        return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE "+column+"=?",Long.class,id);
    }
    class Browser {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
        String token;
        void csrf() throws Exception {
            var response=send("GET","/api/auth/csrf",null,null,false);
            status(response,200); token=node(response).path("token").asText();
        }
        HttpResponse<String> send(String method,String path,Object body,String key,boolean csrf) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(30));
            if(csrf && token!=null) request.header("X-CSRF-TOKEN",token);
            if(key!=null) request.header("Idempotency-Key",key);
            if(body!=null) request.header("Content-Type","application/json");
            String encoded=body instanceof String raw?raw:body==null?null:json.writeValueAsString(body);
            request.method(method,encoded==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(encoded));
            return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        }
    }
}
