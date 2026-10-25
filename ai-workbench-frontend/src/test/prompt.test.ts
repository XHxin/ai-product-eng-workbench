import { describe, expect, it } from 'vitest'
import { buildIterationPrompt, buildPrompt, buildWorkBuddyDeeplink } from '../ui/prompt'

describe('buildPrompt', () => {
  it('points DingTalk chat to the chat skill with the given target', () => {
    const prompt = buildPrompt('dingtalk', 'Nelson的团队')
    expect(prompt).toContain('dingtalk-chat-to-requirements')
    expect(prompt).toContain('Nelson的团队')
    expect(prompt).toContain('工作台需求池')
  })

  it('points AI minutes to the minutes skill', () => {
    const prompt = buildPrompt('tingji', '需求讨论会议261024')
    expect(prompt).toContain('dingtalk-minutes-to-requirements')
    expect(prompt).toContain('需求讨论会议261024')
  })

  it('points document or table sources to the doc skill', () => {
    const prompt = buildPrompt('sheet', 'Nelson需求管理1.0')
    expect(prompt).toContain('dingtalk-doc-to-requirements')
    expect(prompt).toContain('钉钉文档或多维表')
    expect(prompt).toContain('Nelson需求管理1.0')
  })

  it('trims surrounding whitespace in the target', () => {
    const prompt = buildPrompt('dingtalk', '  Nelson的团队  ')
    expect(prompt).toContain('“Nelson的团队”')
  })
})

describe('buildIterationPrompt', () => {
  it('maps a locked iteration to requirement refinement only', () => {
    const prompt = buildIterationPrompt({ id: 3, name: '1025迭代' })
    expect(prompt).toContain('workbench-requirement-refinement')
    expect(prompt).not.toContain('workbench-task-breakdown')
    expect(prompt).toContain('1025迭代')
    expect(prompt).toContain('ID 3')
    expect(prompt).toContain('已锁定')
    expect(prompt).toContain('PRD')
  })

  it('never embeds credentials', () => {
    const prompt = buildIterationPrompt({ id: 1, name: '迭代' })
    expect(prompt).not.toContain('AI_WORKBENCH_PASSWORD')
    expect(prompt).not.toContain('AI_WORKBENCH_USERNAME')
  })
})

describe('buildWorkBuddyDeeplink', () => {
  it('uses the task start action and encodes the prompt', () => {
    const prompt = '请使用 Skill: dingtalk-chat-to-requirements'
    const link = buildWorkBuddyDeeplink(prompt)
    expect(link.startsWith('workbuddy-ai://task?action=start&prompt=')).toBe(true)
    expect(link).toContain(encodeURIComponent(prompt))
  })

  it('caps the prompt at 8000 characters', () => {
    const link = buildWorkBuddyDeeplink('需'.repeat(9000))
    expect(decodeURIComponent(link.split('prompt=')[1])).toHaveLength(8000)
  })
})
