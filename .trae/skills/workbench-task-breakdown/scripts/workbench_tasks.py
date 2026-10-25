#!/usr/bin/env python3
"""Read workbench iteration artifacts and create development task records."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar
from pathlib import Path
from typing import Any

REQUEST_TIMEOUT_SECONDS = 30
MAX_TITLE = 200
MAX_SCOPE = 10000
MAX_ACCEPTANCE = 10000
MAX_DEPENDENCIES = 2000
ALLOWED_REQUIREMENT_STATUSES = {"PRD_CONFIRMED", "DEVELOPMENT"}


def detect_project_root() -> Path:
    for candidate in (Path.cwd(), *Path.cwd().parents):
        if (candidate / ".git").exists():
            return candidate
    return Path.cwd()


DEFAULT_OUTPUT_ROOT = detect_project_root() / ".trae" / "outputs" / "task-breakdown"


class WorkbenchError(RuntimeError):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status = status
        self.code = code


def configure_output() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")


class WorkbenchSession:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))
        self.csrf_token = ""
        self.csrf_header = "X-CSRF-TOKEN"

    def _open(self, request: urllib.request.Request) -> tuple[int, bytes]:
        try:
            with self.opener.open(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", "replace")
            code, message = "HTTP_ERROR", body
            try:
                parsed = json.loads(body)
                code = parsed.get("code", code)
                message = parsed.get("message", message)
            except json.JSONDecodeError:
                pass
            raise WorkbenchError(error.code, code, message) from error
        except urllib.error.URLError as error:
            raise WorkbenchError(0, "NETWORK_ERROR", f"Cannot reach {self.base_url}: {error.reason}") from error

    def request_json(self, method: str, path: str, payload: dict[str, Any] | None = None) -> Any:
        headers = {"Accept": "application/json"}
        data = None
        if payload is not None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if method != "GET":
            headers[self.csrf_header] = self.csrf_token
        request = urllib.request.Request(f"{self.base_url}{path}", data=data, method=method, headers=headers)
        status, body = self._open(request)
        if status == 204 or not body:
            return {}
        try:
            return json.loads(body)
        except json.JSONDecodeError as error:
            raise WorkbenchError(status, "INVALID_RESPONSE", "Server returned non-JSON content") from error

    def refresh_csrf(self) -> None:
        csrf = self.request_json("GET", "/api/auth/csrf")
        token = csrf.get("token")
        header = csrf.get("headerName")
        if not isinstance(token, str) or not token:
            raise WorkbenchError(500, "CSRF_MISSING", "CSRF token was not returned")
        self.csrf_token = token
        self.csrf_header = header if isinstance(header, str) and header else "X-CSRF-TOKEN"

    def login(self, username: str, password: str) -> str:
        self.refresh_csrf()
        form = urllib.parse.urlencode({"username": username, "password": password}).encode("utf-8")
        request = urllib.request.Request(
            f"{self.base_url}/api/auth/login",
            data=form,
            method="POST",
            headers={
                "Content-Type": "application/x-www-form-urlencoded",
                "Accept": "application/json",
                self.csrf_header: self.csrf_token,
            },
        )
        try:
            self._open(request)
        except WorkbenchError as error:
            if error.status == 401:
                raise WorkbenchError(401, "LOGIN_FAILED", "Invalid username or password") from error
            raise
        self.refresh_csrf()
        me = self.request_json("GET", "/api/auth/me")
        roles = me.get("roles") or []
        if "PM" not in roles and "ADMIN" not in roles:
            raise WorkbenchError(403, "ROLE_REQUIRED", "Account must have PM or ADMIN role")
        return str(me.get("username", username))


def require_session(args: argparse.Namespace) -> WorkbenchSession:
    password = os.environ.get("AI_WORKBENCH_PASSWORD", "")
    if not args.username or not password:
        raise WorkbenchError(0, "MISSING_CREDENTIALS", "Set AI_WORKBENCH_USERNAME and AI_WORKBENCH_PASSWORD")
    session = WorkbenchSession(args.base_url)
    session.login(args.username, password)
    if args.base_url.startswith("http://"):
        print("warning: credentials are sent over plain HTTP; use HTTPS for production", file=sys.stderr)
    return session


def print_json(payload: Any) -> None:
    print(json.dumps(payload, ensure_ascii=False, indent=2))


def safe_name(value: str) -> str:
    cleaned = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "-", value).strip()
    return (cleaned[:60] or "requirement").strip()


def require_single_line(value: Any, label: str, limit: int) -> str:
    if not isinstance(value, str) or not value.strip():
        raise WorkbenchError(0, "VALIDATION_FAILED", f"{label} is required")
    text = value.strip()
    if len(text) > limit:
        raise WorkbenchError(0, "VALIDATION_FAILED", f"{label} exceeds {limit} characters")
    return text


def fetch_records(session: WorkbenchSession, requirement_id: int, category: str) -> list[dict[str, Any]]:
    page = session.request_json(
        "GET",
        f"/api/requirements/{requirement_id}/records?category={urllib.parse.quote(category)}&page=0&size=50",
    )
    content = page.get("content")
    return content if isinstance(content, list) else []


def pick_prd(records: list[dict[str, Any]]) -> dict[str, Any] | None:
    ordered = sorted(records, key=lambda item: (item.get("versionNumber") or 0, item.get("id") or 0), reverse=True)
    final_records = [item for item in ordered if item.get("status") == "FINAL"]
    return (final_records or ordered or [None])[0]


def readiness(requirement: dict[str, Any], prd: dict[str, Any] | None) -> list[str]:
    reasons: list[str] = []
    if requirement.get("status") not in ALLOWED_REQUIREMENT_STATUSES:
        reasons.append(f"需求状态为 {requirement.get('status')}，需先通过 PRD 评审进入 PRD_CONFIRMED")
    if prd is None:
        reasons.append("尚未上传 PRD")
    elif prd.get("status") != "FINAL":
        reasons.append(f"PRD V{prd.get('versionNumber')} 状态为 {prd.get('status')}，缺少 FINAL 版本")
    return reasons


def fetch_iteration_requirements(session: WorkbenchSession, iteration_id: int) -> list[dict[str, Any]]:
    requirements = session.request_json("GET", f"/api/iterations/{iteration_id}/requirements")
    if not isinstance(requirements, list):
        raise WorkbenchError(500, "INVALID_RESPONSE", "Iteration requirement list is not an array")
    return requirements


def command_iterations(session: WorkbenchSession) -> None:
    print_json(session.request_json("GET", "/api/iterations?page=0&size=50"))


def command_resolve(session: WorkbenchSession, name: str) -> None:
    page = session.request_json("GET", "/api/iterations?page=0&size=50")
    matches = [item for item in page.get("content", []) if item.get("name") == name]
    if not matches:
        print_json({"status": "not_found", "name": name})
        return
    if len(matches) > 1:
        print_json({"status": "ambiguous", "name": name, "candidates": matches})
        return
    print_json({"status": "resolved", "iteration": matches[0]})


def command_bundle(session: WorkbenchSession, iteration_id: int, output_dir: str | None) -> None:
    iteration = session.request_json("GET", f"/api/iterations/{iteration_id}")
    requirements = fetch_iteration_requirements(session, iteration_id)
    base = Path(output_dir) if output_dir else DEFAULT_OUTPUT_ROOT / str(iteration_id)
    base.mkdir(parents=True, exist_ok=True)

    summary = []
    existing_tasks: list[dict[str, Any]] = []
    for requirement in requirements:
        requirement_id = int(requirement["id"])
        prd_records = fetch_records(session, requirement_id, "PRD")
        task_records = fetch_records(session, requirement_id, "TASK")
        prd = pick_prd(prd_records)
        reasons = readiness(requirement, prd)
        prefix = safe_name(f"{requirement.get('requirementNumber')}-{requirement.get('title')}")

        if prd:
            payload = prd.get("payload") or {}
            markdown = payload.get("content")
            html = payload.get("htmlContent")
            if isinstance(markdown, str) and markdown.strip():
                (base / f"{prefix}-prd.md").write_text(markdown, encoding="utf-8")
            if isinstance(html, str) and html.strip():
                (base / f"{prefix}-prototype.html").write_text(html, encoding="utf-8")

        for task in task_records:
            task_payload = task.get("payload") or {}
            existing_tasks.append({
                "requirementId": requirement_id,
                "taskId": task.get("id"),
                "status": task.get("status"),
                "title": task_payload.get("title"),
                "scope": task_payload.get("scope"),
                "acceptance": task_payload.get("acceptance"),
                "dependencies": task_payload.get("dependencies"),
            })

        summary.append({
            "requirementId": requirement_id,
            "requirementNumber": requirement.get("requirementNumber"),
            "title": requirement.get("title"),
            "status": requirement.get("status"),
            "prdId": prd.get("id") if prd else None,
            "prdVersion": prd.get("versionNumber") if prd else None,
            "prdStatus": prd.get("status") if prd else None,
            "readyForTask": not reasons,
            "blockingReasons": reasons,
            "existingTaskCount": len(task_records),
        })

    bundle = {"iteration": iteration, "requirements": summary, "existingTasks": existing_tasks}
    (base / "bundle.json").write_text(json.dumps(bundle, ensure_ascii=False, indent=2), encoding="utf-8")
    print_json({"status": "ok", "outputDir": str(base), **bundle})


def command_check(session: WorkbenchSession, iteration_id: int) -> None:
    iteration = session.request_json("GET", f"/api/iterations/{iteration_id}")
    requirements = fetch_iteration_requirements(session, iteration_id)
    rows = []
    for requirement in requirements:
        requirement_id = int(requirement["id"])
        prd = pick_prd(fetch_records(session, requirement_id, "PRD"))
        reasons = readiness(requirement, prd)
        rows.append({
            "requirementId": requirement_id,
            "requirementNumber": requirement.get("requirementNumber"),
            "title": requirement.get("title"),
            "status": requirement.get("status"),
            "prdVersion": prd.get("versionNumber") if prd else None,
            "prdStatus": prd.get("status") if prd else None,
            "readyForTask": not reasons,
            "blockingReasons": reasons,
        })
    print_json({"iteration": iteration, "requirements": rows,
                "readyCount": sum(1 for row in rows if row["readyForTask"])})


def load_plan(plan_file: str) -> tuple[int, list[dict[str, Any]]]:
    try:
        plan = json.loads(Path(plan_file).read_text(encoding="utf-8"))
    except OSError as error:
        raise WorkbenchError(0, "FILE_UNREADABLE", f"Cannot read plan file: {error}") from error
    except json.JSONDecodeError as error:
        raise WorkbenchError(0, "INVALID_PLAN", f"Plan file is not valid JSON: {error}") from error
    if not isinstance(plan, dict):
        raise WorkbenchError(0, "INVALID_PLAN", "Plan root must be an object")
    iteration_id = plan.get("iterationId")
    if not isinstance(iteration_id, int):
        raise WorkbenchError(0, "INVALID_PLAN", "Plan must contain an integer iterationId")
    tasks = plan.get("tasks")
    if not isinstance(tasks, list) or not tasks:
        raise WorkbenchError(0, "INVALID_PLAN", "Plan must contain a non-empty tasks array")
    return iteration_id, tasks


def validate_plan_item(item: Any, index: int) -> dict[str, Any]:
    if not isinstance(item, dict):
        raise WorkbenchError(0, "INVALID_PLAN", f"tasks[{index}] must be an object")
    requirement_id = item.get("requirementId")
    if not isinstance(requirement_id, int):
        raise WorkbenchError(0, "INVALID_PLAN", f"tasks[{index}].requirementId must be an integer")
    dependencies = item.get("dependencies", "")
    if dependencies is None:
        dependencies = ""
    if not isinstance(dependencies, str):
        raise WorkbenchError(0, "INVALID_PLAN", f"tasks[{index}].dependencies must be a string")
    if len(dependencies) > MAX_DEPENDENCIES:
        raise WorkbenchError(0, "INVALID_PLAN", f"tasks[{index}].dependencies exceeds {MAX_DEPENDENCIES} characters")
    return {
        "requirementId": requirement_id,
        "title": require_single_line(item.get("title"), f"tasks[{index}].title", MAX_TITLE),
        "scope": require_single_line(item.get("scope"), f"tasks[{index}].scope", MAX_SCOPE),
        "acceptance": require_single_line(item.get("acceptance"), f"tasks[{index}].acceptance", MAX_ACCEPTANCE),
        "dependencies": dependencies.strip(),
    }


def command_create_tasks(session: WorkbenchSession, plan_file: str, dry_run: bool) -> None:
    iteration_id, raw_tasks = load_plan(plan_file)
    requirements = {int(item["id"]): item for item in fetch_iteration_requirements(session, iteration_id)}
    prd_cache: dict[int, dict[str, Any] | None] = {}
    title_cache: dict[int, set[str]] = {}
    results: list[dict[str, Any]] = []

    for index, raw_item in enumerate(raw_tasks):
        try:
            item = validate_plan_item(raw_item, index)
        except WorkbenchError as error:
            results.append({"status": "invalid", "index": index, "code": error.code, "message": str(error)})
            continue

        requirement_id = item["requirementId"]
        requirement = requirements.get(requirement_id)
        if requirement is None:
            results.append({"status": "invalid", "index": index, "requirementId": requirement_id,
                            "code": "REQUIREMENT_NOT_IN_ITERATION",
                            "message": "Requirement is not assigned to this iteration"})
            continue

        if requirement_id not in prd_cache:
            prd_cache[requirement_id] = pick_prd(fetch_records(session, requirement_id, "PRD"))
        prd = prd_cache[requirement_id]
        reasons = readiness(requirement, prd)
        if reasons:
            results.append({"status": "not_ready", "index": index, "requirementId": requirement_id,
                            "requirementNumber": requirement.get("requirementNumber"),
                            "code": "REQUIREMENT_NOT_READY", "message": "; ".join(reasons)})
            continue

        if requirement_id not in title_cache:
            existing = fetch_records(session, requirement_id, "TASK")
            title_cache[requirement_id] = {
                str((task.get("payload") or {}).get("title") or "").strip() for task in existing
            }
        if item["title"] in title_cache[requirement_id]:
            results.append({"status": "skipped", "index": index, "requirementId": requirement_id,
                            "requirementNumber": requirement.get("requirementNumber"),
                            "title": item["title"], "reason": "A task with the same title already exists"})
            continue

        if dry_run:
            results.append({"status": "planned", "index": index, "requirementId": requirement_id,
                            "requirementNumber": requirement.get("requirementNumber"), **item})
            continue

        try:
            created = session.request_json("POST", f"/api/requirements/{requirement_id}/tasks", {
                "title": item["title"],
                "scope": item["scope"],
                "acceptance": item["acceptance"],
                "dependencies": item["dependencies"],
            })
            title_cache[requirement_id].add(item["title"])
            results.append({"status": "created", "index": index, "requirementId": requirement_id,
                            "requirementNumber": requirement.get("requirementNumber"),
                            "taskId": created.get("id"), "taskStatus": created.get("status"),
                            "title": item["title"]})
        except WorkbenchError as error:
            results.append({"status": "failed", "index": index, "requirementId": requirement_id,
                            "code": error.code, "message": str(error), "title": item["title"]})

    counts: dict[str, int] = {}
    for result in results:
        counts[result["status"]] = counts.get(result["status"], 0) + 1
    print_json({
        "status": "dry_run" if dry_run else "completed",
        "iterationId": iteration_id,
        "counts": counts,
        "results": results,
    })
    if any(result["status"] in {"failed", "invalid", "not_ready"} for result in results):
        raise SystemExit(1)


def command_tasks(session: WorkbenchSession, iteration_id: int | None, requirement_id: int | None) -> None:
    if requirement_id is not None:
        print_json({"requirementId": requirement_id, "tasks": fetch_records(session, requirement_id, "TASK")})
        return
    if iteration_id is None:
        print_json({"status": "error", "code": "MISSING_FILTER",
                    "message": "Provide --iteration-id or --requirement-id"})
        raise SystemExit(1)
    requirements = fetch_iteration_requirements(session, iteration_id)
    tasks = []
    for requirement in requirements:
        for task in fetch_records(session, int(requirement["id"]), "TASK"):
            tasks.append({"requirementId": requirement["id"], "requirementNumber": requirement.get("requirementNumber"),
                          "taskId": task.get("id"), "taskStatus": task.get("status"),
                          "title": (task.get("payload") or {}).get("title")})
    print_json({"iterationId": iteration_id, "tasks": tasks})


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="AI Workbench task breakdown client")
    parser.add_argument("--base-url", default=os.environ.get("AI_WORKBENCH_BASE_URL", "http://106.53.107.222"))
    parser.add_argument("--username", default=os.environ.get("AI_WORKBENCH_USERNAME", ""))
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("iterations", help="List iterations")

    resolve = sub.add_parser("resolve", help="Resolve an iteration by exact name")
    resolve.add_argument("--name", required=True)

    bundle = sub.add_parser("bundle", help="Download iteration requirements, PRD and prototype")
    bundle.add_argument("--iteration-id", type=int, required=True)
    bundle.add_argument("--output-dir")

    check = sub.add_parser("check", help="Check task readiness for an iteration")
    check.add_argument("--iteration-id", type=int, required=True)

    create = sub.add_parser("create-tasks", help="Create development tasks from a plan file")
    create.add_argument("--plan-file", required=True)
    create.add_argument("--confirm", action="store_true", help="Actually create tasks; omit for dry run")

    tasks = sub.add_parser("tasks", help="List existing tasks")
    tasks.add_argument("--iteration-id", type=int)
    tasks.add_argument("--requirement-id", type=int)

    return parser.parse_args()


def main() -> int:
    configure_output()
    args = parse_args()
    try:
        session = require_session(args)
        if args.command == "iterations":
            command_iterations(session)
        elif args.command == "resolve":
            command_resolve(session, args.name)
        elif args.command == "bundle":
            command_bundle(session, args.iteration_id, args.output_dir)
        elif args.command == "check":
            command_check(session, args.iteration_id)
        elif args.command == "create-tasks":
            command_create_tasks(session, args.plan_file, dry_run=not args.confirm)
        elif args.command == "tasks":
            command_tasks(session, args.iteration_id, args.requirement_id)
        return 0
    except WorkbenchError as error:
        print_json({"status": "error", "statusCode": error.status, "code": error.code, "message": str(error)})
        return 1


if __name__ == "__main__":
    sys.exit(main())
