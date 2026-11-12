import { test } from 'node:test';
import assert from 'node:assert/strict';
import fixture from '../../docs/fixtures/f02/goods-flow.mock.json';
import snapshot from '../features/goods-mock/f02-snapshot.json';
import { apply, evidenceIds, initialSnapshot, limits, type Command } from '../features/goods-mock/model';
const receipt = (): Command => ({ action: 'receipt', actor: 'organizationAdmin', outcome: 'DELIVERED', reason: '', note: '', at: '2026-11-09T01:39:00Z', evidence: [], lines: limits(initialSnapshot(), 'receipt').map((x,i) => ({ id: x.id, quantity: i ? 1 : 5 })) });
const delivery = (): Command => ({...receipt(), action: 'delivery', actor: 'fieldVolunteer', evidence: [evidenceIds[0]], lines: limits(initialSnapshot(true), 'delivery').map((x,i) => ({id:x.id,quantity:i ? 1 : 4}))});
test('bundled mock snapshot remains identical to authoritative F02', () => {
  assert.deepEqual(snapshot.states, {S1:fixture.states.S1,S2:fixture.states.S2});
  assert.deepEqual(snapshot.actors,fixture.actors); assert.equal(snapshot.organizationId,fixture.organizationId);
  assert.deepEqual(evidenceIds,fixture.mockReadModels.S2.caseContext.availableEvidenceVersionIds);
});
test('R01 partial receipt matches F02 S2, not delivery/confirmation', () => {
  const before = initialSnapshot(); const after = apply(before, receipt());
  assert.deepEqual(after.campaign, fixture.states.S2.campaign);
  assert.deepEqual(after.focalContribution, fixture.states.S2.focalContribution);
  assert.deepEqual(before, initialSnapshot());
});
test('D01 matches S3 stock, no donor provenance or automatic confirmation', () => {
  const after = apply(initialSnapshot(true), delivery());
  assert.deepEqual(after.campaign, fixture.states.S3.campaign);
  assert.deepEqual(after.focalContribution, initialSnapshot(true).focalContribution);
});
test('403 role separation and donor cannot mutate stock', () => {
  for (const actor of ['fieldVolunteer','caseVerifier','donor'] as const) assert.throws(() => apply(initialSnapshot(), {...receipt(),actor}), /403 FORBIDDEN/);
  assert.throws(() => apply(initialSnapshot(true), {...delivery(),actor:'organizationAdmin'}), /403 FORBIDDEN/);
});
test('invalid, duplicate and over-limit multi-item requests are atomic', () => {
  for (const quantity of [0,-1,0.5,NaN,1_000_001,9]) {
    const before = initialSnapshot(); const request = receipt(); request.lines[1].quantity = quantity;
    assert.throws(() => apply(before, request)); assert.deepEqual(before, initialSnapshot());
  }
  const request=receipt(); request.lines.push(request.lines[0]);
  assert.throws(() => apply(initialSnapshot(),request), /400/);
});
test('409 injection and stale receipt/delivery cannot change data', () => {
  const before=initialSnapshot(); assert.throws(() => apply(before,receipt(),true), /409/); assert.deepEqual(before,initialSnapshot());
  const updated=apply(before,receipt()); assert.throws(() => apply(updated,receipt()), /409/);
  const delivered=apply(initialSnapshot(true),delivery()); assert.throws(() => apply(delivered,delivery()), /409/);
});
test('failed attempt has zero stock effect, requires reason and empty goods/evidence', () => {
  const command:Command={...delivery(),outcome:'FAILED',lines:[],evidence:[],reason:'Fictional road unavailable'};
  assert.deepEqual(apply(initialSnapshot(true),command),initialSnapshot(true));
  assert.throws(() => apply(initialSnapshot(true),{...command,reason:''}),/400/);
  assert.throws(() => apply(initialSnapshot(true),{...command,lines:delivery().lines}),/400/);
});
test('delivery rejects non-allowlisted evidence and invalid timestamps', () => {
  assert.throws(() => apply(initialSnapshot(true),{...delivery(),evidence:['unknown']}),/EVIDENCE_REQUIRED/);
  assert.throws(() => apply(initialSnapshot(),{...receipt(),at:'invalid'}),/VALIDATION_ERROR/);
});
