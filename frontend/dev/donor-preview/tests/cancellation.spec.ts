import {test,expect} from '@playwright/test';
import type {Page} from '@playwright/test';
async function fixture(page:Page){await page.goto('/');await page.getByRole('button',{name:'Load partial receipt fixture'}).click();await expect(page.getByRole('heading',{name:'My pledges (1)'})).toBeVisible();await page.getByRole('button',{name:'Cancel unreceived goods',exact:true}).click();}
async function fill(page:Page,quantity='2'){const panel=page.getByRole('region',{name:'Cancel unreceived goods',exact:true});await panel.getByLabel('Cancel Rice',{exact:false}).check();await panel.getByLabel('Cancel quantity (kg)',{exact:true}).fill(quantity);await panel.getByLabel('Cancellation reason').fill('Availability changed');return panel;}
function rice(page:Page){return page.locator('article').getByRole('row').filter({hasText:'Rice'});}
const backendCalls=new WeakMap<Page,string[]>();
test.beforeEach(async({page})=>{const calls:string[]=[];backendCalls.set(page,calls);page.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))calls.push(r.url());});test.info().annotations.push({type:'mode',description:'Mock G03; no backend calls'});await fixture(page);});
test.afterEach(async({page})=>{expect(backendCalls.get(page)).toEqual([]);});

test('G03 multi-item partial cancellation updates history and campaign; G01 still works',async({page})=>{
 const panel=await fill(page);await panel.getByLabel('Cancel Drinking Water',{exact:false}).check();await panel.getByLabel('Cancel quantity (box)',{exact:true}).fill('1');await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();
 await expect(panel.getByRole('status').filter({hasText:'Cancellation saved'})).toBeVisible();await expect(rice(page).getByRole('cell')).toHaveText(['8','2','5','1']);
 await expect(page.locator('article').getByText('PARTIALLY RECEIVED',{exact:true})).toBeVisible();await page.screenshot({path:'test-results/g03-desktop.png',fullPage:true});await panel.getByRole('button',{name:'Close cancellation'}).click();await page.getByRole('button',{name:'Explore campaign',exact:true}).click();
 await expect(page.getByRole('table').getByRole('row').filter({hasText:'Rice'}).getByRole('cell')).toHaveText(['20','12','7','1','0']);
 await page.getByLabel('Rice',{exact:false}).first().check();await page.getByLabel('Quantity (kg)',{exact:true}).fill('1');await page.getByRole('button',{name:'Pledge goods →',exact:true}).click();await expect(page.getByText('Goods pledged. Pending on-chain.',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'View my pledges →'}).click();await expect(page.getByRole('heading',{name:'My pledges (2)'})).toBeVisible();
});
test('G03 local invalid quantities focus summary and do not change history',async({page})=>{
 const panel=await fill(page,'0');await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();await expect(panel.getByRole('alert')).toBeFocused();await panel.getByRole('alert').getByRole('link').click();await expect(panel.getByLabel('Cancel quantity (kg)',{exact:true})).toBeFocused();
 for(const quantity of ['1.5','4']){await panel.getByLabel('Cancel quantity (kg)',{exact:true}).fill(quantity);await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();await expect(panel.getByRole('alert')).toBeVisible();}
 await expect(rice(page).getByRole('cell')).toHaveText(['8','0','5','3']);
});
test('G03 400, 403 and injected 409 preserve quantities; 409 refreshes state',async({page})=>{
 const panel=await fill(page);for(const fault of ['400','403','409']){await page.getByLabel('Mock response').selectOption(fault);const submit=panel.getByRole('button',{name:'Cancel selected goods',exact:true});await expect(submit).toBeEnabled();await submit.click();await expect(panel.getByRole('alert')).toBeVisible();await expect(panel.getByRole('alert')).not.toContainText(fault);await expect(rice(page).getByRole('cell')).toHaveText(['8','0','5','3']);}
});
test('G03 stale receipt conflict does not cancel goods received in the meantime',async({page})=>{
 const panel=await fill(page,'3');await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();await page.getByRole('button',{name:'Mock receipt: receive remaining goods'}).click();await expect(panel.getByRole('alert')).toBeVisible();await expect(panel.getByRole('alert')).not.toContainText('409');
 await expect(rice(page).getByRole('cell')).toHaveText(['8','0','8','0']);await expect(page.locator('article').getByText('RECEIVED',{exact:true})).toBeVisible();await expect(page.locator('article').getByRole('button',{name:'Cancel unreceived goods'})).toBeDisabled();
});
test('G03 response loss blocks edits/navigation and safely replays once',async({page})=>{
 const panel=await fill(page);await page.getByLabel('Mock response').selectOption('timeout-after-save');await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();await expect(panel.getByRole('button',{name:'Retry same cancellation'})).toBeVisible();
 await expect(panel.getByLabel('Cancel quantity (kg)',{exact:true})).toBeDisabled();await expect(page.getByRole('button',{name:'Explore campaign',exact:true})).toBeDisabled();await expect(panel.getByRole('button',{name:'Close cancellation'})).toBeDisabled();
 await panel.getByRole('button',{name:'Retry same cancellation'}).click();await expect(panel.getByRole('status').filter({hasText:'Cancellation saved'})).toBeVisible();await expect(rice(page).getByRole('cell')).toHaveText(['8','2','5','1']);
});
test('G03 mobile full remainder cancellation keeps received history and prevents repeat',async({page})=>{
 await page.setViewportSize({width:390,height:844});const panel=await fill(page,'3');await panel.getByLabel('Cancel Drinking Water',{exact:false}).check();await panel.getByLabel('Cancel quantity (box)',{exact:true}).fill('1');
 await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).dblclick();await expect(panel.getByRole('status').filter({hasText:'Cancellation saved'})).toBeVisible();await expect(rice(page).getByRole('cell')).toHaveText(['8','3','5','0']);await expect(page.locator('article').getByText('RECEIVED',{exact:true})).toBeVisible();
 await expect(page.locator('article').getByRole('button',{name:'Cancel unreceived goods'})).toBeDisabled();await expect(panel.getByRole('button',{name:'Cancel selected goods',exact:true})).toBeDisabled();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();await page.screenshot({path:'test-results/g03-mobile.png',fullPage:true});
});
test('G03 capability revocation hides form/history and suppresses late success',async({page})=>{
 const panel=await fill(page);await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();await page.getByLabel('Mock donor enabled').uncheck();await expect(page.getByRole('region',{name:'Cancel unreceived goods',exact:true})).toHaveCount(0);
 await page.waitForTimeout(700);await expect(page.getByText('Cancellation saved. Received goods are unchanged. Pending on-chain.',{exact:true})).toHaveCount(0);await expect(page.locator('article')).toHaveCount(0);
});


test('receipt control refreshes My pledges without navigation or remount',async({page})=>{
 await page.getByRole('button',{name:'Close cancellation'}).click();
 await page.getByRole('button',{name:'Mock receipt: receive remaining goods'}).click();
 await expect(rice(page).getByRole('cell')).toHaveText(['8','0','8','0']);
 await expect(page).toHaveURL(/me\/commitments/);
});

test('receipt refresh updates open G03 bounds while retaining its draft',async({page})=>{
 const panel=await fill(page,'3');
 await page.getByRole('button',{name:'Mock receipt: receive remaining goods'}).click();
 await expect(rice(page).getByRole('cell')).toHaveText(['8','0','8','0']);
 await expect(panel.getByLabel('Cancel quantity (kg)',{exact:true})).toHaveValue('3');
 await expect(panel.getByLabel('Cancellation reason')).toHaveValue('Availability changed');
 await expect(panel.getByRole('button',{name:'Cancel selected goods',exact:true})).toBeDisabled();
 await expect(panel.getByText('No unreceived goods remain for cancellation.')).toBeVisible();
 await page.setViewportSize({width:1440,height:1100});
 await page.screenshot({path:'test-results/g03-receipt-refresh-desktop.png',fullPage:true});
});


for(const receiptDuringRecovery of [false,true])test(`G03 504 after saving 1kg retries exact key repeatedly (receipt=${receiptDuringRecovery})`,async({page})=>{
 let injected=0;
 await page.route('**/mock.ts*',async route=>{
  const response=await route.fetch();let body=await response.text();
  const start=body.indexOf('async cancel(');expect(start).toBeGreaterThan(0);
  const prefix=body.slice(0,start);let cancel=body.slice(start);
  const gateway='throw new ApiError({type:"about:blank",title:"Gateway timeout",status:504,code:"GATEWAY_TIMEOUT",detail:"Response lost after save",instance:"/api/mock",traceId:"qa",fieldErrors:[]})';
  cancel=cancel.replace(/async cancel\(id, body, key\) \{/,match=>match+' (window.__qaCancellations ??= []).push({key,body:JSON.stringify(body)});');
  cancel=cancel.replace(/throw new TypeError\(["']Mock network response lost after saving\.["']\)/g,()=>{injected++;return gateway;});
  cancel=cancel.replace('return structuredClone(saved.response);','if(window.__qaCancellations.length <= 2) { '+gateway+'; } return structuredClone(saved.response);');
  body=prefix+cancel;await route.fulfill({response,body});
 });
 await fixture(page);const panel=await fill(page,'1');
 await page.getByLabel('Mock response').selectOption('timeout-after-save');
 await panel.getByRole('button',{name:'Cancel selected goods',exact:true}).click();
 await expect(panel.getByRole('alert')).toContainText('Cancellation outcome is unknown.');
 expect(injected).toBeGreaterThan(0);
 await expect(panel.getByLabel('Cancel quantity (kg)',{exact:true})).toBeDisabled();
 await expect(panel.getByLabel('Cancellation reason')).toBeDisabled();
 await expect(page.getByRole('button',{name:'Explore campaign',exact:true})).toBeDisabled();
 await expect(panel.getByRole('button',{name:'Close cancellation'})).toBeDisabled();
 if(receiptDuringRecovery){await page.getByRole('button',{name:'Mock receipt: receive remaining goods'}).click();await expect(rice(page).getByRole('cell')).toHaveText(['8','1','7','0']);}
 await panel.getByRole('button',{name:'Retry same cancellation'}).click();
 await expect(panel.getByRole('alert')).toContainText('Cancellation outcome is unknown.');
 await expect(panel.getByRole('button',{name:'Retry same cancellation'})).toBeEnabled();
 await panel.getByRole('button',{name:'Retry same cancellation'}).click();
 await expect(panel.getByRole('status').filter({hasText:'Cancellation saved'})).toBeVisible();
 await expect(rice(page).getByRole('cell')).toHaveText(receiptDuringRecovery?['8','1','7','0']:['8','1','5','2']);
 const calls=await page.evaluate(()=>(window as unknown as {__qaCancellations:{key:string;body:string}[]}).__qaCancellations);
 expect(calls).toHaveLength(3);expect(new Set(calls.map(x=>x.key)).size).toBe(1);expect(new Set(calls.map(x=>x.body)).size).toBe(1);
});
