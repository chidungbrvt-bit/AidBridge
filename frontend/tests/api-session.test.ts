import test from "node:test";
import assert from "node:assert/strict";
import { ApiClient, ApiError, activeMemberships, canCreateCases, canReadCases, canReviewCases, normalizeMe, type Transport } from "../src/lib/api-client";
import { createMockBackend } from "../src/lib/mock-backend";
import { MOCK_CASE_ID, MOCK_CREATE_CASE, MOCK_IDENTITIES, MOCK_ORGANIZATIONS } from "../src/lib/mock-fixtures";
import type { DecisionRequest } from "../src/lib/types";

function browser(backend = createMockBackend()) {
  let cookie = "";
  let cleared = 0;
  const calls: { path: string; status: number; headers: Headers; request: Request }[] = [];
  const transport: Transport = async (path, init) => {
    const headers = new Headers(init?.headers);
    if (cookie) headers.set("Cookie", cookie);
    const request = new Request(`http://localhost:3000${path}`, { ...init, headers });
    const response = await backend.handle(request);
    const setCookie = response.headers.get("set-cookie");
    if (setCookie) cookie = setCookie.split(";")[0];
    calls.push({ path, status: response.status, headers: response.headers, request });
    return response;
  };
  const api = new ApiClient(transport, () => { cleared += 1; });
  return { api, transport, backend, calls, getCookie: () => cookie, cleared: () => cleared };
}
async function login(client: ReturnType<typeof browser>, index = 0) {
  const { email, password } = MOCK_IDENTITIES[index];
  return client.api.login({ email, password });
}
const rejectsCode = (code: string, status?: number) => (error: unknown) => error instanceof ApiError && error.code === code && (status === undefined || error.status === status);

test("F03: a delayed 401 from the previous session cannot clear a newly logged-in identity", async () => {
  const harness = browser();
  let release!: (response: Response) => void;
  let delayFirstMe = true;
  let unauthorized = 0;
  const api = new ApiClient(async (path, init) => {
    if (path === "/api/auth/me" && delayFirstMe) {
      delayFirstMe = false;
      return new Promise<Response>(resolve => { release = resolve; });
    }
    return harness.transport(path, init);
  }, () => { unauthorized += 1; });
  const previous = api.me();
  const oldFailure = assert.rejects(previous, rejectsCode("UNAUTHENTICATED", 401));
  await api.login({ email: MOCK_IDENTITIES[1].email, password: MOCK_IDENTITIES[1].password });
  release(Response.json({ code: "UNAUTHENTICATED", status: 401 }, { status: 401 }));
  await oldFailure;
  assert.equal(unauthorized, 0);
  assert.equal((await api.me()).userId, MOCK_IDENTITIES[1].user.userId);
});
const reviewBody = async (client: ReturnType<typeof browser>, id = MOCK_CASE_ID): Promise<DecisionRequest> => {
  const current = await client.api.getCase(id);
  return { expectedVersion: current.version, reviewedEvidenceVersionIds: current.evidenceVersions.map((entry) => entry.id) };
};

test("F03: login follows csrf -> login {user} -> refreshed csrf -> current /me, rotates HttpOnly session", async () => {
  const client = browser();
  const csrf = await client.api.csrf();
  const oldCookie = client.getCookie();
  const user = await login(client);
  assert.equal(user.userId, MOCK_IDENTITIES[0].user.userId);
  assert.deepEqual(client.calls.slice(-4).map((entry) => entry.path), ["/api/auth/csrf", "/api/auth/login", "/api/auth/csrf", "/api/auth/me"]);
  assert.notEqual(client.getCookie(), oldCookie);
  const loginCall = client.calls.find((entry) => entry.path === "/api/auth/login")!;
  assert.match(loginCall.headers.get("set-cookie")!, /HttpOnly; SameSite=Lax/);
  assert.equal(loginCall.headers.get("cache-control"), "no-store");
  const newCsrf = await client.api.csrf();
  assert.notEqual(csrf.token, newCsrf.token);
  const oldSession = await client.backend.handle(new Request("http://localhost/api/auth/me", { headers: { Cookie: oldCookie } }));
  assert.equal(oldSession.status, 401);
});

test("F03: wrong password is a form error; CSRF missing is 403 and cannot log in", async () => {
  const client = browser();
  await assert.rejects(client.api.login({ email: MOCK_IDENTITIES[0].email, password: "wrong" }), rejectsCode("INVALID_CREDENTIALS", 401));
  assert.equal(client.cleared(), 0, "Wrong credentials do not trigger an unrelated session redirect");
  const response = await client.transport("/api/auth/login", { method: "POST", body: JSON.stringify({ email: MOCK_IDENTITIES[0].email, password: MOCK_IDENTITIES[0].password }) });
  assert.equal(response.status, 403);
  assert.equal((await response.json()).code, "CSRF_INVALID");
});

test("F03: navigation follows active memberships from /me; platform admin does not bypass org scope", async () => {
  const client = browser(); const user = await login(client);
  assert.equal(activeMemberships(user).length, 1);
  assert.equal(canCreateCases(user, MOCK_ORGANIZATIONS[0].id), true);
  assert.equal(canReviewCases(user, MOCK_ORGANIZATIONS[0].id), true);
  assert.equal(canReadCases(user, MOCK_ORGANIZATIONS[1].id), false);
  const outsider = browser(client.backend); const admin = await login(outsider, 3);
  assert.equal(admin.platformRole, "PLATFORM_ADMIN");
  assert.equal(canReadCases(admin, MOCK_ORGANIZATIONS[0].id), false);
  await assert.rejects(outsider.api.getCase(MOCK_CASE_ID), rejectsCode("RESOURCE_NOT_FOUND", 404));
});

test("F03: logout 204 clears private state, refreshes CSRF, and invalidates the old session", async () => {
  const client = browser(); await login(client); const oldCookie = client.getCookie();
  await client.api.logout();
  assert.equal(client.calls.at(-2)?.status, 204);
  assert.equal(client.calls.at(-1)?.path, "/api/auth/csrf");
  assert.equal(client.cleared(), 1);
  await assert.rejects(client.api.me(), rejectsCode("UNAUTHENTICATED", 401));
  const response = await client.backend.handle(new Request("http://localhost/api/auth/me", { headers: { Cookie: oldCookie } }));
  assert.equal(response.status, 401);
});

test("F03: expired session yields 401 and clears private state", async () => {
  const client = browser(); await login(client); await client.api.mockControl("expire-session");
  await assert.rejects(client.api.listCases({ organizationId: MOCK_ORGANIZATIONS[0].id }), rejectsCode("UNAUTHENTICATED", 401));
  assert.equal(client.cleared(), 1);
});

test("F03: current /me observes revoked membership and all subsequent reads are denied", async () => {
  const client = browser(); const user = await login(client);
  client.backend.updateUser(user.userId, { memberships: user.memberships.map((membership) => ({ ...membership, status: "DISABLED" })) });
  const fresh = await client.api.me();
  assert.equal(canReadCases(fresh, MOCK_ORGANIZATIONS[0].id), false);
  await assert.rejects(client.api.getCase(MOCK_CASE_ID), rejectsCode("RESOURCE_NOT_FOUND", 404));
});

test("F03: compatibility adapter maps documented aliases; inactive and unknown roles grant nothing", () => {
  const user = normalizeMe({ userId: "actor", displayName: "Fictional actor", platformAdmin: true, memberships: [{ organizationId: "a", active: true, roles: ["FIELD_VOLUNTEER", "CASE_VERIFIER", "ROOT"] }, { organizationId: "b", active: false, roles: ["ORGANIZATION_ADMIN"] }] });
  assert.equal(user.fullName, "Fictional actor");
  assert.deepEqual(user.memberships[0].roles, ["VOLUNTEER", "VERIFIER"]);
  assert.equal(canCreateCases(user, "a"), true);
  assert.equal(canReadCases(user, "b"), false);
});

test("F03: stale CSRF fails visibly, then explicit retry refreshes token and keeps mutation key", async () => {
  const client = browser(); await login(client);
  // A second login rotates the shared browser session while this client's CSRF is still old.
  const secondClient = new ApiClient(client.transport);
  await secondClient.login({ email: MOCK_IDENTITIES[0].email, password: MOCK_IDENTITIES[0].password });
  const key = crypto.randomUUID();
  await assert.rejects(client.api.createCase(MOCK_CREATE_CASE, key), rejectsCode("CSRF_INVALID", 403));
  assert.equal(client.calls.at(-1)?.status, 403, "No automatic mutation retry occurs");
  const created = await client.api.createCase(MOCK_CREATE_CASE, key);
  assert.equal(created.status, "SUBMITTED");
  assert.equal(client.calls.at(-2)?.path, "/api/auth/csrf");
  assert.equal(client.calls.at(-1)?.request.headers.get("Idempotency-Key"), key);
});

test("F04: two-item create returns 201/SUBMITTED/Location; replay creates only once and conflicting payload is 409", async () => {
  const client = browser(); await login(client); const key = crypto.randomUUID();
  const created = await client.api.createCase(structuredClone(MOCK_CREATE_CASE), key);
  const first = client.calls.at(-1)!;
  assert.equal(first.status, 201); assert.equal(first.headers.get("Location"), `/api/cases/${created.id}`);
  assert.equal(created.status, "SUBMITTED"); assert.equal(created.needs.length, 2);
  const replayed = await client.api.createCase(structuredClone(MOCK_CREATE_CASE), key);
  assert.deepEqual(replayed, created);
  assert.equal(client.calls.at(-1)?.headers.get("Idempotent-Replayed"), "true");
  await assert.rejects(client.api.createCase({ ...MOCK_CREATE_CASE, internalTitle: "Different title" }, key), rejectsCode("IDEMPOTENCY_CONFLICT", 409));
  assert.equal((await client.api.listCases({ organizationId: MOCK_CREATE_CASE.organizationId })).totalElements, 2);
});

test("F04: validation 400 maps second need and consent field; no partial case creation", async () => {
  const client = browser(); await login(client); const body = structuredClone(MOCK_CREATE_CASE);
  body.needs[1].quantity = 0; body.consent.scope = "";
  await assert.rejects(client.api.createCase(body, crypto.randomUUID()), (error: unknown) => {
    assert.ok(error instanceof ApiError); assert.equal(error.status, 400);
    assert.ok(error.fieldErrors.some((field) => field.field === "needs[1].quantity"));
    assert.ok(error.fieldErrors.some((field) => field.field === "consent.scope")); return true;
  });
  assert.equal((await client.api.listCases({ organizationId: MOCK_CREATE_CASE.organizationId })).totalElements, 1);
});

test("F04: same-org reader gets 403 create/review; other org resources and fixtures get 404", async () => {
  const backend = createMockBackend(); const reader = browser(backend); await login(reader, 2);
  await assert.rejects(reader.api.createCase(MOCK_CREATE_CASE, crypto.randomUUID()), rejectsCode("FORBIDDEN", 403));
  await assert.rejects(reader.api.reviewCase(MOCK_CASE_ID, "verify", await reviewBody(reader), crypto.randomUUID()), rejectsCode("FORBIDDEN", 403));
  const creator = browser(backend); await login(creator);
  await assert.rejects(creator.api.evidenceFixtures(MOCK_ORGANIZATIONS[1].id), rejectsCode("RESOURCE_NOT_FOUND", 404));
  await assert.rejects(creator.api.createCase({ ...MOCK_CREATE_CASE, evidenceFixtureKeys: ["harbor-intake-v1"] }, crypto.randomUUID()), rejectsCode("RESOURCE_NOT_FOUND", 404));
});

test("F04: unknown outcome is retried with same key and body without creating a duplicate", async () => {
  const client = browser(); await login(client); const key = crypto.randomUUID();
  await client.api.mockControl("lose-next-response");
  await assert.rejects(client.api.createCase(MOCK_CREATE_CASE, key), rejectsCode("UNKNOWN_OUTCOME", 503));
  const retry = await client.api.createCase(MOCK_CREATE_CASE, key);
  assert.equal(retry.status, "SUBMITTED");
  assert.equal(client.calls.at(-1)?.headers.get("Idempotent-Replayed"), "true");
  assert.equal((await client.api.listCases({ organizationId: MOCK_CREATE_CASE.organizationId })).totalElements, 2);
});

test("F04: B reviews A case and VERIFIED remains separate from PENDING_ON_CHAIN", async () => {
  const client = browser(); await login(client, 1); const key = crypto.randomUUID();
  const body = await reviewBody(client); const decision = await client.api.reviewCase(MOCK_CASE_ID, "verify", body, key);
  assert.equal(decision.reviewerId, MOCK_IDENTITIES[1].user.userId);
  assert.equal(decision.caseStatus, "VERIFIED"); assert.equal(decision.blockchainStatus, "PENDING_ON_CHAIN");
  const replayed = await client.api.reviewCase(MOCK_CASE_ID, "verify", body, key); assert.deepEqual(replayed, decision);
  const current = await client.api.getCase(MOCK_CASE_ID); assert.equal(current.reviewHistory.length, 1); assert.equal(current.version, 2);
});

test("F04: A is denied self-review even though A also has VERIFIER", async () => {
  const client = browser(); const user = await login(client);
  assert.equal(canReviewCases(user, MOCK_ORGANIZATIONS[0].id), true);
  await assert.rejects(client.api.reviewCase(MOCK_CASE_ID, "verify", await reviewBody(client), crypto.randomUUID()), rejectsCode("SELF_APPROVAL", 403));
  assert.equal((await client.api.getCase(MOCK_CASE_ID)).status, "SUBMITTED");
});

test("F04: stale case or evidence set requires refresh; new valid review succeeds", async () => {
  const client = browser(); await login(client, 1); const stale = await reviewBody(client);
  await client.api.mockControl("stale-case", MOCK_CASE_ID);
  await assert.rejects(client.api.reviewCase(MOCK_CASE_ID, "verify", stale, crypto.randomUUID()), rejectsCode("VERSION_CONFLICT", 409));
  const current = await reviewBody(client);
  await assert.rejects(client.api.reviewCase(MOCK_CASE_ID, "verify", { ...current, reviewedEvidenceVersionIds: [crypto.randomUUID()] }, crypto.randomUUID()), rejectsCode("VERSION_CONFLICT", 409));
  const fresh = await reviewBody(client); const result = await client.api.reviewCase(MOCK_CASE_ID, "verify", fresh, crypto.randomUUID());
  assert.equal(result.caseVersion, 3);
});

test("F04: reject/request changes require reason and produce the documented business transitions", async () => {
  for (const action of ["reject", "request-changes"] as const) {
    const client = browser(); await login(client, 1); const body = await reviewBody(client);
    await assert.rejects(client.api.reviewCase(MOCK_CASE_ID, action, body, crypto.randomUUID()), (error: unknown) => error instanceof ApiError && error.status === 400 && error.fieldErrors.some((field) => field.field === "reason"));
    const result = await client.api.reviewCase(MOCK_CASE_ID, action, { ...body, reason: "Please clarify the fictional intake record." }, crypto.randomUUID());
    assert.equal(result.caseStatus, action === "reject" ? "REJECTED" : "CHANGES_REQUESTED");
    assert.equal(result.blockchainStatus, "NOT_REQUIRED");
  }
});

test("F04: replay rechecks revoked membership before returning a private response", async () => {
  const client = browser(); const user = await login(client); const key = crypto.randomUUID();
  await client.api.createCase(MOCK_CREATE_CASE, key);
  client.backend.updateUser(user.userId, { memberships: [] });
  await assert.rejects(client.api.createCase(MOCK_CREATE_CASE, key), rejectsCode("RESOURCE_NOT_FOUND", 404));
});

test("F04: evidence content remains authenticated, no-store and scoped to owning organization", async () => {
  const backend = createMockBackend(); const creator = browser(backend); await login(creator);
  const detail = await creator.api.getCase(MOCK_CASE_ID); const evidenceId = detail.evidenceVersions[0].id;
  const valid = await creator.transport(`/api/evidence-versions/${evidenceId}/content`);
  assert.equal(valid.status, 200); assert.equal(valid.headers.get("Cache-Control"), "no-store");
  assert.match(valid.headers.get("Content-Disposition")!, /^attachment/);
  const bytes = await valid.text(); assert.match(bytes, /fictional evidence fixture/);
  const outsider = browser(backend); await login(outsider, 3);
  assert.equal((await outsider.transport(`/api/evidence-versions/${evidenceId}/content`)).status, 404);
  assert.equal((await browser(backend).transport(`/api/evidence-versions/${evidenceId}/content`)).status, 401);
});

test("F04: empty state, temporary error and pagination filters are repeatable", async () => {
  const client = browser(); await login(client);
  assert.equal((await client.api.listCases({ organizationId: MOCK_CREATE_CASE.organizationId, status: "REJECTED" })).totalElements, 0);
  await client.api.mockControl("next-error");
  await assert.rejects(client.api.listCases({ organizationId: MOCK_CREATE_CASE.organizationId }), rejectsCode("DEPENDENCY_UNAVAILABLE", 503));
  assert.equal((await client.api.listCases({ organizationId: MOCK_CREATE_CASE.organizationId })).totalElements, 1);
  await assert.rejects(client.api.listCases({ organizationId: MOCK_CREATE_CASE.organizationId, size: 101 }), rejectsCode("VALIDATION_ERROR", 400));
});
