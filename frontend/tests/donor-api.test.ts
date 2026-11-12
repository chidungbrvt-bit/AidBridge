import test from 'node:test';
import assert from 'node:assert/strict';
import { ApiClient } from '../src/lib/api-client';
import { createDonorAdapter } from '../features/donor/adapter';
import { ApiError, cancellationOutcomeUnknown, validateCancellation } from '../features/donor/model';
import fixture from '../../docs/fixtures/f02/goods-flow.mock.json';
import type { CommitmentResponse } from '../features/donor/model';

function harness(status = 200, code = 'OK') {
  const calls: {path: string; init?: RequestInit}[] = [];
  const api = new ApiClient(async (path, init) => {
    calls.push({path, init});
    if (path === '/api/auth/csrf') return Response.json({token:'test-csrf', headerName:'X-CSRF-TOKEN'});
    return Response.json(status >= 400 ? {status, code, title:'Rejected'} : {ok:true}, {status});
  });
  return {calls, api, donor:createDonorAdapter(api)};
}
test('F07 shared client: G01/G03 use same-origin cookies, CSRF, exact body/key and correct IDs', async () => {
  const h=harness(); const body={items:[{campaignItemId:'campaign-item',quantity:2}]};
  await h.donor.pledge('campaign-id',body,'stable-key');
  await h.donor.pledge('campaign-id',body,'stable-key');
  const cancel={items:[{contributionItemId:'contribution-item',quantity:1}],reason:'Availability changed'};
  await h.donor.cancel('pledge-id',cancel,'cancel-key');
  assert.equal(h.calls.filter(x=>x.path==='/api/auth/csrf').length,1);
  const writes=h.calls.filter(x=>x.init?.method==='POST');
  assert.equal(writes[0].path,'/api/campaigns/campaign-id/commitments');
  assert.equal(writes[2].path,'/api/commitments/pledge-id/cancellations');
  assert.equal(writes[0].init?.body,writes[1].init?.body);
  for(const x of writes){ assert.equal(x.init?.credentials,'same-origin'); assert.equal(new Headers(x.init?.headers).get('X-CSRF-TOKEN'),'test-csrf'); }
  assert.equal(new Headers(writes[0].init?.headers).get('Idempotency-Key'),'stable-key');
  assert.deepEqual(JSON.parse(String(writes[2].init?.body)),cancel);
});
test('F07 shared client: public campaigns and own history use documented pagination; no guessed GET commitment', async () => {
  const h=harness(); await h.api.listCampaigns(2); await h.donor.getCampaign('a/b'); await h.donor.getCommitments(3);
  assert.deepEqual(h.calls.map(x=>x.path),['/api/campaigns?page=2&size=20','/api/campaigns/a%2Fb','/api/me/commitments?page=3&size=20']);
});
for(const status of [400,403,409,504])test(`F07 error adapter preserves HTTP ${status} and unknown-outcome classification`,async()=>{
  const h=harness(status,status===409?'QUANTITY_EXCEEDED':'HTTP_ERROR');
  await assert.rejects(()=>h.donor.cancel('id',{items:[],reason:'test'},'key'),e=>{
    assert.ok(e instanceof ApiError); assert.equal(e.problem.status,status); assert.deepEqual(e.problem.fieldErrors,[]);
    assert.equal(cancellationOutcomeUnknown(e),status===504); return true;
  });
  assert.equal(h.calls.filter(x=>x.init?.method==='POST').length,1); // no automatic replay
});
test('F07 CSRF_INVALID explicit retry refreshes token, preserving idempotency key and payload',async()=>{
  let attempts=0,csrf=0; const requests:RequestInit[]=[];
  const api=new ApiClient(async(path,init)=>{
    if(path==='/api/auth/csrf') return Response.json({token:`token-${++csrf}`,headerName:'X-CSRF-TOKEN'});
    requests.push(init!); return ++attempts===1?Response.json({status:403,code:'CSRF_INVALID',title:'Expired'},{status:403}):Response.json({ok:true});
  });
  const donor=createDonorAdapter(api),body={items:[{campaignItemId:'i',quantity:1}]};
  await assert.rejects(()=>donor.pledge('c',body,'original'));
  await donor.pledge('c',body,'original');
  assert.equal(csrf,2); assert.equal(requests[0].body,requests[1].body);
  assert.equal(new Headers(requests[1].headers).get('Idempotency-Key'),'original');
});
test('F07 cancellation reason validation mirrors B07 markup/URL/NUL restrictions',()=>{
  const row=fixture.states.S2.focalContribution as CommitmentResponse;
  const draft={ [row.items[0].id]:{selected:true,quantity:'1'} };
  for(const reason of ['<tag>','https://invalid.test','bad\0text']) assert.ok(validateCancellation(row,draft,reason).errors.reason);
  assert.equal(validateCancellation(row,draft,'Availability changed').errors.reason,undefined);
});
