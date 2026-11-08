package com.aidbridge.organization.repository;

import com.aidbridge.organization.entity.Organization;
import com.aidbridge.organization.entity.OrganizationMembership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {
    @EntityGraph(attributePaths = {"organization", "roles"})
    List<OrganizationMembership> findByUser_IdAndStatusAndOrganization_Status(
            UUID id,
            OrganizationMembership.Status membershipStatus,
            Organization.Status organizationStatus
    );

    @EntityGraph(attributePaths = {"organization", "roles"})
    Optional<OrganizationMembership> findByOrganization_IdAndUser_IdAndStatusAndOrganization_Status(
            UUID organizationId,
            UUID userId,
            OrganizationMembership.Status membershipStatus,
            Organization.Status organizationStatus
    );
}
