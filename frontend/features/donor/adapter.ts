import { ApiClient, ApiError as ClientError } from '../../src/lib/api-client';
import { ApiError, type DonorApi } from './model';

/** Reuse the shared cookie/CSRF client; no fetch, mock fallback or capability bypass here. */
export function createDonorAdapter(client: ApiClient): DonorApi {
  async function call<T>(request: () => Promise<T>): Promise<T> {
    try { return await request(); }
    catch (error) {
      if (error instanceof ClientError) {
        throw new ApiError({ ...error.problem, detail: error.problem.detail ?? error.problem.title,
          instance: error.problem.instance ?? '', traceId: error.problem.traceId ?? '',
          fieldErrors: error.fieldErrors.map(({field, code}) => ({field, code})) });
      }
      throw error; // Transport loss is an unknown mutation outcome, never a successful write.
    }
  }
  return {
    getCampaign: id => call(() => client.getCampaign(id)),
    getCommitments: page => call(() => client.getCommitments(page)),
    pledge: (id, body, key) => call(() => client.pledge(id, body, key)),
    cancel: (id, body, key) => call(() => client.cancel(id, body, key)),
  };
}
