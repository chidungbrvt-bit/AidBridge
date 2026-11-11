package com.aidbridge.verification.entity;

import com.aidbridge.casefile.entity.AidCase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Verification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aid_case_id", nullable = false, updatable = false)
    private AidCase aidCase;

    @Column(name = "verifier_id", nullable = false, updatable = false)
    private UUID verifierId;

    @Column(length = 20, nullable = false, updatable = false)
    private String decision;

    @Column(name = "notes", columnDefinition = "text", updatable = false)
    private String notes;

    @Column(name = "reviewed_case_version", updatable = false)
    private Long reviewedCaseVersion;

    @Generated
    @Column(name = "verified_at", insertable = false, updatable = false, nullable = false)
    private Instant verifiedAt;

    private Verification(
            AidCase aidCase, UUID verifierId, String decision, long reviewedCaseVersion, String notes
    ) {
        this.aidCase = aidCase;
        this.verifierId = verifierId;
        this.decision = decision;
        this.reviewedCaseVersion = reviewedCaseVersion;
        this.notes = notes;
    }

    public static Verification approved(AidCase aidCase, UUID verifierId, long reviewedCaseVersion) {
        return new Verification(aidCase, verifierId, "APPROVED", reviewedCaseVersion, null);
    }

    public static Verification approved(
            AidCase aidCase, UUID verifierId, long reviewedCaseVersion, String notes
    ) {
        return new Verification(aidCase, verifierId, "APPROVED", reviewedCaseVersion, notes);
    }

    public static Verification rejected(
            AidCase aidCase, UUID verifierId, long reviewedCaseVersion, String notes
    ) {
        return new Verification(aidCase, verifierId, "REJECTED", reviewedCaseVersion, notes);
    }

    public static Verification changesRequested(
            AidCase aidCase, UUID verifierId, long reviewedCaseVersion, String notes
    ) {
        return new Verification(aidCase, verifierId, "CHANGES_REQUESTED", reviewedCaseVersion, notes);
    }
}
