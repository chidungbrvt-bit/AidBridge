import assert from 'node:assert/strict';
import { test } from 'node:test';
import fixture from '../../docs/fixtures/f02/goods-flow.mock.json';
import { apply, initialSnapshot, type Command } from '../features/goods-mock/model';
import {
  applyReview, emptyReview, hasOpenDispute, recordDelivery, type ReviewCommand,
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
  return {
    snapshot: apply(initialSnapshot(true), command),
    review: recordDelivery(emptyReview(), command, source.id),
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
  assert.equal(resolved.review.events[0].label, 'Delivery confirmed');
  assert.equal(resolved.review.events.at(-1)?.label, 'Delivery voided · Dispute resolved');
});
