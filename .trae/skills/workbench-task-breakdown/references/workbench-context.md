# Workbench Context

## Repositories

| Repo | Path | Stack |
|---|---|---|
| Backend | `ai-workbench-backend/` | Spring Boot 3.4.5, Java 21, MySQL 8.4, Flyway, Spring Security session + CSRF |
| Frontend | `ai-workbench-frontend/` | Vue 3, TypeScript, Vite, Pinia |

Base URL: `AI_WORKBENCH_BASE_URL`, default `http://106.53.107.222`.

## Roles

- `ADMIN`: all task, PRD, review, iteration operations.
- `PM`: same workflow permissions as ADMIN.
- `DEV`: can supplement development info and mark progress; cannot create tasks or review.
- `RELEASE_MANAGER`: release operations only.

The task script rejects accounts without `ADMIN` or `PM`.

## Requirement statuses

`EXTRACT → POOL → ITERATION → CONFIRMATION → REVIEW → PRD_CONFIRMED → DEVELOPMENT → COMPLETED`

Task creation is only allowed when the requirement is `PRD_CONFIRMED` or `DEVELOPMENT` **and** it has a PRD version with status `FINAL`.

When the requirement is `REVIEW` or the PRD is still `DRAFT`, stop and report the missing review. Do not approve the review on behalf of the PM.

## API endpoints used by this skill

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/iterations?page=0&size=50` | List iterations |
| GET | `/api/iterations/{id}` | Iteration detail |
| GET | `/api/iterations/{id}/requirements` | Requirements assigned to the iteration |
| GET | `/api/requirements/{id}` | Requirement detail |
| GET | `/api/requirements/{id}/records?category=PRD&page=0&size=50` | PRD versions |
| GET | `/api/requirements/{id}/records?category=TASK&page=0&size=50` | Existing tasks |
| POST | `/api/requirements/{id}/tasks` | Create a task (body: `title`, `scope`, `acceptance`, `dependencies`) |
| GET | `/api/records?category=TASK&page=0&size=100` | All tasks |

Write requests require the CSRF header returned by `GET /api/auth/csrf`. The script handles login, CSRF rotation and cookies.

## PRD payload

`workflow_records.payload` for `category=PRD` contains:

- `content`: Markdown specification.
- `htmlContent`: self-contained prototype HTML.
- `templateVersion` and `inputSnapshot` when present.

`bundle` writes these next to `bundle.json` so task decomposition can read them without extra calls.

## Task record

Creating a task returns a record with:

- `id`, `category=TASK`, `requirementId`, `iterationId`, `parentId` (final PRD id)
- `status`: `DRAFT`
- `payload`: `title`, `scope`, `acceptance`, `dependencies`
- `actor`, `createdAt`

Task lifecycle: `DRAFT` (created) → `CONFIRMED` (PM confirms; requirement enters `DEVELOPMENT`) → `IN_PROGRESS` (DEV adds hours, technical plan, subtasks, test points) → `DONE`.

## UI locations

- Requirements and status: `需求分析` page, top `状态流转` panel.
- PRD markdown and prototype: `需求分析` page, `最新 PRD` button or `历史版本`.
- Tasks: `研发任务` page.
- Iteration scope: `迭代规划` page.

## Database tables

- `requirements`, `iterations`
- `workflow_records` (PRD, REVIEW, TASK, DEVELOPMENT, CONFIRMATION, CONCLUSION)
- `audit_logs`, `release_records`, `integration_configs`

Read schema through the MySQL MCP read-only tools when needed. Do not write to the database directly.

## Code areas to inspect for task scoping

Backend:

- `src/main/java/com/aiworkbench/backend/api/` - controllers and request commands
- `src/main/java/com/aiworkbench/backend/service/` - state transitions and business rules
- `src/main/java/com/aiworkbench/backend/domain/` - entities and repositories
- `src/main/resources/db/migration/` - schema changes

Frontend:

- `src/views/` - page-level flows
- `src/components/` - drawers, modals, chips
- `src/api/` - client, endpoints, types
- `src/stores/` - shared state
- `src/style.css` - design tokens and layout

Tests:

- Backend: `src/test/java/com/aiworkbench/backend/`
- Frontend: `src/test/`, Playwright E2E in the frontend repo
