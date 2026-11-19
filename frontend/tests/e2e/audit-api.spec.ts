import { expect, test } from '@playwright/test';
import { MOCK_CASE_ID, MOCK_CREATE_CASE, MOCK_IDENTITIES } from '../../src/lib/mock-fixtures';
import mockGoods from '../../../docs/fixtures/f02/goods-flow.mock.json';

const publicUrl = mockGoods.states.S0.campaign.auditUrl;
const publicRef = mockGoods.publicCaseRef;
const campaign = mockGoods.states.S0.campaign;
const txHash = `0x${'ab'.repeat(32)}`;
const explorerUrl = `https://testnet.snowtrace.io/tx/${txHash}`;
const baseChain = {
  status: 'CONFIRMED_ON_CHAIN', chainId: 43113, network: 'Avalanche Fuji testnet',
  contractAddress: `0x${'12'.repeat(20)}`, txHash, blockNumber: 60959263, explorerUrl,
};
const base = { eventId: 'public-event', type: 'CASE_VERIFIED', occurredAt: '2026-11-14T08:00:00Z',
  chainSequence: 1, logicalEventId: null };

test('T01 staff timeline paginates and leaves incomplete or pending proof unconfirmed', async ({ page }) => {
  const detail = {
    ...MOCK_CREATE_CASE, id: MOCK_CASE_ID, creator: { id: MOCK_IDENTITIES[1].user.userId, fullName: 'Fictional creator' },
    status: 'VERIFIED', version: 1, needs: [], evidenceVersions: [], createdAt: base.occurredAt,
  };
  let calls = 0;
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/auth/csrf') return route.fulfill({ json: { token: 'audit-test', headerName: 'X-CSRF-TOKEN' } });
    if (url.pathname === '/api/auth/me') return route.fulfill({ json: MOCK_IDENTITIES[1].user });
    if (url.pathname === `/api/cases/${MOCK_CASE_ID}`) return route.fulfill({ json: detail });
    if (url.pathname !== `/api/cases/${MOCK_CASE_ID}/audit-trail`) return route.fulfill({ status: 404, json: {} });
    calls++;
    const pageNumber = Number(url.searchParams.get('page'));
    expect(url.searchParams.get('size')).toBe('20');
    const viewer = { ...base, entityType: 'VERIFICATION', actor: { id: 'actor', fullName: 'Fictional verifier' } };
    return route.fulfill({ json: {
      items: pageNumber === 0
        ? [{ ...viewer, blockchain: { ...baseChain, errorCode: null } }]
        : [{ ...viewer, eventId: 'pending-event', blockchain: { ...baseChain, status: 'PENDING_ON_CHAIN', blockNumber: null } },
          { ...viewer, eventId: 'incomplete-event', blockchain: { ...baseChain, blockNumber: null } },
          { ...viewer, eventId: 'failed-event', blockchain: { ...baseChain, status: 'FAILED_ON_CHAIN', errorCode: 'EVENT_MISMATCH' } }],
      page: pageNumber, size: 20, totalElements: 23,
    } });
  });
  await page.goto(`/cases/${MOCK_CASE_ID}`);
  await expect(page.getByText('Case audit trail', { exact: true })).toBeVisible();
  expect(calls).toBe(0);
  await page.getByText('Case audit trail', { exact: true }).click();
  const trail = page.getByRole('region', { name: 'Case audit timeline' });
  await expect(trail.getByText('Fictional verifier')).toBeVisible();
  await expect(trail.getByText('Confirmed on Fuji')).toBeVisible();
  await expect(trail.getByText('60959263')).toBeVisible();
  await expect(trail.getByRole('link', { name: 'View confirmed transaction on Fuji' })).toHaveAttribute('href', explorerUrl);
  await trail.getByRole('button', { name: 'Next' }).click();
  await expect(trail.getByText('Awaiting on-chain verification')).toBeVisible();
  await expect(trail.getByText('Proof unavailable')).toBeVisible();
  await expect(trail.getByText('Blockchain sync failed')).toBeVisible();
  await expect(trail.getByRole('link')).toHaveCount(0);
  await expect(trail.getByRole('button', { name: 'Next' })).toBeDisabled();
});

test('T02 campaign waits for read success before sharing the timeline and renders public fields only', async ({ page, browser, baseURL }) => {
  let ready = false;
  async function serve(publicPage: typeof page, signedIn: boolean) {
    await publicPage.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname;
      if (path === '/api/auth/csrf') return route.fulfill({ json: { token: 'audit-test', headerName: 'X-CSRF-TOKEN' } });
      if (path === '/api/auth/me') return signedIn
        ? route.fulfill({ json: MOCK_IDENTITIES[2].user })
        : route.fulfill({ status: 401, json: { code: 'UNAUTHENTICATED' } });
      if (path === `/api/campaigns/${campaign.id}`) return route.fulfill({ json: campaign });
      if (path === publicUrl && ready) return route.fulfill({ json: {
        items: [{ ...base, publicActorRole: 'VERIFIER', actor: { fullName: 'PRIVATE ACTOR' },
          blockchain: { ...baseChain, explorerUrl: `https://other.example/tx/${txHash}` } }],
        page: 0, size: 20, totalElements: 1,
      } });
      return route.fulfill({ status: 404, json: { code: 'RESOURCE_NOT_FOUND' } });
    });
  }
  await serve(page, true);
  await page.goto(`/campaigns/${campaign.id}`);
  await page.getByText('Public audit trail', { exact: true }).click();
  const trail = page.getByRole('region', { name: 'Public audit timeline' });
  await expect(trail.getByRole('alert')).toHaveAttribute('data-error-code', 'RESOURCE_NOT_FOUND');
  const share = trail.getByRole('link', { name: 'Open public audit page' });
  await expect(share).toHaveCount(0);
  ready = true;
  await trail.getByRole('button', { name: 'Retry audit' }).click();
  await expect(share).toHaveAttribute('href', `/audit/${publicRef}`);
  await expect(trail.getByText('Verifier', { exact: true })).toBeVisible();
  await expect(trail.getByText('PRIVATE ACTOR')).toHaveCount(0);
  await expect(trail.getByText(txHash, { exact: true })).toBeVisible();
  await expect(trail.getByRole('link', { name: 'View confirmed transaction on Fuji' })).toHaveCount(0);

  ready = false;
  await trail.getByRole('button', { name: 'Refresh audit', exact: true }).click();
  await expect(trail.getByRole('alert')).toHaveAttribute('data-error-code', 'RESOURCE_NOT_FOUND');
  await expect(share).toHaveCount(0);
  await expect(trail.getByText('Confirmed on Fuji', { exact: true })).toHaveCount(0);
  await expect(trail.getByText(txHash, { exact: true })).toHaveCount(0);
  ready = true;
  await trail.getByRole('button', { name: 'Retry audit', exact: true }).click();
  await expect(share).toBeVisible();

  const anonymous = await browser.newContext({ baseURL });
  try {
    const publicPage = await anonymous.newPage();
    await serve(publicPage, false);
    await publicPage.goto(`/audit/${publicRef}`);
    await expect(publicPage.getByRole('heading', { name: 'Public audit timeline' })).toBeVisible();
    await expect(publicPage.getByText(txHash, { exact: true })).toBeVisible();
    await expect(publicPage.getByText('PRIVATE ACTOR')).toHaveCount(0);
  } finally {
    await anonymous.close();
  }
});
