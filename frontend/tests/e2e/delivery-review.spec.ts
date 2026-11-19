import { test, expect } from '@playwright/test';

test('F08 delivery flows into F09 review and void using the same item totals', async ({ page }) => {
  const writes: string[] = [];
  page.on('request', request => {
    if (request.method() !== 'GET' && /\/(receipts|deliveries|disputes)(\/|$)/.test(new URL(request.url()).pathname)) writes.push(request.url());
  });
  await page.goto('/login');
  await page.getByLabel('Email address').fill('minh.a@example.test');
  await page.getByLabel('Password', { exact: true }).fill('Mock-only-123!');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.getByRole('link', { name: 'Goods demo · MOCK' }).click();
  await page.getByRole('button', { name: 'Load partial receipt sample' }).click();
  await page.getByRole('button', { name: 'Deliver goods' }).click();
  await page.getByLabel('Simulated actor').selectOption('fieldVolunteer');
  await page.getByRole('checkbox', { name: /^Rice \(kg\)/ }).check();
  await page.getByLabel('Rice quantity').fill('4');
  await page.getByRole('checkbox', { name: /^Drinking Water \(box\)/ }).check();
  await page.getByRole('checkbox', { name: 'Fictional evidence 1', exact: true }).check();
  await page.getByRole('button', { name: 'Record mock delivery' }).click();
  await expect(page.getByTestId('totals-kg').locator('td')).toHaveText(['14', '7', '5', '0']);

  const panel = page.getByRole('region', { name: 'Delivery confirmation and disputes' });
  await panel.getByLabel('Simulated review actor').selectOption('fieldVolunteer');
  await expect(panel.getByRole('alert')).toContainText('A recorder cannot confirm their own delivery.');
  await expect(panel.getByRole('button', { name: 'Submit mock confirmation' })).toBeDisabled();
  await panel.getByLabel('Simulated review actor').selectOption('independentConfirmer');
  await panel.getByRole('checkbox', { name: /^Evidence version 1/ }).check();
  await panel.getByRole('button', { name: 'Submit mock confirmation' }).click();
  await expect(page.getByTestId('totals-kg').locator('td')).toHaveText(['14', '7', '5', '4']);
  await expect(page.getByTestId('totals-box').locator('td')).toHaveText(['3', '1', '1', '1']);
  await expect(panel.getByText('Delivery confirmed · Pending on chain', { exact: true })).toBeVisible();

  await panel.getByRole('button', { name: 'Open dispute', exact: true }).click();
  await panel.getByLabel('Delivery for review').selectOption({ label: 'Delivery 1 · Confirmed' });
  await panel.getByLabel('Review reason (required)').fill('Review the fictional delivery claim.');
  await panel.getByRole('button', { name: 'Open mock dispute' }).click();
  await expect(page.getByRole('button', { name: 'Record mock delivery' })).toBeDisabled();
  await expect(panel.getByText('Blocked by open dispute')).toBeVisible();
  await expect(page.getByTestId('totals-kg').locator('td')).toHaveText(['14', '7', '5', '4']);

  await panel.getByRole('button', { name: 'Resolve dispute', exact: true }).click();
  await panel.getByLabel('Resolution', { exact: true }).selectOption('VOID_DELIVERY');
  await panel.getByLabel('Review reason (required)').fill('Exclude the fictional delivery from effective totals.');
  await panel.getByRole('button', { name: 'Resolve mock dispute' }).click();
  await expect(panel.getByText('Delivery 1: Delivered · Voided')).toBeVisible();
  await expect(page.getByTestId('totals-kg').locator('td')).toHaveText(['14', '7', '1', '0']);
  await expect(page.getByTestId('totals-box').locator('td')).toHaveText(['3', '1', '0', '0']);
  await expect(page.getByRole('button', { name: 'Record mock delivery' })).toBeEnabled();
  expect(writes).toEqual([]);
});
