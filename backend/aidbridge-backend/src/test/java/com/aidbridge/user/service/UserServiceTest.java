package com.aidbridge.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aidbridge.user.entity.User;
import com.aidbridge.user.exception.UserNotFoundException;
import com.aidbridge.user.mapper.UserMapper;
import com.aidbridge.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, new UserMapper());
    }

    @Test
    void returnsAccountResponseByIdWithoutChangingDisabledState() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-04T00:00:00Z");
        User user = new User("Dung", "dung@example.com", "encoded-password");
        user.setStatus(User.Status.DISABLED);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        when(repository.findById(id)).thenReturn(Optional.of(user));

        var response = service.getUserById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.email()).isEqualTo("dung@example.com");
        assertThat(response.status()).isEqualTo(User.Status.DISABLED);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void normalizesEmailBeforeLookupAndReturnsAccountResponse() {
        User user = new User("Khoa", "khoa@example.com", "encoded-password");
        when(repository.findByEmailIgnoreCase("khoa@example.com")).thenReturn(Optional.of(user));

        var response = service.getUserByEmail("  KHOA@EXAMPLE.COM  ");

        assertThat(response.email()).isEqualTo("khoa@example.com");
        assertThat(response.fullName()).isEqualTo("Khoa");
    }

    @Test
    void reportsMissingIdWithoutIncludingAccountIdentifiers() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserById(id))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void reportsMissingEmailWithoutIncludingAccountIdentifiers() {
        when(repository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserByEmail("missing@example.com"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void rejectsNullIdBeforeQueryingRepository() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getUserById(null))
                .withMessage("User ID must not be null");
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void rejectsMissingEmailBeforeQueryingRepository(String email) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getUserByEmail(email))
                .withMessage("Email must not be blank");
        verifyNoInteractions(repository);
    }
}
