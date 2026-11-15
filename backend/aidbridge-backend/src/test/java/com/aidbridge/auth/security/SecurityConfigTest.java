package com.aidbridge.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aidbridge.user.entity.User;
import com.aidbridge.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@Import({SecurityConfig.class, CustomUserDetailsService.class, SecurityConfigTest.ProbeController.class})
class SecurityConfigTest {

    private static String passwordHash;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SessionAuthenticationStrategy sessionAuthenticationStrategy;
    @Autowired private SecurityContextRepository securityContextRepository;
    @Autowired private CsrfTokenRepository csrfTokenRepository;

    @MockitoBean private UserRepository userRepository;

    @BeforeAll
    static void encodeFixturePassword() {
        passwordHash = new BCryptPasswordEncoder(12).encode("fixture-password");
    }

    @Test
    void anonymousPrivateRequestGetsJson401WithoutRedirectOrSavedSession() throws Exception {
        var result = mvc.perform(get("/api/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.instance").value("/api/protected"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Location"))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/campaigns", "/api/campaigns/example", "/api/public/cases/0xabababababababababababababababababababababababababababababababab/audit", "/actuator/health"})
    void allowsOnlyDocumentedPublicGetRoutes(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void legacyCampaignAuditRouteIsNotPublic() throws Exception {
        mvc.perform(get("/api/campaigns/example/audit-trail"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void otherAuthRoutesRemainProtected() throws Exception {
        mvc.perform(get("/api/auth/internal"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/login", "/api/auth/logout", "/api/protected"})
    void rejectsMutationsWithoutCsrfIncludingLoginAndLogout(String path) throws Exception {
        mvc.perform(post(path))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void rejectsInvalidCsrfToken() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void permitsLoginRouteWithValidCsrf() throws Exception {
        // This is a route probe, not an implementation of the future JSON login controller.
        mvc.perform(post("/api/auth/login").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void authenticatesPrivateRoutesAndEnforcesCsrfForAuthenticatedMutations() throws Exception {
        mvc.perform(get("/api/protected").with(user("staff")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/protected").with(user("staff")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/protected").with(user("staff")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void methodSecurityRejectsWrongPlatformRoleWithJson403() throws Exception {
        mvc.perform(get("/api/admin").with(user("staff").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/admin").with(user("admin").roles("PLATFORM_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void publicReadDoesNotPermitAnonymousWrites() throws Exception {
        mvc.perform(post("/api/campaigns").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void httpBasicAndCrossOriginRequestsDoNotOpenAccess() throws Exception {
        mvc.perform(get("/api/protected").with(httpBasic("staff", "password")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(options("/api/protected")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void frameworkChecksPasswordHashAndClearsAuthenticationCredentials() {
        stubUser(User.Status.ACTIVE);

        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("  DUNG@EXAMPLE.COM  ", "fixture-password"));

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo("dung@example.com");
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "FACTOR_PASSWORD");
        assertThat(passwordEncoder.matches("fixture-password", passwordHash)).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", passwordHash)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dung@example.com", "missing@example.com"})
    void rejectsWrongPasswordAndUnknownAccount(String email) {
        if (email.equals("dung@example.com")) {
            stubUser(User.Status.ACTIVE);
        }
        assertThatThrownBy(() -> authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsDisabledAccountEvenWithCorrectPassword() {
        stubUser(User.Status.DISABLED);
        assertThatThrownBy(() -> authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("dung@example.com", "fixture-password")))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    void sessionStrategyRotatesIdAndCsrfThenLogoutInvalidatesSession() throws Exception {
        stubUser(User.Status.ACTIVE);
        var bootstrap = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        var bootstrapJson = objectMapper.readTree(bootstrap.getResponse().getContentAsString());
        MockHttpSession session = (MockHttpSession) bootstrap.getRequest().getSession(false);
        String oldId = session.getId();
        String headerName = bootstrapJson.get("headerName").asText();
        String oldToken = bootstrapJson.get("token").asText();
        var loginRequest = new MockHttpServletRequest();
        session.setNew(false);
        loginRequest.setSession(session);
        loginRequest.setRequestedSessionId(oldId);
        loginRequest.setRequestedSessionIdValid(true);
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("dung@example.com", "fixture-password"));

        // Exercise the beans that the future JSON login controller must explicitly invoke.
        sessionAuthenticationStrategy.onAuthentication(authentication, loginRequest, new MockHttpServletResponse());
        assertThat(session.getId()).isNotEqualTo(oldId);
        assertThat(csrfTokenRepository.loadToken(loginRequest)).isNull();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextRepository.saveContext(context, loginRequest, new MockHttpServletResponse());

        mvc.perform(get("/api/protected").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session).header(headerName, oldToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(session.isInvalid()).isFalse();
        var refreshed = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        String newToken = objectMapper.readTree(refreshed.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/logout").session(session).header(headerName, newToken))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/protected").cookie(new Cookie("JSESSIONID", oldId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresAnAuthenticatedSession() throws Exception {
        mvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private void stubUser(User.Status status) {
        User user = new User("Dung", "dung@example.com", passwordHash);
        user.setStatus(status);
        when(userRepository.findByEmailIgnoreCase("dung@example.com")).thenReturn(Optional.of(user));
    }

    /** Test-only HTTP probes. Production AuthController remains unchanged. */
    @TestComponent
    @RestController
    static class ProbeController {

        @GetMapping("/api/auth/csrf")
        Map<String, String> csrfToken(HttpServletRequest request, HttpServletResponse response) {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            response.setHeader("Cache-Control", "no-store");
            return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
        }

        @GetMapping({"/api/protected", "/api/auth/internal", "/api/campaigns", "/api/campaigns/{id}",
                "/api/public/cases/{publicCaseRef}/audit", "/actuator/health"})
        String read() {
            return "ok";
        }

        @PostMapping({"/api/auth/login", "/api/protected", "/api/campaigns"})
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void write() {
        }

        @GetMapping("/api/admin")
        @PreAuthorize("hasRole('PLATFORM_ADMIN')")
        String admin() {
            return "ok";
        }
    }
}
