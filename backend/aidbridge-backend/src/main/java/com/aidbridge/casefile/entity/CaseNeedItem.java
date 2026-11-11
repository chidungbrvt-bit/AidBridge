package com.aidbridge.casefile.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "case_need_items") // V6 uses a partial unique index for current (unretired) rows.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseNeedItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "aid_case_id",
            nullable = false,
            updatable = false
    )
    private AidCase aidCase;

    @NotBlank
    @Size(max = 120)
    @Column(name = "item_name", nullable = false, length = 120)
    private String itemName;

    @NotBlank
    @Size(max = 40)
    @Column(nullable = false, length = 40)
    private String unit;

    @Min(1)
    @Max(1_000_000)
    @Column(name = "requested_quantity", nullable = false)
    private int requestedQuantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "retired_at_version")
    private Long retiredAtVersion;

    public void retire(long nextVersion) { this.retiredAtVersion = nextVersion; }

    public CaseNeedItem(
            AidCase aidCase,
            String itemName,
            String unit,
            int requestedQuantity
    ) {
        this.aidCase = Objects.requireNonNull(aidCase);
        this.itemName = itemName == null ? null : itemName.strip();
        this.unit = unit == null ? null : unit.strip();
        this.requestedQuantity = requestedQuantity;
    }
}
