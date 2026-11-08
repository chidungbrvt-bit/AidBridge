package com.aidbridge.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 150)
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String email;

    @JsonIgnore
    @NotBlank
    @Size(max = 255)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false, length = 30)
    private PlatformRole platformRole = PlatformRole.USER;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** The caller must encode the password before constructing a user. */
    public User(String fullName, String email, String passwordHash) {
        setFullName(fullName);
        setEmail(email);
        setPasswordHash(passwordHash);
    }

    public void setFullName(String fullName) {
        this.fullName = fullName == null ? null : fullName.strip();
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setPlatformRole(PlatformRole platformRole) {
        this.platformRole = platformRole;
    }

    public enum Status {
        ACTIVE,
        DISABLED
    }

    public enum PlatformRole {
        USER,
        PLATFORM_ADMIN
    }
}
