import { test, expect, type Page } from "@playwright/test";

const password = "Mock-only-123!";
const organizationId = "10000000-0000-4000-8000-000000000001";
const userEmail = { A: "minh.a@example.test", B: "minh.b@example.test" } as const;
type Created = { id: string; title: string; version: number; evidenceVersions: { id: string }[] };

test.describe.configure({ mode: "serial" });
test.setTimeout(60_000);

async function login(page: Page, who: keyof typeof userEmail) {
  await page.goto("/login");
  await page.getByLabel("Email address").fill(userEmail[who]);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in →", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Every need starts here." })).toBeVisible();
}

async function fillCreateForm(page: Page, suffix: string) {
  const token = `${Date.now()}-${Math.random().toString(16).slice(2, 7)}`;
  const title = `Smoke ${suffix} ${token}`;
  await page.getByRole("link", { name: "Create case", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Create a case", exact: true })).toBeVisible();
  await page.getByLabel("Fictional beneficiary alias", { exact: true }).fill(`Demo household ${token}`);
  await page.getByLabel("Approximate area", { exact: true }).fill("Fictional North district");
  await page.getByLabel("Internal title", { exact: true }).fill(title);
  await page.getByLabel("Item 1 quantity", { exact: true }).fill("3");
  await page.getByLabel("Item 2 quantity", { exact: true }).fill("4");
  await page.getByLabel("I confirm this is a simulated consent record for fictional data.", { exact: true }).check();
  await page.getByRole("checkbox", { name: /Fictional intake record/ }).check();
  await page.getByRole("checkbox", { name: /Fictional needs checklist/ }).check();
  return title;
}

async function create(page: Page, suffix: string): Promise<Created> {
  const title = await fillCreateForm(page, suffix);
  const responsePromise = page.waitForResponse(response => new URL(response.url()).pathname === "/api/cases" && response.request().method() === "POST");
  await page.getByRole("button", { name: "Submit case", exact: true }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(201);
  const body = await response.json();
  expect(body.status).toBe("SUBMITTED");
  expect(body.needs).toHaveLength(2);
  expect(body.needs.map((item: { quantity: number }) => item.quantity)).toEqual([3, 4]);
  await expect(page.getByRole("heading", { name: title, exact: true })).toBeVisible();
  return { ...body, title };
}

async function becomeReviewer(page: Page, item: Created) {
  await page.getByRole("button", { name: "Sign out", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Welcome back", exact: true })).toBeVisible();
  await expect(page.getByText(item.title, { exact: true })).toHaveCount(0);
  await login(page, "B");
  await expect(page.getByRole("navigation", { name: "Workspace navigation" }).getByRole("link", { name: "Create case", exact: true })).toHaveCount(0);
  await page.goto(`/cases/${item.id}`);
  await expect(page.getByRole("heading", { name: item.title, exact: true })).toBeVisible();
}

async function acknowledgeEvidence(page: Page) {
  const inputs = page.locator('input[name="reviewedEvidenceVersionIds"]');
  expect(await inputs.count()).toBeGreaterThan(0);
  for (const input of await inputs.all()) await input.check();
}

function businessCard(page: Page) { return page.locator(".status-card").filter({ hasText: "BUSINESS STATUS" }); }
function chainCard(page: Page) { return page.locator(".status-card").filter({ hasText: "BLOCKCHAIN SYNC" }); }

test("wrong password stays on login; session data never enters localStorage", async ({ page, context }) => {
  await page.goto("/login");
  await page.getByLabel("Email address").fill(userEmail.A);
  await page.getByLabel("Password", { exact: true }).fill("wrong-password");
  await page.getByRole("button", { name: "Sign in →", exact: true }).click();
  await expect(page.locator('.notice[role="alert"]')).toHaveText("Email or password is incorrect.");
  await expect(page.getByLabel("Password", { exact: true })).toHaveValue("");
  await login(page, "A");
  await expect(page.getByRole("navigation", { name: "Workspace navigation" }).getByRole("link", { name: "Create case", exact: true })).toBeVisible();
  await expect(page.getByRole("combobox", { name: "Active organization" }).locator("option")).toHaveCount(1);
  const cookie = (await context.cookies()).find(item => item.name === "AIDBRIDGE_MOCK_SESSION");
  expect(cookie?.httpOnly).toBe(true);
  expect(cookie?.sameSite).toBe("Lax");
  expect(await page.evaluate(() => Object.keys(localStorage))).toEqual([]);
});

test("A creates two items; self review denied; B approves with distinct business and chain states", async ({ page }) => {
  await login(page, "A");
  const item = await create(page, "independent-review");
  await expect(page.getByRole("button", { name: "Approve case", exact: true })).toBeDisabled();
  await expect(page.getByText("You created this case. Another person must review it, even if you also have the VERIFIER role.")).toBeVisible();
  const csrf = await (await page.request.get("/api/auth/csrf")).json();
  const denied = await page.request.post(`/api/cases/${item.id}/verify`, {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": crypto.randomUUID() },
    data: { expectedVersion: item.version, reviewedEvidenceVersionIds: item.evidenceVersions.map(evidence => evidence.id) },
  });
  expect(denied.status()).toBe(403);
  expect((await denied.json()).code).toBe("FORBIDDEN");
  await becomeReviewer(page, item);
  const downloaded = page.waitForEvent("download");
  await page.getByRole("link", { name: "Download evidence 1", exact: true }).click();
  expect((await downloaded).suggestedFilename()).toBe("fictional-evidence.txt");
  await acknowledgeEvidence(page);
  await page.getByRole("button", { name: "Approve case", exact: true }).click();
  await expect(businessCard(page).getByText("Verified", { exact: true })).toBeVisible();
  await expect(chainCard(page).getByText("Pending on chain", { exact: true })).toBeVisible();
  await page.reload();
  await expect(businessCard(page).getByText("Verified", { exact: true })).toBeVisible();
  await expect(chainCard(page).getByText("Pending on chain", { exact: true })).toBeVisible();
});

for (const action of [
  { button: "Request changes", status: "CHANGES REQUESTED", suffix: "changes" },
  { button: "Reject case", status: "REJECTED", suffix: "reject" },
]) {
  test(`${action.button} requires a reason and preserves a separate local chain state`, async ({ page }) => {
    await login(page, "A");
    const item = await create(page, action.suffix);
    await becomeReviewer(page, item);
    await acknowledgeEvidence(page);
    await page.getByRole("button", { name: action.button, exact: true }).click();
    await expect(page.locator('.notice[role="alert"]')).toContainText("Check the highlighted review fields.");
    await expect(page.locator('textarea[name="reason"]')).toBeFocused();
    await page.locator('textarea[name="reason"]').fill("Fictional assessment requires clearer supporting evidence.");
    await page.getByRole("button", { name: action.button, exact: true }).click();
    await expect(businessCard(page).getByText(action.status, { exact: true })).toBeVisible();
    await expect(chainCard(page).getByText("Not applicable", { exact: true })).toBeVisible();
  });
}

test("a stale revision blocks review until refresh and renewed evidence acknowledgment", async ({ page }) => {
  await login(page, "A");
  const item = await create(page, "stale-version");
  await becomeReviewer(page, item);
  await acknowledgeEvidence(page);
  await page.getByText("Mock review test controls", { exact: true }).click();
  await page.getByRole("button", { name: "Simulate a stale revision", exact: true }).click();
  await expect(page.getByText(/The mock server revision changed/)).toBeVisible();
  await page.getByRole("button", { name: "Approve case", exact: true }).click();
  await expect(page.locator('.notice[role="alert"]')).toContainText("VERSION_CONFLICT");
  await expect(page.getByRole("button", { name: "Approve case", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Refresh before reviewing again", exact: true }).click();
  await expect(page.getByText(`CASE · REVISION ${item.version + 1}`, { exact: true })).toBeVisible();
  await expect(page.locator('input[name="reviewedEvidenceVersionIds"]').first()).not.toBeChecked();
  await acknowledgeEvidence(page);
  await page.getByRole("button", { name: "Approve case", exact: true }).click();
  await expect(businessCard(page).getByText("Verified", { exact: true })).toBeVisible();
});

test("server validation maps to the duplicate need row and focuses that input", async ({ page }) => {
  await login(page, "A");
  await fillCreateForm(page, "validation-row");
  await page.getByLabel("Item 2 name", { exact: true }).fill("Rice");
  await page.getByLabel("Item 2 unit", { exact: true }).fill("bag");
  const responsePromise = page.waitForResponse(response => new URL(response.url()).pathname === "/api/cases" && response.request().method() === "POST");
  await page.getByRole("button", { name: "Submit case", exact: true }).click();
  expect((await responsePromise).status()).toBe(400);
  await expect(page.getByLabel("Item 2 name", { exact: true })).toBeFocused();
  await expect(page.locator('[id="error-needs[1].name"]')).toHaveText("duplicate need");
  await page.getByLabel("Item 2 name", { exact: true }).fill("Hygiene kit");
  await page.getByLabel("Item 2 unit", { exact: true }).fill("kit");
  await page.getByRole("button", { name: "Submit case", exact: true }).click();
  await expect(businessCard(page).getByText("Submitted", { exact: true })).toBeVisible();
});

test("unknown create outcome survives Back and transient session failure without a second case", async ({ page }) => {
  await login(page, "A");
  const title = await fillCreateForm(page, "unknown-outcome");
  await page.locator(".demo-tools summary").click();
  await page.getByRole("button", { name: "Lose next write response", exact: true }).click();
  await expect(page.getByText("Next write will commit but its response will be lost. Retry with the same key.")).toBeVisible();
  const captured: { key: string | undefined; body: string | null }[] = [];
  page.on("request", request => {
    if (new URL(request.url()).pathname === "/api/cases" && request.method() === "POST") captured.push({ key: request.headers()["idempotency-key"], body: request.postData() });
  });
  await page.getByRole("button", { name: "Submit case", exact: true }).click();
  await expect(page.getByText("The outcome is unknown.", { exact: true })).toBeVisible();
  await expect(page.getByLabel("Internal title", { exact: true })).toBeDisabled();
  await page.goBack();
  await expect(page.getByRole("heading", { name: "Organization cases", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Create case", exact: true }).click();
  await expect(page.getByText("The outcome is unknown.", { exact: true })).toBeVisible();
  await page.route("**/api/auth/me", route => route.fulfill({ status: 503, json: { code: "DEPENDENCY_UNAVAILABLE" } }), { times: 1 });
  await page.evaluate(() => window.dispatchEvent(new Event("focus")));
  await expect(page.getByRole("button", { name: "Check session again" })).toBeVisible();
  await page.getByRole("button", { name: "Check session again" }).click();
  await expect(page.getByLabel("Internal title", { exact: true })).toHaveValue(title);
  await page.route("**/api/cases", route => route.fulfill({ status: 403, json: { code: "CSRF_INVALID" } }), { times: 1 });
  await page.getByRole("button", { name: "Retry same request", exact: true }).click();
  await expect(page.getByRole("heading", { name: title, exact: true })).toBeVisible();
  expect(captured).toHaveLength(3);
  expect(captured[0].key).toBeTruthy();
  expect(captured[1]).toEqual(captured[0]);
  expect(captured[2]).toEqual(captured[0]);
  const response = await page.request.get(`/api/cases?organizationId=${organizationId}&page=0&size=100`);
  expect(response.status()).toBe(200);
  expect((await response.json()).items.filter((item: { internalTitle: string }) => item.internalTitle === title)).toHaveLength(1);
});

test("empty/error list states recover, then expired session removes private state", async ({ page }) => {
  await login(page, "A");
  await page.getByRole("combobox", { name: "Status", exact: true }).selectOption("COMPLETED");
  await expect(page.getByRole("heading", { name: "No cases with this status", exact: true })).toBeVisible();
  await page.locator(".demo-tools summary").click();
  await page.getByRole("button", { name: "Fail next read", exact: true }).click();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.locator(".main-content").getByRole("alert").last()).toContainText("DEPENDENCY_UNAVAILABLE");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByRole("heading", { name: "No cases with this status", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Expire session", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Welcome back", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Organization cases", exact: true })).toHaveCount(0);
  await page.goto("/cases");
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => Object.keys(localStorage))).toEqual([]);
});

test("mobile login, list, creation and detail stay within the viewport", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/login");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await login(page, "A");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await fillCreateForm(page, "mobile");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
  await page.getByRole("button", { name: "Submit case", exact: true }).click();
  await expect(businessCard(page).getByText("Submitted", { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
});
