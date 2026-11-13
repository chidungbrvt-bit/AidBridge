import { test, expect, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import type { CommitmentResponse } from '../../features/donor/model';

// No success fixtures or mock API: all reads/writes reach Next -> Spring Boot -> PostgreSQL.
// Only the two response-loss tests intercept, after route.fetch() has committed to the real backend.
const schema=process.env.F07_E2E_SCHEMA ?? '';
const campaign=process.env.F07_E2E_CAMPAIGN_ID ?? '';
const donor=process.env.F07_E2E_DONOR_ID ?? '';
const recorder=process.env.F07_E2E_RECORDER_ID ?? '';
const evidence: {scenario:string;contributionId?:string;databaseVerified:boolean}[]=[];
function id(value:string){expect(value).toMatch(/^[0-9a-f-]{36}$/i);return value;}
function sql(query:string):string {
  if(process.env.CI!=='true' || process.env.PGHOST!=='127.0.0.1' || process.env.PGDATABASE!=='aidbridge_test'
    || process.env.PGUSER!=='aidbridge_app' || !/^b04_it_[a-f0-9]+$/.test(schema))throw Error('Unsafe E2E DB target');
  return execFileSync('psql',['-X','-q','-A','-t','--set','ON_ERROR_STOP=1','--command',`SET search_path TO "${schema}"; ${query}`],{encoding:'utf8'}).trim();
}
function count(){return Number(sql(`SELECT count(*) FROM contributions WHERE donor_user_id='${id(donor)}'`));}
function cancelled(line:string){return Number(sql(`SELECT cancelled_quantity FROM contribution_items WHERE id='${id(line)}'`));}
function outbox(contribution:string,event:string){
  expect(['AID_PLEDGED','COMMITMENT_REDUCED']).toContain(event);
  return Number(sql(`SELECT count(*) FROM blockchain_jobs j JOIN audit_events a ON a.id=j.audit_event_id WHERE a.entity_id='${id(contribution)}' AND a.event_type='${event}' AND j.status='PENDING'`));
}
async function login(page:Page){
  await page.goto('/login');await page.getByLabel('Email address').fill(process.env.F07_E2E_DONOR_EMAIL!);
  await page.getByLabel('Password',{exact:true}).fill(process.env.F07_E2E_DONOR_PASSWORD!);
  await page.getByRole('button',{name:'Sign in →',exact:true}).click();await expect(page).toHaveURL(/\/campaigns$/);
  const me=await page.request.get('/api/auth/me');expect(me.status()).toBe(200);expect(me.headers()['x-aidbridge-mock']).toBeUndefined();
  const user=await me.json();expect(user.userId).toBe(donor);expect(user.donorEnabled).toBe(true);expect(user.memberships).toEqual([]);
  expect((await page.context().cookies()).some(c=>c.httpOnly && c.name==='JSESSIONID')).toBe(true);
}
async function fillPledge(page:Page){
  await page.goto(`/campaigns/${campaign}`);await expect(page.getByRole('heading',{name:'Pledge essential goods'})).toBeVisible();
  await page.getByLabel('Food',{exact:false}).first().check();await page.getByLabel('Quantity (PACKAGE)',{exact:true}).fill('2');
  await page.getByLabel('Hygiene',{exact:false}).first().check();await page.getByLabel('Quantity (KIT)',{exact:true}).fill('1');
}
async function pledge(page:Page):Promise<CommitmentResponse>{
  await fillPledge(page);
  const response=page.waitForResponse(r=>new URL(r.url()).pathname===`/api/campaigns/${campaign}/commitments` && r.request().method()==='POST');
  await page.getByRole('button',{name:'Pledge goods →',exact:true}).click();const r=await response;expect(r.status()).toBe(201);
  expect(r.headers()['x-aidbridge-mock']).toBeUndefined();const saved=await r.json();
  await expect(page.getByText('Goods pledged. Pending on-chain.',{exact:true})).toBeVisible();return saved;
}
async function openCancel(page:Page,record:CommitmentResponse){
  await page.getByRole('button',{name:'View my pledges →'}).click();
  const card=page.locator('article').filter({hasText:record.id});await expect(card).toBeVisible();
  await card.getByRole('button',{name:'Cancel unreceived goods',exact:true}).click();
  const panel=page.getByRole('region',{name:'Cancel unreceived goods',exact:true});
  await panel.getByLabel('Cancel Food',{exact:false}).check();await panel.getByLabel('Cancel quantity (PACKAGE)',{exact:true}).fill('1');
  await panel.getByLabel('Cancellation reason').fill('CI simulated availability change');return {card,panel};
}
async function foodLine(page:Page,record:CommitmentResponse){
  const c=await (await page.request.get(`/api/campaigns/${campaign}`)).json();
  const item=c.items.find((x:{name:string})=>x.name==='Food');return record.items.find(x=>x.campaignItemId===item.id)!;
}

test.describe.configure({mode:'serial'});
test.setTimeout(60_000);
test.beforeEach(async({page})=>{expect(sql('SELECT current_database()')).toBe('aidbridge_test');await login(page);});
test.afterAll(()=>{
  mkdirSync('test-results',{recursive:true});
  writeFileSync('test-results/f07-real-evidence.json',JSON.stringify({mode:'REAL_NEXT_SPRING_POSTGRES',blockchainEnabled:false,
    backendFixture:'AbstractCampaignHttpIT',receivedFixture:'SQL-only (B08/R01 not implemented)',expectedScenarios:5,
    passedScenarios:evidence.length,scenarios:evidence},null,2));
});

test('real G01/G02/G03: multi-item pledge, own history, partial cancellation and persisted audit/outbox',async({page})=>{
  const before=count();const saved=await pledge(page);expect(count()).toBe(before+1);expect(saved.items).toHaveLength(2);
  expect(outbox(saved.id,'AID_PLEDGED')).toBe(1);const line=await foodLine(page,saved);
  const {card,panel}=await openCancel(page,saved);
  await expect(card.getByRole('row').filter({hasText:'Food'}).getByRole('cell')).toHaveText(['2','0','0','2']);
  await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();
  await expect(card.getByRole('row').filter({hasText:'Food'}).getByRole('cell')).toHaveText(['2','1','0','1']);
  expect(cancelled(line.id)).toBe(1);expect(outbox(saved.id,'COMMITMENT_REDUCED')).toBe(1);
  const real=(await (await page.request.get('/api/me/commitments?page=0&size=20')).json()).items as CommitmentResponse[];
  expect(real.some(x=>x.id===saved.id)).toBe(true);
  expect(Number(sql(`SELECT count(*) FROM contributions WHERE id='${id(saved.id)}' AND donor_user_id='${id(donor)}'`))).toBe(1);
  evidence.push({scenario:'multi-item-history-partial-cancel-outbox',contributionId:saved.id,databaseVerified:true});
});

test('real stale receipt409: SQL receipt fixture arrives after form opens; no cancellation is committed',async({page})=>{
  const saved=await pledge(page),line=await foodLine(page,saved);const {card,panel}=await openCancel(page,saved);
  const receipt=randomUUID();
  // Exactly the receipt fixture used by ContributionCancelIT; no fake R01 endpoint or production seed.
  sql(`BEGIN; INSERT INTO goods_receipts(id,contribution_id,received_by,idempotency_key) VALUES('${receipt}','${id(saved.id)}','${id(recorder)}','${randomUUID()}');
    INSERT INTO goods_receipt_items(id,goods_receipt_id,contribution_id,contribution_item_id,received_quantity) VALUES('${randomUUID()}','${receipt}','${id(saved.id)}','${id(line.id)}',2); COMMIT;`);
  const rejected=page.waitForResponse(r=>r.url().includes(`/api/commitments/${saved.id}/cancellations`) && r.request().method()==='POST');
  await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();expect((await rejected).status()).toBe(409);
  await expect(panel.getByRole('alert')).toContainText('409 Conflict');
  await expect(card.getByRole('row').filter({hasText:'Food'}).getByRole('cell')).toHaveText(['2','0','2','0']);
  expect(cancelled(line.id)).toBe(0);expect(outbox(saved.id,'COMMITMENT_REDUCED')).toBe(0);
  evidence.push({scenario:'real-received-fixture-stale409-no-write',contributionId:saved.id,databaseVerified:true});
});

test('real G01 commit then lost504: exact request/key replay creates one DB contribution',async({page})=>{
  const before=count();let lost=false;let saved:CommitmentResponse|undefined;
  const writes:{key:string;body:string|null}[]=[];let replayed=false;
  await page.route(`**/api/campaigns/${campaign}/commitments`,async route=>{
    const request=route.request();writes.push({key:request.headers()['idempotency-key'],body:request.postData()});
    const actual=await route.fetch();expect(actual.status()).toBe(201);expect(actual.headers()['x-aidbridge-mock']).toBeUndefined();
    if(!lost){lost=true;saved=await actual.json();expect(count()).toBe(before+1);await route.fulfill({status:504,contentType:'application/problem+json',body:JSON.stringify({status:504,code:'GATEWAY_TIMEOUT',title:'CI dropped committed response'})});}
    else{replayed=actual.headers()['idempotent-replayed']==='true';await route.fulfill({response:actual});}
  });
  await fillPledge(page);await page.getByRole('button',{name:'Pledge goods →',exact:true}).click();
  const retry=page.getByRole('button',{name:'Retry same request',exact:true});await expect(retry).toBeVisible();
  await expect(page.getByLabel('Quantity (PACKAGE)',{exact:true})).toBeDisabled();await retry.click();
  await expect(page.getByText('Goods pledged. Pending on-chain.',{exact:true})).toBeVisible();
  expect(writes).toHaveLength(2);expect(writes[0]).toEqual(writes[1]);expect(replayed).toBe(true);expect(count()).toBe(before+1);
  expect(outbox(saved!.id,'AID_PLEDGED')).toBe(1);
  evidence.push({scenario:'real-pledge-commit504-exact-replay',contributionId:saved!.id,databaseVerified:true});
});

test('real G03 commit then lost504: original 1 unit cancellation is stored exactly once',async({page})=>{
  const saved=await pledge(page),line=await foodLine(page,saved);const {card,panel}=await openCancel(page,saved);
  let lost=false,replayed=false;const writes:{key:string;body:string|null}[]=[];
  await page.route(`**/api/commitments/${saved.id}/cancellations`,async route=>{
    const request=route.request();writes.push({key:request.headers()['idempotency-key'],body:request.postData()});
    const actual=await route.fetch();expect(actual.status()).toBe(200);
    if(!lost){lost=true;expect(cancelled(line.id)).toBe(1);await route.fulfill({status:504,contentType:'application/problem+json',body:JSON.stringify({status:504,code:'GATEWAY_TIMEOUT',title:'CI dropped committed response'})});}
    else{replayed=actual.headers()['idempotent-replayed']==='true';await route.fulfill({response:actual});}
  });
  await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();
  const retry=panel.getByRole('button',{name:'Retry same cancellation',exact:true});await expect(retry).toBeVisible();await retry.click();
  await expect(card.getByRole('row').filter({hasText:'Food'}).getByRole('cell')).toHaveText(['2','1','0','1']);
  expect(writes).toHaveLength(2);expect(writes[0]).toEqual(writes[1]);expect(replayed).toBe(true);
  expect(cancelled(line.id)).toBe(1);expect(outbox(saved.id,'COMMITMENT_REDUCED')).toBe(1);
  evidence.push({scenario:'real-cancel-commit504-exact-replay',contributionId:saved.id,databaseVerified:true});
});

test('real HTTP CSRF403 and validation400 leave PostgreSQL unchanged',async({page})=>{
  const c=await (await page.request.get(`/api/campaigns/${campaign}`)).json();const before=count();
  const body={items:[{campaignItemId:c.items[0].id,quantity:1}]};
  const denied=await page.request.post(`/api/campaigns/${campaign}/commitments`,{data:body,headers:{'Idempotency-Key':randomUUID()}});
  expect(denied.status()).toBe(403);expect((await denied.json()).code).toBe('CSRF_INVALID');expect(count()).toBe(before);
  const token=await (await page.request.get('/api/auth/csrf')).json();
  const invalid=await page.request.post(`/api/campaigns/${campaign}/commitments`,{data:{items:[{campaignItemId:c.items[0].id,quantity:0}]},headers:{'Idempotency-Key':randomUUID(),[token.headerName]:token.token}});
  expect(invalid.status()).toBe(400);expect(count()).toBe(before);
  sql(`UPDATE users SET donor_enabled=false WHERE id='${id(donor)}'`);
  try {
    await page.goto(`/campaigns/${campaign}`);await expect(page.getByRole('heading',{name:'Donor access is not enabled'})).toBeVisible();
    const forbidden=await page.request.post(`/api/campaigns/${campaign}/commitments`,{data:body,headers:{'Idempotency-Key':randomUUID(),[token.headerName]:token.token}});
    expect(forbidden.status()).toBe(403);expect((await forbidden.json()).code).toBe('FORBIDDEN');
    expect((await page.request.get('/api/me/commitments?page=0&size=20')).status()).toBe(403);expect(count()).toBe(before);
  } finally { sql(`UPDATE users SET donor_enabled=true WHERE id='${id(donor)}'`); }
  evidence.push({scenario:'real-csrf403-validation400-revoked-grant-no-write',databaseVerified:true});
});
