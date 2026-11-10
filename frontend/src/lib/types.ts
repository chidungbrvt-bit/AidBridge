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
/** API_SPECIFICATION.md contract; B04 DTO source is absent from the archive. */
export interface CreateCaseRequest {
  organizationId: string; beneficiaryAlias: string; approximateArea: string; internalTitle: string;
  publicTitle?: string; publicSummary?: string; priority: "NORMAL" | "URGENT";
  consent: ConsentInput; needs: NeedInput[]; evidenceFixtureKeys: string[]; supersedesCaseId?: string;
}
export type CaseStatus = "SUBMITTED" | "VERIFIED" | "REJECTED" | "CHANGES_REQUESTED" | "IN_PROGRESS" | "COMPLETED";
export type BlockchainStatus = "NOT_REQUIRED" | "PENDING_ON_CHAIN" | "CONFIRMED" | "FAILED";
export interface CaseCreator { id: string; displayName: string }
/** Proposed MOCK projection: API spec does not freeze CaseSummary fields. */
export interface CaseSummary {
  id: string; organizationId: string; internalTitle: string; beneficiaryAlias: string;
  approximateArea: string; priority: "NORMAL" | "URGENT"; status: CaseStatus;
  version: number; creator: CaseCreator; createdAt: string; needsCount: number;
}
export interface EvidenceVersion {
  id: string; evidenceFileId: string; versionNo: number; mimeType: string;
  byteSize: number; sha256: string; createdAt: string;
}
export interface EvidenceFixture { key: string; label: string; mimeType: string; byteSize: number }
/** Title/alias/area, consent summary, goods/dispute shapes and chain projection are mock assumptions pending B04. */
export interface CaseDetailResponse extends CaseSummary {
  publicTitle?: string; publicSummary?: string;
  needs: (NeedInput & { id: string })[]; consent: ConsentInput; evidenceVersions: EvidenceVersion[];
  reviewHistory: DecisionResponse[]; campaignId?: string;
  disputeSummary: { openCount: number };
  goodsTotals: { pledged: number; received: number; delivered: number; confirmed: number };
  blockchainStatus: BlockchainStatus;
}
export interface PageResponse<T> { items: T[]; page: number; size: number; totalElements: number }
export interface CaseListQuery { organizationId: string; status?: string; page?: number; size?: number }
export type ReviewAction = "verify" | "reject" | "request-changes";
export interface DecisionRequest { expectedVersion: number; reviewedEvidenceVersionIds: string[]; reason?: string }
export interface DecisionResponse {
  verificationId: string; caseId: string; decision: "APPROVED" | "REJECTED" | "CHANGES_REQUESTED";
  caseStatus: CaseStatus; caseVersion: number; reviewerId: string; reviewedAt: string;
  auditEventId: string; blockchainStatus: BlockchainStatus;
}
export type MockControlAction = "expire-session" | "stale-case" | "next-error" | "lose-next-response";
