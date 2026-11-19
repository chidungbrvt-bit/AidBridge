import { test, expect, type Page } from '@playwright/test';

const required = ['REAL_A_EMAIL', 'REAL_A_PASSWORD', 'REAL_B_EMAIL', 'REAL_B_PASSWORD', 'REAL_ORGANIZATION_ID', 'REAL_EVIDENCE_FIXTURE_KEYS'] as const;
const configured = required.every(name => !!process.env[name]?.trim());

test.skip(!configured, 'Not Run: authorized backend test identities and organization fixtures are required.');

test('real HTTP: separate A/B sessions, create, denied self-review, revise/resubmit, approve, logout', async ({ browser, baseURL }) => {
  test.setTimeout(120_000);
  const aContext = await browser.newContext({ baseURL });
  const bContext = await browser.newContext({ baseURL });
  const a = await aContext.newPage(), b = await bContext.newPage();
  const org = process.env.REAL_ORGANIZATION_ID!;
  const keys = process.env.REAL_EVIDENCE_FIXTURE_KEYS!.split(',').map(key => key.trim()).filter(Boolean);
  // Reject a mock target before any supplied credentials reach it.
  const preflight = await a.request.get('/api/auth/csrf');
  expect(preflight.status()).toBe(200);
  expect(preflight.headers()['x-aidbridge-mock']).toBeUndefined();
  for (const who of ['A', 'B']) {
    expect(['minh.a@example.test', 'minh.b@example.test', 'minh.reader@example.test', 'minh.outsider@example.test', 'minh.admin@example.test'].includes(process.env[`REAL_${who}_EMAIL`]!.toLowerCase())).toBe(false);
    expect(process.env[`REAL_${who}_PASSWORD`] === 'Mock-only-123!').toBe(false);
  }
  const selectOrganization = async (page: Page) => {
    const control = page.getByLabel('Active organization');
    await control.selectOption(org);
    await expect(control).toHaveValue(org);
  };
  const login = async (page: Page, who: 'A' | 'B') => {
    await page.goto('/login');
    await expect(page.getByText('Real API', { exact: true })).toBeVisible();
    await page.getByLabel('Email address').fill(process.env[`REAL_${who}_EMAIL`]!);
    await page.getByLabel('Password', { exact: true }).fill(process.env[`REAL_${who}_PASSWORD`]!);
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Every need starts here.' })).toBeVisible();
    await selectOrganization(page);
    const me = await (await page.request.get('/api/auth/me')).json();
    const membership = me.memberships.find((item: { organizationId: string; status?: string; active?: boolean }) =>
      item.organizationId === org && (item.status === 'ACTIVE' || item.active === true));
    expect(membership?.roles).toContain('VERIFIER');
    if (who === 'A') expect(membership?.roles).toContain('VOLUNTEER');
    return me;
  };
  const acknowledge = async () => {
    await expect(b.locator('input[name="reviewedEvidenceVersionIds"]').first()).toBeVisible();
    for (const checkbox of await b.locator('input[name="reviewedEvidenceVersionIds"]').all()) await checkbox.check();
  };
  try {
    const aMe = await login(a, 'A'), bMe = await login(b, 'B');
    expect(aMe.userId !== bMe.userId).toBe(true);
    const title = `F04 fictional browser smoke ${Date.now()}`;
    await a.goto('/cases/new');
    await selectOrganization(a);
    await a.getByLabel('Fictional beneficiary alias', { exact: true }).fill('Demo Household A');
    await a.getByLabel('Approximate area', { exact: true }).fill('Fictional test district');
    await a.getByLabel('Internal title', { exact: true }).fill(title);
    await a.getByLabel('I confirm this is a simulated consent record for fictional data.', { exact: true }).check();
    await expect(a.locator('input[name="evidenceFixtureKeys"]').first()).toBeVisible();
    for (const key of keys) {
      const input = a.locator('input[name="evidenceFixtureKeys"]');
      const count = await input.count();
      let found = false;
      for (let index = 0; index < count; index++) {
        if (await input.nth(index).getAttribute('value') === key) { await input.nth(index).check(); found = true; break; }
      }
      expect(found, 'Configured fixture must be allowed for this organization').toBe(true);
    }
    const createResponse = a.waitForResponse(response => new URL(response.url()).pathname === '/api/cases' && response.request().method() === 'POST');
    await a.getByRole('button', { name: 'Submit case', exact: true }).click();
    const created = await createResponse;
    expect(created.status()).toBe(201);
    const item = await created.json();
    expect(item.needs).toHaveLength(2);
    await expect(a.getByRole('heading', { name: title, exact: true })).toBeVisible();
    await expect(a.getByRole('button', { name: 'Approve case', exact: true })).toBeDisabled();
    const csrf = await (await a.request.get('/api/auth/csrf')).json();
    const denied = await a.request.post(`/api/cases/${item.id}/verify`, {
      headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': crypto.randomUUID() },
      data: { expectedVersion: item.version, reviewedEvidenceVersionIds: item.evidenceVersions.map((entry: { id: string }) => entry.id) },
    });
    expect(denied.status()).toBe(403);
    expect((await denied.json()).code).toBe('FORBIDDEN');
    await b.goto(`/cases/${item.id}`);
    await selectOrganization(b);
    await acknowledge();
    const evidence = await b.request.get(`/api/evidence-versions/${item.evidenceVersions[0].id}/content`);
    expect(evidence.status()).toBe(200);
    expect(evidence.headers()['content-disposition']).toMatch(/^attachment/i);
    expect(evidence.headers()['cache-control']).toContain('no-store');
    await b.getByLabel('Review reason').fill('Clarify the fictional household quantity.');
    await b.getByRole('button', { name: 'Request changes', exact: true }).click();
    await expect(b.locator('.status-card').filter({ hasText: 'BUSINESS STATUS' })).toContainText('Changes requested');
    await a.goto(`/cases/${item.id}/edit`);
    await selectOrganization(a);
    await a.getByLabel('Item 1 quantity', { exact: true }).fill('3');
    await a.getByRole('button', { name: 'Save revision', exact: true }).click();
    await expect(a.getByText(/Revision saved/)).toBeVisible();
    await a.getByRole('button', { name: 'Resubmit for review', exact: true }).click();
    await expect(a.locator('.status-card').filter({ hasText: 'BUSINESS STATUS' })).toContainText('Submitted');
    await b.reload();
    await selectOrganization(b);
    await acknowledge();
    await b.getByRole('button', { name: 'Approve case', exact: true }).click();
    await expect(b.locator('.status-card').filter({ hasText: 'BUSINESS STATUS' })).toContainText('Verified');
    await expect(b.locator('.status-card').filter({ hasText: 'BLOCKCHAIN SYNC' })).toContainText('Pending on chain');
    await b.reload();
    await selectOrganization(b);
    await expect(b.locator('.status-card').filter({ hasText: 'BUSINESS STATUS' })).toContainText('Verified');
    // B04 detail does not project chain state. Do not invent confirmation after refresh.
    await expect(b.locator('.status-card').filter({ hasText: 'BLOCKCHAIN SYNC' })).toContainText('Unknown');
    expect(await a.evaluate(() => Object.keys(localStorage))).toEqual([]);
  } finally {
    for (const page of [a, b]) {
      const signOut = page.getByRole('button', { name: 'Sign out', exact: true });
      if (await signOut.isVisible()) {
        await signOut.click();
        await expect(page.getByRole('heading', { name: 'Sign in', exact: true })).toBeVisible();
        expect((await page.request.get('/api/auth/me')).status()).toBe(401);
      }
    }
    await aContext.close(); await bContext.close();
  }
});
