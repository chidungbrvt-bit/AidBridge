/** Server-only, process-memory simulation. No SQL/outbox/Fuji claim; never enabled by real transport mode. */
import { createHash, randomBytes, randomUUID } from "node:crypto";
import type { CampaignResponse } from "../../features/donor/model";
import { MOCK_CASE_ID, MOCK_CREATE_CASE, MOCK_EVIDENCE, MOCK_IDENTITIES, MOCK_ORGANIZATIONS } from "./mock-fixtures";
import type { CaseDetailResponse, CaseStatus, CaseSummary, CreateCaseRequest, DecisionRequest, DecisionResponse, EvidenceVersion, FieldError, MeResponse, MockControlAction, PatchCaseRequest, ProblemDetail, ReviewAction } from "./types";

const COOKIE = "AIDBRIDGE_MOCK_SESSION";
const HEADER = "X-CSRF-TOKEN";
const IDLE_MS = 30 * 60 * 1000;
type Session = { csrf: string; userId?: string; touched: number; nextError?: boolean; loseResponse?: boolean };
type StoredResponse = { body: unknown; status: number; headers: Record<string, string>; digest: string };
class Failure extends Error {
  constructor(readonly status: number, readonly code: string, message: string, readonly fieldErrors?: FieldError[]) { super(message); }
}
function fail(status: number, code: string, detail: string, fields?: FieldError[]): never { throw new Failure(status, code, detail, fields); }
const clone = <T>(value: T): T => structuredClone(value);
const json = (body: unknown, status = 200, headers: Record<string, string> = {}): Response => new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store", ...headers } });
const canonical = (input: unknown): string => {
  if (Array.isArray(input)) return `[${input.map(canonical).join(",")}]`;
  if (input !== null && typeof input === "object") return `{${Object.entries(input).sort(([a], [b]) => a.localeCompare(b)).map(([key, value]) => `${JSON.stringify(key)}:${canonical(value)}`).join(",")}}`;
  return JSON.stringify(input);
};
const summary = (item: CaseDetailResponse): CaseSummary => ({ id: item.id, organizationId: item.organizationId, internalTitle: item.internalTitle, priority: item.priority, status: item.status, version: item.version, createdAt: item.createdAt });
// Current B04 wire naming; the shared client normalizes it for the existing UI.
const detail = (item: CaseDetailResponse) => ({ ...item, creator: { id: item.creator.id, fullName: item.creator.displayName } });
const cookieHeader = (id: string, secure: boolean, clear = false) => `${COOKIE}=${id}; Path=/; HttpOnly; SameSite=Lax${secure ? "; Secure" : ""}${clear ? "; Max-Age=0" : ""}`;
const object = (value: unknown): Record<string, unknown> => value !== null && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};

export function createMockBackend() {
  const sessions = new Map<string, Session>();
  const users = new Map(MOCK_IDENTITIES.map((identity) => [identity.user.userId, clone(identity.user)]));
  const cases = new Map<string, CaseDetailResponse>();
  const campaigns = new Map<string, { caseId: string; organizationId: string; dto: CampaignResponse }>();
  const publicCaseRefs = new Map<string, string>();
  const replay = new Map<string, StoredResponse>();
  const evidence = new Map<string, { caseId: string; bytes: string }>();
  const changesRequestedVersion = new Map<string, number>();
  const revisions = new Map<string, CaseDetailResponse>();
  const saveRevision = (item: CaseDetailResponse) => revisions.set(`${item.id}:${item.version}`, clone(item));

  function buildCase(body: CreateCaseRequest, user: MeResponse, id = randomUUID()): CaseDetailResponse {
    const now = new Date().toISOString();
    const versions: EvidenceVersion[] = body.evidenceFixtureKeys.map((key) => {
      const evidenceId = randomUUID();
      const bytes = `AidBridge fictional evidence fixture\nFixture: ${key}\nSimulated household record. No real beneficiary data.\n`;
      evidence.set(evidenceId, { caseId: id, bytes });
      return { id: evidenceId, evidenceFileId: randomUUID(), versionNo: 1, mimeType: "text/plain", byteSize: Buffer.byteLength(bytes), sha256: createHash("sha256").update(bytes).digest("hex"), createdAt: now };
    });
    return { id, organizationId: body.organizationId, internalTitle: body.internalTitle, beneficiaryAlias: body.beneficiaryAlias, approximateArea: body.approximateArea, priority: body.priority, publicTitle: body.publicTitle, publicSummary: body.publicSummary, status: "SUBMITTED", version: 0, creator: { id: user.userId, displayName: user.fullName }, createdAt: now, needsCount: body.needs.length, needs: body.needs.map((need) => ({ ...need, id: randomUUID() })), consent: clone(body.consent), evidenceVersions: versions, reviewHistory: [], goodsTotals: { pledged: 0, received: 0, delivered: 0, confirmed: 0 }, disputeSummary: { openCount: 0 }, blockchainStatus: "NOT_APPLICABLE" };
  }
  cases.set(MOCK_CASE_ID, buildCase(clone(MOCK_CREATE_CASE), MOCK_IDENTITIES[0].user, MOCK_CASE_ID));
  saveRevision(cases.get(MOCK_CASE_ID)!);

  function ownMembership(user: MeResponse, organizationId: string) {
    const membership = user.memberships.find((entry) => entry.organizationId === organizationId && entry.status === "ACTIVE");
    if (!membership || membership.roles.length === 0) fail(404, "RESOURCE_NOT_FOUND", "Resource not found.");
    return membership;
  }
  function ownCase(user: MeResponse, id: string): CaseDetailResponse {
    const item = cases.get(id);
    if (!item) fail(404, "RESOURCE_NOT_FOUND", "Resource not found.");
    ownMembership(user, item.organizationId);
    return item;
  }
  function requireCampaignAdmin(user: MeResponse, organizationId: string) {
    if (!ownMembership(user, organizationId).roles.includes("ORG_ADMIN")) {
      fail(403, "FORBIDDEN", "An organization administrator is required.");
    }
  }

  function validateCampaignCase(item: CaseDetailResponse) {
    if (item.status !== "VERIFIED" || !item.consent.publicSummaryAllowed ||
        !item.needs.length || (item.disputeSummary?.openCount ?? 0) > 0) {
      fail(409, "INVALID_STATE", "An approved case with public consent is required.");
    }
  }

  function publicCopy(input: unknown, max: number, field: string): string {
    const text = typeof input === "string" ? input.trim() : "";
    if (!text || text.length > max || /[<>\u0000]|:\/\//.test(text)) {
      fail(400, "VALIDATION_ERROR", "Invalid public text.", [{ field, code: "INVALID_PUBLIC_COPY" }]);
    }
    return text;
  }

  function validateCreate(input: unknown): CreateCaseRequest {
    const body = object(input);
    const errors: FieldError[] = [];
    const allowed = new Set(["organizationId", "beneficiaryAlias", "approximateArea", "internalTitle", "publicTitle", "publicSummary", "priority", "consent", "needs", "evidenceFixtureKeys", "supersedesCaseId"]);
    Object.keys(body).filter((key) => !allowed.has(key)).forEach((field) => errors.push({ field, code: "UNEXPECTED_FIELD" }));
    const checkText = (field: string, value: unknown, max: number, required = true) => {
      if ((!required && value === undefined)) return;
      if (typeof value !== "string" || (required && !value.trim()) || value.length > max || /[<>\0]|:\/\//.test(value)) errors.push({ field, code: "INVALID_TEXT" });
    };
    checkText("organizationId", body.organizationId, 36);
    checkText("beneficiaryAlias", body.beneficiaryAlias, 120);
    checkText("approximateArea", body.approximateArea, 160);
    checkText("internalTitle", body.internalTitle, 160);
    checkText("publicTitle", body.publicTitle, 160, false);
    checkText("publicSummary", body.publicSummary, 2000, false);
    if (!["NORMAL", "URGENT"].includes(String(body.priority))) errors.push({ field: "priority", code: "INVALID_VALUE" });
    const consent = object(body.consent);
    if (!["CONSENT", "REPRESENTATIVE"].includes(String(consent.basis))) errors.push({ field: "consent.basis", code: "REQUIRED" });
    checkText("consent.scope", consent.scope, 200);
    if (typeof consent.capturedAt !== "string" || !Number.isFinite(Date.parse(consent.capturedAt)) || Date.parse(consent.capturedAt) > Date.now()) errors.push({ field: "consent.capturedAt", code: "INVALID_TIMESTAMP" });
    if (typeof consent.publicSummaryAllowed !== "boolean") errors.push({ field: "consent.publicSummaryAllowed", code: "REQUIRED" });
    if (consent.publicSummaryAllowed === false && [body.publicTitle, body.publicSummary].some(value => typeof value === "string" && value.trim())) errors.push({ field: "consent.publicSummaryAllowed", code: "PUBLIC_SUMMARY_NOT_ALLOWED" });
    if (!Array.isArray(body.needs) || body.needs.length < 1 || body.needs.length > 20) errors.push({ field: "needs", code: "SIZE_1_TO_20" });
    const needKeys = new Set<string>();
    if (Array.isArray(body.needs)) body.needs.forEach((inputNeed: unknown, index: number) => {
      const need = object(inputNeed);
      checkText(`needs[${index}].name`, need.name, 120);
      checkText(`needs[${index}].unit`, need.unit, 40);
      if (!Number.isInteger(need.quantity) || Number(need.quantity) < 1 || Number(need.quantity) > 1_000_000) errors.push({ field: `needs[${index}].quantity`, code: "INTEGER_1_TO_1000000" });
      const key = `${String(need.name).trim().toLowerCase()}|${String(need.unit).trim().toLowerCase()}`;
      if (needKeys.has(key)) errors.push({ field: `needs[${index}].name`, code: "DUPLICATE_NEED" });
      needKeys.add(key);
    });
    if (!Array.isArray(body.evidenceFixtureKeys) || body.evidenceFixtureKeys.length < 1 || body.evidenceFixtureKeys.length > 10 || body.evidenceFixtureKeys.some((key) => typeof key !== "string") || new Set(body.evidenceFixtureKeys).size !== body.evidenceFixtureKeys.length) errors.push({ field: "evidenceFixtureKeys", code: "SELECT_1_TO_10_UNIQUE" });
    if (body.supersedesCaseId !== undefined && (typeof body.supersedesCaseId !== "string" || !/^[\da-f-]{36}$/i.test(body.supersedesCaseId))) errors.push({ field: "supersedesCaseId", code: "INVALID_UUID" });
    if (errors.length) fail(400, "VALIDATION_ERROR", "Check the highlighted fields.", errors);
    const result = clone(body) as unknown as CreateCaseRequest;
    result.beneficiaryAlias = result.beneficiaryAlias.trim(); result.approximateArea = result.approximateArea.trim(); result.internalTitle = result.internalTitle.trim();
    result.needs = result.needs.map((need) => ({ name: need.name.trim(), unit: need.unit.trim(), quantity: need.quantity }));
    result.consent.scope = result.consent.scope.trim();
    result.publicTitle = result.publicTitle?.trim(); result.publicSummary = result.publicSummary?.trim();
    return result;
  }
  function validateDecision(body: Record<string, unknown>, action: ReviewAction): DecisionRequest {
    const errors: FieldError[] = [];
    if (!Number.isInteger(body.expectedVersion) || Number(body.expectedVersion) < 0) errors.push({ field: "expectedVersion", code: "REQUIRED" });
    if (!Array.isArray(body.reviewedEvidenceVersionIds) || body.reviewedEvidenceVersionIds.length < 1 || body.reviewedEvidenceVersionIds.length > 10 || body.reviewedEvidenceVersionIds.some((id) => typeof id !== "string") || new Set(body.reviewedEvidenceVersionIds).size !== body.reviewedEvidenceVersionIds.length) errors.push({ field: "reviewedEvidenceVersionIds", code: "REQUIRED_UNIQUE_VERSIONS" });
    if (action !== "verify" && (typeof body.reason !== "string" || !body.reason.trim())) errors.push({ field: "reason", code: "REQUIRED" });
    if (body.reason !== undefined && (typeof body.reason !== "string" || body.reason.length > 2000 || /[<>\0]|:\/\//.test(body.reason))) errors.push({ field: "reason", code: "INVALID_TEXT" });
    if (Object.keys(body).some((key) => !["expectedVersion", "reviewedEvidenceVersionIds", "reason"].includes(key))) errors.push({ field: "request", code: "UNEXPECTED_FIELD" });
    if (errors.length) fail(400, "VALIDATION_ERROR", "Check the review fields.", errors);
    return { expectedVersion: Number(body.expectedVersion), reviewedEvidenceVersionIds: body.reviewedEvidenceVersionIds as string[], ...(typeof body.reason === "string" ? { reason: body.reason.trim() } : {}) };
  }
  function idempotent(request: Request, user: MeResponse, body: unknown, operation: () => StoredResponse, session: Session): Response {
    const key = request.headers.get("Idempotency-Key");
    if (!key || !/^[\x21-\x7e]{16,128}$/.test(key)) fail(400, "VALIDATION_ERROR", "An Idempotency-Key of 16 to 128 visible ASCII characters is required.", [{ field: "Idempotency-Key", code: "REQUIRED" }]);
    const scope = `${user.userId}|${request.method}|${new URL(request.url).pathname}|${key}`;
    const digest = canonical(body);
    const saved = replay.get(scope);
    if (saved) {
      if (saved.digest !== digest) fail(409, "IDEMPOTENCY_CONFLICT", "This key belongs to a different request. Restore the original request or start a new action.");
      return json(saved.body, saved.status, { ...saved.headers, "Idempotent-Replayed": "true" });
    }
    const result = operation();
    replay.set(scope, { ...clone(result), digest });
    if (session.loseResponse) { session.loseResponse = false; fail(503, "UNKNOWN_OUTCOME", "The mock response was interrupted after commit. Retry the unchanged request with the same key."); }
    return json(result.body, result.status, result.headers);
  }
  async function handle(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;
    const method = request.method.toUpperCase();
    try {
      const sessionId = request.headers.get("cookie")?.split(";").map((piece) => piece.trim()).find((piece) => piece.startsWith(`${COOKIE}=`))?.slice(COOKIE.length + 1);
      let session = sessionId ? sessions.get(sessionId) : undefined;
      if (session && Date.now() - session.touched > IDLE_MS) { sessions.delete(sessionId!); session = undefined; }
      if (session) session.touched = Date.now();
      const secure = url.protocol === "https:";
      if (method === "GET" && path === "/api/auth/csrf") {
        if (session) return json({ token: session.csrf, headerName: HEADER });
        const id = randomUUID();
        session = { csrf: randomUUID(), touched: Date.now() }; sessions.set(id, session);
        return json({ token: session.csrf, headerName: HEADER }, 200, { "Set-Cookie": cookieHeader(id, secure) });
      }
      let body: Record<string, unknown> = {};
      if (!["GET", "HEAD"].includes(method)) {
        // Existing authenticated sessions expire as 401; anonymous login still requires CSRF.
        if (!session && path !== "/api/auth/login") fail(401, "UNAUTHENTICATED", "Your session has expired. Sign in again.");
        if (!session || request.headers.get(HEADER) !== session.csrf) fail(403, "CSRF_INVALID", "The security token expired. Refresh it and retry.");
        const raw = await request.text();
        try { body = raw ? object(JSON.parse(raw)) : {}; } catch { fail(400, "VALIDATION_ERROR", "A valid JSON body is required."); }
      }
      if (method === "POST" && path === "/api/auth/login") {
        const identity = MOCK_IDENTITIES.find((entry) => entry.email === String(body.email).trim().toLowerCase() && entry.password === body.password);
        const user = identity ? users.get(identity.user.userId) : undefined;
        if (!identity || !user || user.status !== "ACTIVE") fail(401, "INVALID_CREDENTIALS", "Email or password is incorrect.");
        if (sessionId) sessions.delete(sessionId);
        const newId = randomUUID(); sessions.set(newId, { userId: user.userId, csrf: randomUUID(), touched: Date.now() });
        return json({ user: { id: user.userId, fullName: user.fullName, email: identity.email, status: user.status, platformRole: user.platformRole, createdAt: "2026-11-01T00:00:00.000Z" } }, 200, { "Set-Cookie": cookieHeader(newId, secure) });
      }
      const user = session?.userId ? users.get(session.userId) : undefined;
      const campaignMatch = path.match(/^\/api\/campaigns\/([^/]+)(\/publish)?$/);
      if (method === "GET" && path === "/api/campaigns") {
        const page = Number(url.searchParams.get("page") ?? 0);
        const size = Number(url.searchParams.get("size") ?? 20);
        if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100) {
          fail(400, "VALIDATION_ERROR", "Invalid list filter.");
        }
        const visible = [...campaigns.values()].map(entry => entry.dto).filter(campaign => campaign.status !== "DRAFT");
        return json({ items: visible.slice(page * size, (page + 1) * size), page, size, totalElements: visible.length });
      }
      if (method === "GET" && campaignMatch && !campaignMatch[2]) {
        const entry = campaigns.get(campaignMatch[1]);
        if (!entry || (entry.dto.status === "DRAFT" && (user?.status !== "ACTIVE" ||
            !user.memberships.some(m => m.status === "ACTIVE" && m.organizationId === entry.organizationId && m.roles.includes("ORG_ADMIN"))))) {
          fail(404, "RESOURCE_NOT_FOUND", "Resource not found.");
        }
        return json({ ...entry.dto, items: [...entry.dto.items].reverse() });
      }
      if (!user || user.status !== "ACTIVE") fail(401, "UNAUTHENTICATED", "Your session has expired. Sign in again.");
      if (method === "GET" && path === "/api/auth/me") return json(user);
      if (method === "POST" && path === "/api/auth/logout") {
        if (sessionId) sessions.delete(sessionId);
        return json(undefined, 204, { "Set-Cookie": cookieHeader("", secure, true) });
      }
      if (method === "POST" && path === "/api/mock/control") {
        const action = body.action as MockControlAction;
        if (action === "expire-session") sessions.delete(sessionId!);
        else if (action === "stale-case") { const item = ownCase(user, String(body.caseId)); item.version += 1; }
        else if (action === "next-error") session!.nextError = true;
        else if (action === "lose-next-response") session!.loseResponse = true;
        else fail(400, "VALIDATION_ERROR", "Unknown mock control.");
        return json(undefined, 204);
      }
      if (method === "GET" && session!.nextError) { session!.nextError = false; fail(503, "DEPENDENCY_UNAVAILABLE", "Simulated service interruption. Try again."); }
      if (method === "POST" && path === "/api/campaigns") {
        if (Object.keys(body).some(key => !["caseId", "title", "publicSummary"].includes(key)) || typeof body.caseId !== "string") {
          fail(400, "VALIDATION_ERROR", "Invalid draft request.");
        }
        const item = ownCase(user, body.caseId);
        requireCampaignAdmin(user, item.organizationId);
        const title = publicCopy(body.title, 160, "title");
        const publicSummary = publicCopy(body.publicSummary, 2000, "publicSummary");
        return idempotent(request, user, { caseId: item.id, title, publicSummary }, () => {
          validateCampaignCase(item);
          if ([...campaigns.values()].some(entry => entry.caseId === item.id)) fail(409, "INVALID_STATE", "A campaign already exists for this case.");
          const id = randomUUID();
          const organization = MOCK_ORGANIZATIONS.find(org => org.id === item.organizationId)!;
          const publicCaseRef = publicCaseRefs.get(item.id) ?? randomBytes(32).toString("hex");
          publicCaseRefs.set(item.id, publicCaseRef);
          const dto: CampaignResponse = {
            id, status: "DRAFT", title, publicSummary,
            approximateArea: publicCopy(item.approximateArea, 160, "approximateArea"),
            organization: { name: organization.name, publicRef: `0x${createHash("sha256").update(organization.id).digest("hex")}` },
            auditUrl: `/api/public/cases/0x${publicCaseRef}/audit`,
            items: item.needs.map(need => ({
              id: randomUUID(), name: need.name, unit: need.unit, targetQty: need.quantity,
              pledgedQty: 0, receivedQty: 0, deliveredQty: 0, confirmedQty: 0, remainingPledgeQty: need.quantity,
            })),
          };
          campaigns.set(id, { caseId: item.id, organizationId: item.organizationId, dto });
          return { body: dto, status: 201, headers: { Location: `/api/campaigns/${id}` }, digest: "" };
        }, session!);
      }
      if (method === "POST" && campaignMatch?.[2]) {
        const entry = campaigns.get(campaignMatch[1]);
        if (!entry) fail(404, "RESOURCE_NOT_FOUND", "Resource not found.");
        const item = ownCase(user, entry.caseId);
        requireCampaignAdmin(user, entry.organizationId);
        if (Object.keys(body).length) fail(400, "VALIDATION_ERROR", "Publish takes an empty JSON object.");
        return idempotent(request, user, {}, () => {
          if (entry.dto.status !== "DRAFT") fail(409, "INVALID_STATE", "Only a draft can be published.");
          validateCampaignCase(item);
          item.status = "IN_PROGRESS";
          item.version += 1;
          saveRevision(item);
          entry.dto = { ...entry.dto, status: "PUBLISHED", items: [...entry.dto.items].reverse() };
          return { body: entry.dto, status: 200, headers: {}, digest: "" };
        }, session!);
      }
      if (method === "GET" && path === "/api/evidence-fixtures") {
        const organizationId = url.searchParams.get("organizationId") ?? ""; ownMembership(user, organizationId);
        return json((MOCK_EVIDENCE[organizationId] ?? []).map(({ label, ...fixture }) => {
          const bytes = `AidBridge fictional evidence fixture\nFixture: ${fixture.key}\nSimulated household record. No real beneficiary data.\n`;
          return { ...fixture, title: label, byteSize: Buffer.byteLength(bytes), sha256: createHash("sha256").update(bytes).digest("hex") };
        }));
      }
      if (method === "GET" && path === "/api/cases") {
        const organizationId = url.searchParams.get("organizationId") ?? ""; ownMembership(user, organizationId);
        const status = url.searchParams.get("status"); const page = Number(url.searchParams.get("page") ?? 0); const size = Number(url.searchParams.get("size") ?? 20);
        if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100 || (status && !["SUBMITTED", "VERIFIED", "REJECTED", "CHANGES_REQUESTED", "IN_PROGRESS", "COMPLETED"].includes(status))) fail(400, "VALIDATION_ERROR", "Invalid list filter.");
        const items = [...cases.values()].filter((item) => item.organizationId === organizationId && (!status || item.status === status)).sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id));
        return json({ items: items.slice(page * size, (page + 1) * size).map(summary), page, size, totalElements: items.length });
      }
      if (method === "POST" && path === "/api/cases") {
        const input = validateCreate(body);
        const membership = ownMembership(user, input.organizationId);
        if (!membership.roles.includes("VOLUNTEER")) fail(403, "FORBIDDEN", "A volunteer role is required to create a case.");
        const allowlist = MOCK_EVIDENCE[input.organizationId] ?? [];
        if (input.evidenceFixtureKeys.some((key) => !allowlist.some((fixture) => fixture.key === key))) fail(404, "RESOURCE_NOT_FOUND", "Resource not found.");
        if (input.supersedesCaseId && ownCase(user, input.supersedesCaseId).organizationId !== input.organizationId) fail(404, "RESOURCE_NOT_FOUND", "Resource not found.");
        return idempotent(request, user, input, () => { const created = buildCase(input, user); cases.set(created.id, created); saveRevision(created); return { body: detail(created), status: 201, headers: { Location: `/api/cases/${created.id}` }, digest: "" }; }, session!);
      }
      const editMatch = path.match(/^\/api\/cases\/([^/]+)(\/resubmit)?$/);
      if (editMatch && ((method === "PATCH" && !editMatch[2]) || (method === "POST" && editMatch[2]))) {
        const item = ownCase(user, editMatch[1]);
        const membership = ownMembership(user, item.organizationId);
        if (!membership.roles.includes("VOLUNTEER") || item.creator.id !== user.userId) fail(403, "FORBIDDEN", "Only the creating volunteer can revise this case.");
        const allowed = editMatch[2] ? ["expectedVersion"] : ["expectedVersion", "internalTitle", "publicTitle", "publicSummary", "approximateArea", "priority", "needs"];
        const fields: FieldError[] = Object.keys(body).filter(key => !allowed.includes(key)).map(field => ({ field, code: "UNEXPECTED_FIELD" }));
        if (!Number.isInteger(body.expectedVersion) || Number(body.expectedVersion) < 0) fields.push({ field: "expectedVersion", code: "REQUIRED" });
        if (fields.length) fail(400, "VALIDATION_ERROR", "Check the highlighted fields.", fields);
        return idempotent(request, user, body, () => {
          if (body.expectedVersion !== item.version) fail(409, "VERSION_CONFLICT", "This case changed. Refresh before continuing.");
          if (editMatch[2]) {
            if (item.status !== "CHANGES_REQUESTED") fail(409, "INVALID_STATE", "Only cases with requested changes can be resubmitted.");
            if (item.version <= (changesRequestedVersion.get(item.id) ?? item.version)) fail(409, "INVALID_STATE", "Save a revision addressing the requested changes before resubmitting.");
            if (!item.needs.length || !item.evidenceVersions.length) fail(400, "VALIDATION_ERROR", "Needs and evidence are required before resubmission.");
            item.status = "SUBMITTED"; item.version += 1; saveRevision(item);
          } else {
            if (!["SUBMITTED", "CHANGES_REQUESTED"].includes(item.status)) fail(409, "INVALID_STATE", "Only submitted cases or cases with requested changes can be edited.");
            const patch = Object.fromEntries(Object.entries(body).filter(([key, value]) => key !== "expectedVersion" && value !== null && value !== undefined)) as Omit<PatchCaseRequest, "expectedVersion">;
            if (!Object.keys(patch).length) fail(400, "VALIDATION_ERROR", "Provide at least one editable field.");
            const editable = { internalTitle: item.internalTitle, publicTitle: item.publicTitle, publicSummary: item.publicSummary, approximateArea: item.approximateArea, priority: item.priority, needs: item.needs.map(({ name, unit, quantity }) => ({ name, unit, quantity })) };
            const candidate = { ...editable, ...patch };
            const valid = validateCreate({ ...candidate, organizationId: item.organizationId, beneficiaryAlias: item.beneficiaryAlias, consent: item.consent, evidenceFixtureKeys: [MOCK_EVIDENCE[item.organizationId][0].key] });
            const next = { internalTitle: valid.internalTitle, publicTitle: valid.publicTitle, publicSummary: valid.publicSummary, approximateArea: valid.approximateArea, priority: valid.priority, needs: valid.needs };
            Object.assign(item, { ...next, needs: patch.needs ? next.needs.map(need => ({ ...need, id: randomUUID() })) : item.needs });
            item.version += 1; item.needsCount = item.needs.length; saveRevision(item);
          }
          return { body: detail(item), status: 200, headers: {}, digest: "" };
        }, session!);
      }
      const caseMatch = path.match(/^\/api\/cases\/([^/]+)(?:\/(verify|reject|request-changes))?$/);
      if (caseMatch) {
        const item = ownCase(user, caseMatch[1]);
        if (method === "GET" && !caseMatch[2]) return json(detail(item));
        if (method === "POST" && caseMatch[2]) {
          const membership = ownMembership(user, item.organizationId);
          if (!membership.roles.includes("VERIFIER")) fail(403, "FORBIDDEN", "A verifier role is required to review this case.");
          if (item.creator.id === user.userId) fail(403, "FORBIDDEN", "You cannot review a case you created, even with a verifier role.");
          const action = caseMatch[2] as ReviewAction; const input = validateDecision(body, action);
          return idempotent(request, user, input, () => {
            if (input.expectedVersion !== item.version) fail(409, "VERSION_CONFLICT", "This case changed. Refresh the case and review its evidence again before resubmitting.");
            if (item.status !== "SUBMITTED") fail(409, "INVALID_STATE", "Only submitted cases can be reviewed.");
            const expected = item.evidenceVersions.map((version) => version.id).sort();
            if (canonical([...input.reviewedEvidenceVersionIds].sort()) !== canonical(expected)) fail(400, "VALIDATION_ERROR", "The evidence selection does not match the current versions. Refresh before resubmitting.");
            const caseStatus: CaseStatus = action === "verify" ? "VERIFIED" : action === "reject" ? "REJECTED" : "CHANGES_REQUESTED";
            const decision: DecisionResponse = { verificationId: randomUUID(), caseId: item.id, decision: action === "verify" ? "APPROVED" : action === "reject" ? "REJECTED" : "CHANGES_REQUESTED", caseStatus, caseVersion: item.version + 1, reviewerId: user.userId, reviewedAt: new Date().toISOString(), auditEventId: randomUUID(), blockchainStatus: action === "verify" ? "PENDING_ON_CHAIN" : "NOT_APPLICABLE", ...(input.reason ? { reason: input.reason } : {}) };
            item.version += 1; item.status = caseStatus; item.blockchainStatus = decision.blockchainStatus; item.reviewHistory.push(decision);
            if (action === "request-changes") changesRequestedVersion.set(item.id, item.version);
            saveRevision(item);
            return { body: decision, status: 200, headers: {}, digest: "" };
          }, session!);
        }
      }
      const evidenceMatch = path.match(/^\/api\/evidence-versions\/([^/]+)\/content$/);
      if (method === "GET" && evidenceMatch) {
        const record = evidence.get(evidenceMatch[1]); if (!record) fail(404, "RESOURCE_NOT_FOUND", "Resource not found.");
        ownCase(user, record.caseId);
        return new Response(record.bytes, { headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store", "Content-Disposition": "attachment; filename=fictional-evidence.txt", "X-Content-Type-Options": "nosniff" } });
      }
      fail(404, "RESOURCE_NOT_FOUND", "Resource not found.");
    } catch (error) {
      const failure = error instanceof Failure ? error : new Failure(500, "MOCK_INTERNAL_ERROR", "The mock service could not complete the request.");
      const problem: ProblemDetail = { type: `urn:aidbridge:error:${failure.code.toLowerCase().replaceAll("_", "-")}`, title: failure.code.replaceAll("_", " "), status: failure.status, code: failure.code, detail: failure.message, instance: path, traceId: randomUUID(), ...(failure.fieldErrors ? { fieldErrors: failure.fieldErrors } : {}) };
      return json(problem, failure.status, { "Content-Type": "application/problem+json" });
    }
  }
  return {
    handle,
    /** Test-only mutation of fixture accounts. Not exposed through the HTTP control route. */
    updateUser(userId: string, update: Partial<MeResponse>) { const user = users.get(userId); if (user) Object.assign(user, clone(update)); },
    /** Unit-test inspection of immutable simulated revisions; no HTTP exposure. */
    revisionForTest(caseId: string, version: number) { return clone(revisions.get(`${caseId}:${version}`)); },
  };
}
