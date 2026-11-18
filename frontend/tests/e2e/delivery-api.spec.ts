import { test, expect, type Page } from '@playwright/test';
import { MOCK_CASE_ID, MOCK_CREATE_CASE, MOCK_IDENTITIES } from '../../src/lib/mock-fixtures';
import type { DeliveryRequest } from '../../src/lib/goods';

const volunteer = MOCK_IDENTITIES[0].user;
const campaignId = '10000000-0000-4000-8000-000000000001';
const campaignItemId = '20000000-0000-4000-8000-000000000001';
const evidenceId = '30000000-0000-4000-8000-000000000002';
const deliveryId = '60000000-0000-4000-8000-000000000001';
const auditUrl = `/api/public/cases/0x${'37'.repeat(32)}/audit`;

function expectDeliveryContextReads(reads: string[], expected: number) {
  // The B10 review panel also reads C03; each D01 P04 read still needs fresh C03 context.
  expect(reads.every(read => read === 'case' || read === 'campaign')).toBe(true);
  const campaignReads = reads.flatMap((read, index) => read === 'campaign' ? [index] : []);
  expect(campaignReads).toHaveLength(expected);
  for (let index = 0; index < campaignReads.length; index++) {
    expect(reads.slice(index === 0 ? 0 : campaignReads[index - 1] + 1, campaignReads[index])).toContain('case');
  }
}

// HTTP fixtures exercise the real-mode UI and client.
async function harness(page: Page, user = volunteer) {
  const state = {
    delivered: 0, stale: false, loseResponse: false, reads: [] as string[],
    writes: [] as { key: string; body: DeliveryRequest }[],
  };
  const detail = {
    ...MOCK_CREATE_CASE, id: MOCK_CASE_ID, creator: { id: volunteer.userId, fullName: 'Fictional volunteer' },
    status: 'IN_PROGRESS', version: 1, needs: [], createdAt: '2026-11-09T00:00:00Z',
    evidenceVersions: [{ id: evidenceId, versionNo: 1, mimeType: 'text/plain', byteSize: 10 }],
  };
  await page.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/auth/me') return route.fulfill({ json: user });
    if (path === '/api/auth/csrf') return route.fulfill({ json: { token: 'd01-test-csrf', headerName: 'X-CSRF-TOKEN' } });
    if (path === `/api/cases/${MOCK_CASE_ID}`) {
      if (user.memberships[0]?.organizationId !== detail.organizationId) {
        return route.fulfill({ status: 404, json: { code: 'RESOURCE_NOT_FOUND' } });
      }
      return route.fulfill({ json: detail });
    }
    if (path === `/api/org/cases/${MOCK_CASE_ID}`) {
      state.reads.push('case');
      return route.fulfill({ json: { ...detail, campaignId, outstandingCommitments: [] } });
    }
    if (path === `/api/campaigns/${campaignId}`) {
      state.reads.push('campaign');
      return route.fulfill({ json: { id: campaignId, status: 'PUBLISHED',
        items: [{ id: campaignItemId, name: 'Rice', unit: 'PACKAGE', receivedQty: 5, deliveredQty: state.delivered }] } });
    }
    if (path === `/api/cases/${MOCK_CASE_ID}/deliveries` && request.method() === 'POST') {
      const headers = await request.allHeaders();
      expect(headers['x-csrf-token']).toBe('d01-test-csrf');
      expect(headers['idempotency-key']).toBeTruthy();
      const body: DeliveryRequest = request.postDataJSON();
      const key = headers['idempotency-key'];
      const prior = state.writes.find(write => write.key === key);
      state.writes.push({ key, body });
      if (state.stale) {
        state.stale = false;
        state.delivered = 3;
        return route.fulfill({ status: 409, json: { code: 'QUANTITY_EXCEEDED' } });
      }
      const delivered = body.outcome === 'DELIVERED';
      if (delivered && !prior) state.delivered += body.items.reduce((sum, item) => sum + item.quantity, 0);
      if (state.loseResponse) {
        state.loseResponse = false;
        return route.fulfill({ status: 504, json: { code: 'DEPENDENCY_UNAVAILABLE' } });
      }
      return route.fulfill({ status: 201, headers: { Location: `/api/deliveries/${deliveryId}` }, json: {
        id: deliveryId, caseId: MOCK_CASE_ID, recorderId: volunteer.userId,
        items: body.items, evidenceVersionIds: body.evidenceVersionIds, outcome: body.outcome,
        reviewStatus: delivered ? 'PENDING' : 'NOT_APPLICABLE',
        blockchainStatus: delivered ? 'PENDING_ON_CHAIN' : 'NOT_APPLICABLE',
        auditUrl: delivered ? auditUrl : null, auditEventId: 'fixture-event', recordedAt: detail.createdAt,
      } });
    }
    if (path === `/api/deliveries/${deliveryId}` || path === auditUrl) state.reads.push(path);
    return route.fulfill({ status: 404, json: { code: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto(`/cases/${MOCK_CASE_ID}`);
  return { state, panel: page.getByRole('region', { name: 'Goods delivery' }) };
}

test('D01 refreshes stock after a conflict and replays a lost success with the same evidence and key', async ({ page }) => {
  const { state, panel } = await harness(page);
  const quantity = panel.getByLabel('Rice delivery quantity (PACKAGE)', { exact: true });
  const submit = panel.getByRole('button', { name: 'Record delivery' });
  await expect(panel.getByText('Available: 5')).toBeVisible();
  await quantity.fill('4');
  await panel.getByRole('checkbox', { name: 'Evidence 1 · Version 1' }).check();
  state.stale = true;
  await submit.click();
  await expect(panel.getByRole('alert')).toContainText('QUANTITY_EXCEEDED');
  await expect(panel.getByText('Available: 2')).toBeVisible();
  await expect(quantity).toHaveValue('');
  expectDeliveryContextReads(state.reads, 2);
  await quantity.fill('2');
  state.loseResponse = true;
  await submit.click();
  const retry = panel.getByRole('button', { name: 'Retry same delivery' });
  await expect(retry).toBeVisible();
  await expect(quantity).toBeDisabled();
  await retry.click();
  const success = panel.getByRole('status').filter({ hasText: 'Delivery recorded' });
  await expect(success).toContainText('Pending on chain');
  await expect(success).toContainText(deliveryId);
  await expect(panel.getByText('Available: 0')).toBeVisible();
  expect(state.writes).toHaveLength(3);
  expect(state.writes[1]).toEqual(state.writes[2]);
  expect(state.writes[1].key).not.toBe(state.writes[0].key);
  expect(state.writes[1].body).toEqual({
    outcome: 'DELIVERED', items: [{ campaignItemId, quantity: 2 }], evidenceVersionIds: [evidenceId],
  });
  expectDeliveryContextReads(state.reads, 3);
  await expect(panel.locator('a')).toHaveCount(0);
});

test('D01 FAILED sends a reason and empty arrays and keeps stock unchanged', async ({ page }) => {
  const { state, panel } = await harness(page);
  await panel.getByLabel('Handed over at (UTC, optional)').fill('2026-11-09T01:00');
  await panel.getByLabel('Delivery outcome').selectOption('FAILED');
  await panel.getByLabel('Reason for failed attempt').fill('Road was closed.');
  await panel.getByRole('button', { name: 'Record delivery' }).click();
  const success = panel.getByRole('status').filter({ hasText: 'Failed attempt recorded' });
  await expect(success.getByText('Not applicable', { exact: true })).toHaveCount(2);
  expect(state.writes[0].body).toEqual({
    outcome: 'FAILED', items: [], evidenceVersionIds: [], reason: 'Road was closed.',
  });
  await panel.getByLabel('Delivery outcome').selectOption('DELIVERED');
  await expect(panel.getByText('Available: 5')).toBeVisible();
  await expect(panel.locator('a')).toHaveCount(0);
});

for (const [name, index] of [['same-org verifier', 1], ['volunteer from another organization', 3]] as const) {
  test(`D01 keeps the form unavailable for a ${name}`, async ({ page }) => {
    const { state, panel } = await harness(page, MOCK_IDENTITIES[index].user);
    const result = index === 3 ? page.getByRole('alert').filter({ hasText: 'RESOURCE_NOT_FOUND' })
      : page.getByRole('heading', { name: MOCK_CREATE_CASE.internalTitle });
    await expect(result).toBeVisible();
    await expect(panel).toHaveCount(0);
    // Same-org verifiers may read B10 review context, but cannot read D01 stock or submit a delivery.
    expect(state.reads).toEqual(index === 3 ? [] : ['case']);
    expect(state.writes).toEqual([]);
  });
}
