import type { CampaignResponse } from '../../features/donor/model';
import type { CreateCampaignRequest } from './campaign';
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

export function createSessionWork() {
  const caseCreates = new Map<string, PendingWrite<CreateCaseRequest>>();
  const campaigns = new Map<string, CampaignWork>();
  return {
    caseCreates, campaigns,
    clear() {
      caseCreates.clear();
      campaigns.clear();
    },
  };
}
