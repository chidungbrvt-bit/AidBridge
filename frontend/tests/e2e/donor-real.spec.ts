import { test, expect, type BrowserContext, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import type { CampaignResponse } from '../../features/donor/model';
import type { CaseDetailResponse } from '../../src/lib/types';
import type { DeliveryResponse } from '../../src/lib/goods';
import type { CommitmentResponse } from '../../features/donor/model';

// No success fixtures or mock API: all reads/writes reach Next -> Spring Boot -> PostgreSQL.
// Only the two response-loss tests intercept, after route.fetch() has committed to the real backend.
const schema=process.env.F07_E2E_SCHEMA ?? '';
const campaign=process.env.F07_E2E_CAMPAIGN_ID ?? '';
const donor=process.env.F07_E2E_DONOR_ID ?? '';
const recorder=process.env.F07_E2E_RECORDER_ID ?? '';
const volunteer=process.env.F07_E2E_VOLUNTEER_ID ?? '';
const caseId=process.env.F07_E2E_CASE_ID ?? '';
const evidence: {scenario:string;contributionId?:string;deliveryId?:string;databaseVerified:boolean}[]=[];
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

async function campaignFood(page: Page) {
  const response = await page.request.get(`/api/campaigns/${campaign}`);
  expect(response.status()).toBe(200);
  const detail: CampaignResponse = await response.json();
  const item = detail.items.find(entry => entry.name === 'Food');
  if (!item) throw new Error('Food is missing from the campaign seed');
  return item;
}

test.describe.configure({mode:'serial'});
test.setTimeout(60_000);
test.beforeEach(async({page})=>{expect(sql('SELECT current_database()')).toBe('aidbridge_test');await login(page);});
test.afterAll(()=>{
  mkdirSync('test-results',{recursive:true});
  writeFileSync('test-results/f07-real-evidence.json',JSON.stringify({mode:'REAL_NEXT_SPRING_POSTGRES',blockchainEnabled:false,
    backendFixture:'AbstractCampaignHttpIT',receiptWrite:'B08 R01 via the staff UI',deliveryWrite:'B09 D01 via the volunteer UI',expectedScenarios:8,
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

test('real R01 receipt feeds D01 delivery and blocks stale donor cancellation',async({page,browser})=>{
  const saved=await pledge(page),line=await foodLine(page,saved);const {card,panel}=await openCancel(page,saved);
  const staffContext=await browser.newContext({ baseURL: new URL(page.url()).origin });
  try {
    const staff=await staffContext.newPage();
    await staff.goto('/login');
    await staff.getByLabel('Email address').fill(process.env.F07_E2E_RECORDER_EMAIL!);
    await staff.getByLabel('Password',{exact:true}).fill(process.env.F07_E2E_DONOR_PASSWORD!);
    await staff.getByRole('button',{name:'Sign in →',exact:true}).click();
    await expect(staff).toHaveURL(/\/cases$/);
    const me=await staff.request.get('/api/auth/me');
    expect(me.status()).toBe(200);
    expect((await me.json()).userId).toBe(recorder);
    expect(recorder).not.toBe(donor);
    await staff.goto(`/cases/${id(caseId)}`);
    const receiptPanel=staff.getByRole('region',{name:'Goods receipt'});
    await receiptPanel.getByLabel('Outstanding commitment').selectOption(saved.id, { timeout: 10_000 });
    const outstanding=receiptPanel.getByTestId(`outstanding-${line.id}`);
    await expect(outstanding).toHaveText('Outstanding: 2');
    const path=`/api/commitments/${saved.id}/receipts`;
    const received=staff.waitForResponse(r=>new URL(r.url()).pathname===path && r.request().method()==='POST');
    await receiptPanel.getByLabel('Food receipt quantity (PACKAGE)',{exact:true}).fill('2');
    await receiptPanel.getByRole('button',{name:'Record receipt',exact:true}).click();
    const response=await received;
    expect(response.status()).toBe(201);
    expect(response.headers()['x-aidbridge-mock']).toBeUndefined();
    const receipt=await response.json();
    expect(receipt).toMatchObject({contributionId:saved.id,recorderId:recorder,items:[{contributionItemId:line.id,quantity:2}],blockchainStatus:'PENDING_ON_CHAIN'});
    await expect(receiptPanel.getByText(`Receipt ID: ${receipt.id}`)).toBeVisible();
    await expect(receiptPanel.getByLabel('Outstanding commitment')).toHaveValue(saved.id);
    await expect(outstanding).toHaveCount(0);
    expect(Number(sql(`SELECT count(*) FROM goods_receipts r JOIN goods_receipt_items i ON i.goods_receipt_id=r.id
      JOIN audit_events a ON a.entity_id=r.id JOIN blockchain_jobs j ON j.audit_event_id=a.id
      WHERE r.id='${id(receipt.id)}' AND r.received_by='${id(recorder)}' AND i.contribution_item_id='${id(line.id)}'
      AND i.received_quantity=2 AND a.event_type='GOODS_RECEIVED' AND j.status='PENDING'`))).toBe(1);
  } finally {
    await staffContext.close();
  }
  const rejected=page.waitForResponse(r=>r.url().includes(`/api/commitments/${saved.id}/cancellations`) && r.request().method()==='POST');
  await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();expect((await rejected).status()).toBe(409);
  await expect(panel.getByRole('alert')).toContainText('409 Conflict');
  await expect(card.getByRole('row').filter({hasText:'Food'}).getByRole('cell')).toHaveText(['2','0','2','0']);
  expect(cancelled(line.id)).toBe(0);expect(outbox(saved.id,'COMMITMENT_REDUCED')).toBe(0);
  expect(volunteer).not.toBe(recorder);
  expect(volunteer).not.toBe(donor);
  const volunteerContext = await browser.newContext({ baseURL: new URL(page.url()).origin });
  try {
    const field = await volunteerContext.newPage();
    await field.goto('/login');
    await field.getByLabel('Email address').fill(process.env.F07_E2E_VOLUNTEER_EMAIL!);
    await field.getByLabel('Password', { exact: true }).fill(process.env.F07_E2E_DONOR_PASSWORD!);
    await field.getByRole('button', { name: /Sign in/ }).click();
    await expect(field).toHaveURL(/\/cases$/);
    const me = await field.request.get('/api/auth/me');
    expect(me.status()).toBe(200);
    expect((await me.json()).userId).toBe(volunteer);
    const before = await campaignFood(field);
    const caseResponse = await field.request.get(`/api/org/cases/${id(caseId)}`);
    expect(caseResponse.status()).toBe(200);
    const detail: CaseDetailResponse = await caseResponse.json();
    const version = detail.evidenceVersions[0];
    expect(before.receivedQty - before.deliveredQty).toBeGreaterThanOrEqual(2);
    await field.goto(`/cases/${caseId}`);
    const deliveryPanel = field.getByRole('region', { name: 'Goods delivery' });
    await deliveryPanel.getByLabel('Food delivery quantity (PACKAGE)', { exact: true }).fill('1');
    await deliveryPanel.getByRole('checkbox', { name: /Evidence 1 / }).check();
    const delivered = field.waitForResponse(response =>
      new URL(response.url()).pathname === `/api/cases/${caseId}/deliveries` && response.request().method() === 'POST');
    await deliveryPanel.getByRole('button', { name: 'Record delivery', exact: true }).click();
    const response = await delivered;
    expect(response.status()).toBe(201);
    expect(response.headers()['x-aidbridge-mock']).toBeUndefined();
    const delivery: DeliveryResponse = await response.json();
    expect(delivery).toMatchObject({
      caseId, recorderId: volunteer, outcome: 'DELIVERED', reviewStatus: 'PENDING',
      items: [{ campaignItemId: before.id, quantity: 1 }],
      evidenceVersionIds: [version.id], blockchainStatus: 'PENDING_ON_CHAIN',
    });
    await expect(deliveryPanel.getByText(`Delivery ID: ${delivery.id}`)).toBeVisible();
    const after = await campaignFood(field);
    expect(after).toMatchObject({
      receivedQty: before.receivedQty, deliveredQty: before.deliveredQty + 1, confirmedQty: before.confirmedQty,
    });
    expect(Number(sql(`SELECT count(*) FROM deliveries d
      JOIN delivery_items i ON i.delivery_id=d.id
      JOIN delivery_evidence_links e ON e.delivery_id=d.id
      JOIN audit_events a ON a.entity_id=d.id JOIN blockchain_jobs j ON j.audit_event_id=a.id
      WHERE d.id='${id(delivery.id)}' AND d.recorded_by='${id(volunteer)}'
      AND i.campaign_item_id='${id(before.id)}' AND i.delivered_quantity=1
      AND e.evidence_version_id='${id(version.id)}' AND a.id='${id(delivery.auditEventId)}'
      AND a.event_type='DELIVERY_RECORDED' AND j.status='PENDING'`))).toBe(1);
    evidence.push({ scenario: 'real-r01-d01-stale-donor409', contributionId: saved.id, deliveryId: delivery.id, databaseVerified: true });
  } finally {
    await volunteerContext.close();
  }
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


// This separate small campaign reaches FULFILLED and exercises the full B10 workflow.
test('real F09: independent confirmation, dispute guard, void, reload and close', async ({ page, browser }) => {
  test.setTimeout(120_000);
  const reviewCase = id(process.env.F09_E2E_CASE_ID ?? '');
  const reviewCampaign = id(process.env.F09_E2E_CAMPAIGN_ID ?? '');
  const orgCase = `/cases/${reviewCase}`;
  const p04 = `/api/campaigns/${reviewCampaign}`;
  const baseURL = new URL(page.url()).origin;
  const contexts: BrowserContext[] = [];
  async function signIn(view: Page, email: string) {
    await view.getByLabel('Email address').fill(email);
    await view.getByLabel('Password', { exact: true }).fill(process.env.F07_E2E_DONOR_PASSWORD!);
    await view.getByRole('button', { name: /Sign in/ }).click();
    await expect(view).not.toHaveURL(/\/login$/);
  }
  async function actor(email: string) {
    const context = await browser.newContext({ baseURL });
    contexts.push(context);
    const view = await context.newPage();
    await view.goto('/login');
    await signIn(view, email);
    return view;
  }
  async function clickWrite(view: Page, path: string, click: () => Promise<void>, status: number) {
    const pending = view.waitForResponse(r => new URL(r.url()).pathname === path && r.request().method() === 'POST');
    await click();
    const response = await pending;
    const body = await response.json();
    expect(response.status(), `${path}: ${body.code ?? ''} ${body.detail ?? ''}`).toBe(status);
    expect(response.headers()['x-aidbridge-mock']).toBeUndefined();
    return body;
  }
  async function post(view: Page, path: string, data: unknown, status: number, code: string) {
    const csrf = await (await view.request.get('/api/auth/csrf')).json();
    const response = await view.request.post(path, { data, headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': randomUUID() } });
    const body = await response.json();
    expect(response.status(), `${path}: ${body.code} ${body.detail}`).toBe(status);
    expect(body.code).toBe(code);
  }
  const reviewPanel = (view: Page) => view.getByRole('region', { name: 'Delivery review', exact: true });
  async function assertTotals(view: Page, received: number, delivered: number, confirmed: number, status: string) {
    const area = reviewPanel(view);
    await expect(area.getByRole('row').filter({ hasText: 'Rice (BAG)' }).getByRole('cell'))
      .toHaveText(['2', String(received), String(delivered), String(confirmed)]);
    await expect(area.getByText(status, { exact: true })).toBeVisible();
    const response = await view.request.get(p04);
    expect(response.status()).toBe(200);
    const detail: CampaignResponse = await response.json();
    expect(detail.status).toBe(status);
    expect(detail.items.find(item => item.name === 'Rice')).toMatchObject({ receivedQty: received, deliveredQty: delivered, confirmedQty: confirmed });
    expect(detail.items.find(item => item.name === 'Water')).toMatchObject({ receivedQty: received ? 1 : 0, deliveredQty: delivered ? 1 : 0, confirmedQty: confirmed ? 1 : 0 });
  }
  try {
    const staff = await actor(process.env.F07_E2E_RECORDER_EMAIL!);
    const recorderPage = await actor(process.env.F07_E2E_VOLUNTEER_EMAIL!);
    const confirmerPage = await actor(process.env.F09_E2E_CONFIRMER_EMAIL!);
    const platformPage = await actor(process.env.F09_E2E_PLATFORM_EMAIL!);
    const confirmerId = (await (await confirmerPage.request.get('/api/auth/me')).json()).userId;
    const recorderSession = await (await recorderPage.request.get('/api/auth/me')).json();
    expect(recorderSession.userId).toBe(volunteer);
    expect(confirmerId).not.toBe(volunteer);
    expect(recorderSession.memberships[0].roles).toContain('DELIVERY_CONFIRMER');

    const commitmentId = await test.step('G01 and R01 receive both items through Next on real PostgreSQL', async () => {
      const body: CampaignResponse = await (await page.request.get(p04)).json();
      const csrf = await (await page.request.get('/api/auth/csrf')).json();
      const pledge = await page.request.post(`${p04}/commitments`, {
        headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': randomUUID() },
        data: { items: body.items.map(item => ({ campaignItemId: item.id, quantity: item.targetQty })) },
      });
      expect(pledge.status()).toBe(201);
      const commitment: CommitmentResponse = await pledge.json();
      await staff.goto(orgCase);
      const panel = staff.getByRole('region', { name: 'Goods receipt' });
      await panel.getByLabel('Outstanding commitment').selectOption(commitment.id);
      await panel.getByLabel('Rice receipt quantity (BAG)', { exact: true }).fill('2');
      await panel.getByLabel('Water receipt quantity (BOX)', { exact: true }).fill('1');
      await clickWrite(staff, `/api/commitments/${commitment.id}/receipts`,
        () => panel.getByRole('button', { name: 'Record receipt', exact: true }).click(), 201);
      await assertTotals(staff, 2, 0, 0, 'PUBLISHED');
      return commitment.id;
    });

    async function deliver(): Promise<DeliveryResponse> {
      await recorderPage.goto(orgCase);
      const panel = recorderPage.getByRole('region', { name: 'Goods delivery' });
      await panel.getByLabel('Rice delivery quantity (BAG)', { exact: true }).fill('2');
      await panel.getByLabel('Water delivery quantity (BOX)', { exact: true }).fill('1');
      await panel.getByRole('checkbox', { name: /Evidence 1 / }).check();
      return clickWrite(recorderPage, `/api/cases/${reviewCase}/deliveries`,
        () => panel.getByRole('button', { name: 'Record delivery', exact: true }).click(), 201);
    }
    async function confirm(delivery: DeliveryResponse, loseResponse = false) {
      await confirmerPage.goto(orgCase);
      const panel = reviewPanel(confirmerPage);
      await panel.getByRole('combobox', { name: 'Delivery to review', exact: true }).selectOption(delivery.id, { timeout: 10_000 });
      await panel.getByLabel('I reviewed the delivery evidence').check();
      const path = `/api/deliveries/${delivery.id}/confirm`;
      const click = () => panel.getByRole('button', { name: 'Confirm delivery', exact: true }).click();
      const requests: { key?: string; body: string | null }[] = [];
      let replayed = false;
      const routePattern = `**${path}`;
      if (loseResponse) await confirmerPage.route(routePattern, async route => {
        const request = route.request();
        requests.push({ key: request.headers()['idempotency-key'], body: request.postData() });
        const actual = await route.fetch();
        expect(actual.status()).toBe(200);
        if (requests.length === 1) {
          // Let Spring commit to PostgreSQL before discarding the successful response.
          await route.fulfill({ status: 504, json: { code: 'DEPENDENCY_UNAVAILABLE' } });
        } else {
          replayed = actual.headers()['idempotent-replayed'] === 'true';
          await route.fulfill({ response: actual });
        }
      });
      try {
        if (loseResponse) {
          await clickWrite(confirmerPage, path, click, 504);
          const retry = panel.getByRole('button', { name: 'Retry same request' });
          await expect(retry).toBeVisible();
          await expect(confirmerPage.getByRole('button', { name: 'Sign out' })).toBeDisabled();
          await expect(confirmerPage.getByRole('combobox', { name: 'Active organization' })).toBeDisabled();
          await confirmerPage.getByRole('navigation').getByRole('link', { name: 'Campaigns' }).click();
          expect(new URL(confirmerPage.url()).pathname).toBe(orgCase);
          await confirmerPage.context().clearCookies();
          const expired = confirmerPage.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/me' && r.status() === 401);
          await retry.click();
          await expired;
          await expect(confirmerPage).toHaveURL(/\/login$/);
          await expect(confirmerPage.getByText(/same account to check the pending decision/)).toBeVisible();
          await signIn(confirmerPage, process.env.F09_E2E_CONFIRMER_EMAIL!);
          await expect.poll(() => new URL(confirmerPage.url()).pathname).toBe(orgCase);
          const result = await clickWrite(confirmerPage, path, () => retry.click(), 200);
          expect(result).toMatchObject({ reviewerId: confirmerId, decision: 'CONFIRMED', blockchainStatus: 'PENDING_ON_CHAIN' });
          expect(requests).toHaveLength(2);
          expect(requests[0].key).toBeTruthy();
          expect(requests[1]).toEqual(requests[0]);
          expect(replayed).toBe(true);
          expect(Number(sql(`SELECT count(*) FROM delivery_confirmations WHERE delivery_id='${id(delivery.id)}'`))).toBe(1);
          await expect(confirmerPage.getByRole('button', { name: 'Sign out' })).toBeEnabled();
        } else {
          const response = await clickWrite(confirmerPage, path, click, 200);
          expect(response).toMatchObject({ reviewerId: confirmerId, decision: 'CONFIRMED', blockchainStatus: 'PENDING_ON_CHAIN' });
        }
      } finally {
        if (loseResponse) await confirmerPage.unroute(routePattern);
      }
    }
    const delivery = await test.step('D01 records a delivery and own D02 is rejected', async () => {
      const saved = await deliver();
      expect(saved).toMatchObject({ recorderId: volunteer, reviewStatus: 'PENDING' });
      const detail = await recorderPage.request.get(`/api/org/cases/${reviewCase}`);
      const snapshot = await detail.json();
      expect(detail.status(), JSON.stringify({ code: snapshot.code, detail: snapshot.detail })).toBe(200);
      expect(snapshot.deliveries).toEqual(expect.arrayContaining([expect.objectContaining({ id: saved.id, reviewStatus: 'PENDING' })]));
      await recorderPage.reload();
      const panel = reviewPanel(recorderPage);
      await panel.getByRole('combobox', { name: 'Delivery to review', exact: true }).selectOption(saved.id, { timeout: 10_000 });
      await expect(panel.getByText('You recorded this delivery. Another account must confirm it.')).toBeVisible();
      await expect(panel.getByRole('button', { name: 'Confirm delivery', exact: true })).toHaveCount(0);
      await post(recorderPage, `/api/deliveries/${saved.id}/confirm`,
        { decision: 'CONFIRMED', reviewedEvidenceVersionIds: saved.evidenceVersionIds }, 403, 'SELF_CONFIRMATION');
      return saved;
    });
    await test.step('D02 replays an ambiguous result after reauthentication and reaches FULFILLED', async () => {
      await confirm(delivery, true);
      await staff.reload();
      await assertTotals(staff, 2, 2, 2, 'FULFILLED');
    });
    const dispute = await test.step('S01 OPEN disables P05 and backend returns DISPUTE_OPEN', async () => {
      const panel = reviewPanel(staff);
      await panel.getByRole('combobox', { name: 'Delivery to review', exact: true }).selectOption(delivery.id, { timeout: 10_000 });
      await panel.getByLabel('Dispute or review reason').fill('Simulated discrepancy in handover.');
      const saved = await clickWrite(staff, `/api/cases/${reviewCase}/disputes`,
        () => panel.getByRole('button', { name: 'Open dispute', exact: true }).click(), 201);
      await expect(panel.getByText('Open disputes: 1')).toBeVisible();
      await expect(panel.getByRole('button', { name: 'Close campaign' })).toBeDisabled();
      await post(staff, `${p04}/close`, {}, 409, 'DISPUTE_OPEN');
      return saved;
    });
    await test.step('S03 can read only the private snapshot and VOID restores stock after reload', async () => {
      const path = `/api/disputes/${dispute.id}/review`;
      const evidencePath = `${path}/evidence-versions/${delivery.evidenceVersionIds[0]}/content`;
      expect((await staff.request.get(path)).status()).toBe(403);
      expect((await staff.request.get(evidencePath)).status()).toBe(403);
      await platformPage.goto(`/disputes/${dispute.id}`);
      await expect(platformPage.getByRole('heading', { name: 'Delivery snapshot' })).toBeVisible();
      const content = await platformPage.request.get(evidencePath);
      expect(content.status()).toBe(200);
      expect(content.headers()['cache-control']).toContain('no-store');
      expect((await platformPage.request.get(`${path}/evidence-versions/${randomUUID()}/content`)).status()).toBe(404);
      await platformPage.getByLabel('Reviewed evidence 1').check();
      await platformPage.getByLabel('Resolution reason').fill('Simulated delivery invalidated after review.');
      const resolved = await clickWrite(platformPage, `/api/disputes/${dispute.id}/resolve`,
        () => platformPage.getByRole('button', { name: 'Void delivery' }).click(), 200);
      expect(resolved.resolution).toBe('VOID_DELIVERY');
      expect((await platformPage.request.get(evidencePath)).status()).toBe(404);
      await staff.reload();
      await assertTotals(staff, 2, 0, 0, 'PUBLISHED');
      await expect(reviewPanel(staff).getByText('Open disputes: 0')).toBeVisible();
    });
    await test.step('New delivery confirms, P05 closes and timeline refreshes', async () => {
      const corrected = await deliver();
      await confirm(corrected);
      await staff.reload();
      const panel = reviewPanel(staff);
      await assertTotals(staff, 2, 2, 2, 'FULFILLED');
      const closed = await clickWrite(staff, `${p04}/close`,
        () => panel.getByRole('button', { name: 'Close campaign' }).click(), 200);
      expect(closed.status).toBe('CLOSED');
      await assertTotals(staff, 2, 2, 2, 'CLOSED');
      await expect(staff.getByText('COMPLETED', { exact: true })).toBeVisible();
      await staff.getByText('Case audit trail', { exact: true }).click();
      const timeline = staff.getByRole('region', { name: 'Case audit timeline' });
      await expect(timeline.getByRole('heading', { name: 'CASE COMPLETED', exact: true })).toBeVisible();
      await expect(timeline.getByRole('heading', { name: 'DELIVERY CONFIRMED', exact: true }).first()).toBeVisible();
      expect(Number(sql(`SELECT count(*) FROM delivery_voids WHERE delivery_id='${id(delivery.id)}'`))).toBe(1);
      expect(Number(sql(`SELECT count(*) FROM audit_events WHERE aid_case_id='${reviewCase}' AND event_type='CASE_COMPLETED'`))).toBe(1);
    });
    evidence.push({ scenario: 'f09-independent-confirm-void-close-timeline', contributionId: commitmentId, deliveryId: delivery.id, databaseVerified: true });
  } finally {
    await Promise.all(contexts.map(context => context.close()));
  }
});

async function signInStaff(page: Page, email: string) {
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(process.env.F07_E2E_DONOR_PASSWORD!);
  await page.getByRole('button', { name: 'Sign in →', exact: true }).click();
  await expect.poll(() => new URL(page.url()).pathname).not.toBe('/login');
}

async function realWrite(page: Page, path: string, body: unknown, status: number) {
  const csrfResponse = await page.request.get('/api/auth/csrf');
  expect(csrfResponse.status()).toBe(200);
  const csrf = await csrfResponse.json();
  const response = await page.request.post(path, {
    data: body, headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': randomUUID() },
  });
  expect(response.headers()['x-aidbridge-mock']).toBeUndefined();
  expect(response.status(), `${path}: ${response.status()}`).toBe(status);
  return response.json();
}

test('real F09 D02 shows quantities for the selected delivery by campaign item ID', async ({ page, browser }) => {
  const context = await browser.newContext({ baseURL: new URL(page.url()).origin });
  try {
    const staff = await context.newPage();
    await staff.goto('/login');
    await signInStaff(staff, process.env.F07_E2E_RECORDER_EMAIL!);
    const campaignResponse = await page.request.get(`/api/campaigns/${campaign}`);
    expect(campaignResponse.status()).toBe(200);
    const current: CampaignResponse = await campaignResponse.json();
    const food = current.items.find(item => item.name === 'Food')!;
    const hygiene = current.items.find(item => item.name === 'Hygiene')!;
    expect(food).toBeTruthy();
    expect(hygiene).toBeTruthy();
    const pledge: CommitmentResponse = await realWrite(page, `/api/campaigns/${campaign}/commitments`,
      { items: [{ campaignItemId: food.id, quantity: 4 }, { campaignItemId: hygiene.id, quantity: 3 }] }, 201);
    const foodLine = pledge.items.find(item => item.campaignItemId === food.id)!;
    const hygieneLine = pledge.items.find(item => item.campaignItemId === hygiene.id)!;
    await realWrite(staff, `/api/commitments/${pledge.id}/receipts`, {
      receivedAt: new Date().toISOString(),
      items: [{ contributionItemId: foodLine.id, quantity: 4 }, { contributionItemId: hygieneLine.id, quantity: 3 }],
    }, 201);
    const detailResponse = await staff.request.get(`/api/org/cases/${caseId}`);
    expect(detailResponse.status()).toBe(200);
    const detail: CaseDetailResponse = await detailResponse.json();
    await staff.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect(staff).toHaveURL(/\/login$/);
    await signInStaff(staff, process.env.F07_E2E_VOLUNTEER_EMAIL!);
    const first: DeliveryResponse = await realWrite(staff, `/api/cases/${caseId}/deliveries`, {
      outcome: 'DELIVERED', evidenceVersionIds: [detail.evidenceVersions[0].id],
      items: [{ campaignItemId: food.id, quantity: 1 }, { campaignItemId: hygiene.id, quantity: 2 }],
    }, 201);
    const second: DeliveryResponse = await realWrite(staff, `/api/cases/${caseId}/deliveries`, {
      outcome: 'DELIVERED', evidenceVersionIds: [detail.evidenceVersions[0].id],
      items: [{ campaignItemId: hygiene.id, quantity: 1 }, { campaignItemId: food.id, quantity: 2 }],
    }, 201);
    await staff.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect(staff).toHaveURL(/\/login$/);
    await signInStaff(staff, process.env.F09_E2E_CONFIRMER_EMAIL!);
    await staff.goto(`/cases/${caseId}`);
    const panel = staff.getByRole('region', { name: 'Delivery review', exact: true });
    const select = panel.getByRole('combobox', { name: 'Delivery to review', exact: true });
    const table = panel.getByRole('table', { name: 'Selected delivery items', exact: true });
    await select.selectOption(first.id);
    await expect(table.getByRole('row').filter({ hasText: 'Food' }).getByRole('cell')).toHaveText(['PACKAGE', '1']);
    await expect(table.getByRole('row').filter({ hasText: 'Hygiene' }).getByRole('cell')).toHaveText(['KIT', '2']);
    await expect(panel.getByRole('button', { name: 'Confirm delivery', exact: true })).toBeDisabled();
    await panel.getByLabel('I reviewed the delivery evidence').check();
    await expect(panel.getByRole('button', { name: 'Confirm delivery', exact: true })).toBeEnabled();
    await select.selectOption(second.id);
    await expect(table.getByRole('row').filter({ hasText: 'Food' }).getByRole('cell')).toHaveText(['PACKAGE', '2']);
    await expect(table.getByRole('row').filter({ hasText: 'Hygiene' }).getByRole('cell')).toHaveText(['KIT', '1']);
    await expect(panel.getByLabel('I reviewed the delivery evidence')).not.toBeChecked();
    await expect(panel.getByRole('button', { name: 'Confirm delivery', exact: true })).toBeDisabled();
    expect(Number(sql(`SELECT count(*) FROM delivery_items WHERE delivery_id IN ('${id(first.id)}','${id(second.id)}')`))).toBe(4);
    evidence.push({ scenario: 'f09-selected-delivery-quantities', contributionId: pledge.id, deliveryId: second.id, databaseVerified: true });
  } finally {
    await context.close();
  }
});

test('real F09 returns an unauthenticated direct dispute link after login', async ({ page, browser }) => {
  const context = await browser.newContext({ baseURL: new URL(page.url()).origin });
  try {
    const staff = await context.newPage();
    await staff.goto('/login');
    await signInStaff(staff, process.env.F07_E2E_RECORDER_EMAIL!);
    const caseToReview = id(process.env.F09_E2E_CASE_ID ?? '');
    const dispute = await realWrite(staff, `/api/cases/${caseToReview}/disputes`, {
      reason: 'Simulated case-level review for the direct-link regression.',
    }, 201);
    const directPath = `/disputes/${id(dispute.id)}`;
    await staff.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect(staff).toHaveURL(/\/login$/);
    const direct = await context.newPage();
    await direct.goto(directPath);
    await expect.poll(() => new URL(direct.url()).pathname).toBe('/login');
    expect(new URL(direct.url()).searchParams.get('returnTo')).toBe(directPath);
    // The return path survives a fresh load of the login page, without any prior session state.
    await direct.reload();
    await signInStaff(direct, process.env.F09_E2E_PLATFORM_EMAIL!);
    await expect.poll(() => new URL(direct.url()).pathname).toBe(directPath);
    await expect(direct.getByRole('heading', { name: 'Dispute review', exact: true })).toBeVisible();
    await expect(direct.getByText('Simulated case-level review for the direct-link regression.', { exact: true })).toBeVisible();
    await direct.getByLabel('Resolution reason').fill('Simulated case review upheld.');
    const saved = direct.waitForResponse(response => new URL(response.url()).pathname === `/api/disputes/${dispute.id}/resolve`
      && response.request().method() === 'POST');
    await direct.getByRole('button', { name: 'Uphold dispute decision', exact: true }).click();
    expect((await saved).status()).toBe(200);
    await expect(direct.getByText('Dispute resolved. Refresh the case in the organization workspace to see the current totals.')).toBeVisible();
    expect(sql(`SELECT status FROM disputes WHERE id='${id(dispute.id)}'`)).toBe('RESOLVED');
    await direct.getByRole('button', { name: 'Sign out', exact: true }).click();
    await expect.poll(() => new URL(direct.url()).pathname).toBe('/login');
    await direct.goto('/login?returnTo=https%3A%2F%2Fexample.invalid%2Fdisputes%2F' + dispute.id);
    await signInStaff(direct, process.env.F09_E2E_PLATFORM_EMAIL!);
    await expect.poll(() => new URL(direct.url()).pathname).toBe('/cases');
    expect(new URL(direct.url()).origin).toBe(new URL(page.url()).origin);
    evidence.push({ scenario: 'f09-direct-dispute-login-return', databaseVerified: true });
  } finally {
    await context.close();
  }
});
