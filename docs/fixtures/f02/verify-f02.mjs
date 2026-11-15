// Static design-fixture validation only. No package install, HTTP, browser or DB.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const fixture = JSON.parse(readFileSync(new URL('./goods-flow.mock.json', import.meta.url), 'utf8'));
const uiMap = JSON.parse(readFileSync(new URL('./endpoint-ui-states.json', import.meta.url), 'utf8'));
const clone = value => structuredClone(value);
const uuid = value => assert.match(value, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
const integer = (value, min = 0) => assert(Number.isSafeInteger(value) && value >= min && value <= 1_000_000);
const unique = values => assert.equal(new Set(values).size, values.length);
const exactKeys = (obj, keys) => assert.deepEqual(Object.keys(obj).sort(), [...keys].sort());
const utc = value => { assert.match(value, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/); assert(Number.isFinite(Date.parse(value))); };
const commonResponseKeys = ['id','auditEventId','blockchainStatus'];
const responseKeys = {
  G01: [...commonResponseKeys,'campaignId','items','status','createdAt'],
  R01: [...commonResponseKeys,'contributionId','items','receivedAt','recordedAt','recorderId'],
  D01: [...commonResponseKeys,'caseId','recorderId','outcome','reviewStatus','items','evidenceVersionIds','recordedAt'],
};
const campaignKeys = ['id','status','title','publicSummary','approximateArea','organization','items','auditUrl'];
const campaignItemKeys = ['id','name','unit','targetQty','pledgedQty','receivedQty','deliveredQty','confirmedQty','remainingPledgeQty'];
const contributionLineKeys = ['id','campaignItemId','pledgedQty','cancelledQty','receivedQty','outstandingQty'];
const expectedActor = { G01: 'donor', R01: 'organizationAdmin', D01: 'fieldVolunteer' };
const forbiddenActor = { G01: 'caseVerifier', R01: 'fieldVolunteer', D01: 'organizationAdmin' };
const requiredRole = { R01: 'ORGANIZATION_ADMIN', D01: 'FIELD_VOLUNTEER' };
const canAct = (endpoint, actor) => endpoint === 'G01' ? actor.donorEnabled : actor.memberships.some(m => m.active && m.organizationId === fixture.organizationId && m.roles.includes(requiredRole[endpoint]));
const getTotals = state => state.campaign.items.map(item => ({campaignItemId:item.id,unit:item.unit,targetQty:item.targetQty,pledgedQty:item.pledgedQty,receivedQty:item.receivedQty,deliveredQty:item.deliveredQty,confirmedQty:item.confirmedQty}));

assert.equal(fixture.fixtureLabel, 'F02_SHARED_MOCK_ONLY_NOT_LIVE_API');
assert.equal(fixture.scenarios.length, 10);
assert.equal(Object.keys(fixture.states).length, 5);
unique(fixture.scenarios.map(s => s.id));
unique(fixture.scenarios.map(s => s.request.headers['Idempotency-Key']));
unique(Object.values(fixture.actors).map(actor => actor.userId));
for (const actor of Object.values(fixture.actors)) uuid(actor.userId);
const itemIds = fixture.itemDefinitions.map(item => item.id);
unique(itemIds); itemIds.forEach(uuid);
for (const [stateName, state] of Object.entries(fixture.states)) {
  exactKeys(state.campaign, campaignKeys);
  assert.equal(state.campaign.id, fixture.campaignId);
  assert.equal(state.campaign.auditUrl, `/api/campaigns/${fixture.campaignId}/audit-trail`);
  assert.equal(state.campaign.status, 'PUBLISHED');
  assert(!('caseId' in state.campaign));
  const focal = state.focalContribution;
  if (focal) {
    exactKeys(focal, responseKeys.G01); uuid(focal.id);
    assert.equal(focal.campaignId, fixture.campaignId);
    unique(focal.items.map(item => item.id));
    for (const line of focal.items) {
      exactKeys(line, contributionLineKeys); uuid(line.id); assert(!itemIds.includes(line.id));
      assert(itemIds.includes(line.campaignItemId));
      for (const key of ['pledgedQty','cancelledQty','receivedQty','outstandingQty']) integer(line[key]);
      assert.equal(line.outstandingQty, line.pledgedQty-line.cancelledQty-line.receivedQty);
    }
  }
  for (const [index, item] of state.campaign.items.entries()) {
    exactKeys(item, campaignItemKeys);
    const definition = fixture.itemDefinitions[index];
    assert.equal(item.id, definition.id); assert.equal(item.name, definition.name); assert.equal(item.unit, definition.unit);
    for (const key of ['targetQty','pledgedQty','receivedQty','deliveredQty','confirmedQty','remainingPledgeQty']) integer(item[key]);
    assert(item.confirmedQty <= item.deliveredQty && item.deliveredQty <= item.receivedQty && item.receivedQty <= item.pledgedQty && item.pledgedQty <= item.targetQty);
    assert.equal(item.remainingPledgeQty, item.targetQty-item.pledgedQty);
    const line = focal?.items.find(x => x.campaignItemId===item.id);
    const prior = fixture.baselineContext.priorOtherContributions;
    assert.equal(item.pledgedQty, prior.pledgedQty[index] + (line ? line.pledgedQty-line.cancelledQty : 0));
    assert.equal(item.receivedQty, prior.receivedQty[index] + (line?.receivedQty ?? 0));
    const delivered = state.deliveries.filter(d => d.outcome==='DELIVERED').flatMap(d=>d.items).filter(x=>x.campaignItemId===item.id).reduce((n,x)=>n+x.quantity,0);
    assert.equal(item.deliveredQty, prior.deliveredQty[index]+delivered);
    assert.equal(item.confirmedQty, prior.confirmedQty[index]);
    const view = fixture.mockReadModels[stateName].caseContext.deliveryAvailableItems[index];
    assert.equal(view.availableQty, item.receivedQty-item.deliveredQty);
    assert.equal(view.campaignItemId, item.id);
  }
  assert.equal(fixture.mockReadModels[stateName].fixtureLabel, 'ASSUMPTION_UI_PROJECTION_NOT_API_RESPONSE');
}

for (const s of fixture.scenarios) {
  const {endpointId:ep, request:req, response:res} = s;
  const before = fixture.states[s.beforeState], after = fixture.states[s.afterState];
  assert(before && after); assert.equal(s.fixtureLabel,'MOCK_NOT_RUNTIME');
  assert.equal(req.method,'POST'); assert.equal(req.credentials,'same-origin');
  assert.equal(req.headers['Content-Type'],'application/json'); uuid(req.headers['Idempotency-Key']);
  assert.equal(req.headers['X-CSRF-TOKEN'],'MOCK_CSRF_TOKEN_NOT_A_SECRET');
  const expectedPath = ep==='G01' ? `/api/campaigns/${fixture.campaignId}/commitments` : ep==='R01' ? `/api/commitments/${before.focalContribution.id}/receipts` : `/api/cases/${fixture.caseId}/deliveries`;
  assert.equal(req.path,expectedPath);
  assert.deepEqual(s.totalsBefore,getTotals(before)); assert.deepEqual(s.totalsAfter,getTotals(after));
  const body=req.body;
  exactKeys(body, ep==='G01' ? ['items'] : ep==='R01' ? ['receivedAt','items','note'] : body.outcome==='FAILED' ? ['outcome','items','evidenceVersionIds','reason'] : ['outcome','handedOverAt','items','evidenceVersionIds']);
  const idKey=ep==='R01' ? 'contributionItemId' : 'campaignItemId';
  unique(body.items.map(x=>x[idKey]));
  for (const line of body.items) {
    exactKeys(line,[idKey,'quantity']); integer(line.quantity,1); uuid(line[idKey]);
    assert(ep==='R01' ? before.focalContribution.items.some(x=>x.id===line[idKey]) : itemIds.includes(line[idKey]));
  }
  const timestamp = ep==='R01' ? body.receivedAt : body.handedOverAt;
  if (timestamp) {
    utc(timestamp);
    assert(Date.parse(timestamp)>=Date.parse(fixture.baselineContext.campaignCreatedAt));
    assert(Date.parse(timestamp)<=Date.parse(fixture.fixtureClockUtc)+300_000);
  }
  if (ep==='D01') {
    if (body.outcome==='FAILED') { assert.deepEqual(body.items,[]); assert.deepEqual(body.evidenceVersionIds,[]); assert(body.reason.trim().length>0 && body.reason.length<=2000); }
    else { assert(body.evidenceVersionIds.length>0); assert(body.items.length>0); }
    unique(body.evidenceVersionIds); body.evidenceVersionIds.forEach(uuid);
    assert(body.evidenceVersionIds.every(id=>fixture.mockReadModels[s.beforeState].caseContext.availableEvidenceVersionIds.includes(id)));
  } else assert(body.items.length>0);
  if (ep==='R01') assert(body.note.length<=500);
  const actor = fixture.actors[s.actorKey]; assert(actor);
  if (res.status!==201) {
    assert([403,409].includes(res.status));
    exactKeys(res.body,['type','title','status','code','detail','instance','traceId','fieldErrors']);
    assert.equal(res.headers['Content-Type'],'application/problem+json');
    assert.equal(res.body.status,res.status); assert.equal(res.body.instance,req.path);
    assert.deepEqual(after,before); assert.deepEqual(s.totalsAfter,s.totalsBefore);
    assert.equal(s.expectedEffects.businessAuditEventsCreated,0); assert.equal(s.expectedEffects.blockchainJobsCreated,0);
    if (res.status===403) {
      assert.equal(res.body.code,'FORBIDDEN'); assert.equal(s.actorKey,forbiddenActor[ep]); assert(!canAct(ep,actor));
      if (ep!=='G01') assert(actor.memberships.some(m=>m.active && m.organizationId===fixture.organizationId));
      assert.deepEqual(res.body.fieldErrors,[]);
    } else {
      assert.equal(res.body.code,'QUANTITY_EXCEEDED'); assert(canAct(ep,actor));
      assert.equal(res.body.fieldErrors[0].field,'items[1].quantity');
      const capacity = body.items.map(line => ep==='G01' ? before.campaign.items.find(x=>x.id===line[idKey]).remainingPledgeQty : ep==='R01' ? before.focalContribution.items.find(x=>x.id===line[idKey]).outstandingQty : (()=>{const x=before.campaign.items.find(x=>x.id===line[idKey]);return x.receivedQty-x.deliveredQty;})());
      assert(body.items[0].quantity<=capacity[0]); assert(body.items[1].quantity>capacity[1]);
    }
    continue;
  }
  assert.equal(s.actorKey,expectedActor[ep]); assert(canAct(ep,actor));
  assert.equal(res.headers['Content-Type'],'application/json'); assert(res.headers.Location.endsWith(res.body.id));
  exactKeys(res.body,responseKeys[ep]); uuid(res.body.id); uuid(res.body.auditEventId);
  assert.equal(s.expectedEffects.businessAuditEventsCreated,1);
  const expected=clone(before);
  if (ep==='G01') {
    expected.focalContribution=clone(res.body);
    assert.equal(res.body.status,'PLEDGED');
    assert.equal(res.body.items.length,body.items.length);
    for (const line of body.items) {
      const item=expected.campaign.items.find(x=>x.id===line.campaignItemId);
      item.pledgedQty+=line.quantity; item.remainingPledgeQty-=line.quantity;
      const responseLine=res.body.items.find(x=>x.campaignItemId===line.campaignItemId);
      assert.equal(responseLine.pledgedQty,line.quantity); assert.equal(responseLine.receivedQty,0); assert.equal(responseLine.cancelledQty,0);
    }
  } else if (ep==='R01') {
    assert.equal(res.body.contributionId,before.focalContribution.id);
    assert.equal(res.body.recorderId,actor.userId); assert.equal(res.body.receivedAt,body.receivedAt);
    assert.deepEqual(res.body.items,body.items);
    expected.receipts.push(clone(res.body));
    for (const line of body.items) {
      const item=expected.focalContribution.items.find(x=>x.id===line.contributionItemId);
      item.receivedQty+=line.quantity; item.outstandingQty-=line.quantity;
      expected.campaign.items.find(x=>x.id===item.campaignItemId).receivedQty+=line.quantity;
    }
    expected.focalContribution.status='PARTIALLY_RECEIVED';
    assert(expected.focalContribution.items.some(x=>x.outstandingQty>0));
  } else {
    assert.equal(res.body.caseId,fixture.caseId); assert.equal(res.body.recorderId,actor.userId);
    assert.equal(res.body.outcome,body.outcome); assert.deepEqual(res.body.items,body.items);
    assert.deepEqual(res.body.evidenceVersionIds,body.evidenceVersionIds);
    expected.deliveries.push(clone(res.body));
    if (body.outcome==='DELIVERED') {
      assert.equal(res.body.reviewStatus,'PENDING');
      for (const line of body.items) expected.campaign.items.find(x=>x.id===line.campaignItemId).deliveredQty+=line.quantity;
    } else {
      assert.equal(res.body.reviewStatus,'NOT_APPLICABLE'); assert.equal(res.body.blockchainStatus,'NOT_APPLICABLE');
      assert.equal(s.expectedEffects.blockchainJobsCreated,0);
      assert.equal(s.expectedEffects.auditType,'DELIVERY_ATTEMPT_FAILED');
      assert.deepEqual(s.totalsAfter,s.totalsBefore);
    }
  }
  if (!(ep==='D01' && body.outcome==='FAILED')) {
    assert.equal(res.body.blockchainStatus,'PENDING_ON_CHAIN'); assert.equal(s.expectedEffects.blockchainJobsCreated,1);
  }
  assert.deepEqual(after,expected,`Unexpected state delta: ${s.id}`);
  assert(after.campaign.items.every(x=>x.confirmedQty===0));
}
for (const endpoint of uiMap.endpoints) {
  assert(endpoint.commonStateRefs.includes('idempotencyConflict'));
  assert.deepEqual(new Set(endpoint.mockScenarioIds),new Set(fixture.scenarios.filter(s=>s.endpointId===endpoint.endpointId).map(s=>s.id)));
  for (const id of endpoint.commonStateRefs) assert(uiMap.commonStates.some(s=>s.state===id));
  for (const row of endpoint.specificStates) {
    const scenario=fixture.scenarios.find(s=>s.id===row.fixtureScenario);
    assert(scenario); assert.equal(scenario.ui.state,row.state); assert.equal(Number.parseInt(row.response,10),scenario.response.status);
  }
}
for (const ep of ['G01','R01','D01']) {
  for (const suffix of ['success','forbidden','quantity-conflict']) assert(fixture.scenarios.some(s=>s.id===`${ep}-${suffix}`));
}
assert(Object.values(fixture.runtimeEvidence).every(status=>status==='NOT_RUN'));
assert.equal(fixture.replayExpectation.executionStatus,'NOT_RUN');
console.log('PASS F02: 5 snapshots, 10 scenarios, integer quantities/IDs, request/response shapes, partial receipt, role examples, atomic 403/409, failed-attempt zero goods effect and UI-state mapping.');
console.log('NOT_RUN: backend HTTP, frontend rendering, keyboard E2E, TypeScript compiler and blockchain.');
