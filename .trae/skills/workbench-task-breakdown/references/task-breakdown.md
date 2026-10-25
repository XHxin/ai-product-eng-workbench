# Task Breakdown Guide

Turn a final PRD and the current codebase into development tasks that a PM can confirm and a developer can execute without re-reading the whole PRD.

## Task granularity

A good task:

- Delivers one verifiable capability, not a layer.
- Can be finished within about 0.5 to 3 developer days.
- Names its files, endpoints, tables, or screens in `scope`.
- Has acceptance criteria that a tester can run.
- Lists real dependencies, not generic "after backend".

Bad tasks:

- "完成注册功能" - too large, no boundary.
- "改后端" / "改前端" - layer slices that cannot be verified alone.
- "优化代码" - no observable outcome.
- One task per file - too small, creates coordination overhead.

## Decomposition axes

Walk these axes in order and keep only the ones the PRD actually needs:

1. Data and schema: tables, columns, unique indexes, migrations, seed data.
2. Backend domain: entities, repositories, services, state transitions, transactions.
3. Backend API: endpoints, validation, error codes, permission checks, CSRF.
4. External integration: SMS, AI, DingTalk, webhook, timeout and fallback.
5. Frontend page: route, layout, form, validation, states, error display.
6. Frontend integration: API client, store, permission-aware buttons, empty/loading states.
7. Audit and observability: audit events, logs, metrics, masking.
8. Testing: unit, integration, end-to-end, migration verification.
9. Rollout: feature toggle, config, deployment, rollback.

Skip an axis when the PRD has no change there. Do not invent tasks to fill a template.

## Coverage matrix

Before writing tasks, build a matrix in the working notes:

| PRD item | Task IDs |
|---|---|
| FR-001 | T-01, T-05 |
| BR-004 | T-02 |
| AC-003 | T-07 |
| Open question | blocked |

Every functional requirement, business rule, and acceptance criterion must map to at least one task, or be explicitly marked as out of scope with a reason.

## Task fields

The workbench accepts exactly these fields per task:

| Field | Required | Limit | Meaning |
|---|---|---|---|
| `title` | yes | 200 chars | One capability, verb first |
| `scope` | yes | 10000 chars | What to change: modules, files, endpoints, tables, pages |
| `acceptance` | yes | 10000 chars | Observable, testable result |
| `dependencies` | no | 2000 chars | Upstream task titles, PRD sections, external systems |

`acceptance` should use observable statements, for example:

- `POST /api/registrations` returns 201 and persists `app_users` with `status=PENDING_REVIEW`.
- Duplicate phone returns 409 with code `PHONE_ALREADY_REGISTERED`.
- The registration page blocks submit until the agreement is checked.
- Audit log contains `REGISTER_SUBMITTED` and never contains the verification code.

## Plan file schema

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

- `iterationId` must match the target iteration.
- `requirementId` must belong to that iteration.
- Titles are the idempotency key: the script skips a task when the same requirement already has a task with the same title.

## Dependency rules

- Prefer referencing task titles that appear earlier in the same plan.
- Use `schema -> domain -> API -> frontend integration -> page -> e2e` as the default order.
- Keep cross-requirement dependencies explicit, for example `依赖 REQ-2 / 短信服务接入`.
- Never create a circular dependency.

## Anti-patterns

- Copying PRD sections into `scope` without pointing to code.
- Writing acceptance as "功能正常" or "符合需求".
- Splitting by frontend/backend when the capability cannot be tested separately.
- Creating tasks for unconfirmed `[OPEN]` rules. Block them and report the open question instead.
- Creating the same task again with a slightly different title to bypass deduplication.

## Final check

- [ ] Every acceptance criterion maps to a task.
- [ ] No task exceeds the 3-day size.
- [ ] Each task names concrete files, endpoints, tables, or screens.
- [ ] Dependencies are acyclic and ordered.
- [ ] Unconfirmed rules are marked blocked, not implemented.
- [ ] Existing tasks were reviewed to avoid duplicates.
