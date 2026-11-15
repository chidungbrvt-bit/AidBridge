import type { DeliveryRequest, DeliveryResponse } from '../../src/lib/goods';
import fixture from './f02-snapshot.json';
import { fixtureDeliveryResponse, hasOpenDispute, recordDelivery, type ReviewState } from './delivery-review';
import { apply, GoodsError, permitted, type Actor, type Snapshot } from './model';

type DeliveryWrite = { body: string; response: DeliveryResponse };

export function recordFixtureDelivery(
  snapshot: Snapshot,
  review: ReviewState,
  journal: Map<string, DeliveryWrite>,
  request: { caseId: string; actor: Actor; key: string; body: DeliveryRequest },
  context: { id: string; auditEventId: string; recordedAt: string; staleStock?: boolean },
) {
  const { caseId, actor, key, body } = request;
  if (caseId !== fixture.caseId || !fixture.actors[actor].memberships.some(
    member => member.active && member.organizationId === fixture.organizationId,
  )) throw new GoodsError(404, 'RESOURCE_NOT_FOUND');
  if (!permitted('delivery', actor)) throw new GoodsError(403, 'FORBIDDEN');

  const scope = `${actor}:${caseId}:${key}`;
  const payload = JSON.stringify(body);
  const prior = journal.get(scope);
  if (prior) {
    if (prior.body !== payload) throw new GoodsError(409, 'IDEMPOTENCY_CONFLICT');
    return { snapshot, review, response: prior.response };
  }
  if (hasOpenDispute(review)) throw new GoodsError(409, 'DISPUTE_OPEN');
  if (snapshot.campaign.status !== 'PUBLISHED') throw new GoodsError(409, 'INVALID_STATE');
  if (body.outcome === 'FAILED' && body.handedOverAt !== undefined) throw new GoodsError(400, 'VALIDATION_ERROR');

  const next = apply(snapshot, {
    action: 'delivery', actor, outcome: body.outcome, reason: body.outcome === 'FAILED' ? body.reason : '',
    evidence: body.evidenceVersionIds, at: body.handedOverAt, note: '',
    lines: body.items.map(item => ({ id: item.campaignItemId, quantity: item.quantity })),
  }, context.staleStock);
  const response = fixtureDeliveryResponse(actor, body, context.id, context.auditEventId, context.recordedAt);
  journal.set(scope, { body: payload, response });
  return { snapshot: next, review: recordDelivery(review, response), response };
}
