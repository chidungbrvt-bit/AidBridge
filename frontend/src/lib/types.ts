export type OrganizationRole = "VOLUNTEER" | "VERIFIER" | "ORG_ADMIN" | "DELIVERY_CONFIRMER";
export interface Membership { organizationId: string; roles: OrganizationRole[]; status: string }
/** B03 MeResponse as present in the supplied Java source. */
export interface MeResponse {
  userId: string; fullName: string; status: string; platformRole: string;
  donorEnabled: boolean; memberships: Membership[];
}
export interface LoginRequest { email: string; password: string }
export interface LoginResponse { user: { id: string; fullName: string; email: string; status: string; platformRole: string; createdAt: string } }
export interface CsrfResponse { token: string; headerName: string }
export interface FieldError { field: string; code: string; message?: string }
export interface ProblemDetail { type: string; title: string; status: number; code: string; detail?: string; instance?: string; traceId?: string; fieldErrors?: FieldError[] }
export interface NeedInput { name: string; unit: string; quantity: number }
export interface ConsentInput { basis: "CONSENT" | "REPRESENTATIVE"; scope: string; capturedAt: string; publicSummaryAllowed: boolean }
/** Matched to B04 CreateCaseRequest in main 051e40f (PR #9). */
export interface CreateCaseRequest {
  organizationId: string; beneficiaryAlias: string; approximateArea: string; internalTitle: string;
  publicTitle?: string; publicSummary?: string; priority: "NORMAL" | "URGENT";
  consent: ConsentInput; needs: NeedInput[]; evidenceFixtureKeys: string[]; supersedesCaseId?: string;
}
export type CaseStatus = "DRAFT" | "SUBMITTED" | "VERIFIED" | "REJECTED" | "CHANGES_REQUESTED" | "IN_PROGRESS" | "COMPLETED" | "DISPUTED" | "CANCELLED";
export type BlockchainStatus = "NOT_REQUIRED" | "NOT_APPLICABLE" | "PENDING_ON_CHAIN" | "CONFIRMED_ON_CHAIN" | "FAILED_ON_CHAIN" | "CONFIRMED" | "FAILED" | "UNKNOWN";
export interface CaseCreator { id: string; displayName: string }
/** B04 summary is intentionally minimal; optional fields support older mock responses. */
export interface CaseSummary {
  id: string; organizationId: string; internalTitle: string; beneficiaryAlias?: string;
  approximateArea?: string; priority: "NORMAL" | "URGENT"; status: CaseStatus;
  version: number; creator?: CaseCreator; createdAt: string; needsCount?: number;
}
export interface EvidenceVersion {
  id: string; evidenceFileId: string; versionNo: number; mimeType: string;
  byteSize: number; sha256: string; createdAt: string;
}
export interface EvidenceFixture { key: string; label: string; mimeType: string; byteSize: number; sha256?: string }
/** Normalized B04 detail. Review/goods/dispute/chain projections are optional extensions, not fabricated real data. */
export interface CaseDetailResponse extends CaseSummary {
  creator: CaseCreator; beneficiaryAlias: string; approximateArea: string;
  publicTitle?: string; publicSummary?: string;
  needs: (NeedInput & { id: string })[]; consent: ConsentInput; evidenceVersions: EvidenceVersion[];
  reviewHistory: DecisionResponse[]; reviewHistoryAvailable?: boolean;
  disputeSummary?: { openCount: number };
  goodsTotals?: { pledged: number; received: number; delivered: number; confirmed: number };
  blockchainStatus: BlockchainStatus;
}
export interface PageResponse<T> { items: T[]; page: number; size: number; totalElements: number }
export interface CaseListQuery { organizationId: string; status?: string; page?: number; size?: number }
export type ReviewAction = "verify" | "reject" | "request-changes";
export interface DecisionRequest { expectedVersion: number; reviewedEvidenceVersionIds: string[]; reason?: string }
export interface DecisionResponse {
  verificationId: string; caseId: string; decision: "APPROVED" | "REJECTED" | "CHANGES_REQUESTED";
  caseStatus: CaseStatus; caseVersion: number; reviewerId: string; reviewedAt: string;
  auditEventId: string; blockchainStatus: BlockchainStatus; reason?: string;
}
export type MockControlAction = "expire-session" | "stale-case" | "next-error" | "lose-next-response";

/** C04 only; identities, organization, consent and evidence cannot be reassigned by PATCH. */
export interface PatchCaseRequest {
  expectedVersion: number; internalTitle?: string | null; publicTitle?: string | null; publicSummary?: string | null;
  approximateArea?: string | null; priority?: "NORMAL" | "URGENT" | null; needs?: NeedInput[] | null;
}
export interface ResubmitCaseRequest { expectedVersion: number }
