package com.aidbridge.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidbridge.AidbridgeBackendApplication;
import com.aidbridge.organization.entity.Organization;
import com.aidbridge.organization.entity.OrganizationMembership;
import com.aidbridge.organization.service.OrganizationService;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real HTTP + PostgreSQL tests of the existing production auth endpoints.
 * Each JVM uses a fresh schema in aidbridge_test. No clean/drop is performed.
 * Missing connection variables skip this class; this must not be reported as a pass.
 */
@SpringBootTest(
        classes = AidbridgeBackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.open-in-view=false",
                "spring.jpa.show-sql=false",
                "spring.flyway.enabled=true",
                "spring.flyway.target=latest",
                "spring.flyway.clean-disabled=true",
                "spring.flyway.create-schemas=true",
                "server.servlet.session.cookie.secure=false",
                "logging.level.org.hibernate.SQL=OFF",
                "logging.level.org.hibernate.orm.jdbc.bind=OFF"
        }
)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_PASSWORD", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthApiIntegrationTest {

    private static final String SCHEMA = "auth_it_"
            + UUID.randomUUID().toString().replace("-", "");
    private static final String FIXTURE_PASSWORD = "Auth-test-"
            + UUID.randomUUID().toString().substring(0, 12);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private OrganizationService organizationService;
    @LocalServerPort private int port;

    private String passwordHash;
    private UUID userId;
    private UUID organizationId;
    private UUID otherOrganizationId;
    private UUID membershipId;
    private String email;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = requiredEnvironment("AIDBRIDGE_TEST_DATABASE_URL");
        boolean allowed = url.equals("jdbc:postgresql://localhost:5432/aidbridge_test")
                || url.equals("jdbc:postgresql://127.0.0.1:5432/aidbridge_test");
        if (!allowed) {
            throw new IllegalStateException(
                    "Auth integration tests require localhost:5432/aidbridge_test"
            );
        }
        String username = requiredEnvironment("AIDBRIDGE_TEST_DATABASE_USERNAME");
        if (!"aidbridge_app".equals(username)) {
            throw new IllegalStateException("Auth integration tests require role aidbridge_app");
        }
        registry.add("spring.datasource.url", () -> url + "?currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password",
                () -> requiredEnvironment("AIDBRIDGE_TEST_DATABASE_PASSWORD"));
        registry.add("spring.flyway.url", () -> url + "?currentSchema=" + SCHEMA);
        registry.add("spring.flyway.user", () -> username);
        registry.add("spring.flyway.password",
                () -> requiredEnvironment("AIDBRIDGE_TEST_DATABASE_PASSWORD"));
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @BeforeAll
    void encodeTestPassword() {
        passwordHash = passwordEncoder.encode(FIXTURE_PASSWORD);
    }

    @BeforeEach
    void seedIsolatedActors() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("aidbridge_test");
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class))
                .isEqualTo(SCHEMA);

        userId = UUID.randomUUID();
        organizationId = UUID.randomUUID();
        otherOrganizationId = UUID.randomUUID();
        membershipId = UUID.randomUUID();
        email = "staff-" + userId + "@example.com";

        jdbc.update("""
                INSERT INTO users (id, full_name, email, password_hash)
                VALUES (?, ?, ?, ?)
                """, userId, "Integration Test Staff", email, passwordHash);
        jdbc.update("""
                INSERT INTO organizations (id, name, status)
                VALUES (?, ?, 'APPROVED'), (?, ?, 'APPROVED')
                """, organizationId, "Integration Organization A",
                otherOrganizationId, "Integration Organization B");
        jdbc.update("""
                INSERT INTO organization_memberships (id, organization_id, user_id)
                VALUES (?, ?, ?)
                """, membershipId, organizationId, userId);
        jdbc.update("""
                INSERT INTO membership_roles (membership_id, role)
                VALUES (?, 'VOLUNTEER')
                """, membershipId);
    }

    @Test
    void loginMeAndLogoutPersistAndInvalidateTheRealSession() throws Exception {
        Browser browser = new Browser();
        Csrf beforeLogin = csrf(browser);
        String originalCookie = browser.cookie;

        var login = browser.post("/api/auth/login", loginBody(email, FIXTURE_PASSWORD), beforeLogin);
        assertStatus(login, 200);
        assertNoStore(login);
        assertPrivateFieldsAbsent(login);
        // Login returns AuthResponse(user); /me returns the current MeResponse.
        assertThat(json(login).path("user").path("id").asText()).isEqualTo(userId.toString());
        assertThat(browser.cookie != null && !browser.cookie.equals(originalCookie)).isTrue();

        var me = browser.get("/api/auth/me");
        assertStatus(me, 200);
        assertNoStore(me);
        assertPrivateFieldsAbsent(me);
        JsonNode body = json(me);
        assertThat(body.path("userId").asText()).isEqualTo(userId.toString());
        assertThat(body.path("donorEnabled").asBoolean()).isFalse();
        assertThat(body.path("memberships").size()).isEqualTo(1);
        assertThat(body.path("memberships").get(0).path("organizationId").asText())
                .isEqualTo(organizationId.toString());
        assertThat(body.path("memberships").get(0).path("roles").get(0).asText())
                .isEqualTo("VOLUNTEER");

        assertProblem(new Browser(originalCookie).get("/api/auth/me"), 401, "UNAUTHENTICATED");

        String authenticatedCookie = browser.cookie;
        var logout = browser.post("/api/auth/logout", null, csrf(browser));
        assertStatus(logout, 204);
        assertNoStore(logout);
        assertThat(logout.body()).isEmpty();
        assertThat(browser.cookie == null).isTrue();
        assertProblem(new Browser(authenticatedCookie).get("/api/auth/me"), 401, "UNAUTHENTICATED");
        assertProblem(browser.get("/api/auth/me"), 401, "UNAUTHENTICATED");
        csrf(browser);
        assertProblem(browser.get("/api/auth/me"), 401, "UNAUTHENTICATED");
    }

    @Test
    void anonymousMeIsJson401WithoutRedirect() throws Exception {
        Browser browser = new Browser();
        var response = browser.get("/api/auth/me");
        assertProblem(response, 401, "UNAUTHENTICATED");
        assertThat(response.headers().firstValue("Location")).isEmpty();
        assertThat(browser.cookie == null).isTrue();
    }

    @Test
    void wrongPasswordAndUnknownAccountHaveTheSameGenericFailure() throws Exception {
        for (String attemptedEmail : new String[]{email, "missing-" + userId + "@example.com"}) {
            Browser browser = new Browser();
            var response = browser.post("/api/auth/login",
                    loginBody(attemptedEmail, "incorrect-password"), csrf(browser));
            assertProblem(response, 401, "INVALID_CREDENTIALS");
            assertThat(json(response).path("detail").asText()).isEqualTo("Invalid credentials");
            assertProblem(browser.get("/api/auth/me"), 401, "UNAUTHENTICATED");
        }
    }

    @Test
    void disabledUserCannotLoginWithCorrectPassword() throws Exception {
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", userId);
        Browser browser = new Browser();
        assertProblem(browser.post("/api/auth/login", loginBody(email, FIXTURE_PASSWORD), csrf(browser)),
                401, "INVALID_CREDENTIALS");
        assertProblem(browser.get("/api/auth/me"), 401, "UNAUTHENTICATED");
    }

    @Test
    void invalidLoginBodyReturnsSafeFieldErrorArray() throws Exception {
        Browser browser = new Browser();
        String marker = "PRIVATE_INVALID_EMAIL_MARKER";
        var response = browser.post("/api/auth/login",
                objectMapper.writeValueAsString(Map.of("email", marker, "password", "")), csrf(browser));
        assertProblem(response, 400, "VALIDATION_ERROR");
        JsonNode body = json(response);
        assertThat(body.path("fieldErrors").isArray()).isTrue();
        assertThat(body.path("fieldErrors").size()).isEqualTo(2);
        assertThat(body.path("fieldErrors").get(0).path("field").asText()).isEqualTo("email");
        assertThat(body.path("fieldErrors").get(0).path("code").asText()).isEqualTo("INVALID_EMAIL");
        assertThat(body.path("fieldErrors").get(1).path("field").asText()).isEqualTo("password");
        assertThat(body.path("fieldErrors").get(1).path("code").asText()).isEqualTo("NOT_BLANK");
        assertThat(response.body()).doesNotContain(marker);
        assertProblem(browser.get("/api/auth/me"), 401, "UNAUTHENTICATED");
    }

    @Test
    void malformedLoginJsonReturnsGeneric400WithoutParserDetails() throws Exception {
        Browser browser = new Browser();
        var response = browser.post("/api/auth/login", "{\"email\":\"PRIVATE_JSON_MARKER\"", csrf(browser));
        assertProblem(response, 400, "VALIDATION_ERROR");
        assertThat(response.body()).doesNotContain("PRIVATE_JSON_MARKER", "JsonParseException", "stackTrace");
    }

    @Test
    void loginRequiresAValidCsrfTokenEvenForCorrectCredentials() throws Exception {
        Browser browser = new Browser();
        assertProblem(browser.post("/api/auth/login", loginBody(email, FIXTURE_PASSWORD), null),
                403, "CSRF_INVALID");
        Csrf valid = csrf(browser);
        assertProblem(browser.post("/api/auth/login", loginBody(email, FIXTURE_PASSWORD),
                new Csrf(valid.headerName(), "invalid-token")), 403, "CSRF_INVALID");
        assertProblem(browser.get("/api/auth/me"), 401, "UNAUTHENTICATED");
    }

    @Test
    void authenticationRenewsUnderlyingCsrfTokenNotOnlyItsMaskedValue() throws Exception {
        Browser browser = new Browser();
        Csrf oldToken = csrf(browser);
        assertStatus(browser.post("/api/auth/login", loginBody(email, FIXTURE_PASSWORD), oldToken), 200);
        assertProblem(browser.post("/api/auth/logout", null, oldToken), 403, "CSRF_INVALID");
        assertStatus(browser.get("/api/auth/me"), 200);
        assertStatus(browser.post("/api/auth/logout", null, csrf(browser)), 204);
    }

    @Test
    void logoutRejectsMissingAndInvalidCsrfWithoutDestroyingTheSession() throws Exception {
        Browser browser = loggedInBrowser();
        assertProblem(browser.post("/api/auth/logout", null, null), 403, "CSRF_INVALID");
        Csrf token = csrf(browser);
        assertProblem(browser.post("/api/auth/logout", null,
                new Csrf(token.headerName(), "invalid-token")), 403, "CSRF_INVALID");
        assertStatus(browser.get("/api/auth/me"), 200);
        assertStatus(browser.post("/api/auth/logout", null, csrf(browser)), 204);
    }

    @Test
    void disablingAccountAfterLoginInvalidatesTheExistingSession() throws Exception {
        Browser browser = loggedInBrowser();
        String oldCookie = browser.cookie;
        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", userId);
        assertProblem(browser.get("/api/auth/me"), 401, "INVALID_CREDENTIALS");
        jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", userId);
        assertProblem(new Browser(oldCookie).get("/api/auth/me"), 401, "UNAUTHENTICATED");
    }

    @ParameterizedTest
    @EnumSource(value = OrganizationMembership.Status.class, names = {"INVITED", "DISABLED"})
    void membershipRevocationIsReloadedForMeAndServiceGuards(
            OrganizationMembership.Status status
    ) throws Exception {
        Browser browser = loggedInBrowser();
        Authentication authentication = authenticatedActor();
        organizationService.requireRole(authentication, organizationId, OrganizationMembership.Role.VOLUNTEER);
        jdbc.update("UPDATE organization_memberships SET status = ? WHERE id = ?", status.name(), membershipId);
        assertMeHasNoMemberships(browser);
        assertThatThrownBy(() -> organizationService.requireRole(
                authentication, organizationId, OrganizationMembership.Role.VOLUNTEER))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Organization.Status.class, names = {"PENDING", "SUSPENDED"})
    void nonApprovedOrganizationIsReloadedForMeAndServiceGuards(
            Organization.Status status
    ) throws Exception {
        Browser browser = loggedInBrowser();
        Authentication authentication = authenticatedActor();
        jdbc.update("UPDATE organizations SET status = ? WHERE id = ?", status.name(), organizationId);
        assertMeHasNoMemberships(browser);
        assertThatThrownBy(() -> organizationService.requireRole(
                authentication, organizationId, OrganizationMembership.Role.VOLUNTEER))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void revokedRoleIsNotRetainedFromTheLoginSession() throws Exception {
        Browser browser = loggedInBrowser();
        Authentication authentication = authenticatedActor();
        organizationService.requireRole(authentication, organizationId, OrganizationMembership.Role.VOLUNTEER);
        jdbc.update("""
                UPDATE membership_roles SET role = 'VERIFIER'
                WHERE membership_id = ? AND role = 'VOLUNTEER'
                """, membershipId);
        var response = browser.get("/api/auth/me");
        assertStatus(response, 200);
        JsonNode roles = json(response).path("memberships").get(0).path("roles");
        assertThat(roles.size()).isEqualTo(1);
        assertThat(roles.get(0).asText()).isEqualTo("VERIFIER");
        assertThatThrownBy(() -> organizationService.requireRole(
                authentication, organizationId, OrganizationMembership.Role.VOLUNTEER))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void platformAdminDoesNotGainMembershipOrAccessToAnotherOrganization() throws Exception {
        jdbc.update("UPDATE users SET platform_role = 'PLATFORM_ADMIN' WHERE id = ?", userId);
        Browser browser = loggedInBrowser();
        var response = browser.get("/api/auth/me");
        assertStatus(response, 200);
        assertThat(json(response).path("platformRole").asText()).isEqualTo("PLATFORM_ADMIN");
        assertThat(json(response).path("memberships").size()).isEqualTo(1);
        assertThat(json(response).path("memberships").get(0).path("organizationId").asText())
                .isEqualTo(organizationId.toString());
        Authentication authentication = authenticatedActor();
        assertThatThrownBy(() -> organizationService.requireRole(
                authentication, otherOrganizationId, OrganizationMembership.Role.ORG_ADMIN))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void registrationIsNotOpenedByTheExistingRegistrationService() throws Exception {
        Long countBefore = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        String body = objectMapper.writeValueAsString(Map.of(
                "fullName", "Unexpected Signup", "email", "signup-" + userId + "@example.com",
                "password", FIXTURE_PASSWORD));
        Browser anonymous = new Browser();
        assertProblem(anonymous.post("/api/auth/register", body, csrf(anonymous)), 401, "UNAUTHENTICATED");
        Browser authenticated = loggedInBrowser();
        int status = authenticated.post("/api/auth/register", body, csrf(authenticated)).statusCode();
        assertThat(status).isIn(403, 404, 405);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(countBefore);
    }

    @Test
    void healthIsPublicAndDoesNotExposeDatabaseDetails() throws Exception {
        var response = new Browser().get("/actuator/health");
        assertStatus(response, 200);
        assertThat(json(response).path("status").asText()).isEqualTo("UP");
        assertThat(json(response).has("components")).isFalse();
        assertThat(json(response).has("details")).isFalse();
        assertThat(response.body()).doesNotContain("jdbc:", "aidbridge_app", "password");
    }

    private Browser loggedInBrowser() throws Exception {
        Browser browser = new Browser();
        assertStatus(browser.post("/api/auth/login", loginBody(email, FIXTURE_PASSWORD), csrf(browser)), 200);
        return browser;
    }

    private Authentication authenticatedActor() {
        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, FIXTURE_PASSWORD));
    }

    private void assertMeHasNoMemberships(Browser browser) throws Exception {
        var response = browser.get("/api/auth/me");
        assertStatus(response, 200);
        assertNoStore(response);
        assertThat(json(response).path("userId").asText()).isEqualTo(userId.toString());
        assertThat(json(response).path("memberships").isArray()).isTrue();
        assertThat(json(response).path("memberships").size()).isZero();
    }

    private Csrf csrf(Browser browser) throws Exception {
        var response = browser.get("/api/auth/csrf");
        assertStatus(response, 200);
        assertNoStore(response);
        JsonNode body = json(response);
        assertThat(body.path("token").asText().isBlank()).isFalse();
        assertThat(body.path("headerName").asText()).isEqualTo("X-CSRF-TOKEN");
        for (String cookie : response.headers().allValues("Set-Cookie")) {
            if (cookie.startsWith("JSESSIONID=")) {
                String attributes = cookie.toLowerCase(java.util.Locale.ROOT);
                assertThat(attributes).contains("httponly", "samesite=lax", "path=/");
            }
        }
        return new Csrf(body.path("headerName").asText(), body.path("token").asText());
    }

    private String loginBody(String attemptedEmail, String attemptedPassword) {
        return objectMapper.writeValueAsString(Map.of("email", attemptedEmail, "password", attemptedPassword));
    }

    private JsonNode json(HttpResponse<String> response) {
        return objectMapper.readTree(response.body());
    }

    private void assertStatus(HttpResponse<String> response, int expected) {
        assertThat(response.statusCode()).isEqualTo(expected);
    }

    private void assertNoStore(HttpResponse<String> response) {
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    private void assertPrivateFieldsAbsent(HttpResponse<String> response) {
        assertThat(response.body()).doesNotContain(
                FIXTURE_PASSWORD, passwordHash, "passwordHash", "password_hash",
                "privateDataEncrypted", "private_data_encrypted", "storageKey"
        );
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertStatus(response, status);
        assertNoStore(response);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("application/problem+json");
        JsonNode body = json(response);
        assertThat(body.path("type").asText()).isEqualTo("urn:aidbridge:error:"
                + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
        assertThat(body.path("title").asText().isBlank()).isFalse();
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("code").asText()).isEqualTo(code);
        assertThat(body.path("detail").asText().isBlank()).isFalse();
        assertThat(body.path("instance").asText()).startsWith("/api/");
        assertThat(body.path("traceId").asText().isBlank()).isFalse();
        assertPrivateFieldsAbsent(response);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }

    private record Csrf(String headerName, String token) { }

    private class Browser {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        private String cookie;

        Browser() { }

        Browser(String cookie) {
            this.cookie = cookie;
        }

        HttpResponse<String> get(String path) throws Exception {
            return send(path, null, null, false);
        }

        HttpResponse<String> post(String path, String body, Csrf csrf) throws Exception {
            return send(path, body, csrf, true);
        }

        private HttpResponse<String> send(String path, String body, Csrf csrf, boolean post) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(20));
            if (cookie != null) request.header("Cookie", cookie);
            if (csrf != null) request.header(csrf.headerName(), csrf.token());
            if (post) {
                request.header("Content-Type", "application/json");
                request.POST(body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
            } else {
                request.GET();
            }
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            for (String header : response.headers().allValues("Set-Cookie")) {
                for (HttpCookie value : HttpCookie.parse(header)) {
                    if ("JSESSIONID".equals(value.getName())) {
                        cookie = value.getMaxAge() == 0 || value.getValue().isEmpty()
                                ? null : value.getName() + "=" + value.getValue();
                    }
                }
            }
            return response;
        }
    }
}
