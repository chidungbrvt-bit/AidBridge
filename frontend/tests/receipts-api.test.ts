import test from 'node:test';
import assert from 'node:assert/strict';
import {ApiClient,ApiError} from '../src/lib/api-client';
import {buildReceipt,submitReceipt,type StaffContext,type ReceiptWork} from '../features/receipts/model';
const context:StaffContext={id:'case',organizationId:'org',outstandingCommitments:[{contributionId:'pledge',status:'PARTIALLY_RECEIVED',pledgedAt:'2026-11-14T00:00:00Z',items:[{contributionItemId:'line',campaignItemId:'item',itemName:'Rice',unit:'kg',pledgedQty:5,receivedQty:2,outstandingQty:3}]}]};
const body=()=>buildReceipt(context,'pledge',{line:'1'},'2026-11-14T01:00Z','');
test('multi-item receipt maps backend line IDs and rejects one invalid line atomically',()=>{
  const multi=structuredClone(context),first=multi.outstandingCommitments[0].items[0];
  multi.outstandingCommitments[0].items.push({...first,contributionItemId:'second',unit:'box',outstandingQty:1});
  assert.equal(buildReceipt(multi,'pledge',{line:'2',second:'1'},'2026-11-14','').items.length,2);
  const original=structuredClone(multi);assert.throws(()=>buildReceipt(multi,'pledge',{line:'1',second:'2'},'2026-11-14',''));assert.deepEqual(multi,original);
});
test('B08 mapping uses staff contribution IDs, integer partial quantity and safe 500-character note',()=>{
  assert.deepEqual(body().items,[{contributionItemId:'line',quantity:1}]);
  assert.equal(buildReceipt(context,'pledge',{line:'1'},'2026-11-14T01:00Z','x'.repeat(500)).note?.length,500);
  for(const value of ['0','-1','1.5','4','', '2147483648'])assert.throws(()=>buildReceipt(context,'pledge',{line:value},'2026-11-14',''));
  assert.throws(()=>buildReceipt(context,'pledge',{foreign:'1'},'2026-11-14',''));
  assert.throws(()=>buildReceipt(context,'pledge',{line:'1'},'2026-11-14','x'.repeat(501)));
});
test('shared client uses staff read, CSRF/cookies and exact receipt path/body/key without auto replay',async()=>{
  const calls:{path:string;init?:RequestInit}[]=[];
  const api=new ApiClient(async(path,init)=>{calls.push({path,init});return Response.json(path.endsWith('csrf')?{token:'csrf',headerName:'X-CSRF-TOKEN'}:context);});
  await api.getReceiptContext('a/b');await api.receiveGoods('p/q',body(),'same-key');
  assert.equal(calls[0].path,'/api/org/cases/a%2Fb');const write=calls[2];
  assert.equal(write.path,'/api/commitments/p%2Fq/receipts');assert.equal(write.init?.credentials,'same-origin');
  assert.equal(new Headers(write.init?.headers).get('X-CSRF-TOKEN'),'csrf');assert.equal(new Headers(write.init?.headers).get('Idempotency-Key'),'same-key');assert.deepEqual(JSON.parse(String(write.init?.body)),body());
});
for(const status of [400,401,403,404,409,408,500,504])test(`receipt failure ${status} preserves only uncertain requests`,async()=>{
  const work:ReceiptWork={};let count=0;const seen:string[]=[];
  const api={receiveGoods:async(_id:string,b:unknown,key:string)=>{count++;seen.push(JSON.stringify({b,key}));if(count<=2)throw new ApiError({type:'urn:test',status,code:'TEST',title:'Failure'});return {id:'receipt'} as never;}};
  await assert.rejects(()=>submitReceipt(api,work,{id:'pledge',body:body()}));
  const unknown=status>=500||status===408;assert.equal(!!work.pending,unknown);assert.equal(work.receipt,undefined);
  if(unknown){await assert.rejects(()=>submitReceipt(api,work));await submitReceipt(api,work);assert.equal(new Set(seen).size,1);assert.equal(work.pending,undefined);}
});
test('lost response after commit retries same logical receipt; double submit rejected; remount journal survives',async()=>{
  const work:ReceiptWork={},seen=new Map<string,number>();let release:()=>void=()=>{};
  const api={receiveGoods:async(_id:string,_body:unknown,key:string)=>{if(!seen.has(key))seen.set(key,1);await new Promise<void>(r=>{release=r;});if(!work.receipt){work.receipt={id:'saved'} as never;throw new TypeError('Network lost');}return work.receipt;}};
  const first=submitReceipt(api,work,{id:'pledge',body:body()});await assert.rejects(()=>submitReceipt(api,work));release();await assert.rejects(()=>first);
  const retry=submitReceipt(api,work);release();await retry;assert.equal(seen.size,1);
});
