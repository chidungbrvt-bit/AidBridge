import type { CampaignResponse } from '../../features/donor/model';

export type CreateCampaignRequest = { caseId: string; title: string; publicSummary: string };
export type CampaignItem = CampaignResponse['items'][number];

export function sortCampaignItems<T extends Pick<CampaignItem, 'id' | 'name' | 'unit'>>(items: readonly T[]): T[] {
  return [...items].sort((a, b) =>
    a.name.localeCompare(b.name, 'en', { sensitivity: 'base' }) ||
    a.unit.localeCompare(b.unit, 'en', { sensitivity: 'base' }) ||
    a.id.localeCompare(b.id)
  );
}
