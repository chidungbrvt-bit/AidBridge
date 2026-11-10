import fixture from '../../../docs/fixtures/f02/goods-flow.mock.json';
import { ApiError } from './model.ts';
import type { DonorApi, CommitmentRequest, CommitmentResponse, Page, CampaignResponse } from './model.ts';
export const DEMO_CAMPAIGN_ID = fixture.campaignId;
export type MockFault = 'none' | '400' | '409' | '403' | 'load-error' | 'history-error' | 'timeout-after-save';
export function createMockDonorApi(delay = 450) {
  let campaign: CampaignResponse = structuredClone(fixture.states.S0.campaign) as CampaignResponse;
  let history: CommitmentResponse[] = [];
  const replays = new Map<string, { digest: string; response: CommitmentResponse }>();
  let fault: MockFault = 'none';
  let generation = 0;
  const wait = () => new Promise(resolve => setTimeout(resolve, delay));
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
      const digest = JSON.stringify(body); const saved = replays.get(key);
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
      history = [response, ...history]; replays.set(key, { digest, response: structuredClone(response) });
      if (fault === 'timeout-after-save') { fault = 'none'; throw new TypeError('Mock network response lost after saving.'); }
      return structuredClone(response);
    },
  };
  return { api, setFault(value: MockFault) { fault = value; }, reset() { generation++; campaign = structuredClone(fixture.states.S0.campaign) as CampaignResponse; history = []; replays.clear(); fault = 'none'; } };
}
