import { test, expect } from "@playwright/test";

// Mock project only: the default stays English, one click switches every label to Vietnamese.
test("EN/VI switch translates login and workspace, survives reload, stores only the language", async ({ page }) => {
  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Sign in" })).toBeVisible();
  await expect(page.getByRole("button", { name: "EN", exact: true })).toHaveAttribute("aria-pressed", "true");
  expect(await page.evaluate(() => Object.keys(localStorage))).toEqual([]);

  await page.getByRole("button", { name: "VI", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Đăng nhập" })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("lang", "vi");

  await page.reload();
  await expect(page.getByRole("heading", { name: "Đăng nhập" })).toBeVisible();

  await page.getByLabel("Địa chỉ email").fill("minh.a@example.test");
  await page.getByLabel("Mật khẩu", { exact: true }).fill("Mock-only-123!");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Mọi nhu cầu bắt đầu từ đây." })).toBeVisible();
  await expect(page.getByRole("link", { name: "Tạo hồ sơ", exact: true })).toBeVisible();

  await page.getByRole("button", { name: "EN", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Every need starts here." })).toBeVisible();
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  expect(await page.evaluate(() => ({ ...localStorage }))).toEqual({ "aidbridge.language": "en" });
});
