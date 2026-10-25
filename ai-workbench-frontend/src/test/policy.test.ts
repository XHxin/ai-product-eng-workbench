import { describe, expect, it } from 'vitest'
import { canCreateRequirement, describeStatus } from '../stores/policy'

describe('workspace policy', () => {
  it('does not infer privileges from an entered username', () => {
    expect(canCreateRequirement([])).toBe(false)
    expect(canCreateRequirement(['DEV'])).toBe(false)
    expect(canCreateRequirement(['PM'])).toBe(true)
    expect(canCreateRequirement(['ADMIN'])).toBe(true)
  })
  it('maps every lifecycle status to a user-facing label', () => {
    expect(describeStatus('EXTRACT')).toBe('待提取')
    expect(describeStatus('POOL')).toBe('需求池')
    expect(describeStatus('ITERATION')).toBe('已纳入迭代')
    expect(describeStatus('CONFIRMATION')).toBe('待确认')
    expect(describeStatus('REVIEW')).toBe('待评审')
    expect(describeStatus('PRD_CONFIRMED')).toBe('PRD已确认')
    expect(describeStatus('DEVELOPMENT')).toBe('开发中')
    expect(describeStatus('COMPLETED')).toBe('已完成')
  })
})
