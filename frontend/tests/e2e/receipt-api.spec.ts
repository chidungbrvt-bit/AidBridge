import { test, expect } from '@playwright/test';
import { MOCK_CASE_ID, MOCK_CREATE_CASE, MOCK_IDENTITIES } from '../../src/lib/mock-fixtures';
import type { ReceiptRequest } from '../../src/lib/goods';

const admin = MOCK_IDENTITIES.find(entry => entry.email === 'minh.admin@example.test')!.user;
const staff = { ...admin, memberships: admin.memberships.map(membership => ({ ...membership, roles: [...membership.roles, 'VOLUNTEER'] })) };
const campaignId = '10000000-0000-4000-8000-000000000001';
const campaignItemId = '20000000-0000-4000-8000-000000000001';
const commitmentId = '30000000-0000-4000-8000-000000000001';
const contributionItemId = '40000000-0000-4000-8000-000000000001';
const receiptId = '50000000-0000-4000-8000-000000000001';

// Exercises the real-mode browser client with HTTP stubs. Spring and PostgreSQL are tested in donor-real.
test('R01 replays the same receipt after a lost response and refreshes D01 stock', async ({ page, baseURL }) => {
  const detail = {
    ...MOCK_CREATE_CASE, id: MOCK_CASE_ID, creator: { id: MOCK_IDENTITIES[0].user.userId, fullName: 'Fictional volunteer' },
    status: 'IN_PROGRESS', version: 1, evidenceVersions: [], needs: [], createdAt: '2026-11-09T00:00:00Z',
  };
  const receiptPath = `/api/commitments/${commitmentId}/receipts`;
  let reads = 0;
  let committed = false;
  const writes: { body: ReceiptRequest; key: string }[] = [];
  await page.context().addCookies([{ name: 'JSESSIONID', value: 'r01-test-only', url: baseURL!, httpOnly: true }]);
  await page.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/auth/me') return route.fulfill({ json: staff });
    if (path === '/api/auth/csrf') return route.fulfill({ json: { token: 'r01-csrf', headerName: 'X-CSRF-TOKEN' } });
    if (path === `/api/cases/${MOCK_CASE_ID}`) return route.fulfill({ json: detail });
    if (path === `/api/org/cases/${MOCK_CASE_ID}`) {
      reads += 1;
      return route.fulfill({ json: { ...detail, campaignId, outstandingCommitments: [{
        contributionId: commitmentId, status: committed ? 'PARTIALLY_RECEIVED' : 'PLEDGED', pledgedAt: detail.createdAt,
        items: [{ contributionItemId, campaignItemId,
          itemName: 'Rice', unit: 'PACKAGE', pledgedQty: 3, receivedQty: committed ? 2 : 0, outstandingQty: committed ? 1 : 3 }],
      }] } });
    }
    if (path === `/api/campaigns/${campaignId}`) {
      return route.fulfill({ json: { id: campaignId, status: 'PUBLISHED',
        items: [{ id: campaignItemId, name: 'Rice', unit: 'PACKAGE', receivedQty: committed ? 2 : 0, deliveredQty: 0 }] } });
    }
    if (path === receiptPath && request.method() === 'POST') {
      const headers = await request.allHeaders();
      expect(headers['x-csrf-token']).toBe('r01-csrf');
      expect(headers.cookie).toContain('JSESSIONID=r01-test-only');
      const body: ReceiptRequest = request.postDataJSON();
      writes.push({ key: headers['idempotency-key'], body });
      expect(body.items).toEqual([{ contributionItemId, quantity: 2 }]);
      if (!committed) {
        committed = true;
        return route.fulfill({ status: 504, json: { code: 'DEPENDENCY_UNAVAILABLE' } });
      }
      expect(writes[1]).toEqual(writes[0]);
      return route.fulfill({ status: 201, json: {
        id: receiptId, contributionId: commitmentId, recorderId: staff.userId, items: body.items,
        receivedAt: body.receivedAt, recordedAt: body.receivedAt, auditEventId: 'audit-test-id', blockchainStatus: 'PENDING_ON_CHAIN',
      } });
    }
    return route.fulfill({ status: 404, json: { code: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto(`/cases/${MOCK_CASE_ID}`);
  const receipt = page.getByRole('region', { name: 'Goods receipt' });
  await expect(receipt.getByText('Outstanding: 3')).toBeVisible();
  const delivery = page.getByRole('region', { name: 'Goods delivery' });
  await expect(delivery.getByText('Available: 0')).toBeVisible();
  const quantity = receipt.getByLabel('Rice receipt quantity (PACKAGE)', { exact: true });
  await quantity.fill('2');
  await receipt.getByRole('button', { name: 'Record receipt', exact: true }).click();
  const retry = receipt.getByRole('button', { name: 'Retry same receipt' });
  await expect(retry).toBeVisible();
  await expect(quantity).toBeDisabled();
  await retry.click();
  await expect(receipt.locator(`[data-receipt-id="${receiptId}"]`)).toBeVisible();
  await expect(receipt.getByText('Outstanding: 1')).toBeVisible();
  await expect(delivery.getByText('Available: 2')).toBeVisible();
  expect(writes).toHaveLength(2);
  expect(writes[0].key).toBeTruthy();
  expect(reads).toBeGreaterThanOrEqual(2);
});
