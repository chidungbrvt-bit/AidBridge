package com.aidbridge.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aidbridge.user.entity.User;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.clean-disabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "AIDBRIDGE_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("AIDBRIDGE_TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("AIDBRIDGE_TEST_DATABASE_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("AIDBRIDGE_TEST_DATABASE_PASSWORD"));
    }

    @Test
    void persistsIdentityDefaultsAndTimestampsAgainstMigration() {
        User saved = repository.saveAndFlush(new User("Dung", "Dung@example.com", "encoded-password"));
        UUID id = saved.getId();
        entityManager.clear();

        User reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("dung@example.com");
        assertThat(reloaded.getStatus()).isEqualTo(User.Status.ACTIVE);
        assertThat(reloaded.getPlatformRole()).isEqualTo(User.PlatformRole.USER);
        assertThat(reloaded.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsExistingMixedCaseEmailAndReturnsEmptyForUnknownEmail() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, full_name, email, password_hash)
                VALUES (?, ?, ?, ?)
                """, id, "Khoa", "Khoa@Example.com", "encoded-password");

        assertThat(repository.findByEmailIgnoreCase("KHOA@EXAMPLE.COM"))
                .get().extracting(User::getId).isEqualTo(id);
        assertThat(repository.existsByEmailIgnoreCase("khoa@example.com")).isTrue();
        assertThat(repository.findByEmailIgnoreCase("missing@example.com")).isEmpty();
        assertThat(repository.existsByEmailIgnoreCase("missing@example.com")).isFalse();
    }

    @Test
    void rejectsDuplicateEmailRegardlessOfCaseAtDatabaseLevel() {
        jdbc.update("""
                INSERT INTO users (full_name, email, password_hash)
                VALUES (?, ?, ?)
                """, "Existing user", "Dung@Example.com", "encoded-password");

        assertThatThrownBy(() -> repository.saveAndFlush(
                new User("Duplicate user", "DUNG@example.com", "encoded-password")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsDisabledStateAndUpdatesTimestampWithoutChangingCreationTime() {
        User user = repository.saveAndFlush(new User("Minh", "minh@example.com", "encoded-password"));
        // Set an old timestamp in SQL so this check does not depend on clock precision or sleeps.
        jdbc.update("UPDATE users SET updated_at = '2000-01-01T00:00:00Z' WHERE id = ?", user.getId());
        entityManager.clear();
        User existing = repository.findById(user.getId()).orElseThrow();
        var createdAt = existing.getCreatedAt();
        var previousUpdatedAt = existing.getUpdatedAt();
        existing.setStatus(User.Status.DISABLED);
        repository.flush();
        entityManager.clear();

        User reloaded = repository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(User.Status.DISABLED);
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reloaded.getUpdatedAt()).isAfter(previousUpdatedAt);
    }
}
