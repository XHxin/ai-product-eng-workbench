# Chat Requirement Plan Schema

The plan is a JSON object carrying chat target and window metadata with the extracted items.

```json
{
  "chatTargetType": "GROUP",
  "chatTargetId": "cidDQgPy3rWy2QsqTNUkFaJcA==",
  "chatTargetName": "产品需求反馈群",
  "windowStart": "2026-10-01 00:00:00",
  "windowEnd": "2026-10-24 23:59:59",
  "sourceRef": "dingtalk:GROUP:cidDQgPy3rWy2QsqTNUkFaJcA==:2026-10-01 00:00:00~2026-10-24 23:59:59",
  "requirements": [
    {
      "title": "报表导出支持异步与分片",
      "rawText": "[2026-10-20 10:12:03] 张敏: 百万行导出现在会超时，希望改成异步并支持分片。\n[2026-10-20 10:15:40] 李强: 确认，先保证 50 万行以上可用。",
      "normalizedText": "背景：大数据量报表导出耗时过长。\n问题：导出超过 50 万行时会超时，业务无法拿到结果。\n目标：支持异步导出与分片处理，提升大数据量可用性。\n范围：导出服务、异步任务、分片合并。\n约束：不改动现有报表模板结构；具体性能指标待确认。\n验收标准：大数据量导出不再因同步等待超时；任务状态可查询；失败可重试。",
      "requirementType": "IMPROVEMENT",
      "priority": "P1",
      "rationale": "张敏在 2026-10-20 10:12 反馈百万行导出超时，李强 10:15 确认 50 万行以上必须可用；影响核心报表场景，故定为功能优化 P1。",
      "sourceMessageIds": ["msgYjbNVcdXEDgp/XJyrFPn5w=="]
    }
  ]
}
```

## Field rules

| Field | Required | Limit | Rule |
|---|---|---|---|
| `chatTargetType` | yes | — | `GROUP` or `USER` |
| `chatTargetId` | yes | 200 | Group openConversationId or person userId returned by the reader |
| `chatTargetName` | recommended | 200 | Group title or person name |
| `windowStart` | yes | 19 | `yyyy-MM-dd HH:mm:ss` |
| `windowEnd` | yes | 19 | `yyyy-MM-dd HH:mm:ss` |
| `sourceRef` | no | 1000 | Defaults to a derived DingTalk target and window reference |
| `requirements[].title` | yes | 200 | Specific, 6–40 Chinese characters |
| `requirements[].rawText` | yes | 20000 | Quoted messages with sender and time |
| `requirements[].normalizedText` | yes | 20000 | Must contain 背景, 问题, 目标, 范围, 约束, 验收标准 |
| `requirements[].requirementType` | yes | — | `FEATURE`, `IMPROVEMENT`, or `BUG` |
| `requirements[].priority` | yes | — | `P0`, `P1`, `P2`, or `P3` |
| `requirements[].rationale` | yes | 2000 | Evidence-based reason with sender and time |
| `requirements[].sourceMessageIds` | yes | 50 entries | Real `openMessageId` values from the fetched window |

## Publishing behaviour

- `sourceType` is always `DINGTALK`.
- Each `sourceMessageIds` entry is copied from `messages[].id` in the reader output; never invent IDs.
- The idempotency key is `dingtalk-<type>-<targetId>-<sha1(sorted sourceMessageIds + title + normalizedText)[:16]>`.
- The publisher creates the requirement in `EXTRACT` (待提取) and preserves the plan's normalized text, type, and priority for product-manager confirmation.
- Use `--auto-confirm` only when the user explicitly asks to push items directly into the pool (`POOL`).
- A plan item with no actionable requirement must be omitted, not published with placeholder content.
