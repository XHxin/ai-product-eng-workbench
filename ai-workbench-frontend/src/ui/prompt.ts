export type ExternalSource = 'dingtalk' | 'tingji' | 'sheet'

const SKILLS: Record<ExternalSource, string> = {
  dingtalk: 'dingtalk-chat-to-requirements',
  tingji: 'dingtalk-minutes-to-requirements',
  sheet: 'dingtalk-doc-to-requirements',
}

export function buildPrompt(source: ExternalSource, input: string): string {
  const target = input.trim()
  const skill = SKILLS[source]
  const subject = source === 'dingtalk' ? '钉钉用户或群聊' : source === 'tingji' ? 'AI 听记' : '钉钉文档或多维表'
  return [
    `请使用 Skill: ${skill}，从${subject}“${target}”读取内容，提取可执行的产品或工程需求并录入工作台需求池。`,
    '',
    '要求：',
    '1. 只保留可执行的需求、缺陷或明确变更，忽略寒暄、排期和无结论讨论。',
    '2. 每条需求包含背景、问题、目标、范围、约束、验收标准。',
    '3. 引用真实消息、听记或文档记录作为来源，不要编造。',
    '4. 录入前先预览，重复执行不要产生重复需求。',
  ].join('\n')
}

export type IterationPromptTarget = {
  id: number
  name: string
}

export function buildIterationPrompt(iteration: IterationPromptTarget): string {
  return [
    `请使用 Skill: workbench-requirement-refinement，对工作台迭代“${iteration.name}”（ID ${iteration.id}）进行需求分析、调研，输出需求文档和原型，并上传回工作台对应迭代。`,
    '',
    '当前迭代状态：已锁定（LOCKED），可直接生成并上传 PRD 与原型。',
    '',
    '要求：',
    '1. 先读取迭代下的需求、已有 PRD 和原型，再结合当前代码调研。',
    '2. 对缺失的业务规则，通过钉钉向产品经理调研；未确认的规则标记为待确认，不要写成最终结论。',
    '3. 每个需求分别输出 Markdown 需求规格和单文件原型 HTML；产品经理回复后更新并上传新版本。',
    '4. 上传前确认迭代已锁定，PRD 版本号递增，不覆盖历史版本。',
  ].join('\n')
}

export async function copyText(text: string): Promise<boolean> {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch { /* 回退到 execCommand */ }
  }
  const area = document.createElement('textarea')
  area.value = text
  area.setAttribute('readonly', '')
  area.style.position = 'fixed'
  area.style.opacity = '0'
  document.body.appendChild(area)
  area.select()
  const copied = document.execCommand('copy')
  document.body.removeChild(area)
  return copied
}

const WORKBUDDY_DEEPLINK = 'workbuddy-ai://task?action=start&prompt='

export function buildWorkBuddyDeeplink(prompt: string): string {
  return `${WORKBUDDY_DEEPLINK}${encodeURIComponent(prompt.slice(0, 8000))}`
}
