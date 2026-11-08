package com.aidbridge.auth.dto.response;

import com.aidbridge.user.entity.User;

import java.util.List;
import java.util.UUID;

public record MeResponse (
        UUID userId,
        String fullName,
        User.Status status,
        User.PlatformRole platformRole,
        boolean donorEnabled,
        List<MembershipResponse> memberships
) {
    public record MembershipResponse(
            UUID organizationId,
            List<String> roles,
            String status
    ) {
    }
}
