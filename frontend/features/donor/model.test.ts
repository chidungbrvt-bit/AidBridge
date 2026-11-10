import test from 'node:test';
import assert from 'node:assert/strict';
import { createMockDonorApi, DEMO_CAMPAIGN_ID } from './mock.ts';
import { validateDraft, ApiError } from './model.ts';
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
