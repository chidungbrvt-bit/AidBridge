import test from 'node:test';
import assert from 'node:assert/strict';
import { createMockDonorApi, DEMO_CAMPAIGN_ID } from './mock.ts';
import { validateDraft, validateCancellation, ApiError } from './model.ts';
const id=DEMO_CAMPAIGN_ID;
test('form rejects empty, fraction, zero and overflow; accepts multi-item integer request',async()=>{
 const {api}=createMockDonorApi(0);const c=await api.getCampaign(id);const [a,b]=c.items;
 assert(validateDraft(c,{}).errors.form);
 for(const quantity of ['0','-1','1.5','1000001','15','Infinity','']) assert(validateDraft(c,{[a.id]:{selected:true,quantity}}).errors[a.id]);
 const result=validateDraft(c,{[a.id]:{selected:true,quantity:'8'},[b.id]:{selected:true,quantity:'2'}});
 assert.deepEqual(result.errors,{});assert.equal(result.body.items.length,2);
});
test('multi-item pledge updates only pledged totals and donor own history; replay counted once',async()=>{
 const {api}=createMockDonorApi(0);const c=await api.getCampaign(id);
 const body={items:c.items.map((x,i)=>({campaignItemId:x.id,quantity:i===0?8:2}))};
 const first=await api.pledge(id,body,'same-key');const replay=await api.pledge(id,body,'same-key');assert.equal(first.id,replay.id);
 assert.equal((await api.getCommitments(0)).totalElements,1);
 assert.deepEqual((await api.getCampaign(id)).items.map(x=>[x.pledgedQty,x.receivedQty,x.deliveredQty,x.confirmedQty]),[[14,2,1,0],[3,0,0,0]]);
 await assert.rejects(api.pledge(id,{items:[{campaignItemId:c.items[0].id,quantity:1}]},'same-key'),(e:unknown)=>e instanceof ApiError&&e.problem.code==='IDEMPOTENCY_CONFLICT');
});
test('overflow second line rejects entire command; 400 and 403 leave state untouched',async()=>{
 const mock=createMockDonorApi(0);const c=await mock.api.getCampaign(id);
 await assert.rejects(mock.api.pledge(id,{items:[{campaignItemId:c.items[0].id,quantity:8},{campaignItemId:c.items[1].id,quantity:4}]},'overflow'));
 for(const fault of ['400','403'] as const){mock.setFault(fault);await assert.rejects(mock.api.pledge(id,{items:[{campaignItemId:c.items[0].id,quantity:1}]},fault));}
 assert.deepEqual(await mock.api.getCampaign(id),c);assert.equal((await mock.api.getCommitments(0)).totalElements,0);
});
test('response lost after save recovers original pledge by exact key; history pagination is consistent',async()=>{
 const mock=createMockDonorApi(0);const c=await mock.api.getCampaign(id);const body={items:[{campaignItemId:c.items[0].id,quantity:1}]};
 mock.setFault('timeout-after-save');await assert.rejects(mock.api.pledge(id,body,'recover'));
 const recovered=await mock.api.pledge(id,body,'recover');assert.equal((await mock.api.getCommitments(0)).items[0].id,recovered.id);
 for(let n=0;n<5;n++)await mock.api.pledge(id,body,`new-${n}`);
 assert.equal((await mock.api.getCommitments(0)).items.length,5);assert.equal((await mock.api.getCommitments(1)).items.length,1);
});

test('ProblemDetail may omit fieldErrors; UI must receive an empty list',()=>{
 const error=new ApiError({type:'about:blank',title:'Forbidden',status:403,code:'FORBIDDEN',detail:'Access denied',instance:'/api/test',traceId:'test'} as import('./model.ts').ProblemDetail);
 assert.deepEqual(error.problem.fieldErrors,[]);
});
test('reset cancels an in-flight mock write instead of leaking it into fresh data',async()=>{
 const mock=createMockDonorApi(20);const c=await mock.api.getCampaign(id);
 const request=mock.api.pledge(id,{items:[{campaignItemId:c.items[0].id,quantity:1}]},'reset-key');mock.reset();
 await assert.rejects(request);assert.equal((await mock.api.getCommitments(0)).totalElements,0);
});

test('concurrent mock pledges remain atomic; one-line 409 points at submitted row',async()=>{
 const mock=createMockDonorApi(0);const c=await mock.api.getCampaign(id);
 const body={items:[{campaignItemId:c.items[0].id,quantity:8},{campaignItemId:c.items[1].id,quantity:2}]};
 const results=await Promise.allSettled([mock.api.pledge(id,body,'concurrent-a'),mock.api.pledge(id,body,'concurrent-b')]);
 assert.equal(results.filter(x=>x.status==='fulfilled').length,1);assert.equal((await mock.api.getCommitments(0)).totalElements,1);
 assert.deepEqual((await mock.api.getCampaign(id)).items.map(x=>x.pledgedQty),[14,3]);
 mock.setFault('409');await assert.rejects(mock.api.pledge(id,{items:[{campaignItemId:c.items[0].id,quantity:1}]},'single-error'),(e:unknown)=>e instanceof ApiError&&e.problem.fieldErrors[0].field==='items[0].quantity');
});

test('G03 validates positive integer quantities, outstanding limits and reason',async()=>{
 const mock=createMockDonorApi(0);mock.loadPartialReceiptFixture();const p=(await mock.api.getCommitments(0)).items[0],a=p.items[0];
 assert(validateCancellation(p,{},'Reason').errors.form);
 for(const quantity of ['','0','-1','1.5','4','Infinity','1000001'])assert(validateCancellation(p,{[a.id]:{selected:true,quantity}},'Reason').errors[a.id]);
 assert(validateCancellation(p,{[a.id]:{selected:true,quantity:'2'}},'').errors.reason);
 assert(validateCancellation(p,{[a.id]:{selected:true,quantity:'2'}},'x'.repeat(501)).errors.reason);
 const checked=validateCancellation(p,{[a.id]:{selected:true,quantity:'2'}},' Changed availability ');
 assert.deepEqual(checked.errors,{});assert.equal(checked.body.reason,'Changed availability');assert.equal(checked.body.items[0].contributionItemId,a.id);
});
test('G03 partial multi-line cancellation retains original pledge/receipts and releases capacity',async()=>{
 const mock=createMockDonorApi(0);mock.loadPartialReceiptFixture();const p=(await mock.api.getCommitments(0)).items[0];
 const saved=await mock.api.cancel(p.id,{items:p.items.map((x,i)=>({contributionItemId:x.id,quantity:i===0?2:1})),reason:'Changed availability'},'partial');
 assert.deepEqual(saved.items.map(x=>[x.pledgedQty,x.cancelledQty,x.receivedQty,x.outstandingQty]),[[8,2,5,1],[2,1,1,0]]);assert.equal(saved.status,'PARTIALLY_RECEIVED');
 assert.deepEqual((await mock.api.getCampaign(id)).items.map(x=>[x.pledgedQty,x.remainingPledgeQty,x.receivedQty,x.deliveredQty,x.confirmedQty]),[[12,8,7,1,0],[2,2,1,0,0]]);
 const final=await mock.api.cancel(p.id,{items:[{contributionItemId:p.items[0].id,quantity:1}],reason:'Cancel rest'},'rest');assert.equal(final.status,'RECEIVED');assert.equal(final.items[0].receivedQty,5);
});
test('G03 full unreceived cancellation becomes CANCELLED and G01 can reuse released capacity',async()=>{
 const {api}=createMockDonorApi(0);const c=await api.getCampaign(id);const p=await api.pledge(id,{items:[{campaignItemId:c.items[0].id,quantity:8}]},'create');
 const partial=await api.cancel(p.id,{items:[{contributionItemId:p.items[0].id,quantity:3}],reason:'Cannot provide all'},'partial');assert.equal(partial.status,'PLEDGED');assert.equal(partial.items[0].outstandingQty,5);
 const saved=await api.cancel(p.id,{items:[{contributionItemId:p.items[0].id,quantity:5}],reason:'Cannot provide rest'},'cancel');assert.equal(saved.status,'CANCELLED');
 assert.deepEqual((await api.getCampaign(id)).items,c.items);await api.pledge(id,{items:[{campaignItemId:c.items[0].id,quantity:8}]},'new-pledge');assert.equal((await api.getCommitments(0)).totalElements,2);
});
test('G03 received/over-cancel or invalid second line rejects atomically',async()=>{
 const mock=createMockDonorApi(0);mock.loadPartialReceiptFixture();const before=await mock.api.getCommitments(0),campaign=await mock.api.getCampaign(id),p=before.items[0];
 for(const quantities of [[6,1],[2,2]])await assert.rejects(mock.api.cancel(p.id,{items:p.items.map((x,i)=>({contributionItemId:x.id,quantity:quantities[i]})),reason:'Invalid'},crypto.randomUUID()),(e:unknown)=>e instanceof ApiError&&e.problem.status===409);
 assert.deepEqual(await mock.api.getCommitments(0),before);assert.deepEqual(await mock.api.getCampaign(id),campaign);
});
test('G03 malformed quantities, duplicate IDs, foreign commitment and 400/403/409 do not mutate',async()=>{
 const mock=createMockDonorApi(0);mock.loadPartialReceiptFixture();const before=await mock.api.getCommitments(0),campaign=await mock.api.getCampaign(id),p=before.items[0],line={contributionItemId:p.items[0].id,quantity:1};
 for(const quantity of [0,-1,1.5,NaN,1000001])await assert.rejects(mock.api.cancel(p.id,{items:[{...line,quantity}],reason:'Invalid'},crypto.randomUUID()));
 await assert.rejects(mock.api.cancel(p.id,{items:[line,line],reason:'Invalid'},'dup'));
 await assert.rejects(mock.api.cancel('foreign',{items:[line],reason:'Invalid'},'foreign'),(e:unknown)=>e instanceof ApiError&&e.problem.status===404);
 for(const fault of ['400','403','409'] as const){mock.setFault(fault);await assert.rejects(mock.api.cancel(p.id,{items:[line],reason:'Error test'},fault));}
 mock.setFault('none');assert.deepEqual(await mock.api.getCommitments(0),before);assert.deepEqual(await mock.api.getCampaign(id),campaign);
});
test('G03 exact-key replay, concurrent replay and changed-body conflict never release twice',async()=>{
 const mock=createMockDonorApi(0);mock.loadPartialReceiptFixture();const p=(await mock.api.getCommitments(0)).items[0],body={items:[{contributionItemId:p.items[0].id,quantity:2}],reason:'Change'};
 const [a,b]=await Promise.all([mock.api.cancel(p.id,body,'same'),mock.api.cancel(p.id,body,'same')]);assert.equal(a.auditEventId,b.auditEventId);
 await assert.rejects(mock.api.cancel(p.id,{...body,reason:'Different'},'same'),(e:unknown)=>e instanceof ApiError&&e.problem.code==='IDEMPOTENCY_CONFLICT');
 assert.equal((await mock.api.getCommitments(0)).items[0].items[0].cancelledQty,2);
 await assert.rejects(mock.api.cancel(p.id,body,'new-key'),(e:unknown)=>e instanceof ApiError&&e.problem.status===409);
});
test('G03 concurrent different-key requests validate latest outstanding state',async()=>{
 const mock=createMockDonorApi(0);mock.loadPartialReceiptFixture();const p=(await mock.api.getCommitments(0)).items[0],body={items:[{contributionItemId:p.items[0].id,quantity:2}],reason:'Change'};
 const results=await Promise.allSettled([mock.api.cancel(p.id,body,'a'),mock.api.cancel(p.id,body,'b')]);assert.equal(results.filter(x=>x.status==='fulfilled').length,1);
 assert.equal((await mock.api.getCommitments(0)).items[0].items[0].cancelledQty,2);
});
test('G03 receipt racing with stale cancellation rejects without cancelling received goods',async()=>{
 const mock=createMockDonorApi(20);mock.loadPartialReceiptFixture();const p=(await mock.api.getCommitments(0)).items[0];
 const request=mock.api.cancel(p.id,{items:[{contributionItemId:p.items[0].id,quantity:3}],reason:'Stale'},'race');mock.receiveRemainingForDemo();
 await assert.rejects(request,(e:unknown)=>e instanceof ApiError&&e.problem.status===409);const fresh=(await mock.api.getCommitments(0)).items[0];
 assert.equal(fresh.items[0].cancelledQty,0);assert.equal(fresh.items[0].receivedQty,8);assert.equal(fresh.status,'RECEIVED');
});
test('G03 lost response recovery uses original request/key, no duplicate cancellation',async()=>{
 const mock=createMockDonorApi(0);mock.loadPartialReceiptFixture();const p=(await mock.api.getCommitments(0)).items[0],body={items:[{contributionItemId:p.items[0].id,quantity:2}],reason:'Change'};
 mock.setFault('timeout-after-save');await assert.rejects(mock.api.cancel(p.id,body,'recover'));
 const saved=await mock.api.cancel(p.id,body,'recover');assert.equal(saved.items[0].cancelledQty,2);assert.equal((await mock.api.getCommitments(0)).items[0].items[0].outstandingQty,1);
});
test('G03 reset invalidates an in-flight cancellation',async()=>{
 const mock=createMockDonorApi(20);mock.loadPartialReceiptFixture();const p=(await mock.api.getCommitments(0)).items[0];
 const request=mock.api.cancel(p.id,{items:[{contributionItemId:p.items[0].id,quantity:1}],reason:'Reset'},'reset');mock.reset();await assert.rejects(request);assert.equal((await mock.api.getCommitments(0)).totalElements,0);
});
