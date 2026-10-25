import { expect, test, type Page } from '@playwright/test'

const PASSWORD = process.env.E2E_PASSWORD || 'Workbench#2026'
const stamp = Date.now()

async function login(page: Page) {
  await page.goto('/')
  await page.getByLabel('账号').fill('pm')
  await page.getByLabel('密码').fill(PASSWORD)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByTestId('nav-overview')).toBeVisible()
}

async function createRequirement(page: Page, title: string) {
  await page.getByTestId('nav-intake').click()
  await page.getByPlaceholder('例如：看板支持自定义指标').fill(title)
  await page.getByPlaceholder('粘贴客户原话、群消息或听记纪要…').fill('验证需求池删除与迭代移除操作。')
  await page.getByRole('button', { name: '创建需求并确认提取结果' }).click()
  await expect(page.locator('.toast')).toContainText('已创建')
  await page.locator('.modal .x-btn').click()
}

test('需求池可删除需求，规划中迭代可移除需求', async ({ page }) => {
  await login(page)

  const deleteTitle = `待删除需求${stamp}`
  await createRequirement(page, deleteTitle)
  await page.getByTestId('nav-pool').click()
  const deleteRow = page.locator('tr').filter({ hasText: deleteTitle })
  await expect(deleteRow).toBeVisible()
  await deleteRow.getByRole('button', { name: '删除' }).click()
  await page.getByRole('button', { name: '确认删除' }).click()
  await expect(page.locator('.toast')).toContainText('已删除')
  await expect(deleteRow).toHaveCount(0)

  const removeTitle = `待移除需求${stamp}`
  await createRequirement(page, removeTitle)
  const normalizeTicket = page.locator('.ticket').filter({ hasText: removeTitle })
  await normalizeTicket.getByRole('button', { name: '确认提取结果' }).click()
  await expect(page.getByText('需求原文')).toBeVisible()
  await expect(page.getByText('备注', { exact: true })).toBeVisible()
  await expect(page.locator('.modal textarea').nth(1)).toHaveValue('')
  await page.getByRole('button', { name: '确认并同步需求池' }).click()
  await expect(page.locator('.toast')).toContainText('已确认并同步至需求池')

  await page.getByTestId('nav-iterations').click()
  await page.getByRole('button', { name: '＋ 创建迭代' }).click()
  await page.locator('input[placeholder="例如：Q4迭代二"]').fill(`移除测试迭代${stamp}`)
  const dates = page.locator('.modal input[type="date"]')
  await dates.nth(0).fill('2026-10-20')
  await dates.nth(1).fill('2026-11-02')
  await page.locator('.modal-f button.btn-primary').click()
  await expect(page.locator('.toast')).toContainText('已创建迭代')

  const poolTicket = page.locator('.split-wide > .panel').nth(1).locator('.ticket').filter({ hasText: removeTitle })
  await poolTicket.getByRole('button', { name: '纳入本期' }).click()
  await expect(page.locator('.toast')).toContainText('需求已纳入当前迭代')

  const assignedTicket = page.locator('.split-wide > .panel').first().locator('.ticket').filter({ hasText: removeTitle })
  await assignedTicket.getByRole('button', { name: '移除' }).click()
  await page.getByRole('button', { name: '确认移除' }).click()
  await expect(page.locator('.toast')).toContainText('已移除')
  await expect(page.locator('.split-wide > .panel').first().getByText('本期迭代暂无需求')).toBeVisible()
})
