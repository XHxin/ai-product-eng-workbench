---
name: dingtalk-chat-to-requirements
description: Read DingTalk group or direct messages, extract requirements, and publish them to the AI Workbench pool via API. Use for 聊天记录转需求 or 群聊/单聊录入需求池. Not for unrelated sends.
---

# DingTalk Chat to Requirements

Convert a bounded window of DingTalk chat messages into structured requirements and publish them to the AI Workbench requirement pool.

## Preconditions

- The DingTalk CLI `dws` is available and authenticated. Check with `dws auth status --format json`.
- Workbench credentials are supplied through environment variables; never hardcode or echo them.
  - `AI_WORKBENCH_BASE_URL` (default `http://106.53.107.222`)
  - `AI_WORKBENCH_USERNAME` (must have `PM` or `ADMIN`)
  - `AI_WORKBENCH_PASSWORD`
- The target is clear: a group name, group ID, person name, or user ID. If the user gives only a vague description, ask for the exact group or person.
- A time window is required. Default to the last 7 days only when the user accepts that default; otherwise use the window the user specifies.

`dws chat search` searches conversation names only. To resolve a person, use `dws contact user search`.

## Workflow

Run every command from this skill directory (the directory containing this `SKILL.md`).

### 1. Resolve the target

Resolve a group:

```bash
python scripts/fetch_chat.py --group "<group name>" --resolve-only
```

Resolve a person:

```bash
python scripts/fetch_chat.py --user "<person name>" --resolve-only
```

- Exit code `2` means not found. Report that and stop.
- Exit code `3` means multiple candidates. Show the candidates and ask the user to choose. Re-run with `--group-id` or `--user-id`.
- Use the returned `target.id` for later troubleshooting; do not invent IDs.

### 2. Read a bounded message window

Group:

```bash
python scripts/fetch_chat.py --group "<group name>" --start "2026-10-01 00:00:00" --end "2026-10-24 23:59:59" --max-messages 100 --out chat.json
```

Direct chat with a person:

```bash
python scripts/fetch_chat.py --user "<person name>" --start "2026-10-01 00:00:00" --end "2026-10-24 23:59:59" --max-messages 100 --out chat.json
```

Rules:

- `--user` reads the 1:1 conversation with that person, not their messages across other groups.
- Read only the specified target and window. Do not scan other conversations.
- `--max-messages` defaults to 100 and is capped at 300.
- The reader pulls backwards from `--end`, so the most recent messages in the window are retained if the cap is reached.
- Inspect `messageCount`, `truncated`, and `contentTruncated`. If truncated, narrow the window or raise the cap before extracting requirements.
- Ignore messages with empty content. Keep sender, time, and message ID for traceability.

### 3. Extract requirements with AI

Read `chat.json` and produce a requirement plan. Write it to a temporary JSON file outside the repository.

Rules:

- Include only actionable product or engineering requirements, bug reports, or explicit change requests.
- Ignore greetings, scheduling, approvals unrelated to the product, and unresolved chatter.
- Merge repeated asks from the same thread into one requirement. Do not create one requirement per message.
- Every requirement must cite at least one real message ID in `sourceMessageIds`, with sender and time in `rationale`.
- Do not invent scope, metrics, owners, or decisions absent from the messages. Record uncertainty as an assumption inside `normalizedText`.
- `title` is 6–40 Chinese characters, specific and free of filler.
- `normalizedText` must contain these labelled sections: 背景, 问题, 目标, 范围, 约束, 验收标准. Use "待确认" where the chat does not settle it.
- `rawText` must quote the relevant messages with sender and time, not a paraphrase.
- `requirementType` is one of `FEATURE`, `IMPROVEMENT`, `BUG`.
- `priority` is one of `P0`, `P1`, `P2`, `P3`, based on user impact, urgency, and dependency risk stated in the chat.
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

The script logs in with CSRF protection, creates each requirement, and by default confirms it into the pool. Use `--no-auto-confirm` to keep items in 待提取 for product-manager review.

Idempotency keys use the target ID, the cited message IDs, and the requirement content. Re-running the same plan reuses existing requirements instead of duplicating them.

### 5. Report results

Report:

- target type and name
- message window and number of messages read
- number of requirements created or reused
- mapping of requirement ID, title, type, priority, final status
- any failed item with the server error message

If publishing fails, keep the plan file path and the exact failing step. Do not claim success for failed items.

## Safety

- Never print `AI_WORKBENCH_PASSWORD`, session cookies, or CSRF tokens.
- Never send, reply to, recall, or react to messages; this skill is read-only on DingTalk.
- Do not publish when the user only asked to read or summarize chats.
- Do not read another organization's conversations without confirming the target organization.
- The default base URL is HTTP. Warn once when credentials would cross a public network without TLS.

## Failure handling

- `dws` authentication error: run `dws auth status --format json`, then ask the user to authenticate. Do not retry more than once.
- Group or person not found: report the query and stop. Do not guess a similar name.
- Workbench `401`: re-check credentials. `403`: the account lacks PM or ADMIN role. `409`: report the existing workflow state instead of forcing an update.
- Network error: report the base URL and stop. Do not retry writes automatically; the idempotency key makes a manual re-run safe.
