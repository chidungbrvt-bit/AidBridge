package com.aidbridge.auth.dto.response;

import com.aidbridge.user.entity.User;
import java.time.Instant;
import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String fullName,
        String email,
        User.Status status,
        Instant createdAt,
        Instant updatedAt
) {
}