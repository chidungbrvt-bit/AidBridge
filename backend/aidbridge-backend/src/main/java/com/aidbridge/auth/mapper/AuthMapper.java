package com.aidbridge.auth.mapper;

import com.aidbridge.auth.dto.response.MeResponse;
import com.aidbridge.organization.entity.OrganizationMembership;
import com.aidbridge.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class AuthMapper {
    public MeResponse toMeResponse (
            User user,
            List<OrganizationMembership> memberships,
            boolean donorEnabled
    ) {
        Objects.requireNonNull(user, "User must be not null");
        Objects.requireNonNull(memberships, "Memberships must be not null");
        List<MeResponse.MembershipResponse> membershipResponses = memberships.stream()
                .map(this::toMembershipResponse).toList();
        return new MeResponse(
                user.getId(),
                user.getFullName(),
                user.getStatus(),
                user.getPlatformRole(),
                donorEnabled,
                membershipResponses
        );
    }

    private MeResponse.MembershipResponse toMembershipResponse(OrganizationMembership membership) {
        List<String> roles = membership.getRoles().stream().map(OrganizationMembership.Role::name)
                .sorted().toList();
        return new MeResponse.MembershipResponse(
                membership.getOrganization().getId(),
                membership.getOrganization().getName(),
                roles,
                membership.getStatus().name()
        );
    }
}
