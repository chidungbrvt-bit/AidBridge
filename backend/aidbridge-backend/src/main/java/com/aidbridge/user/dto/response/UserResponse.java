package com.aidbridge.user.dto.response;

import com.aidbridge.user.entity.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        User.Status status,
        User.PlatformRole platformRole,
        Instant createdAt
) {
}
