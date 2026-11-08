package com.aidbridge.organization.repository;

import com.aidbridge.organization.entity.Organization;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository
        extends JpaRepository<Organization, UUID> {
}