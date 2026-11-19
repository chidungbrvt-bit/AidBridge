import { test, expect, type Page } from "@playwright/test";

type CaseRecord = { id: string; version: number; status: string; internalTitle: string; needs: { name: string; quantity: number }[]; evidenceVersions: { id: string }[] };
const accounts = { A: "minh.a@example.test", B: "minh.b@example.test" };
test.setTimeout(60_000);

async function login(page: Page, identity: keyof typeof accounts) {
  await page.goto("/login");
  await page.getByLabel("Email address").fill(accounts[identity]);
  await page.getByLabel("Password", { exact: true }).fill("Mock-only-123!");
  await page.getByRole("button", { name: "Sign in →", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Every need starts here." })).toBeVisible();
}

async function create(page: Page): Promise<CaseRecord> {
  const title = `Revision smoke ${crypto.randomUUID()}`;
  await page.goto("/cases/new");
  await page.getByLabel("Fictional beneficiary alias", { exact: true }).fill("Demo Household A");
  await page.getByLabel("Approximate area", { exact: true }).fill("Fictional North district");
  await page.getByLabel("Internal title", { exact: true }).fill(title);
  await page.getByLabel("Item 1 quantity", { exact: true }).fill("3");
  await page.getByLabel("Item 2 quantity", { exact: true }).fill("4");
  await page.getByLabel("I confirm this is a simulated consent record for fictional data.", { exact: true }).check();
  await page.getByRole("checkbox", { name: /Fictional intake record/ }).check();
  await page.getByRole("checkbox", { name: /Fictional needs checklist/ }).check();
  const response = page.waitForResponse(r => new URL(r.url()).pathname === "/api/cases" && r.request().method() === "POST");
  await page.getByRole("button", { name: "Submit case", exact: true }).click();
  const created = await response;
  expect(created.status()).toBe(201);
  await expect(page.getByRole("heading", { name: title, exact: true })).toBeVisible();
  return created.json();
}

async function edit(page: Page) {
  await page.getByRole("link", { name: "Edit case", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Edit case", exact: true })).toBeVisible();
}

async function reviewEvidence(page: Page) {
  await expect(page.locator('input[name="reviewedEvidenceVersionIds"]').first()).toBeVisible();
  for (const checkbox of await page.locator('input[name="reviewedEvidenceVersionIds"]').all()) await checkbox.check();
}

function mutation(page: Page, id: string, method = "PATCH", suffix = "") {
  return page.waitForResponse(r => new URL(r.url()).pathname === `/api/cases/${id}${suffix}` && r.request().method() === method);
}
const business = (page: Page) => page.locator(".status-card").filter({ hasText: "BUSINESS STATUS" });

test("two independent browser sessions complete create -> changes -> edit -> resubmit -> approve", async ({ page, browser, baseURL }) => {
  const reviewerContext = await browser.newContext({ baseURL });
  const reviewer = await reviewerContext.newPage();
  try {
    await login(page, "A");
    const item = await create(page);
    expect(item.version).toBe(0);
    await expect(page.getByRole("button", { name: "Approve case", exact: true })).toBeDisabled();
    await login(reviewer, "B");
    await reviewer.goto(`/cases/${item.id}`);
    await expect(reviewer.getByRole("link", { name: "Edit case", exact: true })).toHaveCount(0);
    await reviewEvidence(reviewer);
    await reviewer.getByLabel("Review reason", { exact: true }).fill("Clarify the quantity of the second item.");
    await reviewer.getByRole("button", { name: "Request changes", exact: true }).click();
    await expect(business(reviewer).getByText("Changes requested", { exact: true })).toBeVisible();

    await page.getByRole("button", { name: "Refresh case", exact: true }).click();
    await expect(page.getByText("Clarify the quantity of the second item.", { exact: true })).toBeVisible();
    await edit(page);
    await expect(page.getByRole("button", { name: "Resubmit for review", exact: true })).toBeDisabled();
    await page.getByLabel("Item 2 quantity", { exact: true }).fill("8");
    const patchResponse = mutation(page, item.id);
    await page.getByRole("button", { name: "Save revision", exact: true }).click();
    const saved = await patchResponse;
    expect(saved.status()).toBe(200);
    const revision = await saved.json();
    expect(revision.status).toBe("CHANGES_REQUESTED");
    expect(revision.evidenceVersions).toEqual(item.evidenceVersions);
    await expect(page.getByText(/Revision saved\. Use Resubmit for review/)).toBeVisible();
    const resubmitResponse = mutation(page, item.id, "POST", "/resubmit");
    await page.getByRole("button", { name: "Resubmit for review", exact: true }).click();
    expect((await resubmitResponse).status()).toBe(200);
    await expect(business(page).getByText("Submitted", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Approve case", exact: true })).toBeDisabled();

    await reviewer.getByRole("button", { name: "Refresh case", exact: true }).click();
    await expect(business(reviewer).getByText("Submitted", { exact: true })).toBeVisible();
    await expect(reviewer.getByRole("table", { name: "Requested goods" }).getByRole("cell", { name: "8", exact: true })).toBeVisible();
    await expect(reviewer.locator('input[name="reviewedEvidenceVersionIds"]').first()).not.toBeChecked();
    await reviewEvidence(reviewer);
    await reviewer.getByRole("button", { name: "Approve case", exact: true }).click();
    await expect(business(reviewer).getByText("Verified", { exact: true })).toBeVisible();
    await expect(reviewer.locator(".status-card").filter({ hasText: "BLOCKCHAIN SYNC" }).getByText("Pending on chain", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Refresh case", exact: true }).click();
    await expect(page.getByRole("link", { name: "Edit case", exact: true })).toHaveCount(0);
  } finally { await reviewerContext.close(); }
});

test("edit validation identifies the duplicate item row without saving a partial revision", async ({ page }) => {
  await login(page, "A");
  const item = await create(page);
  await edit(page);
  await page.getByLabel("Item 2 name", { exact: true }).fill("Rice");
  await page.getByLabel("Item 2 unit", { exact: true }).fill("bag");
  const invalid = mutation(page, item.id);
  await page.getByRole("button", { name: "Save revision", exact: true }).click();
  expect((await invalid).status()).toBe(400);
  await expect(page.getByLabel("Item 2 name", { exact: true })).toBeFocused();
  await expect(page.locator('[id="error-needs[1].name"]')).toHaveText("duplicate need");
  const unchanged = await (await page.request.get(`/api/cases/${item.id}`)).json();
  expect(unchanged.version).toBe(item.version);
  expect(unchanged.needs).toEqual(item.needs);
  await page.getByLabel("Item 2 name", { exact: true }).fill("Hygiene kit");
  await page.getByLabel("Item 2 unit", { exact: true }).fill("kit");
  await page.getByLabel("Item 2 quantity", { exact: true }).fill("5");
  await page.getByRole("button", { name: "Save revision", exact: true }).click();
  await expect(page.getByText(/Revision saved\./)).toBeVisible();
  await expect(page.getByText(`CASE · REVISION ${item.version + 1}`, { exact: true })).toBeVisible();
});

test("stale PATCH locks the form until refresh and a new version is used", async ({ page }) => {
  await login(page, "A");
  const item = await create(page);
  await edit(page);
  const csrf = await (await page.request.get("/api/auth/csrf")).json();
  const control = await page.request.post("/api/mock/control", { headers: { [csrf.headerName]: csrf.token }, data: { action: "stale-case", caseId: item.id } });
  expect(control.status()).toBe(204);
  await page.getByLabel("Approximate area", { exact: true }).fill("Stale unsaved area");
  const stale = mutation(page, item.id);
  await page.getByRole("button", { name: "Save revision", exact: true }).click();
  expect((await stale).status()).toBe(409);
  await expect(page.getByRole("alert").filter({ hasText: "VERSION_CONFLICT" })).toBeVisible();
  await expect(page.getByLabel("Approximate area", { exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Refresh before editing again", exact: true }).click();
  await expect(page.getByText(`CASE · REVISION ${item.version + 1}`, { exact: true })).toBeVisible();
  await expect(page.getByLabel("Approximate area", { exact: true })).toHaveValue("Fictional North district");
  await page.getByLabel("Approximate area", { exact: true }).fill("Fresh fictional area");
  const fresh = mutation(page, item.id);
  await page.getByRole("button", { name: "Save revision", exact: true }).click();
  const saved = await fresh;
  expect(saved.status()).toBe(200);
  expect(saved.request().postDataJSON().expectedVersion).toBe(item.version + 1);
  await expect(page.getByText(`CASE · REVISION ${item.version + 2}`, { exact: true })).toBeVisible();
});

test("unknown PATCH outcome blocks navigation and preserves the request through a CSRF retry", async ({ page }) => {
  await login(page, "A");
  const item = await create(page);
  await edit(page);
  await page.getByLabel("Item 2 quantity", { exact: true }).fill("7");
  await page.locator(".demo-tools summary").click();
  await page.getByRole("button", { name: "Lose next write response", exact: true }).click();
  await expect(page.getByText("Next write will commit but its response will be lost. Retry with the same key.")).toBeVisible();
  const captured: { key: string | undefined; body: string | null }[] = [];
  page.on("request", request => {
    if (new URL(request.url()).pathname === `/api/cases/${item.id}` && request.method() === "PATCH") captured.push({ key: request.headers()["idempotency-key"], body: request.postData() });
  });
  const lost = mutation(page, item.id);
  await page.getByRole("button", { name: "Save revision", exact: true }).click();
  expect((await lost).status()).toBe(503);
  await expect(page.getByText("The outcome is unknown.", { exact: true })).toBeVisible();
  await expect(page.getByLabel("Item 2 quantity", { exact: true })).toBeDisabled();
  await expect(page.getByRole("combobox", { name: "Active organization", exact: true })).toBeDisabled();
  await page.getByRole("navigation", { name: "Workspace navigation" }).getByRole("link", { name: "Cases", exact: true }).click({ force: true });
  await expect(page).toHaveURL(new RegExp(`/cases/${item.id}/edit$`));
  await expect(page.getByRole("heading", { name: "Edit case", exact: true })).toBeVisible();

  // Like a second tab signing in as the same actor: browser cookies rotate,
  // while this mounted ApiClient still holds the previous CSRF token.
  const csrf = await (await page.request.get("/api/auth/csrf")).json();
  const rotated = await page.request.post("/api/auth/login", {
    headers: { [csrf.headerName]: csrf.token },
    data: { email: accounts.A, password: "Mock-only-123!" },
  });
  expect(rotated.status()).toBe(200);
  const staleToken = mutation(page, item.id);
  await page.getByRole("button", { name: "Retry same request", exact: true }).click();
  const rejectedRetry = await staleToken;
  expect(rejectedRetry.status()).toBe(403);
  expect((await rejectedRetry.json()).code).toBe("CSRF_INVALID");
  await expect(page.getByRole("alert").filter({ hasText: "CSRF_INVALID" })).toBeVisible();
  await expect(page.getByText("The outcome is unknown.", { exact: true })).toBeVisible();
  await expect(page.getByLabel("Item 2 quantity", { exact: true })).toBeDisabled();
  await expect(page.getByRole("combobox", { name: "Active organization", exact: true })).toBeDisabled();
  const retry = mutation(page, item.id);
  await page.getByRole("button", { name: "Retry same request", exact: true }).click();
  const replayed = await retry;
  expect(replayed.status()).toBe(200);
  expect(replayed.headers()["idempotent-replayed"]).toBe("true");
  await expect(page.getByText(/Revision saved\./)).toBeVisible();
  expect(captured).toHaveLength(3);
  expect(captured[0].key).toBeTruthy();
  expect(captured[1]).toEqual(captured[0]);
  expect(captured[2]).toEqual(captured[0]);
  const current = await (await page.request.get(`/api/cases/${item.id}`)).json();
  expect(current.version).toBe(item.version + 1);
  expect(current.needs[1].quantity).toBe(7);
  expect(current.evidenceVersions).toEqual(item.evidenceVersions);
});
