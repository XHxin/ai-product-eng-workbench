#!/usr/bin/env python3
"""Read workbench iterations and requirements, and upload a PRD version."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar
from pathlib import Path
from typing import Any

REQUEST_TIMEOUT_SECONDS = 30
MAX_CONTENT_CHARS = 100000


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


def read_text_file(path: str, label: str) -> str:
    try:
        text = Path(path).read_text(encoding="utf-8")
    except OSError as error:
        raise WorkbenchError(0, "FILE_UNREADABLE", f"Cannot read {label}: {error}") from error
    if not text.strip():
        raise WorkbenchError(0, "FILE_EMPTY", f"{label} is empty")
    if len(text) > MAX_CONTENT_CHARS:
        raise WorkbenchError(0, "FILE_TOO_LARGE", f"{label} exceeds {MAX_CONTENT_CHARS} characters")
    return text


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


def command_iterations(session: WorkbenchSession) -> None:
    page = session.request_json("GET", "/api/iterations?page=0&size=50")
    print_json(page)


def command_iteration(session: WorkbenchSession, iteration_id: int) -> None:
    print_json(session.request_json("GET", f"/api/iterations/{iteration_id}"))


def command_requirements(session: WorkbenchSession, iteration_id: int) -> None:
    print_json(session.request_json("GET", f"/api/iterations/{iteration_id}/requirements"))


def command_requirement(session: WorkbenchSession, requirement_id: int) -> None:
    print_json(session.request_json("GET", f"/api/requirements/{requirement_id}"))


def command_records(session: WorkbenchSession, requirement_id: int, category: str) -> None:
    page = session.request_json(
        "GET", f"/api/requirements/{requirement_id}/records?category={urllib.parse.quote(category)}&page=0&size=50")
    print_json(page)


def command_prd(session: WorkbenchSession, args: argparse.Namespace) -> None:
    markdown = read_text_file(args.markdown_file, "markdown file")
    html = read_text_file(args.html_file, "HTML file")
    requirement = session.request_json("GET", f"/api/requirements/{args.requirement_id}")
    iteration_id = requirement.get("iterationId")
    if not isinstance(iteration_id, int):
        raise WorkbenchError(0, "NOT_PLANNED", "Requirement is not assigned to an iteration")
    iteration = session.request_json("GET", f"/api/iterations/{iteration_id}")
    if iteration.get("status") != "LOCKED":
        raise WorkbenchError(0, "ITERATION_NOT_LOCKED", "Lock the iteration before uploading a PRD")
    if requirement.get("status") not in {"ITERATION", "CONFIRMATION", "REVIEW"}:
        raise WorkbenchError(0, "STATE_NOT_ALLOWED",
                             f"Requirement status {requirement.get('status')} does not allow a new PRD")
    summary = {
        "requirementId": args.requirement_id,
        "requirementNumber": requirement.get("requirementNumber"),
        "title": requirement.get("title"),
        "iterationId": iteration_id,
        "iterationStatus": iteration.get("status"),
        "requirementStatus": requirement.get("status"),
        "markdownChars": len(markdown),
        "htmlChars": len(html),
    }
    if args.dry_run:
        print_json({"status": "dry_run", "action": "upload_prd", **summary})
        return
    result = session.request_json("POST", f"/api/requirements/{args.requirement_id}/prd", {
        "content": markdown,
        "htmlContent": html,
    })
    print_json({"status": "uploaded", **summary,
                "prdId": result.get("id"), "versionNumber": result.get("versionNumber"),
                "prdStatus": result.get("status")})


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="AI Workbench iteration and requirement API client")
    parser.add_argument("--base-url", default=os.environ.get("AI_WORKBENCH_BASE_URL", "http://106.53.107.222"))
    parser.add_argument("--username", default=os.environ.get("AI_WORKBENCH_USERNAME", ""))
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("iterations", help="List iterations")

    iteration = sub.add_parser("iteration", help="Get one iteration")
    iteration.add_argument("--id", type=int, required=True)

    requirements = sub.add_parser("requirements", help="List requirements in an iteration")
    requirements.add_argument("--iteration-id", type=int, required=True)

    requirement = sub.add_parser("requirement", help="Get one requirement")
    requirement.add_argument("--id", type=int, required=True)

    records = sub.add_parser("records", help="List workflow records")
    records.add_argument("--requirement-id", type=int, required=True)
    records.add_argument("--category", default="PRD")

    prd = sub.add_parser("prd", help="Upload a PRD version")
    prd.add_argument("--requirement-id", type=int, required=True)
    prd.add_argument("--markdown-file", required=True)
    prd.add_argument("--html-file", required=True)
    prd.add_argument("--dry-run", action="store_true")

    return parser.parse_args()


def main() -> int:
    configure_output()
    args = parse_args()
    try:
        session = require_session(args)
        if args.command == "iterations":
            command_iterations(session)
        elif args.command == "iteration":
            command_iteration(session, args.id)
        elif args.command == "requirements":
            command_requirements(session, args.iteration_id)
        elif args.command == "requirement":
            command_requirement(session, args.id)
        elif args.command == "records":
            command_records(session, args.requirement_id, args.category)
        elif args.command == "prd":
            command_prd(session, args)
        return 0
    except WorkbenchError as error:
        print_json({"status": "error", "statusCode": error.status, "code": error.code, "message": str(error)})
        return 1


if __name__ == "__main__":
    sys.exit(main())
