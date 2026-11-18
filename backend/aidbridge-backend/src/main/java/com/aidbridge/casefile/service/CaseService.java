package com.aidbridge.casefile.service;

import com.aidbridge.audit.service.LocalAuditService;
import com.aidbridge.casefile.dto.request.*;
import com.aidbridge.casefile.dto.response.*;
import com.aidbridge.casefile.entity.*;
import com.aidbridge.casefile.mapper.CaseMapper;
import com.aidbridge.casefile.repository.*;
import com.aidbridge.organization.entity.OrganizationMembership;
import com.aidbridge.organization.service.OrganizationService;
import com.aidbridge.shared.exception.BusinessException;
import com.aidbridge.shared.idempotency.IdempotencyService;
import com.aidbridge.shared.idempotency.IdempotencyService.Result;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaseService {
    private final OrganizationService organizations;
    private final AidCaseRepository cases;
    private final BeneficiaryRepository beneficiaries;
    private final CaseNeedItemRepository needs;
    private final ConsentRepository consents;
    private final EvidenceRepository evidence;
    private final EvidenceService evidenceService;
    private final EvidenceFixtureCatalog fixtures;
    private final LocalAuditService audit;
    private final IdempotencyService idempotency;
    private final CaseMapper mapper;

    @Transactional
    public Result<CaseDetailResponse> create(Authentication auth, String key, CreateCaseRequest request) {
        var member = organizations.requireRole(auth, request.organizationId(), OrganizationMembership.Role.VOLUNTEER);
        UUID org = member.getOrganization().getId(), actor = member.getUser().getId();
        return idempotency.execute(actor, org, "POST:/api/cases", key, request, CaseDetailResponse.class, () -> {
            validateNeeds(request.needs());
            text(request.consent().scope(),false);
            if (request.consent().capturedAt().isAfter(Instant.now())) throw BusinessException.invalid();
            if (request.supersedesCaseId() != null)
                cases.findByIdAndOrganization_Id(request.supersedesCaseId(), org).orElseThrow(BusinessException::missing);
            String title = text(request.internalTitle(), false);
            String publicTitle = text(request.publicTitle(), true), summary = text(request.publicSummary(), true);
            publicConsent(request.consent().publicSummaryAllowed(), publicTitle, summary);
            if (new HashSet<>(request.evidenceFixtureKeys()).size() != request.evidenceFixtureKeys().size())
                throw BusinessException.invalid();
            var selected = request.evidenceFixtureKeys().stream().map(k -> fixtures.resolve(org, k)).toList();
            Beneficiary b = beneficiaries.saveAndFlush(new Beneficiary(member.getOrganization(),
                    "BEN-" + UUID.randomUUID(), Beneficiary.BeneficiaryType.HOUSEHOLD,
                    text(request.beneficiaryAlias(), false), member.getUser()));
            UUID consentId = consents.append(b.getId(), org, actor, request.consent());
            AidCase c = new AidCase(member.getOrganization(), b, member.getUser(), "CASE-" + UUID.randomUUID(),
                    title, "", request.priority(), null);
            c.submitInitial(consentId, text(request.approximateArea(), false), publicTitle, summary, request.supersedesCaseId());
            cases.saveAndFlush(c);
            insertNeeds(c, request.needs());
            for (var fixture : selected) evidenceService.append(c.getId(), org, actor, fixture, null, null, c.getVersion());
            return finish(c, actor, "CASE_CREATED", 201, "/api/cases/" + c.getId());
        });
    }

    public CaseDetailResponse get(Authentication auth, UUID id) { return mapper.detail(scoped(auth, id, false).aidCase()); }

    public CasePage list(Authentication auth, UUID org, AidCase.Status status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw BusinessException.invalid();
        organizations.currentMemberships(auth).stream()
                .filter(m -> m.getOrganization().getId().equals(org) && !m.getRoles().isEmpty())
                .findFirst().orElseThrow(BusinessException::missing);
        var pageable = PageRequest.of(page, size, Sort.by("createdAt", "id"));
        var result = status == null ? cases.findAllByOrganization_Id(org, pageable)
                : cases.findAllByOrganization_IdAndStatus(org, status, pageable);
        return new CasePage(result.stream().map(mapper::summary).toList(), page, size, result.getTotalElements());
    }

    @Transactional
    public Result<CaseDetailResponse> patch(Authentication auth, UUID id, String key, PatchCaseRequest request) {
        Access access = scoped(auth, id, true);
        creator(access);
        AidCase c = access.aidCase();
        return idempotency.execute(access.actor(), access.org(), "PATCH:/api/cases/" + id, key, request,
                CaseDetailResponse.class, () -> {
            editable(c, request.expectedVersion());
            if (request.internalTitle() == null && request.publicTitle() == null && request.publicSummary() == null
                    && request.approximateArea() == null && request.priority() == null && request.needs() == null)
                throw BusinessException.invalid();
            var consent = requireConsent(c);
            String title = request.publicTitle() == null ? c.getPublicTitle() : text(request.publicTitle(), true);
            String summary = request.publicSummary() == null ? c.getPublicSummary() : text(request.publicSummary(), true);
            publicConsent(consent.publicSummaryAllowed(), title, summary);
            if (request.needs() != null) {
                validateNeeds(request.needs());
                var previous = needs.findByAidCase_IdAndAidCase_Organization_IdAndRetiredAtVersionIsNullOrderByCreatedAtAscIdAsc(id,access.org());
                previous.forEach(n -> n.retire(c.getVersion() + 1));
                needs.flush();
                insertNeeds(c, request.needs());
            }
            c.edit(request.internalTitle() == null ? c.getInternalTitle() : text(request.internalTitle(), false),
                    title, summary, request.approximateArea() == null ? c.getApproximateArea() : text(request.approximateArea(), false),
                    request.priority() == null ? c.getPriority() : request.priority());
            return finish(c, access.actor(), "CASE_UPDATED", 200, null);
        });
    }

    @Transactional
    public Result<CaseDetailResponse> resubmit(Authentication auth, UUID id, String key, ResubmitCaseRequest request) {
        Access access = scoped(auth, id, true);
        creator(access);
        AidCase c = access.aidCase();
        return idempotency.execute(access.actor(),access.org(),"POST:/api/cases/"+id+"/resubmit", key, request,
                CaseDetailResponse.class, () -> {
            editable(c, request.expectedVersion());
            if (c.getStatus() != AidCase.Status.CHANGES_REQUESTED) throw BusinessException.state();
            requireConsent(c);
            if (evidence.current(id,access.org()).isEmpty()
                    || needs.findByAidCase_IdAndAidCase_Organization_IdAndRetiredAtVersionIsNullOrderByCreatedAtAscIdAsc(id,access.org()).isEmpty())
                throw BusinessException.invalid();
            if (!audit.changedAfterReview(id,access.org())) throw BusinessException.state();
            c.resubmit();
            return finish(c, access.actor(), "CASE_RESUBMITTED", 200, null);
        });
    }

    public List<EvidenceFixtureResponse> fixtureList(Authentication auth, UUID org) {
        if (organizations.currentMemberships(auth).stream()
                .noneMatch(m -> m.getOrganization().getId().equals(org) && !m.getRoles().isEmpty()))
            throw BusinessException.missing();
        return fixtures.list(org);
    }

    @Transactional
    public Result<EvidenceVersionResponse> appendEvidence(Authentication auth, UUID id, String key, EvidenceVersionRequest request) {
        Access access = scoped(auth, id, true);
        creator(access);
        AidCase c = access.aidCase();
        return idempotency.execute(access.actor(),access.org(),"POST:/api/cases/"+id+"/evidence-versions",key,request,
                EvidenceVersionResponse.class, () -> {
            editable(c,request.expectedCaseVersion());
            requireConsent(c);
            var fixture = fixtures.resolve(access.org(),request.fixtureKey());
            if (request.evidenceFileId() == null && evidence.current(id,access.org()).size() >= 10)
                throw BusinessException.invalid();
            var result = evidenceService.append(id, access.org(), access.actor(), fixture,
                    request.evidenceFileId(), request.supersedesVersionId(), c.getVersion()+1);
            c.evidenceChanged();
            finish(c,access.actor(),"EVIDENCE_VERSION_CREATED",200,null);
            return new Result<>(result,201,"/api/evidence-versions/"+result.id()+"/content",false);
        });
    }

    public record Content(byte[] bytes, String filename, String mimeType) { }

    @Transactional
    public Content content(Authentication auth, UUID versionId) {
        var memberships = organizations.currentMemberships(auth);
        var ids = memberships.stream().filter(m -> !m.getRoles().isEmpty()).map(m -> m.getOrganization().getId()).toList();
        var stored = evidence.scoped(versionId, ids).orElseThrow(BusinessException::missing);
        Access access = scoped(auth,stored.caseId(),false);
        return readContent(stored, access.actor(), Map.of("evidenceVersionId", versionId));
    }

    @Transactional
    public Content contentForDispute(UUID organizationId, UUID caseId, UUID actorId, UUID disputeId, UUID versionId) {
        var stored = evidence.scoped(versionId, List.of(organizationId))
                .filter(version -> caseId.equals(version.caseId()))
                .orElseThrow(BusinessException::missing);
        return readContent(stored, actorId, Map.of("evidenceVersionId", versionId, "disputeId", disputeId));
    }

    private Content readContent(EvidenceRepository.StoredVersion stored, UUID actorId, Map<String, UUID> context) {
        UUID versionId = stored.metadata().id();
        if (stored.fixtureKey() == null) throw BusinessException.missing();
        var fixture = fixtures.resolve(stored.orgId(), stored.fixtureKey());
        if (!fixture.hash().equals(stored.metadata().sha256()) || fixture.bytes().length != stored.metadata().byteSize())
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE","Evidence content is unavailable");
        audit.append(stored.orgId(),stored.caseId(),actorId,versionId,"EVIDENCE_CONTENT_READ",context);
        return new Content(fixture.bytes(),"evidence-"+versionId+".txt",stored.metadata().mimeType());
    }

    private record Access(AidCase aidCase, OrganizationMembership member) {
        UUID actor() { return member.getUser().getId(); }
        UUID org() { return member.getOrganization().getId(); }
    }
    private Access scoped(Authentication auth, UUID id, boolean lock) {
        var members = organizations.currentMemberships(auth);
        var ids = members.stream().filter(m -> !m.getRoles().isEmpty()).map(m -> m.getOrganization().getId()).toList();
        if (ids.isEmpty()) throw BusinessException.missing();
        AidCase c = (lock ? cases.lockScoped(id,ids) : cases.findByIdAndOrganization_IdIn(id,ids)).orElseThrow(BusinessException::missing);
        return new Access(c,members.stream().filter(m -> m.getOrganization().getId().equals(c.getOrganization().getId())).findFirst().orElseThrow());
    }
    private void creator(Access a) {
        if (!a.member().getRoles().contains(OrganizationMembership.Role.VOLUNTEER)
                || !a.actor().equals(a.aidCase().getCreatedBy().getId())) throw new AccessDeniedException("Access is denied");
    }
    private void editable(AidCase c, long version) {
        if (c.getVersion()!=version) throw BusinessException.stale();
        if (c.getStatus()!=AidCase.Status.SUBMITTED && c.getStatus()!=AidCase.Status.CHANGES_REQUESTED)
            throw BusinessException.state();
    }
    private CaseDetailResponse.Consent requireConsent(AidCase c) {
        var consent = consents.find(c.getConsentRecordId(),c.getOrganization().getId(),c.getBeneficiary().getId());
        if (consent == null || consent.basis() == null || consent.capturedAt() == null) throw BusinessException.invalid();
        return consent;
    }
    private void insertNeeds(AidCase c, List<NeedInput> inputs) {
        needs.saveAllAndFlush(inputs.stream().map(n -> new CaseNeedItem(c,n.name(),n.unit(),n.quantity())).toList());
    }
    private Result<CaseDetailResponse> finish(AidCase c, UUID actor, String event, int status, String location) {
        cases.flush();
        var response = mapper.detail(c);
        audit.revision(c.getOrganization().getId(),c.getId(),c.getVersion(),response);
        audit.append(c.getOrganization().getId(),c.getId(),actor,c.getId(),event,Map.of("caseVersion",c.getVersion()));
        return new Result<>(response,status,location,false);
    }
    private void validateNeeds(List<NeedInput> inputs) {
        Set<String> seen = new HashSet<>();
        for (NeedInput n : inputs) {
            text(n.name(),false); text(n.unit(),false);
            if (!seen.add(n.name().toLowerCase(Locale.ROOT)+"\u0000"+n.unit().toLowerCase(Locale.ROOT)))
                throw BusinessException.invalid();
        }
    }
    private static void publicConsent(boolean allowed, String title, String summary) {
        if (!allowed && (title!=null || summary!=null)) throw BusinessException.invalid();
    }
    private static String text(String input, boolean optional) {
        String value = input==null ? null : input.strip();
        if (value==null || value.isEmpty()) { if(optional) return null; throw BusinessException.invalid(); }
        if (value.contains("<") || value.contains(">") || value.contains("://") || value.indexOf('\0')>=0)
            throw BusinessException.invalid();
        return value;
    }
}
