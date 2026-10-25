# Current Workbench Context

Verify against the latest code before relying on any detail. This file is a starting map, not the source of truth.

## Repositories

- Backend: `ai-workbench-backend` (Spring Boot 3.4, Java 21, MySQL, Flyway).
- Frontend: `ai-workbench-frontend` (Vue 3, TypeScript, Vite).
- Live URL: `http://106.53.107.222`.

## API endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/auth/csrf` | CSRF token |
| POST | `/api/auth/login` | Session login |
| GET | `/api/auth/me` | Current user and roles |
| GET | `/api/iterations` | Iteration page |
| GET | `/api/iterations/{id}` | Iteration detail |
| GET | `/api/iterations/{id}/requirements` | Requirements in an iteration |
| GET | `/api/requirements/{id}` | Requirement detail |
| GET | `/api/requirements/{id}/records?category=PRD` | PRD versions |
| POST | `/api/requirements/{id}/prd` | Upload PRD content and prototype HTML |

PRD upload requires a `LOCKED` iteration and requirement status `ITERATION`, `CONFIRMATION`, or `REVIEW`.

## Database tables

- `requirements`: requirement identity, raw and normalized text, type, priority, status, iteration.
- `iterations`: iteration name, dates, status.
- `workflow_records`: PRD, confirmation, review, task, development records with payload JSON.
- `release_records`: release reminder, confirmation, writeback, and notification states.
- `audit_logs`: action, actor, resource, details.
- `integration_configurations`: provider, endpoint, encrypted secret, model.

## Requirement statuses

`EXTRACT`, `POOL`, `ITERATION`, `CONFIRMATION`, `REVIEW`, `PRD_CONFIRMED`, `DEVELOPMENT`, `COMPLETED`.

## Frontend design tokens

Read `ai-workbench-frontend/src/style.css` before building the prototype. Key values:

- brand `#4f46e5`, deep sidebar `#1e1b4b` to `#312e81`
- background `#f6f7fb`, panel `#ffffff`, line `#e2e8f0`
- ink `#0f172a`, secondary `#334155`, muted `#64748b`
- radius 10-14px, soft shadow, status chips, tables, drawer, modal
- fonts: PingFang SC, Microsoft YaHei, system UI

The prototype must be a single self-contained HTML file with inline CSS and JavaScript.
