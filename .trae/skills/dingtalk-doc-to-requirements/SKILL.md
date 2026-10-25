---
name: dingtalk-doc-to-requirements
description: Read a DingTalk online document or AI table, extract requirements, and publish them to the AI Workbench pool via API. Use for 钉钉文档/多维表转需求 or 录入需求池. Not for editing or non-document files.
---

# DingTalk Document to Requirements

Convert a specified DingTalk online document (adoc) into structured requirements and publish them to the AI Workbench requirement pool.

## Preconditions

- The DingTalk CLI `dws` is available and authenticated. Check with `dws auth status --format json`.
- Workbench credentials are supplied through environment variables; never hardcode or echo them.
  - `AI_WORKBENCH_BASE_URL` (default `http://106.53.107.222`)
  - `AI_WORKBENCH_USERNAME` (must have `PM` or `ADMIN`)
  - `AI_WORKBENCH_PASSWORD`
- The target is a DingTalk online document or AI table, identified by full alidocs URL, node ID, or exact title.
- Share short links containing `/i/p/` cannot be read by this skill. Ask for a node URL or the exact document title instead.
- This skill supports `contentType=ALIDOC` with `extension=adoc` (online document) or `extension=able` (AI table). Online sheets (`axls`) and uploaded files (`xlsx`, `pdf`, `docx`) are out of scope.

## Workflow

Run every command from this skill directory (the directory containing this `SKILL.md`).

### 1. Resolve the document

By title:

```bash
python scripts/fetch_doc.py --title "<exact document title>" --resolve-only
```

By node ID or URL:

```bash
python scripts/fetch_doc.py --node-id "<nodeId>" --resolve-only
python scripts/fetch_doc.py --url "https://alidocs.dingtalk.com/i/nodes/<nodeId>" --resolve-only
```

- Exit code `2` means not found. Report that and stop.
- Exit code `3` means multiple candidates. Show the candidates and ask the user to choose. Re-run with `--node-id`.
- Do not invent node IDs; use only values returned by `dws`.

### 2. Read the document or AI table

```bash
python scripts/fetch_doc.py --title "<exact title>" --max-chars 30000 --out doc.json
```

For an AI table, also bound the rows:

```bash
python scripts/fetch_doc.py --title "<exact title>" --max-records 200 --out doc.json
```

- The reader returns Markdown plus metadata (`nodeId`, `title`, `docUrl`, `sourceKind`).
- `sourceKind=DOCUMENT` means an online document. `sourceKind=AITABLE` means an AI table; the output also includes `recordMap` with each record ID and title.
- For AI tables, use `--table-id` when the base has multiple tables and the user named one.
- `--max-chars` defaults to 30000 and must be at least 2000.
- `--max-records` defaults to 200 and is capped at 1000.
- If `truncated` is true, say so explicitly. Extract only from the returned portion and do not assume the missing content.

### 3. Extract requirements with AI

Read `doc.json` and produce a requirement plan. Write it to a temporary JSON file outside the repository.

Rules:

- If the document is a requirement list or PRD, treat each independent requirement, feature, or defect as a separate item. Do not turn headings, background, glossary, or appendices into requirements unless they contain an explicit ask.
- For an AI table, treat each record as a requirement candidate. Preserve its 需求编号, 需求标题, 需求状态, 需求来源, 需求描述, 需求评估说明, 跟进人, and 期望上线时间 when present. Skip records that are explicitly completed or online unless the user asks to import history.
- Merge duplicate descriptions of the same outcome.
- Online documents must cite at least one exact quote in `sourceQuotes`; AI table items must cite at least one real `recordId` in `sourceRecordIds`. Include the section heading or record title in `rationale`.
- Do not invent scope, metrics, owners, or decisions absent from the source. Record uncertainty as an assumption inside `normalizedText`.
- `title` is 6–40 Chinese characters, specific and free of filler.
- `normalizedText` must contain these labelled sections: 背景, 问题, 目标, 范围, 约束, 验收标准. Use "待确认" where the document does not settle it.
- `rawText` must quote the relevant document passages or AI table row values with their headings, not a paraphrase.
- `requirementType` is one of `FEATURE`, `IMPROVEMENT`, `BUG`.
- `priority` is one of `P0`, `P1`, `P2`, `P3`. Preserve a priority stated in the document; otherwise derive it from user impact and dependency risk and explain the choice.
- If no actionable requirement exists, report that and do not publish.

See `references/requirement-plan.md` for the exact JSON schema and an example.

### 4. Preview, then publish

Always preview first:

```bash
python scripts/publish_requirements.py --plan plan.json --dry-run
```

Show the requirement count, titles, types, priorities, and target base URL. Unless the user asked only for extraction, continue:

```bash
python scripts/publish_requirements.py --plan plan.json
```

The script logs in with CSRF protection, creates each requirement with `sourceType=DOCUMENT`, and by default confirms it into the pool. Use `--no-auto-confirm` to keep items in 待提取 for product-manager review.

Idempotency keys use the node ID, table ID when present, cited quotes or record IDs, and the requirement content. Re-running the same plan reuses existing requirements instead of duplicating them.

### 5. Report results

Report:

- document or AI table title, node ID, and URL
- source kind (`DOCUMENT` or `AITABLE`), table name if applicable
- whether the content or rows were truncated
- number of requirements created or reused
- mapping of requirement ID, title, type, priority, final status
- any failed item with the server error message

If publishing fails, keep the plan file path and the exact failing step. Do not claim success for failed items.

## Safety

- Never print `AI_WORKBENCH_PASSWORD`, session cookies, or CSRF tokens.
- Never modify the document; this skill is read-only on DingTalk.
- Do not publish when the user only asked to read or summarize the document.
- Do not read another organization's documents without confirming the target organization.
- The default base URL is HTTP. Warn once when credentials would cross a public network without TLS.

## Failure handling

- `dws` authentication error: run `dws auth status --format json`, then ask the user to authenticate. Do not retry more than once.
- Share short link (`/i/p/`): explain that it cannot be resolved by `dws` and ask for a node link or exact title.
- Unsupported type (`axls`, `able`, `xlsx`, `pdf`, `docx`): report the detected type and stop; do not silently switch products.
- Workbench `401`: re-check credentials. `403`: the account lacks PM or ADMIN role. `409`: report the existing workflow state instead of forcing an update.
- Network error: report the base URL and stop. Do not retry writes automatically; the idempotency key makes a manual re-run safe.
