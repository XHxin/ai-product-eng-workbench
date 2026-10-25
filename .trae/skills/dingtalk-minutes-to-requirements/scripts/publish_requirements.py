#!/usr/bin/env python3
"""Publish an AI-extracted requirement plan to the AI Workbench API."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar
from typing import Any

REQUEST_TIMEOUT_SECONDS = 30
MAX_TITLE = 200
MAX_RAW_TEXT = 20000
MAX_NORMALIZED_TEXT = 20000
MAX_RATIONALE = 2000
VALID_TYPES = {"FEATURE", "IMPROVEMENT", "BUG"}
VALID_PRIORITIES = {"P0", "P1", "P2", "P3"}


class WorkbenchError(RuntimeError):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status = status
        self.code = code


class PlanError(RuntimeError):
    pass


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

    def request_json(self, method: str, path: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
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
            parsed = json.loads(body)
        except json.JSONDecodeError as error:
            raise WorkbenchError(status, "INVALID_RESPONSE", "Server returned non-JSON content") from error
        return parsed if isinstance(parsed, dict) else {"data": parsed}

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


def read_plan(path: str) -> dict[str, Any]:
    if path == "-":
        text = sys.stdin.read()
    else:
        try:
            text = open(path, encoding="utf-8").read()
        except OSError as error:
            raise PlanError(f"Cannot read plan file: {error}") from error
    try:
        payload = json.loads(text)
    except json.JSONDecodeError as error:
        raise PlanError(f"Plan is not valid JSON: {error}") from error
    if isinstance(payload, list):
        payload = {"requirements": payload}
    if not isinstance(payload, dict):
        raise PlanError("Plan must be a JSON object or array")
    requirements = payload.get("requirements")
    if not isinstance(requirements, list) or not requirements:
        raise PlanError("Plan must contain a non-empty requirements array")
    return payload


def required_text(item: dict[str, Any], key: str, limit: int, index: int) -> str:
    value = item.get(key)
    if not isinstance(value, str) or not value.strip():
        raise PlanError(f"requirements[{index}].{key} is required")
    trimmed = value.strip()
    if len(trimmed) > limit:
        raise PlanError(f"requirements[{index}].{key} exceeds {limit} characters")
    return trimmed


def normalize_requirements(payload: dict[str, Any]) -> list[dict[str, str]]:
    normalized = []
    for index, item in enumerate(payload["requirements"]):
        if not isinstance(item, dict):
            raise PlanError(f"requirements[{index}] must be an object")
        requirement_type = required_text(item, "requirementType", 20, index)
        priority = required_text(item, "priority", 4, index)
        if requirement_type not in VALID_TYPES:
            raise PlanError(f"requirements[{index}].requirementType must be one of {sorted(VALID_TYPES)}")
        if priority not in VALID_PRIORITIES:
            raise PlanError(f"requirements[{index}].priority must be one of {sorted(VALID_PRIORITIES)}")
        normalized.append({
            "title": required_text(item, "title", MAX_TITLE, index),
            "rawText": required_text(item, "rawText", MAX_RAW_TEXT, index),
            "normalizedText": required_text(item, "normalizedText", MAX_NORMALIZED_TEXT, index),
            "requirementType": requirement_type,
            "priority": priority,
            "rationale": required_text(item, "rationale", MAX_RATIONALE, index),
        })
    return normalized


def build_idempotency_key(payload: dict[str, Any], requirement: dict[str, str]) -> str:
    minutes_key = payload.get("minutesUuid") or payload.get("minutesShareUrl") or payload.get("minutesTitle") or "unknown"
    content_key = hashlib.sha1(
        f"{requirement['title']}\x00{requirement['normalizedText']}".encode("utf-8")
    ).hexdigest()[:16]
    safe_minutes_key = "".join(character for character in str(minutes_key) if character.isalnum() or character in "-_")[:80]
    return f"tingji-{safe_minutes_key}-{content_key}"[:200]


def resolve_source_ref(payload: dict[str, Any], override: str | None) -> str:
    if override:
        return override[:1000]
    share_url = payload.get("minutesShareUrl")
    if isinstance(share_url, str) and share_url.strip():
        return share_url.strip()[:1000]
    minutes_uuid = payload.get("minutesUuid")
    if isinstance(minutes_uuid, str) and minutes_uuid.strip():
        return f"tingji:{minutes_uuid.strip()}"[:1000]
    return "tingji:unknown"


def publish(session: WorkbenchSession, requirements: list[dict[str, str]], payload: dict[str, Any],
            source_ref: str, auto_confirm: bool) -> dict[str, Any]:
    results: list[dict[str, Any]] = []
    failures: list[dict[str, Any]] = []
    for index, requirement in enumerate(requirements):
        try:
            created = session.request_json("POST", "/api/requirements", {
                "title": requirement["title"],
                "rawText": requirement["rawText"],
                "sourceType": "TINGJI",
                "sourceRef": source_ref,
                "idempotencyKey": build_idempotency_key(payload, requirement),
                "normalizedText": requirement["normalizedText"],
                "requirementType": requirement["requirementType"],
                "priority": requirement["priority"],
            })
            requirement_id = created.get("id")
            if not isinstance(requirement_id, int):
                raise WorkbenchError(500, "INVALID_RESPONSE", "Created requirement has no id")
            status = str(created.get("status", ""))
            if auto_confirm and status == "EXTRACT":
                confirmed = session.request_json("POST", f"/api/requirements/{requirement_id}/normalize", {
                    "title": requirement["title"],
                    "normalizedText": requirement["normalizedText"],
                    "requirementType": requirement["requirementType"],
                    "priority": requirement["priority"],
                    "rationale": requirement["rationale"],
                })
                status = str(confirmed.get("status", status))
            results.append({
                "index": index,
                "requirementId": requirement_id,
                "requirementNumber": created.get("requirementNumber"),
                "title": requirement["title"],
                "requirementType": requirement["requirementType"],
                "priority": requirement["priority"],
                "status": status,
                "autoConfirmed": auto_confirm and status != "EXTRACT",
            })
        except WorkbenchError as error:
            failures.append({
                "index": index,
                "title": requirement["title"],
                "status": error.status,
                "code": error.code,
                "message": str(error),
            })
    return {"results": results, "failures": failures}


def dry_run(requirements: list[dict[str, str]], payload: dict[str, Any], base_url: str,
            source_ref: str, auto_confirm: bool) -> dict[str, Any]:
    preview = []
    for index, requirement in enumerate(requirements):
        preview.append({
            "index": index,
            "title": requirement["title"],
            "requirementType": requirement["requirementType"],
            "priority": requirement["priority"],
            "idempotencyKey": build_idempotency_key(payload, requirement),
            "targetStatus": "POOL" if auto_confirm else "EXTRACT",
        })
    return {
        "status": "dry_run",
        "baseUrl": base_url,
        "minutesTitle": payload.get("minutesTitle"),
        "sourceRef": source_ref,
        "autoConfirm": auto_confirm,
        "requirements": preview,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Publish extracted requirements to AI Workbench")
    parser.add_argument("--plan", required=True, help="Plan JSON file, or - for stdin")
    parser.add_argument("--base-url", default=os.environ.get("AI_WORKBENCH_BASE_URL", "http://106.53.107.222"))
    parser.add_argument("--username", default=os.environ.get("AI_WORKBENCH_USERNAME", ""))
    parser.add_argument("--source-ref", help="Override sourceRef for every requirement")
    parser.add_argument("--dry-run", action="store_true", help="Validate and preview without calling the API")
    parser.add_argument("--auto-confirm", action=argparse.BooleanOptionalAction, default=False,
                        help="Confirm created requirements into the pool (default false; keeps them in 待提取)")
    return parser.parse_args()


def main() -> int:
    configure_output()
    args = parse_args()
    try:
        payload = read_plan(args.plan)
        requirements = normalize_requirements(payload)
        source_ref = resolve_source_ref(payload, args.source_ref)
    except PlanError as error:
        print(json.dumps({"status": "invalid_plan", "message": str(error)}, ensure_ascii=False, indent=2))
        return 1

    base_url = args.base_url.rstrip("/")
    if args.dry_run:
        print(json.dumps(dry_run(requirements, payload, base_url, source_ref, args.auto_confirm),
                         ensure_ascii=False, indent=2))
        return 0

    password = os.environ.get("AI_WORKBENCH_PASSWORD", "")
    if not args.username or not password:
        print(json.dumps({
            "status": "missing_credentials",
            "message": "Set AI_WORKBENCH_USERNAME and AI_WORKBENCH_PASSWORD",
        }, ensure_ascii=False, indent=2))
        return 1
    if base_url.startswith("http://"):
        print("warning: credentials are sent over plain HTTP; use HTTPS for production", file=sys.stderr)

    session = WorkbenchSession(base_url)
    try:
        session.login(args.username, password)
        outcome = publish(session, requirements, payload, source_ref, args.auto_confirm)
    except WorkbenchError as error:
        print(json.dumps({
            "status": "failed",
            "baseUrl": base_url,
            "statusCode": error.status,
            "code": error.code,
            "message": str(error),
        }, ensure_ascii=False, indent=2))
        return 1

    status = "ok" if not outcome["failures"] else "partial"
    print(json.dumps({
        "status": status,
        "baseUrl": base_url,
        "minutesTitle": payload.get("minutesTitle"),
        "minutesUuid": payload.get("minutesUuid"),
        "sourceRef": source_ref,
        "autoConfirm": args.auto_confirm,
        "created": outcome["results"],
        "failures": outcome["failures"],
    }, ensure_ascii=False, indent=2))
    return 0 if status == "ok" else 1


if __name__ == "__main__":
    sys.exit(main())
