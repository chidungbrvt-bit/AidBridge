import type { CampaignResponse } from '../../features/donor/model';
import type { CreateCampaignRequest } from './campaign';
import type { DeliveryRequest, GoodsReviewAction, ReceiptRequest } from './goods';
import type { CreateCaseRequest } from './types';

export type PendingWrite<T> = {
  key: string;
  body: T;
  inFlight?: boolean;
};

export type CampaignWork = {
  draft?: CampaignResponse;
  pending?: (PendingWrite<CreateCampaignRequest> & { action: 'create' })
    | (PendingWrite<Record<string, never>> & { action: 'publish'; campaignId: string });
};

export type ReceiptAttempt = PendingWrite<ReceiptRequest> & { commitmentId: string };

export type GoodsReviewAttempt = PendingWrite<GoodsReviewAction> & {
  actorId: string;
  route: string;
  organizationId?: string;
};

export function createSessionWork() {
  const caseCreates = new Map<string, PendingWrite<CreateCaseRequest>>();
  const campaigns = new Map<string, CampaignWork>();
  const receipts = new Map<string, ReceiptAttempt>();
  const deliveries = new Map<string, PendingWrite<DeliveryRequest>>();
  return {
    caseCreates, campaigns, receipts, deliveries,
    clear() {
      caseCreates.clear();
      campaigns.clear();
      receipts.clear();
      deliveries.clear();
    },
  };
}
