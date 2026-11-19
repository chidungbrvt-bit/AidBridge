import { expect, test } from '@playwright/test';

const required = ['REAL_F06_ADMIN_EMAIL', 'REAL_F06_ADMIN_PASSWORD', 'REAL_F06_VERIFIED_CASE_ID', 'REAL_ORGANIZATION_ID'] as const;
const configured = required.every(name => !!process.env[name]?.trim());

test.skip(!configured, 'Not Run: supply a dedicated authorized test org admin and verified case with public consent.');

test('F06 real P01–P04: staff draft, publish and public read of a test campaign', async ({ page, request }) => {
  const org = process.env.REAL_ORGANIZATION_ID!;
  const caseId = process.env.REAL_F06_VERIFIED_CASE_ID!;
  const email = process.env.REAL_F06_ADMIN_EMAIL!;
  const password = process.env.REAL_F06_ADMIN_PASSWORD!;
  expect(email.toLowerCase().endsWith('@example.test') && password === 'Mock-only-123!').toBe(false);
  const preflight = await page.request.get('/api/auth/csrf');
  expect(preflight.status()).toBe(200);
  expect(preflight.headers()['x-aidbridge-mock']).toBeUndefined();
  await page.goto('/login');
  await expect(page.getByText('REAL API', { exact: true })).toBeVisible();
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in →', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible();
  await page.goto(`/cases/${encodeURIComponent(caseId)}`);
  const active = page.getByLabel('Active organization');
  await active.selectOption(org);
  await expect(active).toHaveValue(org);
  await expect(page.locator('.status-card').filter({ hasText: 'BUSINESS STATUS' })).toContainText('Verified');
  const me = await (await page.request.get('/api/auth/me')).json();
  expect(me.memberships.find((m: { organizationId: string; status: string }) => m.organizationId === org && m.status === 'ACTIVE')?.roles).toContain('ORG_ADMIN');

  const title = `F06 fictional goods smoke ${Date.now()}`;
  await page.getByLabel('Campaign title').fill(title);
  await page.getByLabel('Campaign public summary').fill('Fictional essential goods for an authorized test organization.');
  const creating = page.waitForResponse(response => new URL(response.url()).pathname === '/api/campaigns' && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Create campaign draft' }).click();
  const created = await creating;
  expect(created.status()).toBe(201);
  const draft = await created.json();
  expect(draft.status).toBe('DRAFT');
  expect(draft.items.length).toBeGreaterThanOrEqual(2);
  expect((await page.request.get(`/api/campaigns/${draft.id}`)).status()).toBe(200);
  expect((await request.get(`/api/campaigns/${draft.id}`)).status()).toBe(404);
  const listed = await request.get('/api/campaigns');
  expect(listed.status()).toBe(200);
  expect((await listed.json()).items.some((row: { id: string }) => row.id === draft.id)).toBe(false);
  await expect(active).toHaveValue(org);

  const publishing = page.waitForResponse(response => new URL(response.url()).pathname === `/api/campaigns/${draft.id}/publish`);
  await page.getByRole('button', { name: 'Publish campaign' }).click();
  expect((await publishing).status()).toBe(200);
  await expect(page.locator('.status-card').filter({ hasText: 'BUSINESS STATUS' })).toContainText('In progress');
  const response = await request.get(`/api/campaigns/${draft.id}`);
  expect(response.status()).toBe(200);
  const published = await response.json();
  expect(published).toMatchObject({ id: draft.id, status: 'PUBLISHED', title });
  expect(published).not.toHaveProperty('caseId');
  expect(published).not.toHaveProperty('beneficiaryAlias');
  expect(published).not.toHaveProperty('evidenceVersions');
  const byId = (rows: { id: string; targetQty: number; unit: string }[]) =>
    rows.map(({ id, targetQty, unit }) => ({ id, targetQty, unit })).sort((a, b) => a.id.localeCompare(b.id));
  expect(byId(published.items)).toEqual(byId(draft.items));
  await page.getByRole('link', { name: 'View published campaign' }).click();
  await expect(page.getByRole('heading', { name: title })).toBeVisible();
  await expect(page.getByRole('table', { name: 'Campaign goods progress' }).getByRole('rowheader')).toHaveCount(draft.items.length);
});
