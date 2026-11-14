import {test,expect} from '@playwright/test';
// HTTP fixtures only, not Spring Boot/PostgreSQL E2E.
for(const scenario of ['multiple','lost','refresh-error','conflict'])test(`R01 HTTP fixture: ${scenario}`,async({page})=>{
  const lose=scenario==='lost';let readFailed=false;
  let received=0,writes=0;const keys=new Set<string>();const bodies:string[]=[];
  await page.route('**/api/**',async route=>{
    const r=route.request(),p=new URL(r.url()).pathname;
    const json=(body:unknown,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
    if(p==='/api/auth/csrf')return json({token:'csrf',headerName:'X-CSRF-TOKEN'});
    if(p==='/api/auth/me')return json({userId:'staff',fullName:'Test Admin',status:'ACTIVE',memberships:[{organizationId:'org',status:'ACTIVE',roles:['ORG_ADMIN']}]});
    if(p==='/api/org/cases/case'&&scenario==='refresh-error'&&writes>0&&!readFailed){readFailed=true;return json({status:503,code:'READ_UNAVAILABLE',title:'Read unavailable'},503);}
    if(p==='/api/org/cases/case')return json({id:'case',organizationId:'org',outstandingCommitments:received<3?[{contributionId:'real-pledge',status:'PARTIALLY_RECEIVED',items:[{contributionItemId:'real-line',campaignItemId:'real-item',itemName:'Rice',unit:'kg',pledgedQty:3,receivedQty:received,outstandingQty:3-received}]}]:[]});
    if(p==='/api/commitments/real-pledge/receipts'){
      writes++;if(scenario==='conflict')return json({status:409,code:'QUANTITY_EXCEEDED',title:'Stock changed'},409);
      expect(r.headers()['x-csrf-token']).toBe('csrf');const key=r.headers()['idempotency-key'];expect(key).toBeTruthy();bodies.push(r.postData()!);
      if(!keys.has(key)){keys.add(key);received+=r.postDataJSON().items[0].quantity;}
      if(lose&&writes===1)return json({status:504,code:'GATEWAY_TIMEOUT',title:'Lost response'},504);
      return json({id:'receipt-id',contributionId:'real-pledge',items:r.postDataJSON().items,recordedAt:'2026-11-14T01:00:00Z',blockchainStatus:'PENDING_ON_CHAIN'},201);
    }
    return json({status:404,code:'MISSING',title:'Missing'},404);
  });
  await page.goto('/cases/case/receipts');await expect(page.getByRole('heading',{name:'Receive pledged goods'})).toBeVisible();
  await page.getByRole('checkbox',{name:/Rice/}).check();await page.getByLabel('Receive Rice',{exact:true}).fill('1');
  await page.getByRole('button',{name:'Record API receipt'}).click();
  if(scenario==='conflict'){await expect(page.getByText(/409 QUANTITY_EXCEEDED/)).toBeVisible();expect(received).toBe(0);expect(writes).toBe(1);return;}
  if(scenario==='refresh-error'){await expect(page.getByText(/Context unavailable/)).toBeVisible();await expect(page.getByRole('region',{name:'Backend receipt'})).toContainText('receipt-id');await expect(page.getByRole('button',{name:'Record API receipt'})).toBeDisabled();await page.getByRole('button',{name:'Refresh staff quantities'}).click();}
  if(lose){await expect(page.getByText(/Outcome unknown/)).toBeVisible();await expect(page.getByLabel('Received at (UTC)')).toBeDisabled();await page.getByRole('button',{name:'Retry same receipt'}).click();expect(keys.size).toBe(1);expect(bodies[0]).toBe(bodies[1]);}
  await expect(page.getByRole('region',{name:'Backend receipt'})).toContainText('receipt-id');await expect(page.getByText(/received 1 · outstanding 2/)).toBeVisible();
  if(scenario==='multiple'){await page.getByRole('checkbox',{name:/Rice/}).check();await page.getByLabel('Receive Rice',{exact:true}).fill('2');await page.getByRole('button',{name:'Record API receipt'}).click();await expect(page.getByText('No outstanding goods remain.')).toBeVisible();}
  expect(received).toBe(scenario==='multiple'?3:1);
});
