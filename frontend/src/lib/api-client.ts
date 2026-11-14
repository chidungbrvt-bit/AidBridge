import type { CreateCampaignRequest } from "./campaign";
import type { ReceiptRequest, ReceiptResponse, StaffContext } from '../../features/receipts/model';
import type { CampaignResponse, CommitmentRequest, CommitmentResponse, CancellationRequest, Page } from "../../features/donor/model";
import type { BlockchainStatus, CaseDetailResponse, CaseListQuery, CaseSummary, CreateCaseRequest, CsrfResponse, DecisionRequest, DecisionResponse, EvidenceFixture, FieldError, LoginRequest, LoginResponse, MeResponse, Membership, MockControlAction, OrganizationRole, PageResponse, PatchCaseRequest, ProblemDetail, ResubmitCaseRequest, ReviewAction } from "./types";

export type Transport = (path: string, init?: RequestInit) => Promise<Response>;
export const realTransport: Transport = (path, init) => {
  if (!path.startsWith("/api/")) throw new Error("Only same-origin API paths are supported.");
  return fetch(path, { ...init, credentials: "same-origin", cache: "no-store", redirect: "error" });
};
/** Explicit mock transport: the same browser cookie protocol, served by the local mock route. */
export const mockTransport: Transport = (path, init) => realTransport(path, init);

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];
  constructor(readonly problem: ProblemDetail) {
    super(problem.detail || problem.title);
    this.name = "ApiError";
    this.status = problem.status;
    this.code = problem.code;
    this.fieldErrors = problem.fieldErrors ?? [];
  }
}

const aliases: Record<string, OrganizationRole> = {
  VOLUNTEER: "VOLUNTEER", FIELD_VOLUNTEER: "VOLUNTEER",
  VERIFIER: "VERIFIER", CASE_VERIFIER: "VERIFIER",
  ORG_ADMIN: "ORG_ADMIN", ORGANIZATION_ADMIN: "ORG_ADMIN", DELIVERY_CONFIRMER: "DELIVERY_CONFIRMER",
};
/** Compatibility adapter for the documented projection and actual B03 Java DTO. Unknown roles grant nothing. */
export function normalizeMe(input: unknown): MeResponse {
  const value = input as Record<string, unknown>;
  if (!value || typeof value.userId !== "string" || !Array.isArray(value.memberships)) {
    throw new ApiError({ type: "urn:aidbridge:error:invalid-response", status: 502, code: "INVALID_RESPONSE", title: "Invalid session response" });
  }
  return {
    userId: value.userId,
    fullName: String(value.fullName ?? value.displayName ?? "Member"),
    status: typeof value.status === "string" ? value.status : "ACTIVE",
    platformRole: typeof value.platformRole === "string" ? value.platformRole : value.platformAdmin === true ? "PLATFORM_ADMIN" : "USER",
    donorEnabled: value.donorEnabled === true,
    memberships: value.memberships.flatMap((entry: Record<string, unknown>) => {
      if (!entry || typeof entry.organizationId !== "string" || !Array.isArray(entry.roles)) return [];
      return [{ organizationId: entry.organizationId, status: typeof entry.status === "string" ? entry.status : entry.active === true ? "ACTIVE" : "DISABLED", roles: entry.roles.flatMap((role: unknown) => typeof role === "string" && aliases[role] ? [aliases[role]] : []) }];
    }),
  };
}
export const activeMemberships = (user: MeResponse | null): Membership[] => user?.status === "ACTIVE" ? user.memberships.filter((membership) => membership.status === "ACTIVE") : [];
const rolesFor = (user: MeResponse | null, organizationId: string) => activeMemberships(user).find((membership) => membership.organizationId === organizationId)?.roles ?? [];
export const canReadCases = (user: MeResponse | null, organizationId: string): boolean => rolesFor(user, organizationId).length > 0;
export const canCreateCases = (user: MeResponse | null, organizationId: string): boolean => rolesFor(user, organizationId).includes("VOLUNTEER");
export const canManageCampaigns = (user: MeResponse | null, organizationId: string): boolean => rolesFor(user, organizationId).includes("ORG_ADMIN");
export const canReviewCases = (user: MeResponse | null, organizationId: string): boolean => rolesFor(user, organizationId).includes("VERIFIER");

export function normalizeBlockchainStatus(value: unknown): BlockchainStatus {
  if (value === "NOT_REQUIRED") return "NOT_APPLICABLE";
  if (value === "FAILED") return "FAILED_ON_CHAIN";
  if (value === "CONFIRMED") return "CONFIRMED_ON_CHAIN";
  return ["NOT_APPLICABLE", "PENDING_ON_CHAIN", "CONFIRMED_ON_CHAIN", "FAILED_ON_CHAIN"].includes(String(value)) ? value as BlockchainStatus : "UNKNOWN";
}
/** Display adapter only: fullName is the current B04 contract; displayName was the F03/F04 mock. */
export function normalizeCaseSummary(input: unknown): CaseSummary {
  const value = input as CaseSummary & { creator?: { id: string; fullName?: string; displayName?: string }; needs?: unknown[] };
  if (!value || typeof value.id !== "string" || typeof value.organizationId !== "string" || typeof value.internalTitle !== "string") {
    throw new ApiError({ type: "urn:aidbridge:error:invalid-response", status: 502, code: "INVALID_RESPONSE", title: "Invalid case response" });
  }
  return { ...value, creator: value.creator ? { id: value.creator.id, displayName: value.creator.fullName ?? value.creator.displayName ?? "Staff member" } : undefined, needsCount: typeof value.needsCount === "number" ? value.needsCount : Array.isArray(value.needs) ? value.needs.length : undefined };
}
export function normalizeCaseDetail(input: unknown): CaseDetailResponse {
  const value = input as CaseDetailResponse;
  const summary = normalizeCaseSummary(input);
  if (!summary.creator || !Array.isArray(value.needs) || !Array.isArray(value.evidenceVersions) || !value.consent) throw new ApiError({ type: "urn:aidbridge:error:invalid-response", status: 502, code: "INVALID_RESPONSE", title: "Invalid case detail response" });
  return { ...value, ...summary, creator: summary.creator, beneficiaryAlias: value.beneficiaryAlias, approximateArea: value.approximateArea, publicTitle: typeof value.publicTitle === "string" ? value.publicTitle : undefined, publicSummary: typeof value.publicSummary === "string" ? value.publicSummary : undefined, reviewHistory: Array.isArray(value.reviewHistory) ? value.reviewHistory.map(normalizeDecision) : [],
    reviewHistoryAvailable: Array.isArray(value.reviewHistory), blockchainStatus: normalizeBlockchainStatus(value.blockchainStatus) };
}
function normalizeDecision(value: DecisionResponse): DecisionResponse {
  return { ...value, blockchainStatus: normalizeBlockchainStatus(value.blockchainStatus) };
}

export class ApiClient {
  private csrfToken: CsrfResponse | undefined;
  private authVersion = 0;
  constructor(private readonly transport: Transport, private readonly onUnauthorized?: () => void) {}

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const startedWithAuthVersion = this.authVersion;
    const response = await this.transport(path, { ...init, cache: "no-store", credentials: "same-origin" });
    if (!response.ok) {
      let raw: Partial<ProblemDetail> = {};
      try { raw = await response.json(); } catch { /* Proxy/backend failure may not be JSON. */ }
      const status = response.status;
      const error = new ApiError({ type: raw.type ?? "urn:aidbridge:error:http-error", title: raw.title ?? "Request failed", status, code: raw.code ?? (status === 401 ? "UNAUTHENTICATED" : "HTTP_ERROR"), detail: raw.detail, traceId: raw.traceId, fieldErrors: raw.fieldErrors });
      if (status === 401 && path !== "/api/auth/login" && startedWithAuthVersion === this.authVersion) { this.csrfToken = undefined; this.onUnauthorized?.(); }
      // Do not automatically replay a mutation. The UI retains its key and payload for an explicit retry.
      if (error.code === "CSRF_INVALID" && startedWithAuthVersion === this.authVersion) this.csrfToken = undefined;
      throw error;
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }
  async csrf(): Promise<CsrfResponse> {
    const startedWithAuthVersion = this.authVersion;
    const token = await this.request<CsrfResponse>("/api/auth/csrf");
    if (startedWithAuthVersion === this.authVersion) this.csrfToken = token;
    return token;
  }
  private async mutate<T>(path: string, body?: unknown, key?: string, method: "POST" | "PATCH" = "POST"): Promise<T> {
    const csrf = this.csrfToken ?? await this.csrf();
    return this.request<T>(path, { method, headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token, ...(key ? { "Idempotency-Key": key } : {}) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  }
  async login(credentials: LoginRequest): Promise<MeResponse> {
    this.authVersion += 1;
    await this.csrf();
    await this.mutate<LoginResponse>("/api/auth/login", credentials);
    this.csrfToken = undefined;
    await this.csrf();
    return this.me();
  }
  async me(): Promise<MeResponse> { return normalizeMe(await this.request<unknown>("/api/auth/me")); }
  async logout(): Promise<void> {
    await this.mutate<void>("/api/auth/logout");
    this.authVersion += 1;
    this.csrfToken = undefined;
    this.onUnauthorized?.();
    await this.csrf();
  }
  async listCases(query: CaseListQuery): Promise<PageResponse<CaseSummary>> {
    const parameters = new URLSearchParams({ organizationId: query.organizationId, page: String(query.page ?? 0), size: String(query.size ?? 20) });
    if (query.status) parameters.set("status", query.status);
    const result = await this.request<PageResponse<unknown>>(`/api/cases?${parameters}`);
    return { ...result, items: result.items.map(normalizeCaseSummary) };
  }
  async getCase(id: string): Promise<CaseDetailResponse> { return normalizeCaseDetail(await this.request(`/api/cases/${encodeURIComponent(id)}`)); }
  async getReceiptContext(id:string):Promise<StaffContext>{return this.request(`/api/org/cases/${encodeURIComponent(id)}`);}
  async receiveGoods(id:string,body:ReceiptRequest,key:string):Promise<ReceiptResponse>{return this.mutate(`/api/commitments/${encodeURIComponent(id)}/receipts`,body,key);}
  async evidenceFixtures(organizationId: string): Promise<EvidenceFixture[]> {
    const rows = await this.request<(EvidenceFixture & { title?: string })[]>(`/api/evidence-fixtures?${new URLSearchParams({ organizationId })}`);
    return rows.map(row => ({ ...row, label: row.title ?? row.label ?? "Evidence fixture" }));
  }
  async createCase(body: CreateCaseRequest, key: string): Promise<CaseDetailResponse> { return normalizeCaseDetail(await this.mutate("/api/cases", body, key)); }
  async patchCase(id: string, body: PatchCaseRequest, key: string): Promise<CaseDetailResponse> { return normalizeCaseDetail(await this.mutate(`/api/cases/${encodeURIComponent(id)}`, body, key, "PATCH")); }
  async resubmitCase(id: string, body: ResubmitCaseRequest, key: string): Promise<CaseDetailResponse> { return normalizeCaseDetail(await this.mutate(`/api/cases/${encodeURIComponent(id)}/resubmit`, body, key)); }
  async reviewCase(id: string, action: ReviewAction, body: DecisionRequest, key: string): Promise<DecisionResponse> { return normalizeDecision(await this.mutate(`/api/cases/${encodeURIComponent(id)}/${action}`, body, key)); }
  async createCampaign(body: CreateCampaignRequest, key: string): Promise<CampaignResponse> { return this.mutate("/api/campaigns", body, key); }
  async publishCampaign(id: string, key: string): Promise<CampaignResponse> { return this.mutate(`/api/campaigns/${encodeURIComponent(id)}/publish`, {}, key); }
  async listCampaigns(page = 0): Promise<Page<CampaignResponse>> { return this.request(`/api/campaigns?${new URLSearchParams({page: String(page), size: '20'})}`); }
  async getCampaign(id: string): Promise<CampaignResponse> { return this.request(`/api/campaigns/${encodeURIComponent(id)}`); }
  async getCommitments(page = 0): Promise<Page<CommitmentResponse>> { return this.request(`/api/me/commitments?${new URLSearchParams({page: String(page), size: '20'})}`); }
  async pledge(id: string, body: CommitmentRequest, key: string): Promise<CommitmentResponse> { return this.mutate(`/api/campaigns/${encodeURIComponent(id)}/commitments`, body, key); }
  async cancel(id: string, body: CancellationRequest, key: string): Promise<CommitmentResponse> { return this.mutate(`/api/commitments/${encodeURIComponent(id)}/cancellations`, body, key); }
  mockControl(action: MockControlAction, caseId?: string): Promise<void> { return this.mutate("/api/mock/control", { action, caseId }); }
}
