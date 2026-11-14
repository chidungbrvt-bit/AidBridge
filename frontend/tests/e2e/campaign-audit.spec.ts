import { test, expect, type Page } from '@playwright/test';

const password = 'Mock-only-123!';
const caseId = '30000000-0000-4000-8000-000000000001';

async function signIn(page: Page, email: string) {
  await page.goto('/login');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in →', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Every need starts here.' })).toBeVisible();
}

test('F06 admin drafts, retries lost publish, and opens the same campaign in F07', async ({ browser, page, request, baseURL }) => {
  const reviewer = await browser.newContext({ baseURL });
  const review = await reviewer.newPage();
  try {
    await signIn(page, 'minh.admin@example.test');
    await page.goto(`/cases/${caseId}`);
    await expect(page.getByRole('heading', { name: 'Essential supplies for Household Willow' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Create campaign draft' })).toHaveCount(0);

    await signIn(review, 'minh.b@example.test');
    await review.goto(`/cases/${caseId}`);
    await expect(review.locator('input[name="reviewedEvidenceVersionIds"]').first()).toBeVisible();
    for (const checkbox of await review.locator('input[name="reviewedEvidenceVersionIds"]').all()) await checkbox.check();
    await review.getByRole('button', { name: 'Approve case', exact: true }).click();
    await expect(review.locator('.status-card').filter({ hasText: 'BUSINESS STATUS' })).toContainText('VERIFIED');
    await expect(review.getByRole('button', { name: 'Create campaign draft' })).toHaveCount(0);
    const deniedToken = await (await review.request.get('/api/auth/csrf')).json();
    const denied = await review.request.post('/api/campaigns', {
      headers: { [deniedToken.headerName]: deniedToken.token, 'Idempotency-Key': crypto.randomUUID() },
      data: { caseId, title: 'Verifier write denied', publicSummary: 'Fictional essentials.' },
    });
    expect(denied.status()).toBe(403);

    await page.getByRole('button', { name: 'Refresh case', exact: true }).click();
    const title = 'River district essentials';
    await page.getByLabel('Campaign title').fill(title);
    await page.getByLabel('Campaign public summary').fill('Fictional food and hygiene supplies for the North River district.');
    const create = page.waitForResponse(response => new URL(response.url()).pathname === '/api/campaigns' && response.request().method() === 'POST');
    await page.getByRole('button', { name: 'Create campaign draft' }).click();
    const created = await create;
    expect(created.status()).toBe(201);
    expect(Object.keys(created.request().postDataJSON()).sort()).toEqual(['caseId', 'publicSummary', 'title']);
    const draft = await created.json();
    expect(draft.status).toBe('DRAFT');
    expect(draft.auditUrl).toMatch(/^\/api\/public\/cases\/0x[0-9a-f]{64}\/audit$/);
    expect(draft.items).toHaveLength(2);
    expect(draft.items.map(({ name, targetQty, unit }: { name: string; targetQty: number; unit: string }) => [name, targetQty, unit]))
      .toEqual(expect.arrayContaining([['Hygiene kit', 2, 'kit'], ['Rice', 2, 'bag']]));
    expect((await request.get(`/api/campaigns/${draft.id}`)).status()).toBe(404);
    expect((await review.request.get(`/api/campaigns/${draft.id}`)).status()).toBe(404);
    const listBefore = await (await request.get('/api/campaigns')).json();
    expect(listBefore.items.some((row: { id: string }) => row.id === draft.id)).toBe(false);
    expect((await page.request.get(`/api/campaigns/${draft.id}`)).status()).toBe(200);

    const draftRows = page.getByRole('table', { name: 'Campaign goods progress' }).getByRole('rowheader');
    await expect(draftRows).toHaveText(['Hygiene kit', 'Rice']);
    await page.locator('.demo-tools summary').click();
    await page.getByRole('button', { name: 'Lose next write response' }).click();
    await expect(page.getByText(/Next write will commit but its response will be lost/)).toBeVisible();
    const attempts: { key?: string; body: string | null }[] = [];
    page.on('request', sent => {
      if (new URL(sent.url()).pathname === `/api/campaigns/${draft.id}/publish`) {
        attempts.push({ key: sent.headers()['idempotency-key'], body: sent.postData() });
      }
    });
    await page.getByRole('button', { name: 'Publish campaign' }).click();
    await expect(page.getByText('UNKNOWN_OUTCOME')).toBeVisible();
    await page.getByRole('button', { name: 'Retry same campaign request' }).click();
    await expect(page.locator('.status-card').filter({ hasText: 'BUSINESS STATUS' })).toContainText('IN PROGRESS');
    expect(attempts).toHaveLength(2);
    expect(attempts[0]).toEqual({ key: expect.any(String), body: '{}' });
    expect(attempts[1]).toEqual(attempts[0]);

    const publicCampaign = await request.get(`/api/campaigns/${draft.id}`);
    expect(publicCampaign.status()).toBe(200);
    const published = await publicCampaign.json();
    expect(published).toMatchObject({ id: draft.id, status: 'PUBLISHED', title, auditUrl: draft.auditUrl });
    expect(published.items.map((item: { id: string }) => item.id).sort()).toEqual(draft.items.map((item: { id: string }) => item.id).sort());
    expect(published).not.toHaveProperty('caseId');
    expect(published).not.toHaveProperty('beneficiaryAlias');
    expect(published).not.toHaveProperty('evidenceVersions');
    const listAfter = await (await request.get('/api/campaigns')).json();
    expect(listAfter.items.filter((row: { id: string }) => row.id === draft.id)).toHaveLength(1);

    await page.getByRole('link', { name: 'View published campaign' }).click();
    await expect(page.getByRole('heading', { name: title })).toBeVisible();
    await expect(page.locator(`a[href="${published.auditUrl}"]`)).toHaveCount(0);
    await expect(page.getByRole('table', { name: 'Campaign goods progress' }).getByRole('rowheader')).toContainText(['Hygiene kit', 'Rice']);
    await expect(page.getByRole('button', { name: 'Pledge goods →' })).toBeDisabled();
  } finally {
    await reviewer.close();
  }
});

test('F05 shows three chain fixtures with separate business and evidence state', async ({ page }) => {
  await signIn(page, 'minh.a@example.test');
  await page.getByRole('link', { name: 'Audit preview' }).click();
  const timeline = page.getByRole('region', { name: 'Audit timeline' });
  await expect(timeline.getByRole('listitem')).toHaveCount(3);
  for (const label of ['Pending on chain', 'Failed on chain', 'Confirmed on chain']) {
    const row = timeline.getByRole('listitem').filter({ hasText: label });
    await expect(row.getByText('VERIFIED', { exact: true })).toBeVisible();
    await expect(row.getByText('APPROVED', { exact: true })).toBeVisible();
  }
  const confirmed = timeline.getByRole('listitem').filter({ hasText: 'Confirmed on chain' });
  await expect(confirmed.getByText('Independent Fuji proof fixture')).toBeVisible();
  await expect(confirmed.getByText('60893112', { exact: true })).toBeVisible();
  await expect(timeline.getByRole('link')).toHaveCount(0);
});
