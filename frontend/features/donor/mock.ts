import fixture from '../../../docs/fixtures/f02/goods-flow.mock.json';
import { ApiError } from './model.ts';
import type { DonorApi, CommitmentRequest, CommitmentResponse, Page, CampaignResponse, CancellationRequest } from './model.ts';
export const DEMO_CAMPAIGN_ID = fixture.campaignId;
export type MockFault = 'none' | '400' | '409' | '403' | 'load-error' | 'history-error' | 'timeout-after-save';
export function createMockDonorApi(delay = 450) {
  let campaign: CampaignResponse = structuredClone(fixture.states.S0.campaign) as CampaignResponse;
  let history: CommitmentResponse[] = [];
  const replays = new Map<string, { digest: string; response: CommitmentResponse }>();
  let fault: MockFault = 'none';
  let generation = 0;
  const wait = () => new Promise(resolve => setTimeout(resolve, delay));
  const deriveStatus = (record: CommitmentResponse) => {
    const outstanding = record.items.reduce((n, item) => n + item.outstandingQty, 0);
    const received = record.items.reduce((n, item) => n + item.receivedQty, 0);
    record.status = outstanding === 0 ? (received === 0 ? 'CANCELLED' : 'RECEIVED') : received > 0 ? 'PARTIALLY_RECEIVED' : 'PLEDGED';
  };
  const problem = (status: number, code: string, detail: string, path: string, field?: string) => new ApiError({ type: `urn:aidbridge:error:${code.toLowerCase().replaceAll('_', '-')}`, title: code === 'QUANTITY_EXCEEDED' ? 'Quantity exceeds remaining capacity' : 'Request could not be completed', status, code, detail, instance: path, traceId: 'mock-request', fieldErrors: field ? [{ field, code }] : [] });
  const api: DonorApi = {
    async getCampaign(id) { await wait(); if (fault === 'load-error') throw problem(503, 'DEPENDENCY_UNAVAILABLE', 'Unable to load the campaign. Try again.', `/api/campaigns/${id}`); if (id !== campaign.id) throw problem(404, 'RESOURCE_NOT_FOUND', 'Campaign not found.', `/api/campaigns/${id}`); return structuredClone(campaign); },
    async getCommitments(page) { await wait(); if (fault === 'history-error') throw problem(503, 'DEPENDENCY_UNAVAILABLE', 'Unable to load your pledge history. Try again.', '/api/me/commitments'); const size = 5; return { items: structuredClone(history.slice(page * size, (page + 1) * size)), page, size, totalElements: history.length } satisfies Page<CommitmentResponse>; },
    async pledge(id, body: CommitmentRequest, key) {
      const started = generation; await wait();
      if (started !== generation) throw new TypeError('Mock context was reset.');
      const path = `/api/campaigns/${id}/commitments`;
      if (fault === '403') throw problem(403, 'FORBIDDEN', 'You can view this campaign but cannot pledge goods.', path);
      if (id !== campaign.id) throw problem(404, 'RESOURCE_NOT_FOUND', 'Campaign not found.', path);
      const replayKey = `pledge:${id}:${key}`;
      const digest = JSON.stringify(body); const saved = replays.get(replayKey);
      if (saved) { if (saved.digest !== digest) throw problem(409, 'IDEMPOTENCY_CONFLICT', 'This key was already used with different data.', path); return structuredClone(saved.response); }
      if (fault === '400') { fault = 'none'; throw problem(400, 'VALIDATION_ERROR', 'Check the highlighted quantity and submit again.', path, 'items[0].quantity'); }
      if (fault === '409') { fault = 'none'; throw problem(409, 'QUANTITY_EXCEEDED', 'Available quantities changed. Review your entries and try again.', path, `items[${Math.max(0, Math.min(1, body.items.length - 1))}].quantity`); }
      if (!body.items.length || new Set(body.items.map(x => x.campaignItemId)).size !== body.items.length) throw problem(400, 'VALIDATION_ERROR', 'Select unique item lines.', path);
      for (const [index, line] of body.items.entries()) {
        const item = campaign.items.find(x => x.id === line.campaignItemId);
        if (!item || !Number.isSafeInteger(line.quantity) || line.quantity < 1 || line.quantity > 1_000_000) throw problem(400, 'VALIDATION_ERROR', 'Invalid item or quantity.', path, `items[${index}].quantity`);
        if (line.quantity > item.remainingPledgeQty) throw problem(409, 'QUANTITY_EXCEEDED', 'Quantity exceeds remaining capacity.', path, `items[${index}].quantity`);
      }
      const response: CommitmentResponse = { id: crypto.randomUUID(), campaignId: id, items: body.items.map(line => ({ id: crypto.randomUUID(), campaignItemId: line.campaignItemId, pledgedQty: line.quantity, cancelledQty: 0, receivedQty: 0, outstandingQty: line.quantity })), status: 'PLEDGED', createdAt: new Date().toISOString(), auditEventId: crypto.randomUUID(), blockchainStatus: 'PENDING_ON_CHAIN' };
      // Validate every line before applying any mutation. No API/private data is persisted.
      for (const line of body.items) { const item = campaign.items.find(x => x.id === line.campaignItemId)!; item.pledgedQty += line.quantity; item.remainingPledgeQty -= line.quantity; }
      history = [response, ...history]; replays.set(replayKey, { digest, response: structuredClone(response) });
      if (fault === 'timeout-after-save') { fault = 'none'; throw new TypeError('Mock network response lost after saving.'); }
      return structuredClone(response);
    },
    async cancel(id, body: CancellationRequest, key) {
      const started = generation; const request = structuredClone(body); await wait();
      if (started !== generation) throw new TypeError('Mock context was reset.');
      const path = `/api/commitments/${id}/cancellations`;
      if (fault === '403') throw problem(403, 'FORBIDDEN', 'You cannot cancel goods for this account.', path);
      const record = history.find(item => item.id === id);
      if (!record) throw problem(404, 'RESOURCE_NOT_FOUND', 'Commitment not found in your history.', path);
      const replayKey = `cancel:${id}:${key}`, digest = JSON.stringify(request), saved = replays.get(replayKey);
      if (saved) { if (saved.digest !== digest) throw problem(409, 'IDEMPOTENCY_CONFLICT', 'This key was already used with different data.', path); return structuredClone(saved.response); }
      if (fault === '400') { fault = 'none'; throw problem(400, 'VALIDATION_ERROR', 'Check cancellation quantities.', path, 'items[0].quantity'); }
      if (fault === '409') { fault = 'none'; throw problem(409, 'QUANTITY_EXCEEDED', 'Unreceived quantities changed. Refresh and review before cancelling.', path, 'items[0].quantity'); }
      if (!request.items.length || new Set(request.items.map(item => item.contributionItemId)).size !== request.items.length
        || !request.reason.trim() || request.reason.trim().length > 500) throw problem(400, 'VALIDATION_ERROR', 'Select unique lines and enter a reason from 1 to 500 characters.', path);
      for (const [index, line] of request.items.entries()) {
        const item = record.items.find(item => item.id === line.contributionItemId);
        if (!item || !Number.isSafeInteger(line.quantity) || line.quantity < 1 || line.quantity > 1_000_000) throw problem(400, 'VALIDATION_ERROR', 'Invalid contribution item or quantity.', path, `items[${index}].quantity`);
        if (line.quantity > item.outstandingQty) throw problem(409, 'QUANTITY_EXCEEDED', 'Received or already cancelled goods cannot be cancelled. Review the current outstanding quantity.', path, `items[${index}].quantity`);
      }
      // All lines validated against the latest mock state; no await until mutation is complete.
      for (const line of request.items) {
        const item = record.items.find(item => item.id === line.contributionItemId)!;
        item.cancelledQty += line.quantity; item.outstandingQty -= line.quantity;
        const total = campaign.items.find(total => total.id === item.campaignItemId)!;
        total.pledgedQty -= line.quantity; total.remainingPledgeQty += line.quantity;
      }
      deriveStatus(record); record.auditEventId = crypto.randomUUID(); record.blockchainStatus = 'PENDING_ON_CHAIN';
      replays.set(replayKey, { digest, response: structuredClone(record) });
      if (fault === 'timeout-after-save') { fault = 'none'; throw new TypeError('Mock network response lost after saving.'); }
      return structuredClone(record);
    },
  };
  return { api, setFault(value: MockFault) { fault = value; },
    loadPartialReceiptFixture() { generation++; replays.clear(); fault = 'none'; campaign = structuredClone(fixture.states.S2.campaign) as CampaignResponse; history = [structuredClone(fixture.states.S2.focalContribution) as CommitmentResponse]; },
    // Preview/test-only external receipt simulation, not a donor API or real R01 implementation.
    receiveRemainingForDemo() {
      const record = history[0]; if (!record) return;
      for (const item of record.items) { const qty = item.outstandingQty; item.receivedQty += qty; item.outstandingQty = 0; campaign.items.find(total => total.id === item.campaignItemId)!.receivedQty += qty; }
      deriveStatus(record);
    },
    reset() { generation++; campaign = structuredClone(fixture.states.S0.campaign) as CampaignResponse; history = []; replays.clear(); fault = 'none'; } };
}
