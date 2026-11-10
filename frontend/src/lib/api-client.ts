import type { CaseDetailResponse, CaseListQuery, CaseSummary, CreateCaseRequest, CsrfResponse, DecisionRequest, DecisionResponse, EvidenceFixture, FieldError, LoginRequest, LoginResponse, MeResponse, Membership, MockControlAction, OrganizationRole, PageResponse, ProblemDetail, ReviewAction } from "./types";

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
export const canReviewCases = (user: MeResponse | null, organizationId: string): boolean => rolesFor(user, organizationId).includes("VERIFIER");

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
  private async mutate<T>(path: string, body?: unknown, key?: string): Promise<T> {
    const csrf = this.csrfToken ?? await this.csrf();
    return this.request<T>(path, { method: "POST", headers: { "Content-Type": "application/json", [csrf.headerName]: csrf.token, ...(key ? { "Idempotency-Key": key } : {}) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
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
  listCases(query: CaseListQuery): Promise<PageResponse<CaseSummary>> {
    const parameters = new URLSearchParams({ organizationId: query.organizationId, page: String(query.page ?? 0), size: String(query.size ?? 20) });
    if (query.status) parameters.set("status", query.status);
    return this.request(`/api/cases?${parameters}`);
  }
  getCase(id: string): Promise<CaseDetailResponse> { return this.request(`/api/cases/${encodeURIComponent(id)}`); }
  evidenceFixtures(organizationId: string): Promise<EvidenceFixture[]> { return this.request(`/api/evidence-fixtures?${new URLSearchParams({ organizationId })}`); }
  createCase(body: CreateCaseRequest, key: string): Promise<CaseDetailResponse> { return this.mutate("/api/cases", body, key); }
  reviewCase(id: string, action: ReviewAction, body: DecisionRequest, key: string): Promise<DecisionResponse> { return this.mutate(`/api/cases/${encodeURIComponent(id)}/${action}`, body, key); }
  mockControl(action: MockControlAction, caseId?: string): Promise<void> { return this.mutate("/api/mock/control", { action, caseId }); }
}
