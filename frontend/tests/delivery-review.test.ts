import assert from 'node:assert/strict';
import { test } from 'node:test';
import fixture from '../../docs/fixtures/f02/goods-flow.mock.json';
import { apply, initialSnapshot, type Command } from '../features/goods-mock/model';
import { recordFixtureDelivery } from '../features/goods-mock/fixture-delivery';
import {
  applyReview, canCompleteCampaign, deliveryRequest, emptyReview, fixtureDeliveryResponse, hasOpenDispute, recordDelivery, type ReviewCommand,
} from '../features/goods-mock/delivery-review';

const source = fixture.states.S3.deliveries[0];
const command: Command = {
  action: 'delivery', actor: 'fieldVolunteer', outcome: 'DELIVERED', reason: '', note: '',
  at: source.recordedAt, evidence: source.evidenceVersionIds,
  lines: source.items.map(item => ({ id: item.campaignItemId, quantity: item.quantity })),
};
const confirm: ReviewCommand = { operation: 'D02', deliveryId: source.id, body: {
  decision: 'CONFIRMED', reviewedEvidenceVersionIds: source.evidenceVersionIds,
} };

function delivered() {
  const response = fixtureDeliveryResponse(command.actor, deliveryRequest(command), source.id, 'audit-d01', source.recordedAt);
  return {
    snapshot: apply(initialSnapshot(true), command),
    review: recordDelivery(emptyReview(), response),
  };
}

test('D01 F02 S3 stays unconfirmed until D02 reviews the exact snapshot with a different actor', () => {
  const { snapshot, review } = delivered();
  assert.deepEqual(snapshot.campaign, fixture.states.S3.campaign);
  assert.throws(() => applyReview(snapshot, review, 'fieldVolunteer', confirm, 'self'), /403 SELF_CONFIRMATION/);
  assert.throws(() => applyReview(snapshot, review, 'independentConfirmer', {
    ...confirm, body: { ...confirm.body, reviewedEvidenceVersionIds: [source.evidenceVersionIds[0]] },
  }, 'partial'), /400/);
  const result = applyReview(snapshot, review, 'independentConfirmer', confirm, 'confirm');
  assert.deepEqual(result.snapshot.campaign.items.map(item => item.confirmedQty), [4, 1]);
  assert.equal(result.snapshot.campaign.status, 'PUBLISHED');
  assert.equal(result.review.events[0].blockchainStatus, 'PENDING_ON_CHAIN');
  assert.throws(() => applyReview(result.snapshot, result.review, 'independentConfirmer', confirm, 'duplicate'), /409/);
  assert.deepEqual(snapshot.campaign.items.map(item => item.confirmedQty), [0, 0]);
});

test('D02 request review retains reservation. Uphold permits a fresh review without auto-confirmation', () => {
  const { snapshot, review } = delivered();
  const flagged = applyReview(snapshot, review, 'independentConfirmer', {
    ...confirm, body: { ...confirm.body, decision: 'REQUIRES_REVIEW', reason: 'Check the fixture evidence.' },
  }, 'flag');
  assert.ok(hasOpenDispute(flagged.review));
  assert.deepEqual(flagged.snapshot.campaign.items, snapshot.campaign.items);
  assert.throws(() => applyReview(flagged.snapshot, flagged.review, 'independentConfirmer', confirm, 'blocked'), /409/);
  const uphold: ReviewCommand = { operation: 'S03', disputeId: 'flag', body: {
    outcome: 'UPHOLD', reason: 'Retain the delivery record.', reviewEvidenceVersionIds: source.evidenceVersionIds,
  } };
  assert.throws(() => applyReview(flagged.snapshot, flagged.review, 'organizationAdmin', uphold, 'wrong-role'), /403/);
  const resolved = applyReview(flagged.snapshot, flagged.review, 'platformAdmin', uphold, 'uphold');
  assert.equal(resolved.review.deliveries[0].status, 'PENDING');
  assert.equal(hasOpenDispute(resolved.review), false);
  assert.deepEqual(resolved.snapshot.campaign.items, snapshot.campaign.items);
  assert.equal(applyReview(resolved.snapshot, resolved.review, 'independentConfirmer', confirm, 'new-review').review.deliveries[0].status, 'CONFIRMED');
});

test('S01/S03 void excludes one confirmed delivery from projections and retains other disputes and history', () => {
  const { snapshot, review } = delivered();
  const confirmed = applyReview(snapshot, review, 'independentConfirmer', confirm, 'confirm');
  const flagged = applyReview(confirmed.snapshot, confirmed.review, 'caseVerifier', {
    operation: 'S01', body: { deliveryId: source.id, reason: 'Review delivery quantities.' },
  }, 'delivery-dispute');
  const second = applyReview(flagged.snapshot, flagged.review, 'organizationAdmin', {
    operation: 'S01', body: { reason: 'Review the case separately.' },
  }, 'case-dispute');
  const body = { outcome: 'VOID_DELIVERY' as const, reason: 'Void the fixture claim.', reviewEvidenceVersionIds: [] };
  assert.throws(() => applyReview(second.snapshot, second.review, 'platformAdmin', {
    operation: 'S03', disputeId: 'case-dispute', body,
  }, 'invalid'), /400/);
  const resolved = applyReview(second.snapshot, second.review, 'platformAdmin', {
    operation: 'S03', disputeId: 'delivery-dispute', body,
  }, 'void');
  assert.deepEqual(resolved.snapshot.campaign, initialSnapshot(true).campaign);
  assert.ok(hasOpenDispute(resolved.review));
  assert.equal(resolved.review.deliveries[0].status, 'VOIDED');
  assert.equal(resolved.review.events[0].label, 'Delivery recorded');
  assert.equal(resolved.review.events[1].label, 'Delivery confirmed');
  assert.equal(resolved.review.events.at(-1)?.label, 'Delivery voided · Dispute resolved');
});

test('D01 FAILED omits handover and adds only a local audit event', () => {
  const request = deliveryRequest({ ...command, outcome: 'FAILED', lines: [], evidence: [], reason: ' Road closed ' });
  assert.deepEqual(request, { outcome: 'FAILED', items: [], evidenceVersionIds: [], reason: 'Road closed' });
  const response = fixtureDeliveryResponse(command.actor, request, 'failed', 'local-audit', source.recordedAt);
  assert.equal(response.reviewStatus, 'NOT_APPLICABLE');
  assert.equal(response.blockchainStatus, 'NOT_APPLICABLE');
  assert.equal(response.outcome === 'FAILED' && response.auditUrl, null);
  const review = recordDelivery(emptyReview(), response);
  assert.deepEqual(review.events, [{ id: 'local-audit', label: 'Delivery attempt failed', blockchainStatus: 'NOT_APPLICABLE' }]);
  assert.equal(recordDelivery(review, response), review);
});

test('D01 replay preserves the record and reservation, rejecting changed payload, scope and role', () => {
  const before = initialSnapshot(true);
  const review = emptyReview();
  const journal = new Map();
  const request = { caseId: fixture.caseId, actor: command.actor, key: 'd01-idempotency-key', body: deliveryRequest(command) };
  const context = { id: source.id, auditEventId: 'd01', recordedAt: source.recordedAt };
  const first = recordFixtureDelivery(before, review, journal, request, context);
  assert.deepEqual(first.snapshot.campaign, fixture.states.S3.campaign);
  assert.equal(first.response.recorderId, source.recorderId);
  assert.deepEqual(first.review.deliveries[0].evidenceVersionIds, first.response.evidenceVersionIds);
  assert.throws(() => recordFixtureDelivery(before, review, journal, { ...request, actor: 'donor' }, context), /404 RESOURCE_NOT_FOUND/);
  assert.throws(() => recordFixtureDelivery(before, review, journal, { ...request, actor: 'caseVerifier' }, context), /403 FORBIDDEN/);
  assert.throws(() => recordFixtureDelivery(first.snapshot, first.review, journal, {
    ...request, body: deliveryRequest({ ...command, evidence: [source.evidenceVersionIds[0]] }),
  }, context), /409 IDEMPOTENCY_CONFLICT/);
  assert.deepEqual(recordFixtureDelivery(first.snapshot, first.review, journal, request, { ...context, id: 'ignored' }), first);
});

test('open dispute blocks completion even after every item reaches its confirmed target', () => {
  const { snapshot, review } = delivered();
  for (const row of snapshot.campaign.items) {
    row.targetQty = source.items.find(item => item.campaignItemId === row.id)!.quantity;
  }
  const confirmed = applyReview(snapshot, review, 'independentConfirmer', confirm, 'confirm');
  assert.equal(canCompleteCampaign(confirmed.snapshot, confirmed.review), true);
  const disputed = applyReview(confirmed.snapshot, confirmed.review, 'organizationAdmin', {
    operation: 'S01', body: { reason: 'Recheck a fully confirmed campaign.' },
  }, 'dispute');
  assert.equal(disputed.snapshot.campaign.status, 'FULFILLED');
  assert.equal(canCompleteCampaign(disputed.snapshot, disputed.review), false);
});
