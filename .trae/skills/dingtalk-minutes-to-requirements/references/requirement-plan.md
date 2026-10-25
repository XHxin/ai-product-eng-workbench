# Requirement Plan Schema

The plan is a JSON object so minutes metadata can travel with the extracted items.

```json
{
  "minutesTitle": "需求讨论会议261024",
  "minutesUuid": "76327569643439393538343036345f3439333235353739365f39",
  "minutesShareUrl": "https://shanji.dingtalk.com/app/transcribes/...",
  "requirements": [
    {
      "title": "看板支持自定义指标公式",
      "rawText": "16:32 业务方希望看板指标可由业务自定义公式，并能共享给团队。",
      "normalizedText": "背景：现有看板指标由研发硬编码，业务调整周期长。\n问题：业务无法自行配置指标，需求排队等待研发。\n目标：支持业务方定义指标与计算公式，并按权限共享。\n范围：指标定义、公式校验、计算引擎复用、共享权限。\n约束：公式需运行在安全沙箱内，不得执行任意代码。\n验收标准：公式计算准确率 100%；1 万条指标配置在 30 秒内完成；越权访问被拒绝。",
      "requirementType": "FEATURE",
      "priority": "P1",
      "rationale": "16:32-16:40 明确要求业务自定义并共享指标；影响多个业务团队且本期需要交付，因此定为新功能、P1。"
    }
  ]
}
```

## Field rules

| Field | Required | Limit | Rule |
|---|---|---|---|
| `minutesTitle` | yes | 200 | Exact minutes title |
| `minutesUuid` | recommended | 200 | DingTalk task UUID; also used for idempotency |
| `minutesShareUrl` | no | 1000 | Used as `sourceRef` when present |
| `requirements[].title` | yes | 200 | Specific, 6–40 Chinese characters |
| `requirements[].rawText` | yes | 20000 | Supporting excerpt from the minutes |
| `requirements[].normalizedText` | yes | 20000 | Must contain 背景, 问题, 目标, 范围, 约束, 验收标准 |
| `requirements[].requirementType` | yes | — | `FEATURE`, `IMPROVEMENT`, or `BUG` |
| `requirements[].priority` | yes | — | `P0`, `P1`, `P2`, or `P3` |
| `requirements[].rationale` | yes | 2000 | Evidence-based reason for type and priority |

## Publishing behaviour

- `sourceType` is always `TINGJI`.
- `sourceRef` is `minutesShareUrl` when present, otherwise `tingji:<minutesUuid>`.
- The idempotency key is `tingji-<minutesUuid>-<sha1(title + normalizedText)[:16]>`.
- The publisher creates the requirement in `EXTRACT` (待提取) and preserves the plan's normalized text, type, and priority for product-manager confirmation.
- Use `--auto-confirm` only when the user explicitly asks to push items directly into the pool (`POOL`).
- A plan item with no clear actionable outcome must be omitted, not published with placeholder content.
