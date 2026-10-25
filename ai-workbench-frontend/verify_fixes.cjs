const { chromium } = require('playwright');

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  await page.goto('http://106.53.107.222');
  await page.getByLabel('账号').fill('admin');
  await page.getByLabel('密码').fill('Workbench2026!');
  await page.getByRole('button', { name: '登录' }).click();
  await page.waitForTimeout(1200);
  await page.reload();
  await page.waitForTimeout(1500);

  const csrfFailure = await page.evaluate(async () => {
    const response = await fetch('/api/iterations/1/lock', { method: 'POST', credentials: 'include' });
    return { status: response.status, text: await response.text() };
  });
  console.log('CSRF_STATUS', csrfFailure.status);
  console.log('CSRF_MESSAGE', csrfFailure.text);

  await page.getByTestId('nav-iterations').click();
  await page.waitForTimeout(800);
  await page.getByRole('button', { name: '＋ 创建迭代' }).click();
  await page.waitForTimeout(300);
  await page.locator('input[placeholder="例如：Q4迭代二"]').fill('CSRF 校验临时迭代');
  const dateInputs = page.locator('.modal input[type="date"]');
  await dateInputs.nth(0).fill('2026-10-30');
  await dateInputs.nth(1).fill('2026-10-20');
  await page.locator('.modal-f button.btn-primary').click();
  await page.waitForTimeout(1000);
  console.log('TOAST', await page.locator('.toast, [data-testid="toast"]').first().textContent().catch(() => ''));
  await page.locator('.modal-f .btn-ghost').click();
  await page.waitForTimeout(300);

  await page.getByTestId('nav-analysis').click();
  await page.waitForTimeout(1500);
  await page.getByRole('button', { name: /最新 PRD V2/ }).first().click();
  await page.waitForTimeout(800);
  console.log('MD_HEADING', await page.locator('.doc.md h1').first().textContent());
  console.log('MD_TABLE', await page.locator('.doc.md table').count());
  console.log('RAW_MARKDOWN_HIDDEN', (await page.locator('.doc.md').innerText()).includes('## 1. 文档信息'));
  await page.screenshot({ path: 'C:/Users/13019/AppData/Local/Temp/workbench-md-preview.png' });
  await browser.close();
})();
