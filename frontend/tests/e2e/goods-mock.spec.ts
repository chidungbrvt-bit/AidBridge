import { test, expect, type Page } from '@playwright/test';
test.beforeEach(async ({page}) => {
  await page.goto('/login'); await page.getByLabel('Email address').fill('minh.a@example.test');
  await page.getByLabel('Password',{exact:true}).fill('Mock-only-123!');
  await page.getByRole('button',{name:'Sign in →',exact:true}).click();
  await page.getByRole('link',{name:'Goods demo · MOCK'}).click();
  await expect(page.getByRole('heading',{name:'Receive and deliver goods'})).toBeVisible();
});
async function goods(page:Page,rice:string,water:string) {
  await page.getByRole('checkbox',{name:/^Rice \(kg\)/}).check(); await page.getByLabel('Rice quantity').fill(rice);
  await page.getByRole('checkbox',{name:/^Drinking Water \(box\)/}).check(); await page.getByLabel('Drinking Water quantity').fill(water);
}
test('R01 partial receipt updates immediately; no B08/B09 network writes',async({page})=>{
  const writes:string[]=[]; page.on('request',r=>{if(r.method()==='POST' && /\/(receipts|deliveries)/.test(r.url())) writes.push(r.url());});
  await goods(page,'5','1'); await page.getByRole('button',{name:'Record mock receipt',exact:true}).click();
  await expect(page.getByRole('status').filter({hasText:'Mock receipt recorded'})).toBeVisible();
  await expect(page.getByTestId('totals-kg').locator('td')).toHaveText(['14','7','1','0']);
  await expect(page.getByRole('checkbox',{name:/^Rice/})).toHaveAccessibleName(/^Rice \(kg\).*Unreceived: 3$/);
  await page.screenshot({path:'test-results/f08-desktop.png',fullPage:true});
  expect(writes).toEqual([]);
});
test('D01 delivery keeps confirmed separate; failed attempt zero effect',async({page})=>{
  await page.getByRole('button',{name:'Load partial receipt sample',exact:true}).click();
  await page.getByRole('button',{name:'Deliver goods',exact:true}).click(); await page.getByLabel('Simulated actor').selectOption('fieldVolunteer');
  await goods(page,'4','1'); await page.getByRole('checkbox',{name:'Fictional evidence 1',exact:true}).check();
  await page.getByRole('button',{name:'Record mock delivery',exact:true}).click();
  await expect(page.getByRole('status').filter({hasText:'Awaiting independent confirmation'})).toBeVisible();
  await expect(page.getByTestId('totals-kg').locator('td')).toHaveText(['14','7','5','0']);
  await page.getByRole('combobox',{name:/^Outcome/}).selectOption('FAILED'); await page.getByLabel('Delivery reason').fill('Fictional road blocked');
  await page.getByRole('button',{name:'Record mock delivery',exact:true}).click(); await expect(page.getByRole('status').filter({hasText:'Stock unchanged'})).toBeVisible();
  await expect(page.getByTestId('totals-kg').locator('td')).toHaveText(['14','7','5','0']);
});
test('403 and 409 do not mutate stock, including over-receive',async({page})=>{
  await page.getByLabel('Simulated actor').selectOption('donor'); await expect(page.getByRole('button',{name:'Record mock receipt',exact:true})).toBeDisabled();
  await expect(page.getByRole('alert').filter({hasText:'403 FORBIDDEN'})).toBeVisible();
  await page.getByLabel('Simulated actor').selectOption('organizationAdmin'); await goods(page,'5','1');
  await page.getByRole('checkbox',{name:/Reject next write/}).check(); await page.getByRole('button',{name:'Record mock receipt',exact:true}).click();
  await expect(page.getByRole('alert').filter({hasText:'409 QUANTITY_EXCEEDED'})).toBeVisible();
  await expect(page.getByTestId('totals-kg').locator('td')).toHaveText(['14','2','1','0']);
  await page.getByRole('checkbox',{name:/Reject next write/}).uncheck(); await page.getByLabel('Drinking Water quantity').fill('3');
  await page.getByRole('button',{name:'Record mock receipt',exact:true}).click(); await expect(page.getByRole('alert').filter({hasText:'409'})).toBeVisible();
  await expect(page.getByTestId('totals-kg').locator('td')).toHaveText(['14','2','1','0']);
});
test('loading/empty/error recovery and keyboard submit',async({page})=>{
  for(const state of ['loading','empty','error']) {
    await page.getByRole('button',{name:`Show ${state} context`,exact:true}).click();
    await expect(page.getByRole('button',{name:'Record mock receipt',exact:true})).toHaveCount(0);
  }
  await page.getByRole('button',{name:'Retry mock read',exact:true}).click(); await goods(page,'1','1');
  const button=page.getByRole('button',{name:'Record mock receipt',exact:true}); await button.focus(); await page.keyboard.press('Enter');
  await expect(page.getByRole('status').filter({hasText:'Mock receipt recorded'})).toBeFocused();
});
test('mobile form and shell remain usable without page overflow',async({page})=>{
  await page.setViewportSize({width:390,height:844}); await goods(page,'1','1');
  await page.getByRole('button',{name:'Record mock receipt',exact:true}).click(); await expect(page.getByRole('status').filter({hasText:'Mock receipt recorded'})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
  await page.screenshot({path:'test-results/f08-mobile.png',fullPage:true});
});

test('D01 over-delivery has line feedback and no writes; corrected request succeeds', async ({page}) => {
  await page.getByRole('button',{name:'Load partial receipt sample',exact:true}).click();
  await page.getByRole('button',{name:'Deliver goods',exact:true}).click();
  await page.getByLabel('Simulated actor').selectOption('fieldVolunteer');
  await goods(page,'4','2'); await page.getByRole('checkbox',{name:'Fictional evidence 1',exact:true}).check();
  await page.getByRole('button',{name:'Record mock delivery',exact:true}).click();
  await expect(page.getByRole('alert').filter({hasText:'409 QUANTITY_EXCEEDED'})).toContainText('Drinking Water: only 1 box');
  await expect(page.getByLabel('Drinking Water quantity')).toHaveAttribute('aria-invalid','true');
  await expect(page.getByTestId('totals-kg').locator('td')).toHaveText(['14','7','1','0']);
  await expect(page.getByTestId('totals-box').locator('td')).toHaveText(['3','1','0','0']);
  await expect(page.getByRole('region',{name:'Latest mock result',exact:true})).toHaveCount(0);
  await page.getByLabel('Drinking Water quantity').fill('1');
  await page.getByRole('button',{name:'Record mock delivery',exact:true}).click();
  await expect(page.getByRole('region',{name:'Latest mock result',exact:true})).toContainText('Delivered');
  await expect(page.getByTestId('totals-kg').locator('td')).toHaveText(['14','7','5','0']);
});
test('FAILED requires reason and records zero effective goods after partial receipt', async ({page}) => {
  await goods(page,'5','1'); await page.getByRole('button',{name:'Record mock receipt',exact:true}).click();
  await expect(page.getByRole('status').filter({hasText:'Mock receipt recorded'})).toBeVisible();
  await page.getByRole('button',{name:'Deliver goods',exact:true}).click();
  await page.getByLabel('Simulated actor').selectOption('fieldVolunteer');
  await page.getByRole('combobox',{name:/^Outcome/}).selectOption('FAILED');
  await page.getByRole('button',{name:'Record mock delivery',exact:true}).click();
  await expect(page.getByRole('alert').filter({hasText:'400 VALIDATION_ERROR'})).toBeVisible();
  await page.getByLabel('Delivery reason').fill('Fictional road blocked');
  await page.getByRole('button',{name:'Record mock delivery',exact:true}).click();
  const result=page.getByRole('region',{name:'Latest mock result',exact:true});
  await expect(result).toContainText('Failed');await expect(result).toContainText('Reason: Fictional road blocked');
  await expect(result).toContainText('Blockchain: Not applicable');
  await expect(page.getByTestId('totals-kg').locator('td')).toHaveText(['14','7','1','0']);
  await expect(page.getByTestId('totals-box').locator('td')).toHaveText(['3','1','0','0']);
  await page.getByRole('button',{name:'Receive goods',exact:true}).click();
  await expect(page.getByRole('checkbox',{name:/^Rice/})).toHaveAccessibleName(/^Rice \(kg\).*Unreceived: 3$/);
});
