---
name: workbench-task-breakdown
description: Read a workbench iteration with its requirements, PRDs and prototypes through the workbench API, analyze the current codebase, split the work into development tasks, and create task records through the workbench API. Use for 迭代任务拆分、研发任务录入 or PRD 转开发任务. Not for changing requirement status or approving reviews.
---

# Workbench Task Breakdown

Turn a locked iteration's requirements and final PRDs into development tasks that are grounded in the existing code and published to the workbench task module.

## Preconditions

- Workbench credentials come from environment variables; never hardcode or echo them.
  - `AI_WORKBENCH_BASE_URL` (default `http://106.53.107.222`)
  - `AI_WORKBENCH_USERNAME` (must have `ADMIN` or `PM`)
  - `AI_WORKBENCH_PASSWORD`
- The target iteration exists and is `LOCKED`.
- Each requirement to split must be `PRD_CONFIRMED` or `DEVELOPMENT` and have a PRD version with status `FINAL`.
- This skill does not approve reviews, change requirement status, or create PRDs. If a requirement is still `REVIEW` or its PRD is `DRAFT`, report it as blocked and stop for that requirement.

## Inputs

Required:

- Iteration: ID or exact name.
- Repositories to inspect: default `ai-workbench-backend/` and `ai-workbench-frontend/`.

Optional:

- Requirement IDs: split only these; default is every ready requirement in the iteration.
- Output directory: default `.trae/outputs/task-breakdown/<iterationId>/`.

Ask for missing or ambiguous inputs in one message. Never guess an iteration ID.

## Phase 0 - Resolve the iteration

Run commands from this skill directory.

```bash
python scripts/workbench_tasks.py iterations
python scripts/workbench_tasks.py resolve --name "<exact iteration name>"
```

- `not_found` or `ambiguous`: show candidates and stop.
- Keep the resolved iteration ID for the rest of the run.

## Phase 1 - Download the iteration bundle

```bash
python scripts/workbench_tasks.py bundle --iteration-id <id>
python scripts/workbench_tasks.py check --iteration-id <id>
```

`bundle` writes to `.trae/outputs/task-breakdown/<id>/`:

```text
bundle.json                     iteration, requirement summary, existing tasks
REQ-1-注册功能-prd.md            latest or FINAL PRD markdown
REQ-1-注册功能-prototype.html   PRD prototype HTML
```

When this skill is installed globally instead of inside the workspace repository, pass `--output-dir "<your workspace>/.trae/outputs/task-breakdown/<id>"` so artifacts land in the workspace.

Read every requirement's Markdown PRD and prototype. Use `bundle.json` for status, PRD version, blocking reasons and existing task titles.

If `check` reports `readyForTask: false`, list the blocking reasons and exclude that requirement. Do not lower the gate.

## Phase 2 - Research the current code

Before splitting tasks:

- Read the backend endpoints, services, entities and migrations that the PRD touches.
- Read the frontend views, components, API client, store and styles that the PRD touches.
- Read existing tests for the same area.
- Read `references/workbench-context.md` for API paths, statuses, permissions and task limits.
- Use the MySQL MCP read-only tools only when the schema is unclear.

Record evidence as `[FACT]`, `[INFERENCE]`, or `[OPEN]`. Never turn an inference into a task requirement.

Read `references/task-breakdown.md` before writing the plan.

## Phase 3 - Decompose into tasks

Build a coverage matrix from PRD items to task IDs:

- every `FR-*`, confirmed `BR-*`, and acceptance criterion
- every page, endpoint, table, migration, external integration, audit event and test layer that actually changes

Apply the granularity rules in `references/task-breakdown.md`:

- one verifiable capability per task
- about 0.5 to 3 developer days
- concrete files, endpoints, tables or screens in `scope`
- observable acceptance statements
- explicit, acyclic dependencies

Use the plan schema:

```json
{
  "iterationId": 1,
  "tasks": [
    {
      "requirementId": 1,
      "title": "新增 app_users 表与数据库认证",
      "scope": "ai-workbench-backend: Flyway 迁移、UserEntity、UserRepository、SecurityConfiguration",
      "acceptance": "迁移执行成功；手机号唯一索引生效；预置账号仍可登录。",
      "dependencies": ""
    }
  ]
}
```

Write the plan to `.trae/outputs/task-breakdown/<iterationId>/task-plan.json`.

Do not create tasks for `[OPEN]` rules. Report them as blocked scope instead.

## Phase 4 - Validate the plan

```bash
python scripts/workbench_tasks.py create-tasks --plan-file "<task-plan.json>"
```

This is a dry run. Check the output:

- `planned`: will be created.
- `skipped`: the requirement already has a task with the same title.
- `not_ready`: requirement status or PRD gate failed.
- `invalid`: schema or field limit violation.

Fix the plan and re-run until only acceptable `planned` and `skipped` items remain. Show the dry-run summary to the user.

## Phase 5 - Create the tasks

Creating task records changes shared workbench data. Use the same rule as other workbench skills: when the invoking request explicitly asks to create or publish the tasks, that request is the approval. Otherwise ask for confirmation first.

```bash
python scripts/workbench_tasks.py create-tasks --plan-file "<task-plan.json>" --confirm
```

Then verify:

```bash
python scripts/workbench_tasks.py tasks --iteration-id <id>
```

Report:

- created task IDs and titles per requirement
- skipped tasks and why
- blocked requirements and their missing gate
- the UI path: `研发任务` page, where new tasks appear as `待确认`

Created tasks are `DRAFT`. The PM must confirm them in the workbench before development starts; confirming a task moves its requirement to `DEVELOPMENT`.

## Quality gate

- [ ] Iteration resolved by exact name or explicit ID.
- [ ] Every ready requirement's PRD Markdown and prototype were read.
- [ ] Backend and frontend code paths were reviewed.
- [ ] Coverage matrix has no unmapped PRD item.
- [ ] Every task has title, scope, observable acceptance and valid dependencies.
- [ ] No task exceeds the size limit or mixes unrelated capabilities.
- [ ] Existing tasks were checked; duplicates are skipped.
- [ ] Blocked requirements are reported, not forced.
- [ ] Creation was verified through the task records API.

## Safety

- Never print `AI_WORKBENCH_PASSWORD`, cookies or CSRF tokens.
- Do not write to the production database directly.
- Do not approve PRDs, change requirement status, or confirm tasks on behalf of the PM.
- Do not create duplicate tasks by rewording titles.
- Warn once when credentials cross a public network over plain HTTP.

## Failure handling

- `MISSING_CREDENTIALS`: set the environment variables.
- `ROLE_REQUIRED`: use an `ADMIN` or `PM` account.
- `REQUIREMENT_NOT_READY`: report the requirement status and missing FINAL PRD; wait for review.
- `REQUIREMENT_NOT_IN_ITERATION`: fix the plan's `requirementId`.
- `ITERATION_NOT_FOUND`: re-resolve the iteration.
- `409` on create: report the state conflict instead of retrying blindly.
- `skipped` results are normal for reruns; do not force duplicates.
