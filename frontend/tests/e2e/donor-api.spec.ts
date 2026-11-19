import { test, expect, type Page } from '@playwright/test';
import { createMockDonorApi, DEMO_CAMPAIGN_ID } from '../../features/donor/mock';
import { ApiError } from '../../features/donor/model';

// HTTP contract simulation only: the production Next shell/session/client are real;
// this Playwright routing is not a running Spring Boot/PostgreSQL backend.
async function harness(page: Page, partial=false, enabled=true) {
  const mock=createMockDonorApi(5); if(partial)mock.loadPartialReceiptFixture();
  const state={csrfFailures:0,csrfReads:0,losePledge:0,loseCancel:0,receiveOnCancel:false,historyError:false,enabled,
    writes:[] as {path:string;key:string;body:unknown}[], reads:[] as string[]};
  await page.context().addCookies([{name:'JSESSIONID',value:'f07-test-only',url:'http://127.0.0.1:3000',httpOnly:true}]);
  await page.route('**/api/**',async route=>{
    const r=route.request(),url=new URL(r.url()),path=url.pathname;
    const json=async(body:unknown,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
    if(path==='/api/auth/csrf'){state.csrfReads++;return json({token:'contract-test-csrf',headerName:'X-CSRF-TOKEN'});}
    if(path==='/api/auth/me')return json({userId:'70000000-0000-4000-8000-000000000001',fullName:'Fictional Donor',status:'ACTIVE',platformRole:'USER',donorEnabled:state.enabled,memberships:[]});
    if(r.method()==='GET')state.reads.push(r.url());
    if(r.method()==='POST'){
      expect(r.headers()['x-csrf-token']).toBe('contract-test-csrf');
      expect(r.headers().cookie).toContain('JSESSIONID=f07-test-only');
      expect(r.headers()['idempotency-key']).toBeTruthy();
      state.writes.push({path,key:r.headers()['idempotency-key'],body:r.postDataJSON()});
      if(state.csrfFailures-->0)return json({status:403,code:'CSRF_INVALID',title:'Expired token'},403);
    }
    try {
      if(path==='/api/campaigns')return json({items:[await mock.api.getCampaign(DEMO_CAMPAIGN_ID)],page:0,size:20,totalElements:1});
      if(path===`/api/campaigns/${DEMO_CAMPAIGN_ID}`){const c=await mock.api.getCampaign(DEMO_CAMPAIGN_ID); c.items.reverse();return json(c);}
      if(path==='/api/me/commitments'){
        if(state.historyError)return json({status:503,code:'DEPENDENCY_UNAVAILABLE',title:'History unavailable'},503);
        return json(await mock.api.getCommitments(Number(url.searchParams.get('page')??0)));
      }
      if(path===`/api/campaigns/${DEMO_CAMPAIGN_ID}/commitments`){
        const result=await mock.api.pledge(DEMO_CAMPAIGN_ID,r.postDataJSON(),r.headers()['idempotency-key']);
        if(state.losePledge-->0)return json({status:504,code:'GATEWAY_TIMEOUT',title:'Response lost'},504);
        return json(result,201);
      }
      const id=/^\/api\/commitments\/([^/]+)\/cancellations$/.exec(path)?.[1];
      if(id){
        if(state.receiveOnCancel){state.receiveOnCancel=false;mock.receiveRemainingForDemo();}
        const result=await mock.api.cancel(id,r.postDataJSON(),r.headers()['idempotency-key']);
        if(state.loseCancel-->0)return json({status:504,code:'GATEWAY_TIMEOUT',title:'Response lost'},504);
        return json(result);
      }
      return json({status:404,code:'RESOURCE_NOT_FOUND',title:'Missing test endpoint'},404);
    }catch(e){if(e instanceof ApiError)return json(e.problem,e.problem.status);throw e;}
  });
  return {mock,state};
}
async function pledge(page:Page){
  await page.goto(`/campaigns/${DEMO_CAMPAIGN_ID}`);
  await expect(page.getByRole('heading',{name:'Pledge essential goods'})).toBeVisible();
  await page.getByLabel('Rice',{exact:false}).first().check();
  await page.getByLabel('Quantity (kg)',{exact:true}).fill('2');
  await page.getByLabel('Drinking Water',{exact:false}).first().check();
  await page.getByLabel('Quantity (box)',{exact:true}).fill('1');
  await page.getByRole('button',{name:'Pledge goods →',exact:true}).click();
}
async function cancellation(page:Page){
  await page.goto('/me/commitments');
  await expect(page.getByRole('heading',{name:/My pledges \(1\)/})).toBeVisible();
  await expect(page.locator('article').getByRole('row').filter({hasText:'Rice'})).toBeVisible();
  await page.getByRole('button',{name:'Cancel unreceived goods',exact:true}).click();
  const panel=page.getByRole('region',{name:'Cancel unreceived goods',exact:true});
  await panel.getByLabel('Cancel Rice',{exact:false}).check();
  await panel.getByLabel('Cancel quantity (kg)',{exact:true}).fill('1');
  await panel.getByLabel('Cancellation reason').fill('Availability changed');
  return panel;
}
function rice(page:Page){return page.locator('article').getByRole('row').filter({hasText:'Rice'});}

test('real shell G01 → G02 → G03 uses CSRF and IDs, with no staff membership or developer controls',async({page})=>{
  const h=await harness(page);await pledge(page);
  await expect(page.getByText('Goods pledged. Pending on-chain.',{exact:true})).toBeVisible();
  await expect(page.getByText('No active organization membership',{exact:true})).toHaveCount(0);
  await expect(page.getByText('Mock test controls',{exact:true})).toHaveCount(0);
  await expect(page.getByRole('button',{name:'Load partial receipt fixture'})).toHaveCount(0);
  await page.getByRole('button',{name:'View my pledges →'}).click();
  await expect(page).toHaveURL(/me\/commitments/);
  await expect(rice(page).getByRole('cell')).toHaveText(['2','0','0','2']);
  await page.getByRole('button',{name:'Cancel unreceived goods',exact:true}).click();
  const panel=page.getByRole('region',{name:'Cancel unreceived goods',exact:true});
  await panel.getByLabel('Cancel Rice',{exact:false}).check();await panel.getByLabel('Cancel quantity (kg)',{exact:true}).fill('1');
  await panel.getByLabel('Cancellation reason').fill('Availability changed');await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();
  await expect(rice(page).getByRole('cell')).toHaveText(['2','1','0','1']);
  await page.screenshot({path:test.info().outputPath('f07-api-desktop.png'),fullPage:true});
  expect(h.state.writes).toHaveLength(2);
  expect(h.state.writes[0].body).toEqual({items:expect.arrayContaining([expect.objectContaining({quantity:2}),expect.objectContaining({quantity:1})])});
  expect((h.state.writes[1].body as {items:{contributionItemId:string}[]}).items[0].contributionItemId).toBeTruthy();
});

test('G01 committed then 504 twice retries exact request/key once, locks draft and navigation',async({page})=>{
  const h=await harness(page);h.state.losePledge=2;await pledge(page);
  const retry=page.getByRole('button',{name:'Retry same request',exact:true});await expect(retry).toBeVisible();
  await expect(page.getByLabel('Quantity (kg)',{exact:true})).toBeDisabled();
  await expect(page.getByRole('button',{name:'My pledges',exact:true})).toBeDisabled();
  await retry.click();await expect(retry).toBeEnabled();await retry.click();
  await expect(page.getByText('Goods pledged. Pending on-chain.',{exact:true})).toBeVisible();
  expect(h.state.writes).toHaveLength(3);expect(new Set(h.state.writes.map(x=>x.key)).size).toBe(1);
  expect(h.state.writes.map(x=>JSON.stringify(x.body))).toEqual(Array(3).fill(JSON.stringify(h.state.writes[0].body)));
  expect((await h.mock.api.getCommitments(0)).totalElements).toBe(1);
});

test('G03 committed 1kg then repeated 504 retries without cancelling 2kg',async({page})=>{
  const h=await harness(page,true);h.state.loseCancel=2;const panel=await cancellation(page);
  await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();
  const retry=panel.getByRole('button',{name:'Retry same cancellation',exact:true});await expect(retry).toBeVisible();
  await expect(panel.getByLabel('Cancellation reason')).toBeDisabled();
  await retry.click();await expect(retry).toBeEnabled();await retry.click();
  await expect(rice(page).getByRole('cell')).toHaveText(['8','1','5','2']);
  expect(new Set(h.state.writes.map(x=>x.key)).size).toBe(1);expect(h.state.writes).toHaveLength(3);
});

test('G03 receipt races stale form: HTTP409 refreshes outstanding and never cancels received goods',async({page})=>{
  const h=await harness(page,true);const panel=await cancellation(page);h.state.receiveOnCancel=true;
  await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();
  await expect(panel.getByRole('alert')).toBeVisible();await expect(panel.getByRole('alert')).not.toContainText('409');
  await expect(rice(page).getByRole('cell')).toHaveText(['8','0','8','0']);
  await expect(panel.getByRole('button',{name:'Cancel selected goods',exact:true})).toBeDisabled();
});

for(const fault of ['400','403','409'] as const)test(`G01 HTTP${fault} is visible, no optimistic mutation or private-data assumption`,async({page})=>{
 const h=await harness(page);h.mock.setFault(fault);await pledge(page);
 await expect(page.getByRole('alert').filter({hasText:fault}).first()).toBeVisible();
 expect((await h.mock.api.getCommitments(0)).items).toHaveLength(0);
});

test('history error/empty and disabled donor capability do not fabricate records',async({page})=>{
  const h=await harness(page);h.state.historyError=true;await page.goto('/me/commitments');
  await expect(page.getByRole('alert').filter({hasText:'Unable to load your pledge history.'})).not.toContainText('503');h.state.historyError=false;
  await page.getByRole('button',{name:'Retry history loading'}).click();await expect(page.getByRole('heading',{name:'No pledges yet'})).toBeVisible();
  h.state.enabled=false;h.state.reads=[];await page.reload();await expect(page.getByRole('heading',{name:'Donor access is not enabled'})).toBeVisible();
  expect(h.state.reads.filter(x=>x.includes('/api/me/commitments'))).toEqual([]);await expect(page.locator('article')).toHaveCount(0);
});

test('mobile keyboard cancellation: valid layout, positive integer and plain-text reason validation',async({page})=>{
 await page.setViewportSize({width:390,height:844});const h=await harness(page,true);const panel=await cancellation(page);
 await panel.getByLabel('Cancel quantity (kg)',{exact:true}).fill('0');await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();
 await expect(panel.getByRole('alert')).toBeFocused();expect(h.state.writes).toHaveLength(0);
 await panel.getByLabel('Cancel quantity (kg)',{exact:true}).fill('1');await panel.getByLabel('Cancellation reason').fill('https://not-allowed.test');
 await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();await expect(panel.getByRole('alert')).toContainText('plain text');
 await panel.getByLabel('Cancellation reason').fill('Availability changed');await panel.getByLabel('Cancellation reason').press('Tab');
 await expect(panel.getByRole('button',{name:'Cancel selected goods',exact:true})).toBeFocused();await page.keyboard.press('Enter');
 await expect(rice(page).getByRole('cell')).toHaveText(['8','1','5','2']);
 await page.screenshot({path:test.info().outputPath('f07-api-mobile.png'),fullPage:true});
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
});


test('G02 pagination obeys server page/size and clears stale rows',async({page})=>{
 const h=await harness(page);const c=await h.mock.api.getCampaign(DEMO_CAMPAIGN_ID),rice=c.items.find(x=>x.name==='Rice')!;
 for(let i=0;i<6;i++)await h.mock.api.pledge(DEMO_CAMPAIGN_ID,{items:[{campaignItemId:rice.id,quantity:1}]},`seed-fixture-${i}`);
 await page.goto('/me/commitments');await expect(page.locator('article')).toHaveCount(5);
 const nav=page.getByRole('navigation',{name:'History pages'});await nav.getByRole('button',{name:'Next',exact:true}).click();
 await expect(nav).toContainText('Page 2');await expect(page.locator('article')).toHaveCount(1);
 expect(h.state.reads.some(url=>url.includes('page=1&size=20'))).toBeTruthy();
 await nav.getByRole('button',{name:'Previous',exact:true}).click();await expect(page.locator('article')).toHaveCount(5);
});

test('G02 HTTP401 clears private UI through the shared session handler',async({page})=>{
 await harness(page,true);
 await page.route('**/api/me/commitments?*',route=>route.fulfill({status:401,contentType:'application/json',body:JSON.stringify({status:401,code:'UNAUTHENTICATED',title:'Session ended'})}));
 await page.goto('/me/commitments');await expect(page).toHaveURL(/login/);
 await expect(page.getByRole('heading',{name:'Sign in'})).toBeVisible();await expect(page.locator('article')).toHaveCount(0);
});


for(const kind of ['pledge','cancel'] as const)test(`G01/G03 ${kind}: expired CSRF refreshes token and retries same key/body`,async({page})=>{
 const h=await harness(page,kind==='cancel');h.state.csrfFailures=1;let csrfBeforeRetry=0;
 if(kind==='pledge'){
   await pledge(page);const retry=page.getByRole('button',{name:'Retry same request',exact:true});await expect(retry).toBeVisible();
   csrfBeforeRetry=h.state.csrfReads;await retry.click();await expect(page.getByText('Goods pledged. Pending on-chain.',{exact:true})).toBeVisible();
 }else{
   const panel=await cancellation(page);await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();
   const retry=panel.getByRole('button',{name:'Retry same cancellation',exact:true});await expect(retry).toBeVisible();csrfBeforeRetry=h.state.csrfReads;await retry.click();
   await expect(rice(page).getByRole('cell')).toHaveText(['8','1','5','2']);
 }
 expect(h.state.csrfReads).toBe(csrfBeforeRetry+1);expect(h.state.writes).toHaveLength(2);
 expect(h.state.writes[0]).toEqual(h.state.writes[1]);
});


test('campaign discovery handles loading/error/empty and keyboard navigation into G01',async({page})=>{
 const h=await harness(page);let responseMode: 'error'|'empty'|'full'='error';
 await page.route('**/api/campaigns?*',async route=>{
   await new Promise(resolve=>setTimeout(resolve,300));
   const body=responseMode==='error'?{status:503,code:'DEPENDENCY_UNAVAILABLE',title:'Unavailable'}:
     {items:responseMode==='empty'?[]:[await h.mock.api.getCampaign(DEMO_CAMPAIGN_ID)],page:0,size:20,totalElements:responseMode==='empty'?0:1};
   await route.fulfill({status:responseMode==='error'?503:200,contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('/campaigns');await expect(page.getByRole('status').filter({hasText:'Loading campaigns'})).toBeVisible();
 await expect(page.getByRole('alert').filter({hasText:'Unable to load public campaigns.'})).toBeVisible();responseMode='empty';
 await page.getByRole('button',{name:'Retry campaigns'}).click();await expect(page.getByText('No public campaigns are available.')).toBeVisible();
 responseMode='full';await page.reload();const link=page.getByRole('link',{name:'View needs and pledge'});await expect(link).toBeVisible();
 await link.focus();await page.keyboard.press('Enter');await expect(page).toHaveURL(new RegExp(DEMO_CAMPAIGN_ID));
 await expect(page.getByRole('heading',{name:'Pledge essential goods'})).toBeVisible();
});
