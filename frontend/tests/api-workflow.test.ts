import test from "node:test";
import assert from "node:assert/strict";
import { ApiClient, ApiError, normalizeBlockchainStatus, normalizeCaseDetail, normalizeCaseSummary, type Transport } from "../src/lib/api-client";
import { createMockBackend } from "../src/lib/mock-backend";
import { MOCK_CASE_ID, MOCK_CREATE_CASE, MOCK_IDENTITIES } from "../src/lib/mock-fixtures";
import type { CaseDetailResponse, DecisionRequest, PatchCaseRequest } from "../src/lib/types";

function browser(backend = createMockBackend()) {
  let cookie = "";
  const calls: { request: Request; response: Response }[] = [];
  const transport: Transport = async (path, init) => {
    const headers = new Headers(init?.headers);
    if (cookie) headers.set("Cookie", cookie);
    const request = new Request(`http://localhost:3000${path}`, { ...init, headers });
    const response = await backend.handle(request);
    const setCookie = response.headers.get("Set-Cookie");
    if (setCookie) cookie = setCookie.split(";")[0];
    calls.push({ request, response });
    return response;
  };
  return { api: new ApiClient(transport), backend, calls, transport };
}
async function login(client: ReturnType<typeof browser>, index = 0) {
  const { email, password } = MOCK_IDENTITIES[index];
  return client.api.login({ email, password });
}
const key = () => crypto.randomUUID();
const rejectsCode = (code: string, status: number) => (error: unknown) => error instanceof ApiError && error.code === code && error.status === status;
const review = (item: CaseDetailResponse, reason?: string): DecisionRequest => ({ expectedVersion: item.version, reviewedEvidenceVersionIds: item.evidenceVersions.map(e => e.id), ...(reason ? { reason } : {}) });

test("F04 C04/C05: independent changes request -> creator revision -> resubmit -> verifier approval", async () => {
  const backend = createMockBackend(), creator = browser(backend), verifier = browser(backend);
  await login(creator); await login(verifier, 1);
  const original = await creator.api.createCase(structuredClone(MOCK_CREATE_CASE), key());
  const decision = await verifier.api.reviewCase(original.id, "request-changes", review(original, "Clarify the second need quantity."), key());
  assert.equal(decision.caseStatus, "CHANGES_REQUESTED");
  assert.equal(decision.blockchainStatus, "NOT_APPLICABLE");
  await assert.rejects(creator.api.resubmitCase(original.id, { expectedVersion: decision.caseVersion }, key()), rejectsCode("INVALID_STATE", 409));
  const changed = await creator.api.patchCase(original.id, { expectedVersion: decision.caseVersion, needs: [{ name: "Rice", unit: "kg", quantity: 30 }, { name: "Drinking water", unit: "bottle", quantity: 24 }] }, key());
  assert.equal(changed.status, "CHANGES_REQUESTED", "Saving a correction does not silently resubmit");
  assert.equal(changed.version, decision.caseVersion + 1);
  assert.deepEqual(changed.evidenceVersions, original.evidenceVersions, "Editing case fields does not rewrite reviewed evidence");
  assert.equal(changed.reviewHistory[0].reason, "Clarify the second need quantity.");
  const submitted = await creator.api.resubmitCase(original.id, { expectedVersion: changed.version }, key());
  assert.equal(submitted.status, "SUBMITTED");
  assert.equal(submitted.version, changed.version + 1);
  await assert.rejects(creator.api.reviewCase(original.id, "verify", review(submitted), key()), rejectsCode("FORBIDDEN", 403));
  const approved = await verifier.api.reviewCase(original.id, "verify", review(submitted), key());
  assert.equal(approved.caseStatus, "VERIFIED");
  assert.equal(approved.blockchainStatus, "PENDING_ON_CHAIN");
  assert.equal((await creator.api.getCase(original.id)).reviewHistory.length, 2);
  const oldRevision = backend.revisionForTest(original.id, original.version)!;
  assert.deepEqual(oldRevision.needs, original.needs);
  assert.equal(oldRevision.status, "SUBMITTED");
  assert.equal(oldRevision.reviewHistory.length, 0);
  oldRevision.needs[0].quantity = 999;
  assert.notEqual(backend.revisionForTest(original.id, original.version)!.needs[0].quantity, 999, "Test inspection cannot mutate historical revisions");
});

test("F04: PATCH is creator and active-VOLUNTEER scoped; outsider cannot discover the case", async () => {
  const backend = createMockBackend(), creator = browser(backend);
  const user = await login(creator);
  const original = await creator.api.getCase(MOCK_CASE_ID);
  for (const index of [1, 2, 3]) {
    const other = browser(backend); await login(other, index);
    await assert.rejects(other.api.patchCase(original.id, { expectedVersion: original.version, internalTitle: "Forbidden edit" }, key()), rejectsCode(index === 3 ? "RESOURCE_NOT_FOUND" : "FORBIDDEN", index === 3 ? 404 : 403));
    await assert.rejects(other.api.resubmitCase(original.id, { expectedVersion: original.version }, key()), rejectsCode(index === 3 ? "RESOURCE_NOT_FOUND" : "FORBIDDEN", index === 3 ? 404 : 403));
  }
  backend.updateUser(user.userId, { memberships: user.memberships.map(m => ({ ...m, roles: ["VERIFIER"] })) });
  await assert.rejects(creator.api.patchCase(original.id, { expectedVersion: original.version, internalTitle: "Role revoked" }, key()), rejectsCode("FORBIDDEN", 403));
  assert.deepEqual(await creator.api.getCase(original.id), original);
});

test("F04: invalid PATCH fields/second need and empty patch do not create a partial revision", async () => {
  const creator = browser(); await login(creator);
  const original = await creator.api.getCase(MOCK_CASE_ID);
  const invalid = { expectedVersion: original.version, needs: [{ name: "Rice", unit: "kg", quantity: 10 }, { name: "Water", unit: "bottle", quantity: 0 }] };
  await assert.rejects(creator.api.patchCase(original.id, invalid, key()), (error: unknown) => error instanceof ApiError && error.status === 400 && error.fieldErrors.some(f => f.field === "needs[1].quantity"));
  await assert.rejects(creator.api.patchCase(original.id, { expectedVersion: original.version, organizationId: crypto.randomUUID() } as PatchCaseRequest, key()), (error: unknown) => error instanceof ApiError && error.status === 400 && error.fieldErrors.some(f => f.field === "organizationId"));
  await assert.rejects(creator.api.patchCase(original.id, { expectedVersion: original.version, internalTitle: null }, key()), rejectsCode("VALIDATION_ERROR", 400));
  assert.deepEqual(await creator.api.getCase(original.id), original);
  assert.equal(creator.backend.revisionForTest(original.id, original.version + 1), undefined);
});

test("F04: nullable PATCH retains fields, empty public fields clear, and public consent is enforced", async () => {
  const creator = browser(); await login(creator);
  const original = await creator.api.getCase(MOCK_CASE_ID);
  const updated = await creator.api.patchCase(original.id, { expectedVersion: original.version, internalTitle: null, publicTitle: "", publicSummary: "", priority: "URGENT" }, key());
  assert.equal(updated.internalTitle, original.internalTitle);
  assert.equal(updated.publicTitle, "");
  assert.equal(updated.publicSummary, "");
  assert.equal(updated.priority, "URGENT");
  const body = structuredClone(MOCK_CREATE_CASE);
  body.consent.publicSummaryAllowed = false;
  await assert.rejects(creator.api.createCase(body, key()), rejectsCode("VALIDATION_ERROR", 400));
});

test("F04: stale edits require a fresh version and terminal cases cannot be changed or resubmitted", async () => {
  const backend = createMockBackend(), creator = browser(backend), verifier = browser(backend);
  await login(creator); await login(verifier, 1);
  const original = await creator.api.getCase(MOCK_CASE_ID);
  const updated = await creator.api.patchCase(original.id, { expectedVersion: original.version, internalTitle: "Revised intake" }, key());
  await assert.rejects(creator.api.patchCase(original.id, { expectedVersion: original.version, internalTitle: "Stale overwrite" }, key()), rejectsCode("VERSION_CONFLICT", 409));
  await assert.rejects(creator.api.resubmitCase(original.id, { expectedVersion: updated.version }, key()), rejectsCode("INVALID_STATE", 409));
  const rejected = await verifier.api.reviewCase(original.id, "reject", review(updated, "Duplicate fictional request."), key());
  await assert.rejects(creator.api.patchCase(original.id, { expectedVersion: rejected.caseVersion, internalTitle: "Terminal edit" }, key()), rejectsCode("INVALID_STATE", 409));
  await assert.rejects(creator.api.resubmitCase(original.id, { expectedVersion: rejected.caseVersion }, key()), rejectsCode("INVALID_STATE", 409));
});

test("F04: lost PATCH response replays the original result after later edits without a second mutation", async () => {
  const creator = browser(); const user = await login(creator);
  const original = await creator.api.getCase(MOCK_CASE_ID), mutationKey = key();
  const body = { expectedVersion: original.version, internalTitle: "Saved before response loss" };
  await creator.api.mockControl("lose-next-response");
  await assert.rejects(creator.api.patchCase(original.id, body, mutationKey), rejectsCode("UNKNOWN_OUTCOME", 503));
  const saved = await creator.api.getCase(original.id);
  assert.equal(saved.version, original.version + 1);
  const later = await creator.api.patchCase(original.id, { expectedVersion: saved.version, priority: "URGENT" }, key());
  const replayed = await creator.api.patchCase(original.id, body, mutationKey);
  assert.equal(replayed.version, saved.version);
  assert.equal(replayed.priority, saved.priority);
  assert.equal(creator.calls.at(-1)!.response.headers.get("Idempotent-Replayed"), "true");
  assert.equal((await creator.api.getCase(original.id)).version, later.version);
  await assert.rejects(creator.api.patchCase(original.id, { ...body, internalTitle: "Different payload" }, mutationKey), rejectsCode("IDEMPOTENCY_CONFLICT", 409));
  creator.backend.updateUser(user.userId, { memberships: [] });
  await assert.rejects(creator.api.patchCase(original.id, body, mutationKey), rejectsCode("RESOURCE_NOT_FOUND", 404));
});

test("F04: lost resubmit response reuses its key and revision; next request-changes requires a new edit", async () => {
  const backend = createMockBackend(), creator = browser(backend), verifier = browser(backend);
  await login(creator); await login(verifier, 1);
  const original = await creator.api.getCase(MOCK_CASE_ID);
  const changes = await verifier.api.reviewCase(original.id, "request-changes", review(original, "Clarify area."), key());
  const edited = await creator.api.patchCase(original.id, { expectedVersion: changes.caseVersion, approximateArea: "Fictional district north" }, key());
  const body = { expectedVersion: edited.version }, mutationKey = key();
  await creator.api.mockControl("lose-next-response");
  await assert.rejects(creator.api.resubmitCase(original.id, body, mutationKey), rejectsCode("UNKNOWN_OUTCOME", 503));
  const replayed = await creator.api.resubmitCase(original.id, body, mutationKey);
  assert.equal(replayed.status, "SUBMITTED");
  assert.equal(replayed.version, edited.version + 1);
  assert.equal(creator.calls.at(-1)!.response.headers.get("Idempotent-Replayed"), "true");
  const second = await verifier.api.reviewCase(original.id, "request-changes", review(replayed, "Clarify quantity too."), key());
  await assert.rejects(creator.api.resubmitCase(original.id, { expectedVersion: second.caseVersion }, key()), rejectsCode("INVALID_STATE", 409));
});

test("B04 source DTO adapter: minimal list, fullName detail, null public fields, and unavailable projections stay honest", async () => {
  const creator = browser(); await login(creator);
  const mock = await creator.api.getCase(MOCK_CASE_ID);
  const { creator: ignoredCreator, beneficiaryAlias: ignoredAlias, approximateArea: ignoredArea, needsCount: ignoredCount, ...minimal } = {
    id: mock.id, organizationId: mock.organizationId, internalTitle: mock.internalTitle, status: mock.status, priority: mock.priority, version: 0, createdAt: mock.createdAt,
    creator: mock.creator, beneficiaryAlias: mock.beneficiaryAlias, approximateArea: mock.approximateArea, needsCount: mock.needsCount,
  };
  void ignoredCreator; void ignoredAlias; void ignoredArea; void ignoredCount;
  const summary = normalizeCaseSummary(minimal);
  assert.equal(summary.creator, undefined);
  assert.equal(summary.beneficiaryAlias, undefined);
  assert.equal(summary.needsCount, undefined, "Missing real needs count must not appear as zero");
  const { reviewHistory, blockchainStatus, goodsTotals, disputeSummary, ...sourceDetail } = mock;
  void reviewHistory; void blockchainStatus; void goodsTotals; void disputeSummary;
  const detail = normalizeCaseDetail({ ...sourceDetail, creator: { id: mock.creator.id, fullName: "Fictional creator A" }, publicTitle: null, publicSummary: null, version: 0 });
  assert.equal(detail.creator.displayName, "Fictional creator A");
  assert.equal(detail.version, 0);
  assert.equal(detail.reviewHistoryAvailable, false);
  assert.deepEqual(detail.reviewHistory, []);
  assert.equal(detail.blockchainStatus, "UNKNOWN");
  assert.equal(detail.goodsTotals, undefined);
  assert.equal(detail.disputeSummary, undefined);
  assert.equal(detail.publicTitle, undefined);
  assert.equal(detail.publicSummary, undefined);
});

test("B04 wire contract: PATCH/resubmit preserve version zero, method, CSRF, key, and source evidence title", async () => {
  const mock = createMockBackend(), harness = browser(mock); await login(harness);
  const detail = await harness.api.getCase(MOCK_CASE_ID);
  const calls: { path: string; init?: RequestInit }[] = [];
  const api = new ApiClient(async (path, init) => {
    calls.push({ path, init });
    if (path === "/api/auth/csrf") return Response.json({ token: "csrf-fixture", headerName: "X-CSRF-TOKEN" });
    if (path.startsWith("/api/evidence-fixtures")) return Response.json([{ key: "fixture-v1", title: "Source fixture title", mimeType: "text/plain", byteSize: 12, sha256: "a".repeat(64) }]);
    return Response.json({ ...detail, version: 1 });
  });
  const mutationKey = key();
  await api.patchCase(detail.id, { expectedVersion: 0, internalTitle: "First revision" }, mutationKey);
  const patch = calls.at(-1)!;
  assert.equal(patch.path, `/api/cases/${detail.id}`);
  assert.equal(patch.init!.method, "PATCH");
  assert.equal(patch.init!.credentials, "same-origin");
  assert.equal(new Headers(patch.init!.headers).get("X-CSRF-TOKEN"), "csrf-fixture");
  assert.equal(new Headers(patch.init!.headers).get("Idempotency-Key"), mutationKey);
  assert.deepEqual(JSON.parse(String(patch.init!.body)), { expectedVersion: 0, internalTitle: "First revision" });
  await api.resubmitCase(detail.id, { expectedVersion: 0 }, mutationKey);
  assert.equal(calls.at(-1)!.path, `/api/cases/${detail.id}/resubmit`);
  assert.equal(calls.at(-1)!.init!.method, "POST");
  assert.deepEqual(JSON.parse(String(calls.at(-1)!.init!.body)), { expectedVersion: 0 });
  const fixtures = await api.evidenceFixtures(detail.organizationId);
  assert.equal(fixtures[0].label, "Source fixture title");
});

test("B05 status compatibility preserves local decisions and never infers chain confirmation", () => {
  assert.equal(normalizeBlockchainStatus("NOT_REQUIRED"), "NOT_APPLICABLE");
  assert.equal(normalizeBlockchainStatus("NOT_APPLICABLE"), "NOT_APPLICABLE");
  assert.equal(normalizeBlockchainStatus("PENDING_ON_CHAIN"), "PENDING_ON_CHAIN");
  assert.equal(normalizeBlockchainStatus(undefined), "UNKNOWN");
  assert.equal(normalizeBlockchainStatus("unexpected"), "UNKNOWN");
});

test("B04 mock wire follows source summary/fullName/title and evidence hashes match authenticated content", async () => {
  const client = browser(); await login(client);
  const list = await (await client.transport(`/api/cases?organizationId=${MOCK_CREATE_CASE.organizationId}`)).json();
  assert.deepEqual(Object.keys(list.items[0]).sort(), ["id", "organizationId", "internalTitle", "priority", "status", "version", "createdAt"].sort());
  assert.equal(list.items[0].version, 0);
  const detail = await (await client.transport(`/api/cases/${MOCK_CASE_ID}`)).json();
  assert.equal(detail.creator.fullName, MOCK_IDENTITIES[0].user.fullName);
  assert.equal(detail.creator.displayName, undefined);
  const fixtures = await (await client.transport(`/api/evidence-fixtures?organizationId=${MOCK_CREATE_CASE.organizationId}`)).json();
  assert.equal(typeof fixtures[0].title, "string");
  assert.equal(fixtures[0].label, undefined);
  assert.equal(fixtures[0].sha256, detail.evidenceVersions[0].sha256);
  assert.equal(fixtures[0].byteSize, detail.evidenceVersions[0].byteSize);
  const bytes = await (await client.transport(`/api/evidence-versions/${detail.evidenceVersions[0].id}/content`)).arrayBuffer();
  const digest = Buffer.from(await crypto.subtle.digest("SHA-256", bytes)).toString("hex");
  assert.equal(digest, fixtures[0].sha256);
});
