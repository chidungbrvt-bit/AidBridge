package com.aidbridge.user.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.aidbridge.user.entity.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    void mapsPersistedAccountIncludingDisabledStateAndPlatformRole() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-04T00:00:00Z");
        User user = new User("Dung", "dung@example.com", "private-password-hash");
        user.setStatus(User.Status.DISABLED);
        user.setPlatformRole(User.PlatformRole.PLATFORM_ADMIN);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", createdAt);

        var response = mapper.toResponse(user);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.fullName()).isEqualTo("Dung");
        assertThat(response.email()).isEqualTo("dung@example.com");
        assertThat(response.status()).isEqualTo(User.Status.DISABLED);
        assertThat(response.platformRole()).isEqualTo(User.PlatformRole.PLATFORM_ADMIN);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void serializesOnlyResponseFieldsWithoutCredentials() {
        User user = new User("Minh", "minh@example.com", "private-password-hash");
        ReflectionTestUtils.setField(user, "id", UUID.fromString("80fdca37-9212-47a1-b286-ec50a3dc420d"));
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-11-04T00:00:00Z"));
        JsonMapper jsonMapper = JsonMapper.builder().build();

        String json = jsonMapper.writeValueAsString(mapper.toResponse(user));

        assertThat(jsonMapper.readTree(json)).isEqualTo(jsonMapper.readTree("""
                {
                  "id": "80fdca37-9212-47a1-b286-ec50a3dc420d",
                  "fullName": "Minh",
                  "email": "minh@example.com",
                  "status": "ACTIVE",
                  "platformRole": "USER",
                  "createdAt": "2026-11-04T00:00:00Z"
                }
                """));
        assertThat(json).doesNotContain("password", "private-password-hash");
    }

    @Test
    void rejectsNullUserInsteadOfCreatingAnEmptyAccountResponse() {
        assertThatNullPointerException()
                .isThrownBy(() -> mapper.toResponse(null))
                .withMessage("User must not be null");
    }
}
