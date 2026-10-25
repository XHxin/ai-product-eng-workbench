import { expect, test, type Page } from '@playwright/test'

const PASSWORD = process.env.E2E_PASSWORD || 'Workbench#2026'
const stamp = Date.now()
const requirementTitle = `E2E看板指标${stamp}`
const requirementRaw = '客户希望看板支持自定义指标，可按业务公式配置并共享给团队。'
const iterationName = `E2E迭代${stamp}`

async function login(page: Page, username: string) {
  await page.goto('/')
  await page.getByLabel('账号').fill(username)
  await page.getByLabel('密码').fill(PASSWORD)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.getByTestId('nav-overview')).toBeVisible()
}

async function logout(page: Page) {
  await page.locator('.avatar').click()
  await expect(page.getByRole('button', { name: '登录' })).toBeVisible()
}

function field(page: Page, label: string, root = '.content') {
  return page.locator(`${root} .field`).filter({ hasText: label }).locator('input, textarea, select').first()
}

function modalField(page: Page, label: string) {
  return field(page, label, '.modal')
}

test.describe.configure({ mode: 'serial' })

test('需求从录入到发布完成的全流程在浏览器中可用', async ({ page }) => {
  await login(page, 'pm')
  await expect(page.getByRole('button', { name: '发布中心' })).toHaveCount(0)

  // 录入需求并确认提取结果
  await page.getByTestId('nav-intake').click()
  await expect(page.getByText('来源标识（可选）')).toHaveCount(0)
  await field(page, '需求标题').fill(requirementTitle)
  await field(page, '原始内容').fill(requirementRaw)
  await page.getByRole('button', { name: '创建需求并确认提取结果' }).click()
  await expect(page.getByRole('heading', { name: 'AI提取结果确认' })).toBeVisible()
  await modalField(page, '备注').fill('影响留存，需本期交付')
  await page.getByRole('button', { name: '确认并同步需求池' }).click()
  await expect(page.locator('.toast')).toContainText('已确认并同步至需求池')

  // 创建迭代、纳入需求并锁定
  await page.getByTestId('nav-iterations').click()
  await page.getByRole('button', { name: '＋ 创建迭代' }).click()
  await modalField(page, '迭代名称').fill(iterationName)
  await modalField(page, '开始日期').fill('2026-10-20')
  await modalField(page, '结束日期').fill('2026-11-02')
  await page.locator('.modal').getByRole('button', { name: '创建' }).click()
  await expect(page.locator('.seg button').filter({ hasText: iterationName })).toBeVisible()

  const poolTicket = page.locator('.ticket').filter({ hasText: requirementTitle })
  await poolTicket.getByRole('button', { name: '纳入本期' }).click()
  await expect(page.locator('.toast')).toContainText('需求已纳入当前迭代')
  await page.getByRole('button', { name: '锁定范围' }).click()
  await expect(page.locator('.toast')).toContainText('迭代范围已锁定')

  // 需求分析：PRD、确认问题、评审
  await page.getByTestId('nav-analysis').click()
  await page.locator('.seg button').filter({ hasText: iterationName }).click()
  const prdEditor = page.locator('textarea[maxlength="100000"]').first()
  await expect(prdEditor).toHaveValue(new RegExp(requirementTitle))
  await page.getByRole('button', { name: '保存为新版本' }).click()
  await expect(page.locator('.toast')).toContainText('PRD 新版本已保存')

  await field(page, '新增确认问题').fill('自定义指标是否复用现有 OLAP？')
  await field(page, '来源引用').fill('dingtalk-msg-1')
  await field(page, '来源回复').fill('复用现有 OLAP，不新建计算服务')
  await page.getByRole('button', { name: '记录问题' }).click()
  await expect(page.locator('.toast')).toContainText('待确认问题已记录')

  await page.getByRole('button', { name: '录入结论' }).click()
  await field(page, '确认结论').fill('确认复用现有 OLAP')
  await page.getByRole('button', { name: '保存结论' }).click()
  await expect(page.locator('.toast')).toContainText('确认结论已保存')

  await page.getByRole('button', { name: '提交待评审' }).click()
  await expect(page.locator('.toast')).toContainText('已进入待评审状态')

  await field(page, '评审时间').fill('2026-10-24T15:00')
  await field(page, '参与人').fill('张敏, 陈宇')
  await field(page, '评审结论').fill('方案可行，通过评审')
  await page.locator('#prd-panel').getByRole('button', { name: '登记评审结果' }).click()
  await expect(page.locator('.toast')).toContainText('评审通过')

  // 研发任务：生成、确认、开发补充与完成
  await page.getByTestId('nav-tasks').click()
  await page.locator('.seg button').filter({ hasText: iterationName }).click()
  await page.getByRole('button', { name: '＋ 新建任务' }).click()
  await page.locator('.modal select').selectOption({ index: 0 })
  await modalField(page, '任务标题').fill('实现自定义指标计算')
  await modalField(page, '范围').fill('指标定义与计算引擎')
  await modalField(page, '验收标准').fill('计算准确率100%')
  await page.locator('.modal').getByRole('button', { name: '创建任务' }).click()
  await expect(page.locator('.toast')).toContainText('任务已创建')
  await page.getByRole('button', { name: '确认任务' }).click()
  await expect(page.locator('.toast')).toContainText('任务已确认')

  await logout(page)
  await login(page, 'dev')
  await page.getByTestId('nav-tasks').click()
  await page.locator('.seg button').filter({ hasText: iterationName }).click()
  await page.getByRole('button', { name: '补充开发信息' }).first().click()
  await modalField(page, '确认工时').fill('5')
  await modalField(page, '技术方案').fill('复用现有 OLAP 计算服务')
  await modalField(page, '技术子任务').fill('指标模型, 权限校验')
  await modalField(page, '测试点').fill('准确率, 1万指标性能')
  await page.locator('.modal').getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.toast')).toContainText('开发信息已保存')
  await page.locator('.ticket input[type="checkbox"]').first().check()
  await page.getByRole('button', { name: /批量完成任务/ }).click()
  await expect(page.locator('.toast')).toContainText('已完成 1 个任务')

  // 审计：管理员可见完整操作记录
  await logout(page)
  await login(page, 'admin')
  await page.getByTestId('nav-audit').click()
  await expect(page.locator('table')).toContainText('REQUIREMENT_NORMALIZED')
  await expect(page.locator('table')).not.toContainText('sample-api-secret')
})

test('角色权限由后端约束，前端不明文伪造', async ({ page }) => {
  await login(page, 'dev')
  await page.getByTestId('nav-intake').click()
  await expect(page.getByRole('button', { name: '创建需求并确认提取结果' })).toBeDisabled()
  await page.getByTestId('nav-audit').click()
  await expect(page.getByText('审计日志仅管理员可查看。')).toBeVisible()
})

test('需求池搜索可定位需求并打开详情', async ({ page }) => {
  await login(page, 'pm')
  await page.getByTestId('nav-pool').click()
  await page.getByPlaceholder('搜索需求编号或标题').fill(requirementTitle)
  await expect(page.getByRole('heading', { name: '需求池' })).toBeVisible()
  await expect(page.getByRole('cell', { name: requirementTitle })).toBeVisible()
  await page.locator('tr').filter({ hasText: requirementTitle }).click()
  await expect(page.locator('.drawer')).toContainText(requirementTitle)
  await expect(page.locator('.drawer-f').getByRole('button', { name: '需求分析' })).toHaveCount(0)
  await expect(page.locator('.drawer-f').getByRole('button', { name: '研发任务' })).toHaveCount(0)
})
