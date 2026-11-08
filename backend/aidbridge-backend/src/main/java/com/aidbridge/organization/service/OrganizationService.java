package com.aidbridge.organization.service;

import com.aidbridge.organization.entity.Organization;
import com.aidbridge.organization.entity.OrganizationMembership;
import com.aidbridge.organization.repository.OrganizationMembershipRepository;
import com.aidbridge.user.entity.User;
import com.aidbridge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizationService {
    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;

    public List<OrganizationMembership> currentMemberships(Authentication authentication) {
        User user = currentUser(authentication);
        return membershipRepository.findByUser_IdAndStatusAndOrganization_Status(
                user.getId(), OrganizationMembership.Status.ACTIVE, Organization.Status.APPROVED);
    }

    public User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BadCredentialsException("Invalid credentials");
        }
        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (user.getStatus() != User.Status.ACTIVE) throw new BadCredentialsException("Invalid credentials");
        return user;
    }
    public OrganizationMembership requireRole(
            Authentication authentication,
            UUID organizationId,
            OrganizationMembership.Role requiredRole
    ) {
        // 1. Require an authenticated user.
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BadCredentialsException("Invalid credentials");
        }

        // 2. Reload the current account state.
        User user = userRepository
                .findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() ->
                        new BadCredentialsException("Invalid credentials")
                );

        if (user.getStatus() != User.Status.ACTIVE) {
            throw new BadCredentialsException("Invalid credentials");
        }

        // 3. Require active membership in an approved organization.
        OrganizationMembership membership = membershipRepository
                .findByOrganization_IdAndUser_IdAndStatusAndOrganization_Status(
                        organizationId,
                        user.getId(),
                        OrganizationMembership.Status.ACTIVE,
                        Organization.Status.APPROVED
                )
                .orElseThrow(() ->
                        new AccessDeniedException("Access is denied")
                );

        // 4. Require the role needed for this operation.
        if (requiredRole == null
                || !membership.getRoles().contains(requiredRole)) {
            throw new AccessDeniedException("Access is denied");
        }

        return membership;
    }
}
