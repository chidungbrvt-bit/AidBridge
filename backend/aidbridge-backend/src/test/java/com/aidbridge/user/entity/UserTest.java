package com.aidbridge.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class UserTest {

    @Test
    void normalizesIdentityAndUsesLeastPrivilegedDefaults() {
        User user = new User("  Dung Nguyen  ", "  DUNG@example.com  ", "encoded-password");

        assertThat(user.getFullName()).isEqualTo("Dung Nguyen");
        assertThat(user.getEmail()).isEqualTo("dung@example.com");
        assertThat(user.getStatus()).isEqualTo(User.Status.ACTIVE);
        assertThat(user.getPlatformRole()).isEqualTo(User.PlatformRole.USER);
        user.setEmail("  NEW@example.com  ");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void rejectsMissingIdentityInvalidEmailAndMissingAccountState() {
        User user = new User(" ", "invalid-email", " ");
        user.setStatus(null);
        user.setPlatformRole(null);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(user))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("fullName", "email", "passwordHash", "status", "platformRole");
        }
    }

    @Test
    void neverSerializesPasswordHash() {
        User user = new User("Dung", "dung@example.com", "private-encoded-password");

        String json = JsonMapper.builder().build().writeValueAsString(user);

        assertThat(json).contains("dung@example.com")
                .doesNotContain("passwordHash", "password_hash", "private-encoded-password");
    }
}
