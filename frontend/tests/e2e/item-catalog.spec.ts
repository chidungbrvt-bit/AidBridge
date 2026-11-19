import { test, expect, type Page } from "@playwright/test";
import { createMockDonorApi, DEMO_CAMPAIGN_ID } from "../../features/donor/mock";

async function createForm(page: Page) {
  await page.goto("/login");
  await page.getByLabel("Email address").fill("minh.a@example.test");
  await page.getByLabel("Password", { exact: true }).fill("Mock-only-123!");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Every need starts here." })).toBeVisible();
  await page.goto("/cases/new");
  await page.getByLabel("Fictional beneficiary alias", { exact: true }).fill("Catalog demo household");
  await page.getByLabel("Approximate area", { exact: true }).fill("Fictional North district");
  await page.getByLabel("Internal title", { exact: true }).fill(`Catalog ${crypto.randomUUID()}`);
  await page.locator('[name="consentConfirmed"]').check();
  await page.getByRole("checkbox", { name: /Fictional intake record/ }).check();
  return page.getByLabel("Item 1 name", { exact: true });
}

function createResponse(page: Page) {
  return page.waitForResponse(response => new URL(response.url()).pathname === "/api/cases" && response.request().method() === "POST");
}

for (const language of ["en", "vi"] as const) {
  test(`catalog selection sends canonical Rice/kg in ${language}`, async ({ page }) => {
    const selector = await createForm(page);
    if (language === "vi") await page.getByRole("button", { name: "VI", exact: true }).click();
    await selector.selectOption({ label: language === "vi" ? "Bộ đèn pin, pin và còi cứu hộ" : "Emergency lighting kit" });
    await expect(page.getByLabel("Item 1 unit", { exact: true })).toHaveValue(language === "vi" ? "bộ" : "set");
    await expect(page.getByText(language === "vi" ? "Khẩn cấp · 24–72 giờ đầu" : "Urgent · first 24–72 h", { exact: true })).toBeVisible();
    await selector.selectOption({ label: language === "vi" ? "Gạo" : "Rice" });
    await expect(selector).toHaveValue("Rice");
    await expect(page.getByLabel("Item 1 unit", { exact: true })).toHaveValue("kg");
    await expect(page.getByLabel("Item 1 unit", { exact: true })).toHaveAttribute("readonly", "");
    await expect(selector.locator("optgroup")).toHaveCount(12);
    const response = createResponse(page);
    await page.getByRole("button", { name: language === "vi" ? "Gửi hồ sơ" : "Submit case", exact: true }).click();
    const saved = await response;
    expect(saved.status()).toBe(201);
    expect(saved.request().postDataJSON().needs[0]).toEqual({ name: "Rice", unit: "kg", quantity: 2 });
    const detail = await saved.json();
    await expect(page).toHaveURL(new RegExp(`/cases/${detail.id}$`));
    if (language === "en") await page.getByRole("button", { name: "VI", exact: true }).click();
    const table = page.getByRole("table", { name: "Hàng hóa cần hỗ trợ" });
    await expect(table.getByRole("cell", { name: "Gạo", exact: true })).toBeVisible();
    await expect(table.getByRole("cell", { name: "kg", exact: true })).toBeVisible();
    await page.getByRole("button", { name: "EN", exact: true }).click();
    await expect(page.getByRole("table", { name: "Requested goods" }).getByRole("cell", { name: "Rice", exact: true })).toBeVisible();
  });
}

test("duplicate catalog and alias names focus the row and send no create request", async ({ page }) => {
  await createForm(page);
  const writes: string[] = [];
  page.on("request", request => {
    if (request.method() === "POST" && new URL(request.url()).pathname === "/api/cases") writes.push(request.url());
  });
  await page.getByLabel("Item 2 name", { exact: true }).selectOption("Rice");
  await page.getByRole("button", { name: "Submit case", exact: true }).click();
  await expect(page.locator('[id="error-needs[1].name"]')).toHaveText("duplicate need");
  await expect(page.getByLabel("Item 2 name", { exact: true })).toBeFocused();
  expect(writes).toEqual([]);
  await page.getByLabel("Item 2 name", { exact: true }).selectOption("other");
  await page.getByLabel("Item 2 other name", { exact: true }).fill("gạo");
  await page.getByRole("button", { name: "VI", exact: true }).click();
  await page.locator('button[type="submit"]').click();
  await expect(page.locator('[id="error-needs[1].name"]')).toHaveText("Mặt hàng đã được chọn ở dòng trước");
  await expect(page.locator('[name="needs[1].name"]')).toBeFocused();
  expect(writes).toEqual([]);
});

test("Other preserves arbitrary names and units through creation, language switching and editing", async ({ page }) => {
  const selector = await createForm(page);
  await selector.selectOption("other");
  await page.getByLabel("Item 1 other name", { exact: true }).fill("Welcome back");
  await page.getByLabel("Item 1 unit", { exact: true }).fill("crate");
  const response = createResponse(page);
  await page.getByRole("button", { name: "Submit case", exact: true }).click();
  const saved = await response;
  expect(saved.status()).toBe(201);
  expect(saved.request().postDataJSON().needs[0]).toEqual({ name: "Welcome back", unit: "crate", quantity: 2 });
  const detail = await saved.json();
  await expect(page).toHaveURL(new RegExp(`/cases/${detail.id}$`));
  await page.getByRole("button", { name: "VI", exact: true }).click();
  await expect(page.getByRole("cell", { name: "Welcome back", exact: true })).toBeVisible();
  await expect(page.getByRole("cell", { name: "crate", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "EN", exact: true }).click();
  await page.getByRole("link", { name: "Edit case", exact: true }).click();
  await expect(page.getByLabel("Item 1 name", { exact: true })).toHaveValue("other");
  await expect(page.getByLabel("Item 1 other name", { exact: true })).toHaveValue("Welcome back");
  await expect(page.getByLabel("Item 1 unit", { exact: true })).toBeEditable();
  await page.getByLabel("Item 1 quantity", { exact: true }).fill("3");
  const revision = page.waitForResponse(response => response.request().method() === "PATCH" && response.url().endsWith(`/api/cases/${detail.id}`));
  await page.getByRole("button", { name: "Save revision", exact: true }).click();
  const patched = await revision;
  expect(patched.status()).toBe(200);
  expect(patched.request().postDataJSON().needs[0]).toEqual({ name: "Welcome back", unit: "crate", quantity: 3 });
  await page.getByLabel("Item 1 name", { exact: true }).selectOption("Rice");
  await expect(page.getByLabel("Item 1 unit", { exact: true })).toHaveValue("kg");
  await expect(page.getByLabel("Item 1 unit", { exact: true })).toHaveAttribute("readonly", "");
});


test("server field validation still focuses the catalog selector", async ({ page }) => {
  await createForm(page);
  await page.route("**/api/cases", async route => {
    if (route.request().method() !== "POST") return route.continue();
    return route.fulfill({ status: 400, json: {
      status: 400, code: "VALIDATION_ERROR", title: "Invalid needs",
      fieldErrors: [{ field: "needs[1].name", code: "DUPLICATE_NEED" }],
    } });
  });
  const response = createResponse(page);
  await page.getByRole("button", { name: "Submit case", exact: true }).click();
  expect((await response).status()).toBe(400);
  await expect(page.getByLabel("Item 2 name", { exact: true })).toBeFocused();
  await expect(page.locator('[id="error-needs[1].name"]')).toHaveText("duplicate need");
  await expect(page.getByRole("button", { name: "Submit case", exact: true })).toBeEnabled();
});

test("legacy aliases preselect the catalog and keep saved units until a new item is selected", async ({ page }) => {
  await createForm(page);
  // Read the same authenticated mock API used by the form to create historical data.
  const csrf = await (await page.request.get("/api/auth/csrf")).json();
  const created = await page.request.post("/api/cases", {
    headers: { [csrf.headerName]: csrf.token, "Idempotency-Key": crypto.randomUUID() },
    data: {
      organizationId: "10000000-0000-4000-8000-000000000001",
      beneficiaryAlias: "Historical catalog demo", approximateArea: "Fictional district",
      internalTitle: `Historical ${crypto.randomUUID()}`, priority: "NORMAL",
      consent: { basis: "CONSENT", scope: "Fictional test only", capturedAt: new Date().toISOString(), publicSummaryAllowed: false },
      needs: [{ name: "Food", unit: "PACKAGE", quantity: 2 }],
      evidenceFixtureKeys: ["riverlight-intake-v1"],
    },
  });
  expect(created.status()).toBe(201);
  const detail = await created.json();
  await page.goto(`/cases/${detail.id}`);
  await expect(page.getByRole("cell", { name: "Food", exact: true })).toBeVisible();
  await expect(page.getByRole("cell", { name: "PACKAGE", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Edit case", exact: true }).click();
  await expect(page.getByLabel("Item 1 name", { exact: true })).toHaveValue("Dry rations");
  await expect(page.getByLabel("Item 1 unit", { exact: true })).toHaveValue("PACKAGE");
  await page.getByRole("button", { name: "VI", exact: true }).click();
  await expect(page.getByLabel("Item 1 unit", { exact: true })).toHaveValue("gói");
  await expect(page.getByLabel("Item 1 name", { exact: true }).locator("option:checked")).toHaveText("Lương khô");
  await page.getByLabel("Item 1 name", { exact: true }).selectOption("Rice");
  await expect(page.getByLabel("Item 1 unit", { exact: true })).toHaveValue("kg");
});


test("donor groups historical goods by category and translates relief labels and quantities", async ({ page }) => {
  await createForm(page);
  const { api } = createMockDonorApi(0);
  const campaign = await api.getCampaign(DEMO_CAMPAIGN_ID);
  await page.route(`**/api/campaigns/${DEMO_CAMPAIGN_ID}`, route => route.fulfill({ json: campaign }));
  await page.goto(`/campaigns/${DEMO_CAMPAIGN_ID}`);
  const table = page.locator(".ab-panel table").first();
  await expect(table.getByRole("rowheader").filter({ hasText: "Rice" })).toBeVisible();
  await expect(table.locator('th[scope="colgroup"]')).toContainText(["Food", "Drinking water"]);
  await expect(table.getByText("Urgent · first 24–72 h", { exact: true })).toBeVisible();
  await expect(page.getByLabel("Quantity (kg)", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "VI", exact: true }).click();
  await expect(table.locator('th[scope="colgroup"]')).toContainText(["Lương thực, thực phẩm", "Nước uống"]);
  await expect(table.getByRole("rowheader").filter({ hasText: "Gạo" })).toBeVisible();
  await expect(table.getByText("Khẩn cấp · 24–72 giờ đầu", { exact: true })).toBeVisible();
  await expect(page.getByLabel("Số lượng (kg)", { exact: true })).toBeVisible();
});


test("Vietnamese catalog fields keep unit and quantity side by side on mobile", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await createForm(page);
  await page.getByRole("button", { name: "VI", exact: true }).click();
  await page.getByLabel("Item 1 name", { exact: true }).selectOption("Infant formula (age-appropriate)");
  const unit = await page.getByLabel("Item 1 unit", { exact: true }).boundingBox();
  const quantity = await page.getByLabel("Item 1 quantity", { exact: true }).boundingBox();
  expect(unit).not.toBeNull();
  expect(quantity).not.toBeNull();
  expect(Math.abs(unit!.y - quantity!.y)).toBeLessThan(2);
  expect(unit!.x).toBeLessThan(quantity!.x);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});


for (const language of ["en", "vi"] as const) {
  test(`instant foods and school combo send fixed units in ${language}`, async ({ page }) => {
    await createForm(page);
    await expect(page.getByLabel("Item 1 unit", { exact: true })).toHaveValue("kg");
    await page.getByRole("button", { name: "+ Add item", exact: true }).click();
    if (language === "vi") await page.getByRole("button", { name: "VI", exact: true }).click();
    await page.getByLabel("Item 1 name", { exact: true }).selectOption("Instant noodles");
    await page.getByLabel("Item 2 name", { exact: true }).selectOption("Instant porridge");
    const school = page.getByLabel("Item 3 name", { exact: true });
    await school.selectOption("School supplies combo");
    await expect(school.locator("option:checked")).toHaveText(language === "vi" ? "Combo đồ dùng học tập" : "School supplies combo");
    for (const index of [1, 2]) {
      await expect(page.getByLabel(`Item ${index} unit`, { exact: true })).toHaveValue(language === "vi" ? "thùng" : "box");
      await expect(page.getByLabel(`Item ${index} unit`, { exact: true })).toHaveAttribute("readonly", "");
    }
    await expect(page.getByLabel("Item 3 unit", { exact: true })).toHaveValue("combo");
    await expect(page.getByLabel("Item 3 unit", { exact: true })).toHaveAttribute("readonly", "");
    for (const retired of ["Notebooks", "Pens", "School bag", "Textbooks"]) {
      await expect(school.locator(`option[value="${retired}"]`)).toHaveCount(0);
    }
    const response = createResponse(page);
    await page.getByRole("button", { name: language === "vi" ? "Gửi hồ sơ" : "Submit case", exact: true }).click();
    const saved = await response;
    expect(saved.status()).toBe(201);
    expect(saved.request().postDataJSON().needs).toEqual([
      { name: "Instant noodles", unit: "box", quantity: 2 },
      { name: "Instant porridge", unit: "box", quantity: 2 },
      { name: "School supplies combo", unit: "combo", quantity: 1 },
    ]);
  });
}


for (const language of ["en", "vi"] as const) {
  test(`relief bundles, seasonings and water send fixed units in ${language}`, async ({ page }) => {
    await createForm(page);
    const lines = [
      { name: "Seasonings", unit: "set", viUnit: "bộ" },
      { name: "Drinking water", unit: "box", viUnit: "thùng" },
      { name: "Hygiene kit", unit: "kit", viUnit: "bộ" },
      { name: "Bedding kit", unit: "set", viUnit: "bộ" },
      { name: "Cooking utensils kit", unit: "set", viUnit: "bộ" },
      { name: "Baby care kit", unit: "set", viUnit: "bộ" },
    ];
    for (let index = 2; index < lines.length; index++) {
      await page.getByRole("button", { name: "+ Add item", exact: true }).click();
    }
    if (language === "vi") await page.getByRole("button", { name: "VI", exact: true }).click();
    for (const [index, line] of lines.entries()) {
      const name = page.getByLabel(`Item ${index + 1} name`, { exact: true });
      await name.selectOption(line.name);
      const unit = page.getByLabel(`Item ${index + 1} unit`, { exact: true });
      await expect(unit).toHaveValue(language === "vi" ? line.viUnit : line.unit);
      await expect(unit).toHaveAttribute("readonly", "");
    }
    const selector = page.getByLabel("Item 1 name", { exact: true });
    await expect(selector.locator("option:checked")).toHaveText(language === "vi" ? "Gia vị" : "Seasonings");
    for (const retired of ["Salt", "Sugar", "Fish sauce", "Cooking oil", "Soap", "Toilet paper", "Water jug", "Bandages", "Cooking pot"]) {
      await expect(selector.locator(`option[value="${retired}"]`)).toHaveCount(0);
    }
    const response = createResponse(page);
    await page.getByRole("button", { name: language === "vi" ? "Gửi hồ sơ" : "Submit case", exact: true }).click();
    const saved = await response;
    expect(saved.status()).toBe(201);
    expect(saved.request().postDataJSON().needs).toEqual(lines.map((line, index) => ({
      name: line.name, unit: line.unit, quantity: index < 2 ? 2 : 1,
    })));
  });
}
