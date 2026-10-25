---
name: dingtalk-minutes-to-requirements
description: Read a DingTalk AI minutes by title, extract requirements, and create them in the AI Workbench 待提取 list (需求录入页面) via API. Use for 听记 or 会议纪要转需求 or 录入需求池. Do not use for unrelated minutes reads.
---

# DingTalk Minutes to Requirements

Convert a specified DingTalk AI minutes into structured requirements and publish them to the AI Workbench requirement pool.

## Preconditions

- The DingTalk CLI `dws` is available and authenticated. Check with `dws auth status --format json`.
- Workbench credentials are supplied through environment variables; never hardcode or echo them.
  - `AI_WORKBENCH_BASE_URL` (default `http://106.53.107.222`)
  - `AI_WORKBENCH_USERNAME` (must have `PM` or `ADMIN`)
  - `AI_WORKBENCH_PASSWORD`
- The target minutes title is known. If the user gives only a UUID or share URL, resolve it with `dws minutes list all --format json` first.

If any precondition is missing, report the exact missing item and stop.

## Workflow

Run every command from this skill directory (the directory containing this `SKILL.md`).

### 1. Locate and read the minutes

Run:

```bash
python scripts/fetch_minutes.py --title "<minutes title>" --max-chars 12000 --out minutes.json
```

- Exit code `2` means not found; report that and stop.
- Exit code `3` means multiple exact matches; show the candidates and ask the user to choose one. Re-run with `--id <uuid>`.
- Read `summary` from the JSON. The script truncates long content; increase `--max-chars` only when needed.
- Add `--include-transcript` when the summary omits decisions, constraints, or acceptance criteria. Keep `--transcript-chars` bounded (default 20000).

### 2. Extract requirements with AI

Read the summary and produce a requirement plan using the rules below. Write it to a temporary JSON file outside the repository. Do not publish raw minutes verbatim as requirements.

Rules:

- Include only actionable product or engineering requirements. Ignore greetings, scheduling, and unresolved small talk.
- One requirement per independent outcome. Merge duplicates; do not split a single outcome into fragments.
- Every requirement must be traceable to the minutes. Put the supporting quote or time range in `rationale`.
- Do not invent scope, metrics, or decisions that are absent. Record uncertainty as an assumption inside `normalizedText` instead of presenting it as confirmed.
- `title` is 6–40 Chinese characters, specific and free of filler.
- `normalizedText` must contain these labelled sections: 背景, 问题, 目标, 范围, 约束, 验收标准. Use "待确认" where the minutes do not settle it.
- `rawText` must be the relevant excerpt from the minutes that supports this requirement, not a paraphrase.
- `requirementType` is one of `FEATURE`, `IMPROVEMENT`, `BUG`.
- `priority` is one of `P0`, `P1`, `P2`, `P3`, based on user impact, urgency, and dependency risk stated in the minutes.
- `rationale` states why the type and priority were chosen and cites the evidence.

See `references/requirement-plan.md` for the exact JSON schema and a complete example.

### 3. Preview, then publish

Always preview first:

```bash
python scripts/publish_requirements.py --plan plan.json --dry-run
```

Show the user the requirement count, titles, types, priorities, and target base URL. Unless the user explicitly asked only for extraction, continue with the real run:

```bash
python scripts/publish_requirements.py --plan plan.json
```

The script logs in with CSRF protection and creates each requirement in 待提取 (`EXTRACT`) with the AI-extracted text, type, and priority preserved for product-manager confirmation. Use `--auto-confirm` only when the user explicitly asks to push items directly into the pool.

The script uses deterministic idempotency keys derived from the minutes UUID and requirement content. Re-running the same plan returns existing requirements instead of creating duplicates.

### 4. Report results

Report:

- minutes title and UUID
- number of requirements created or reused
- mapping of requirement ID, title, type, priority, final status
- any item that failed, with the server error message

If publishing fails, keep the plan file path and the exact failing step. Do not claim success for failed items.

## Safety

- Never print `AI_WORKBENCH_PASSWORD`, session cookies, or CSRF tokens.
- Do not publish when the user only asked to read or summarize the minutes.
- Do not modify the minutes. Read-only calls only.
- The default base URL is HTTP. Warn once when credentials would cross a public network without TLS.
- Do not create requirements from another organization's minutes without confirming the target organization.

## Failure handling

- `dws` authentication error: run `dws auth status --format json`, then ask the user to authenticate. Do not retry more than once.
- Workbench `401`: re-check credentials. `403`: the account lacks PM or ADMIN role. `409`: treat as an existing workflow state and report the message rather than forcing an update.
- Network error: report the base URL and stop. Do not retry writes automatically; the idempotency key makes a manual re-run safe.
