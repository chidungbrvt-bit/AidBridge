package com.aidbridge.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aidbridge.auth.controller.AuthController;
import com.aidbridge.auth.mapper.AuthMapper;
import com.aidbridge.auth.security.CustomUserDetailsService;
import com.aidbridge.auth.security.SecurityConfig;
import com.aidbridge.auth.service.AuthService;
import com.aidbridge.organization.repository.OrganizationMembershipRepository;
import com.aidbridge.user.entity.User;
import com.aidbridge.user.exception.UserNotFoundException;
import com.aidbridge.user.mapper.UserMapper;
import com.aidbridge.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Production MVC/controller/service/filter chain; repository access is mocked, no DB required. */
@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, CustomUserDetailsService.class,
        AuthService.class, UserMapper.class, AuthMapper.class})
class GlobalExceptionHandlerTest {

    private static final String EMAIL = "staff@example.com";
    private static final String PASSWORD = "Fixture-only-password";
    private static final String PRIVATE_MARKER = "PRIVATE_INTERNAL_MARKER";
    private static String passwordHash;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private OrganizationMembershipRepository membershipRepository;
    private UUID userId;

    @Test
    void businessConflictAndDatabaseOutageHaveSafeStableCodes() throws Exception {
        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenThrow(com.aidbridge.shared.exception.BusinessException.stale());
        expectProblem(mvc.perform(get("/api/auth/me").with(user(EMAIL))),
                409,"VERSION_CONFLICT","/api/auth/me");
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException(PRIVATE_MARKER))
                .when(userRepository).findByEmailIgnoreCase(EMAIL);
        expectProblem(mvc.perform(get("/api/auth/me").with(user(EMAIL))),
                503,"DEPENDENCY_UNAVAILABLE","/api/auth/me");
        org.mockito.Mockito.doThrow(new org.springframework.transaction.CannotCreateTransactionException(PRIVATE_MARKER))
                .when(userRepository).findByEmailIgnoreCase(EMAIL);
        expectProblem(mvc.perform(get("/api/auth/me").with(user(EMAIL))),
                503,"DEPENDENCY_UNAVAILABLE","/api/auth/me");
    }

    @BeforeAll
    static void encodeFixturePassword() {
        passwordHash = new BCryptPasswordEncoder(12).encode(PASSWORD);
    }

    @BeforeEach
    void provideActiveAccount() {
        userId = UUID.randomUUID();
        User account = new User("Test Staff", EMAIL, passwordHash);
        ReflectionTestUtils.setField(account, "id", userId);
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account));
        when(membershipRepository.findByUser_IdAndStatusAndOrganization_Status(any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void validLoginStillReturnsAuthResponseAndMeReturnsMeResponse() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        mvc.perform(get("/api/auth/me").with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.memberships").isArray())
                .andExpect(jsonPath("$.donorEnabled").value(false));
    }

    @Test
    void validationUsesFieldAndCodeArrayWithoutEchoingRejectedInput() throws Exception {
        var result = expectProblem(mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", PRIVATE_MARKER, "password", "")))),
                400, "VALIDATION_ERROR", "/api/auth/login")
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[1].code").value("NOT_BLANK"))
                .andReturn();
        assertSafe(result.getResponse().getContentAsString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"email\":\"PRIVATE_INTERNAL_MARKER\"",
            "{\"email\":{\"secret\":\"PRIVATE_INTERNAL_MARKER\"},\"password\":\"example\"}"})
    void malformedJsonAndWrongFieldTypesHaveSafe400Envelopes(String body) throws Exception {
        expectProblem(mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body)),
                400, "VALIDATION_ERROR", "/api/auth/login");
    }

    @Test
    void authenticationFailureIsGenericAndDoesNotEchoExceptionDetails() throws Exception {
        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenThrow(new BadCredentialsException(PRIVATE_MARKER));
        expectProblem(mvc.perform(get("/api/auth/me").with(user(EMAIL))),
                401, "INVALID_CREDENTIALS", "/api/auth/me")
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void accessDenialFromServiceGetsTheSameSafe403Contract() throws Exception {
        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenThrow(new AccessDeniedException(PRIVATE_MARKER));
        expectProblem(mvc.perform(get("/api/auth/me").with(user(EMAIL))),
                403, "FORBIDDEN", "/api/auth/me");
    }

    @Test
    void missingUserGetsAComplete404Envelope() throws Exception {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenThrow(new UserNotFoundException());
        expectProblem(mvc.perform(get("/api/auth/me").with(user(EMAIL))),
                404, "RESOURCE_NOT_FOUND", "/api/auth/me");
    }

    @Test
    void unexpectedFailureNeverExposesItsMessageOrStackTrace() throws Exception {
        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenThrow(new IllegalStateException(PRIVATE_MARKER));
        expectProblem(mvc.perform(get("/api/auth/me").with(user(EMAIL))),
                500, "INTERNAL_SERVER_ERROR", "/api/auth/me");
    }

    @Test
    void unsupportedMethodPreservesAllowHeaderAndAddsSafeEnvelope() throws Exception {
        expectProblem(mvc.perform(patch("/api/auth/me").with(user(EMAIL)).with(csrf())),
                405, "METHOD_NOT_ALLOWED", "/api/auth/me")
                .andExpect(header().string("Allow", containsString("GET")));
    }

    @Test
    void unsupportedContentTypeUsesTheSameEnvelope() throws Exception {
        expectProblem(mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.TEXT_PLAIN).content(PRIVATE_MARKER)),
                415, "UNSUPPORTED_MEDIA_TYPE", "/api/auth/login");
    }

    @Test
    void frameworkStatusExceptionDoesNotExposeItsReason() throws Exception {
        when(userRepository.findByEmailIgnoreCase(EMAIL))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, PRIVATE_MARKER));
        expectProblem(mvc.perform(get("/api/auth/me").with(user(EMAIL))),
                409, "CONFLICT", "/api/auth/me");
    }

    private ResultActions expectProblem(ResultActions result, int status, String code, String path)
            throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.type").value("urn:aidbridge:error:"
                        + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
        assertSafe(result.andReturn().getResponse().getContentAsString());
        return result;
    }

    private void assertSafe(String body) {
        assertThat(body).doesNotContain(PRIVATE_MARKER, PASSWORD, passwordHash,
                "passwordHash", "password_hash", "IllegalStateException", "java.lang.");
    }
}
