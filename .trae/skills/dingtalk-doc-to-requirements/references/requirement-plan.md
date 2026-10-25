# Document Requirement Plan Schema

The plan is a JSON object carrying document metadata with the extracted items.

```json
{
  "documentTitle": "AI产研协作工作台需求文档",
  "documentNodeId": "LeBq413JAYYRdnnPFqgnbqZeJDOnGvpb",
  "documentUrl": "https://alidocs.dingtalk.com/i/nodes/LeBq413JAYYRdnnPFqgnbqZeJDOnGvpb",
  "sourceKind": "DOCUMENT",
  "sourceRef": "https://alidocs.dingtalk.com/i/nodes/LeBq413JAYYRdnnPFqgnbqZeJDOnGvpb",
  "requirements": [
    {
      "title": "需求池支持按迭代锁定范围",
      "rawText": "## 需求池与迭代规划\n已锁定迭代不允许继续纳入需求。",
      "normalizedText": "背景：需求需要按迭代规划并锁定范围。\n问题：未锁定范围时需求范围可能持续变化，导致 PRD 和任务反复调整。\n目标：锁定迭代后禁止继续纳入需求，并阻止生成正式 PRD。\n范围：需求池、迭代规划、PRD 生成门禁。\n约束：产品经理确认锁定后不可直接撤销；具体解绑流程待确认。\n验收标准：锁定迭代不能再纳入需求；未锁定迭代不能生成正式 PRD；锁定操作可追溯。",
      "requirementType": "FEATURE",
      "priority": "P1",
      "rationale": "文档「需求池与迭代规划」章节明确要求锁定迭代范围；该门禁影响 PRD 和任务准确性，故定为新功能 P1。",
      "sourceQuotes": ["已锁定迭代不允许继续纳入需求。"]
    }
  ]
}
```

## Field rules

| Field | Required | Limit | Rule |
|---|---|---|---|
| `documentTitle` | yes | 200 | Document or AI table title |
| `documentNodeId` | yes | 200 | Document nodeId or AI table baseId |
| `documentUrl` | recommended | 1000 | Used as the default `sourceRef` |
| `sourceKind` | recommended | 20 | `DOCUMENT` or `AITABLE` |
| `tableId` | for AITABLE | 200 | Data table ID from the reader |
| `tableName` | for AITABLE | 200 | Data table name |
| `sourceRef` | no | 1000 | Defaults to the document URL or `dingtalk-doc:<nodeId>` |
| `requirements[].title` | yes | 200 | Specific, 6–40 Chinese characters |
| `requirements[].rawText` | yes | 20000 | Quoted document passages or AI table row values |
| `requirements[].normalizedText` | yes | 20000 | Must contain 背景, 问题, 目标, 范围, 约束, 验收标准 |
| `requirements[].requirementType` | yes | — | `FEATURE`, `IMPROVEMENT`, or `BUG` |
| `requirements[].priority` | yes | — | `P0`, `P1`, `P2`, or `P3` |
| `requirements[].rationale` | yes | 2000 | Evidence-based reason with section or record reference |
| `requirements[].sourceQuotes` | for DOCUMENT | 20 entries, 2000 chars each | Exact quotes from the document |
| `requirements[].sourceRecordIds` | for AITABLE | 50 entries | Real record IDs from `recordMap` |

## Publishing behaviour

- `sourceType` is `DOCUMENT`.
- The idempotency key is `dingtalk-doc-<nodeId>-<sha1(tableId + traceKey + title + normalizedText)[:16]>`, where `traceKey` uses sorted `sourceRecordIds` for an AI table or `sourceQuotes` for a document.
- By default the publisher creates the requirement and immediately confirms it into the pool with the plan's type, priority, and rationale.
- With `--no-auto-confirm`, the requirement stays in `EXTRACT` (待提取) for product-manager review.
- A plan item with no actionable requirement must be omitted, not published with placeholder content.
- For AI tables, skip records already marked completed or online unless the user explicitly asks to import historical records.
