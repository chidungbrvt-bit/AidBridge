import {test,expect} from '@playwright/test';
const backendCalls=new WeakMap<import('@playwright/test').Page,string[]>();
test.beforeEach(async({page})=>{const calls:string[]=[];backendCalls.set(page,calls);page.on('request',request=>{if(new URL(request.url()).pathname.startsWith('/api/'))calls.push(request.url());});});
test.afterEach(async({page})=>{expect(backendCalls.get(page)).toEqual([]);});
async function fill(page:import('@playwright/test').Page){await page.getByLabel('Rice',{exact:false}).first().check();await page.getByLabel('Quantity (kg)').fill('8');await page.getByLabel('Drinking Water',{exact:false}).first().check();await page.getByLabel('Quantity (box)').fill('2');}
test.beforeEach(async({page})=>{await page.goto('/');await expect(page.getByRole('heading',{name:'Pledge essential goods'})).toBeVisible();});
test('multi-item pledge to own history, separate totals, mobile layout',async({page})=>{
 await fill(page);await page.getByRole('button',{name:'Pledge goods →',exact:true}).click();await expect(page.getByText('Goods pledged. Pending on-chain.',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'View my pledges →'}).click();await expect(page).toHaveURL(/me\/commitments/);await expect(page.getByRole('table')).toContainText('Rice');await expect(page.getByRole('table')).toContainText('Drinking Water');await expect(page.getByRole('heading',{name:'My pledges (1)'})).toBeVisible();
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
});
test('local validation and server 400/409 never create successful history',async({page})=>{
 await page.getByRole('button',{name:'Pledge goods →',exact:true}).click();await expect(page.getByRole('alert')).toContainText('Select at least one item');
 await fill(page);await page.getByLabel('Mock response').selectOption('400');await page.getByRole('button',{name:'Pledge goods →',exact:true}).click();await expect(page.getByRole('alert')).toContainText('400 VALIDATION_ERROR');await expect(page.getByRole('alert')).toBeFocused();
 await page.getByLabel('Mock response').selectOption('409');await page.getByRole('button',{name:'Pledge goods →',exact:true}).click();await expect(page.getByRole('alert')).toContainText('409 QUANTITY_EXCEEDED');
 await page.getByRole('button',{name:'My pledges',exact:true}).click();await expect(page.getByRole('heading',{name:'No pledges yet'})).toBeVisible();
});
test('history empty/error/loading and campaign read error',async({page})=>{
 await page.getByRole('button',{name:'My pledges',exact:true}).click();await expect(page.getByRole('status').filter({hasText:'Loading your pledges'})).toBeVisible();await expect(page.getByRole('heading',{name:'No pledges yet'})).toBeVisible();
 await page.getByLabel('Mock response').selectOption('history-error');await page.getByRole('button',{name:'Refresh history'}).click();await expect(page.getByRole('alert')).toContainText('Unable to load your pledge history');
 await page.getByLabel('Mock response').selectOption('none');await page.getByRole('button',{name:'Retry history loading'}).click();await expect(page.getByRole('heading',{name:'No pledges yet'})).toBeVisible();
 await page.getByLabel('Mock response').selectOption('load-error');await page.getByRole('button',{name:'Refresh history'}).click();await page.getByRole('button',{name:'Explore campaign',exact:true}).click();await expect(page.getByRole('alert')).toContainText('Unable to load campaign information');
});
test('lost response retry preserves one commitment; donor disabled stays read only',async({page})=>{
 await fill(page);await page.getByLabel('Mock response').selectOption('timeout-after-save');await page.getByRole('button',{name:'Pledge goods →',exact:true}).click();await expect(page.getByRole('button',{name:'Retry same request'})).toBeVisible();await page.getByRole('button',{name:'Retry same request'}).click();await expect(page.getByText('Goods pledged. Pending on-chain.',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'View my pledges →'}).click();await expect(page.getByRole('heading',{name:'My pledges (1)'})).toBeVisible();await page.getByLabel('Mock donor enabled').uncheck();await expect(page.getByText('Donor access is not enabled')).toBeVisible();await expect(page.getByRole('table')).toHaveCount(0);
});
test('desktop screenshot and real mode fail closed',async({page})=>{
 await page.setViewportSize({width:1440,height:1100});await page.screenshot({path:'test-results/donor-desktop.png',fullPage:true});await page.setViewportSize({width:390,height:844});await page.screenshot({path:'test-results/donor-mobile.png',fullPage:true});
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();await page.goto('/?mode=real');await expect(page.getByRole('heading',{name:'Real API is not connected'})).toBeVisible();
});

test('mobile pledge and 403 permission rejection',async({page})=>{
 await page.setViewportSize({width:390,height:844});await fill(page);await page.getByLabel('Mock response').selectOption('403');await page.getByRole('button',{name:'Pledge goods →',exact:true}).click();await expect(page.getByRole('alert')).toContainText('403 FORBIDDEN');
 await page.getByLabel('Mock response').selectOption('none');await page.getByRole('button',{name:'Pledge goods →',exact:true}).click();await expect(page.getByText('Goods pledged. Pending on-chain.',{exact:true})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBeTruthy();
});
test('keyboard error summary targets invalid quantity; no backend requests',async({page})=>{
 const apiRequests:string[]=[];page.on('request',r=>{if(new URL(r.url()).pathname.startsWith('/api/'))apiRequests.push(r.url());});
 await page.getByLabel('Rice',{exact:false}).first().check();await page.getByLabel('Quantity (kg)').fill('1.5');await page.getByRole('button',{name:'Pledge goods →',exact:true}).click();await expect(page.getByRole('alert')).toBeFocused();await page.getByRole('alert').getByRole('link').click();await expect(page.getByLabel('Quantity (kg)')).toBeFocused();expect(apiRequests).toEqual([]);
});
test('capability revocation during request does not disclose late success',async({page})=>{
 await fill(page);await page.getByRole('button',{name:'Pledge goods →',exact:true}).click();await page.getByLabel('Mock donor enabled').uncheck();await expect(page.getByRole('button',{name:'Pledge goods →',exact:true})).toBeDisabled();await expect(page.getByRole('button',{name:'Pledge goods →',exact:true})).not.toHaveText('Saving pledge...');await expect(page.getByText('Goods pledged. Pending on-chain.',{exact:true})).toHaveCount(0);
});
