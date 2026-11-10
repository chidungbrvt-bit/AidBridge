import type { CampaignResponse, CommitmentRequest, CommitmentResponse, ProblemDetail } from '../../../docs/fixtures/f02/goods-flow.types';
export type { CampaignResponse, CommitmentRequest, CommitmentResponse, ProblemDetail };
export interface Page<T> { items: T[]; page: number; size: number; totalElements: number }
export interface DonorApi {
  getCampaign(id: string): Promise<CampaignResponse>;
  getCommitments(page: number): Promise<Page<CommitmentResponse>>;
  pledge(id: string, body: CommitmentRequest, key: string): Promise<CommitmentResponse>;
}
export class ApiError extends Error {
  problem: ProblemDetail;
  constructor(problem: ProblemDetail) { super(problem.detail); this.name = 'ApiError'; this.problem = problem; }
}
export type Draft = Record<string, { selected: boolean; quantity: string }>;
export function validateDraft(campaign: CampaignResponse, draft: Draft) {
  const errors: Record<string, string> = {};
  const items: CommitmentRequest['items'] = [];
  for (const item of campaign.items) {
    const row = draft[item.id];
    if (!row?.selected) continue;
    const qty = Number(row.quantity);
    if (!/^\d+$/.test(row.quantity) || !Number.isSafeInteger(qty) || qty < 1 || qty > 1_000_000) errors[item.id] = 'Enter a whole number from 1 to 1,000,000.';
    else if (qty > item.remainingPledgeQty) errors[item.id] = `Only ${item.remainingPledgeQty} ${item.unit} remain available to pledge.`;
    else items.push({ campaignItemId: item.id, quantity: qty });
  }
  if (!campaign.items.some(item => draft[item.id]?.selected)) errors.form = 'Select at least one item.';
  return { errors, body: { items } };
}
