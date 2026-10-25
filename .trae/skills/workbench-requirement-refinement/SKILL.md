---
name: workbench-requirement-refinement
description: Refine a workbench iteration requirement via code and database research plus DingTalk PM questioning, then publish a spec and prototype. Use for 迭代需求细化 or 需求调研. Not for unrelated coding.
---

# Workbench Requirement Refinement

Turn a thin workbench iteration requirement into a closed-loop specification and a styled prototype. Ask the product manager only after researching the current project.

## Inputs

Required:

- Iteration: ID or exact name.
- Product manager name: used to resolve the DingTalk contact.

Optional:

- Requirement ID. If omitted, refine all requirements in the iteration, one at a time.
- Output directory. Default: `.trae/outputs/requirement-refinement/<iterationId>-<requirementId>/`.

Ask for missing inputs in one message. Do not guess an iteration, requirement, or product manager when names are ambiguous.

## Phase 0 - Resolve workbench objects

Run commands from this skill directory.

```bash
python scripts/workbench_api.py iterations
python scripts/workbench_api.py requirements --iteration-id <id>
python scripts/workbench_api.py requirement --id <requirementId>
python scripts/workbench_api.py records --requirement-id <requirementId> --category PRD
```

If only an iteration name is given, list iterations and require an exact name match. If multiple match, ask. Never invent IDs.

## Phase 1 - Research before asking

Do all of the following before drafting questions.

### Documents

- Read the workspace design document `AI产研协作工作台_系统详细设计方案.docx` using the docx skill's text-extraction workflow.
- Read `AI产研协作工作台_原型_V2.html` and any PRD/spec files in the workspace.
- Read `references/workbench-context.md` for current entities, API paths, and frontend design tokens.

### Database

Use the MySQL MCP read-only tools (`get_schema_info`, `get_table_sample`, `execute_sql` with SELECT only).

- Inspect `requirements`, `iterations`, `workflow_records`, `audit_logs`, `release_records`, and any related tables.
- Confirm columns, statuses, foreign keys, unique constraints, and audit fields.
- Do not write, alter, or export bulk data.

### Code

- Backend: `ai-workbench-backend/src/main/java/com/aiworkbench/backend/` - controllers, services, entities, migrations.
- Frontend: `ai-workbench-frontend/src/` - views, API client, store, and `style.css` for design tokens.
- Read the implementation path that handles the target requirement, its state transitions, and its tests.
- Treat code as the current implementation, not automatically as the target business rule.

### Evidence

Record findings as:

- `[FACT]` verified in document, database, code, or API.
- `[INFERENCE]` derived but not confirmed.
- `[OPEN]` requires PM decision.
- `[CONFLICT]` sources disagree.

Never write an inference or open question as a confirmed rule.

## Phase 2 - Find the gaps

Read `references/grill-me.md` and build a gap list covering:

- business goal and background
- roles and permissions
- trigger and preconditions
- inputs and validation
- normal, branch, and exception flows
- state transitions and illegal transitions
- data creation, update, deletion, and audit
- concurrency, retry, and idempotency
- external dependencies and async handling
- acceptance criteria
- dependencies on other requirements
- explicit out-of-scope items

Only ask about gaps that cannot be answered from the evidence.

## Phase 3 - Grill the product manager over DingTalk

### Resolve the contact

```bash
python scripts/pm_chat.py resolve --name "<PM name>"
```

If the result is ambiguous or not found, show candidates and stop. Do not send to a guessed contact.

### Draft one message with related questions

Follow `references/grill-me.md`:

- Group related questions in one message to reduce waiting rounds.
- Use numbered questions, options, a recommendation, and impact.
- Ask for a compact reply format such as `1-A, 2-B, 3-补充说明`.
- Prefer 5-8 high-value questions per round; do not dump the entire gap list.
- State known facts and why each decision is needed.
- Never include credentials or unrelated sensitive data.

Write the message to a temporary UTF-8 text file. Use one state file per requirement:

```text
.trae/state/requirement-refinement/<iterationId>-<requirementId>.json
```

Preview first:

```bash
python scripts/pm_chat.py send --pm-name "<PM name>" --text-file "<questions.txt>" --title "需求调研：<requirement title>" --state-file "<state.json>"
```

Show the preview to the user. Send only after explicit approval, then re-run with `--confirm`. Sending a real DingTalk message is a high-impact action.

When the invoking request explicitly says to send the questions (for example "发送消息给产品经理并根据回复完善"), that request is the approval. Show the drafted message, then send with `--confirm` in the same run. Do not stop at a preview-only draft; a draft-only run is incomplete.

Completion of the grill phase requires: message sent, replies polled, and either a follow-up round or a reported `WAITING_FOR_REPLY` state. Do not claim the requirement was refined by the PM without a reply.

### Wait without blocking

```bash
python scripts/pm_chat.py poll --state-file "<state.json>"
python scripts/pm_chat.py status --state-file "<state.json>"
```

- A poll checks only the direct chat with the resolved PM.
- If there is no reply, report `WAITING_FOR_REPLY`, list the open questions, and end the current run.
- On the next run, poll first before sending anything. Do not repeat questions already answered.
- Do not loop indefinitely and do not claim the PM replied unless `poll` returns messages.
- Parse replies, update decisions, identify new gaps, and send a focused follow-up round only when needed.

A send that returns `messageId: null` is still a real send. Confirm delivery before reporting success:

```bash
dws chat message list-direct --user <userId> --time "<sentAt>" --forward=true --limit 20 --format json
```

### Apply a reply and publish a new version

When `poll` returns messages:

1. Map each answer to its stable question ID and record the reply quote and reply time as a decision.
2. Rewrite `requirement-spec.md`: update 业务规则, 决策记录, 未决事项, 验收标准, and document status. Remove only the `[OPEN]` tags that the reply actually answers.
3. Regenerate `prototype.html` if any confirmed rule changes a flow, state, copy, validation, or error message.
4. Upload a new version with `workbench_api.py prd`. Every upload creates a new version; never overwrite the previous one.
5. Verify `records --category PRD` returns the new highest `versionNumber`.
6. Report the new version number and the decisions applied.

Do not stop after updating local files. A reply that is not reflected in a newly uploaded PRD version has not completed the loop.

## Phase 4 - Produce the specification and prototype

When the key gaps are answered, or when the user asks for a draft with explicit open items:

- Write the Markdown specification using `references/spec-template.md`.
- Set document status to `DRAFT`, `PENDING_CONFIRMATION`, `READY_FOR_REVIEW`, or `CONFIRMED` based on actual PM decisions. Never mark `CONFIRMED` without PM confirmation.
- Include evidence IDs, decision IDs, open questions, Mermaid flow/state diagrams, roles, data rules, exceptions, and Given-When-Then acceptance criteria.
- Build a single-file prototype HTML with inline CSS/JavaScript. Use the current frontend style: deep purple sidebar, white cards, `--brand:#4f46e5`, rounded 10-14px, chip/table/drawer patterns from `ai-workbench-frontend/src/style.css`.
- The prototype must reflect the confirmed flow and states. Mark unconfirmed areas visibly. Do not use external assets or fake integration success.

Default output:

```text
.trae/outputs/requirement-refinement/<iterationId>-<requirementId>/
├── requirement-spec.md
└── prototype.html
```

## Phase 5 - Upload back to the workbench

Check the iteration and requirement state:

```bash
python scripts/workbench_api.py iteration --id <iterationId>
python scripts/workbench_api.py requirement --id <requirementId>
```

- The iteration must be `LOCKED`. If it is not, stop and ask the PM to lock it. Do not lock automatically.
- The requirement status must allow a PRD: `ITERATION`, `CONFIRMATION`, or `REVIEW`.
- Preview the upload, then upload:

```bash
python scripts/workbench_api.py prd --requirement-id <requirementId> --markdown-file "<spec.md>" --html-file "<prototype.html>" --dry-run
python scripts/workbench_api.py prd --requirement-id <requirementId> --markdown-file "<spec.md>" --html-file "<prototype.html>"
```

Verify with `records --category PRD` and report the new PRD ID, version, and status.

If the PM replies, update the specification and status, then upload a new version. If there is no reply yet, keep the current DRAFT version, report `WAITING_FOR_REPLY`, and do not pretend the requirement is confirmed.

The workbench 需求分析 page shows each PRD version under 历史版本 with 需求文档 and 原型预览 tabs. To confirm visibility: open 需求分析, select the iteration and requirement, then use the 最新 PRD V(version) button in the page header (or 查看 on a version row), and switch to 原型预览. Report the page path and the version number you verified.

## Quality gate

Before declaring completion:

- [ ] Iteration and requirement identity verified through the API.
- [ ] Design docs, MySQL schema, and relevant code reviewed.
- [ ] Existing PRD versions reviewed.
- [ ] Key gaps either answered by the PM or marked `[OPEN]`.
- [ ] Product manager identity confirmed; no guessed contact.
- [ ] Every sent question has a recorded send time and state.
- [ ] DingTalk delivery verified, even when `messageId` is null.
- [ ] PM replies applied to the specification and uploaded as a new PRD version.
- [ ] Specification is internally consistent and has no flow/state break.
- [ ] Acceptance criteria are observable and testable.
- [ ] Prototype matches the current frontend design language.
- [ ] Upload verified through the PRD records API.
- [ ] No unconfirmed inference is presented as a decision.

## Safety

- Never print `AI_WORKBENCH_PASSWORD`, cookies, or CSRF tokens.
- Never write to the production database directly; persistence goes through the workbench API.
- Do not send DingTalk messages without user approval of the drafted content.
- Do not mark a requirement confirmed without an explicit PM reply.
- Do not modify the original requirement description unless the user explicitly asks.
- The default base URL is HTTP. Warn once when credentials cross a public network without TLS.

## Failure handling

- `dws` authentication error: run `dws auth status --format json` and ask the user to authenticate once.
- PM not found or ambiguous: show candidates and stop.
- Workbench `401`: check credentials. `403`: PM/ADMIN role required. `409`: report the state conflict instead of forcing an update.
- Iteration not locked: stop and request locking by the PM.
- No reply after polling: save state, report waiting, and end the run.
