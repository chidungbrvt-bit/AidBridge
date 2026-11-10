// Product expectations checker only; no API/DB/browser/chain calls.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const data=JSON.parse(readFileSync(new URL('./f01-demo-checklist.json',import.meta.url),'utf8'));
const api=readFileSync(new URL('../../architecture/API_SPECIFICATION.md',import.meta.url),'utf8');
const roles=readFileSync(new URL('../../..'+'/backend/aidbridge-backend/src/main/java/com/aidbridge/organization/entity/OrganizationMembership.java',import.meta.url),'utf8');
assert.equal(data.household,'Demo Household A');
assert.equal(data.positiveChecklist.length,11); assert.equal(data.alternativeChecklist.length,16);
const ids=[...data.positiveChecklist,...data.alternativeChecklist].map(x=>x.id);
assert.equal(new Set(ids).size,27);
const roleBody=/enum Role\s*\{([^}]+)\}/.exec(roles)?.[1];
assert(roleBody);
const roleNames=new Set(roleBody.split(',').map(x=>x.trim()).filter(Boolean));
for(const a of Object.values(data.identities)) if(a.role!=='DONOR_CAPABILITY') assert(roleNames.has(a.role));
assert.equal(new Set(Object.values(data.identities).map(x=>x.userId)).size,Object.keys(data.identities).length);
assert.notEqual(data.identities.volunteer.userId,data.identities.verifier.userId);
assert.notEqual(data.identities.volunteer.userId,data.identities.confirmer.userId);
for(const state of Object.values(data.states)) {
 assert.equal(state.totals.length,2);
 for(const item of state.totals) {
  for(const k of ['target','pledged','received','delivered','confirmed']) assert(Number.isInteger(item[k])&&item[k]>=0);
  assert(item.confirmed<=item.delivered && item.delivered<=item.received && item.received<=item.pledged && item.pledged<=item.target);
 }
}
for(const step of data.positiveChecklist) {
 assert(api.includes('| '+step.apiId+' '+step.endpoint+' '),step.endpoint);
 assert(data.states[step.after]); if(step.before) assert(data.states[step.before]);
 assert.equal(step.executionStatus,'NOT_RUN'); assert(data.identities[step.actor]);
}
for(const step of data.alternativeChecklist) {assert(data.states[step.resetToState]);assert.equal(step.executionStatus,'NOT_RUN');}
assert.deepEqual(data.states.partialReceipt.totals.map(x=>x.received),[2,1]);
assert.deepEqual(data.states.delivered.totals.map(x=>x.confirmed),[0,0]);
assert.deepEqual(data.states.closed.totals.map(x=>x.confirmed),[2,2]);
assert.equal(data.states.closed.caseStatus,'COMPLETED');
console.log('PASS F01 design checks: 11 steps, 16 alternatives, source API references, role enums, distinct identities, multi-item arithmetic and honest execution labels.');
console.log('NOT_RUN: actual HTTP, role/state guards, browser E2E, NGO validation and Fuji.');
