#!/usr/bin/env python3
"""Read a bounded DingTalk chat window for one group or one direct-chat person."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

DWS_TIMEOUT_SECONDS = 120
MAX_PAGES = 20
MAX_MESSAGES_LIMIT = 300
MAX_WINDOW_DAYS = 90
DEFAULT_MAX_CHARS = 30000


class ChatReadError(RuntimeError):
    pass


def configure_output() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")


def run_dws(arguments: list[str]) -> Any:
    executable = shutil.which("dws")
    if executable is None:
        raise ChatReadError("dws CLI not found in PATH")
    completed = subprocess.run(
        [executable, *arguments, "--format", "json"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=DWS_TIMEOUT_SECONDS,
        check=False,
    )
    if completed.returncode != 0:
        raise ChatReadError(completed.stderr.strip() or completed.stdout.strip() or "dws command failed")
    try:
        payload = json.loads(completed.stdout)
    except json.JSONDecodeError as error:
        raise ChatReadError(f"dws returned non-JSON output: {error}") from error
    if not payload.get("success"):
        raise ChatReadError(payload.get("errorMsg") or "dws returned unsuccessful result")
    if "result" not in payload:
        raise ChatReadError("dws result payload is missing")
    return payload["result"]


def normalized(value: str) -> str:
    return " ".join(value.split())


def resolve_group(name: str) -> dict[str, Any] | None:
    result = run_dws(["chat", "search", "--query", name, "--limit", "20"])
    groups = result.get("groups") or []
    wanted = normalized(name)
    exact = [group for group in groups if normalized(str(group.get("title", ""))) == wanted]
    if len(exact) == 1:
        group = exact[0]
        return {
            "type": "GROUP",
            "id": group.get("openConversationId"),
            "name": group.get("title"),
            "memberCount": group.get("memberCount"),
            "groupType": group.get("groupType"),
        }
    if len(exact) > 1:
        raise LookupError(json.dumps({
            "status": "ambiguous",
            "targetType": "GROUP",
            "query": name,
            "candidates": [{
                "name": group.get("title"),
                "id": group.get("openConversationId"),
                "memberCount": group.get("memberCount"),
            } for group in exact],
        }, ensure_ascii=False))
    raise LookupError(json.dumps({
        "status": "not_found",
        "targetType": "GROUP",
        "query": name,
        "candidates": [{
            "name": group.get("title"),
            "id": group.get("openConversationId"),
            "memberCount": group.get("memberCount"),
        } for group in groups[:10]],
    }, ensure_ascii=False))


def resolve_user(name: str) -> dict[str, Any] | None:
    result = run_dws(["contact", "user", "search", "--query", name])
    users = result if isinstance(result, list) else (result.get("result") or [])
    wanted = normalized(name)
    exact = [user for user in users
             if normalized(str(user.get("name", ""))) == wanted]
    if len(exact) == 1:
        user = exact[0]
        return {
            "type": "USER",
            "id": user.get("userId"),
            "name": user.get("name"),
            "openDingTalkId": user.get("openDingTalkId"),
        }
    if len(exact) > 1:
        raise LookupError(json.dumps({
            "status": "ambiguous",
            "targetType": "USER",
            "query": name,
            "candidates": [{
                "name": user.get("name"),
                "id": user.get("userId"),
                "title": user.get("title"),
            } for user in exact],
        }, ensure_ascii=False))
    raise LookupError(json.dumps({
        "status": "not_found",
        "targetType": "USER",
        "query": name,
        "candidates": [{
            "name": user.get("name"),
            "id": user.get("userId"),
            "title": user.get("title"),
        } for user in users[:10]],
    }, ensure_ascii=False))


def parse_time(value: str) -> datetime:
    text = value.strip()
    for pattern in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(text, pattern)
        except ValueError:
            continue
    try:
        return datetime.fromisoformat(text)
    except ValueError as error:
        raise ChatReadError(f"invalid time value: {value}") from error


def message_from_entry(entry: dict[str, Any]) -> dict[str, Any] | None:
    content = entry.get("content")
    if not isinstance(content, str) or not content.strip():
        return None
    created_at = parse_time(str(entry.get("createTime", "")))
    return {
        "id": entry.get("openMessageId"),
        "time": created_at.strftime("%Y-%m-%d %H:%M:%S"),
        "sender": entry.get("sender") or "未知",
        "content": content.strip(),
        "conversationId": entry.get("openConversationId"),
        "_createdAt": created_at,
    }


def fetch_messages(target: dict[str, Any], start: datetime, end: datetime,
                   max_messages: int, page_size: int) -> tuple[list[dict[str, Any]], bool]:
    if target["type"] == "GROUP":
        base_args = ["chat", "message", "list", "--group", str(target["id"])]
    else:
        base_args = ["chat", "message", "list-direct", "--user", str(target["id"])]

    collected: dict[str, dict[str, Any]] = {}
    truncated = False
    cursor_time = end.strftime("%Y-%m-%d %H:%M:%S")
    previous_cursor = ""
    for _ in range(MAX_PAGES):
        if cursor_time == previous_cursor:
            break
        previous_cursor = cursor_time
        result = run_dws([*base_args, "--time", cursor_time, "--forward=false", "--limit", str(page_size)])
        entries = result.get("messages") or []
        if not entries:
            break
        page_times = []
        for entry in entries:
            if not isinstance(entry, dict):
                continue
            try:
                created_at = parse_time(str(entry.get("createTime", "")))
            except ChatReadError:
                continue
            page_times.append(created_at)
            if created_at > end or created_at < start:
                continue
            message = message_from_entry(entry)
            if message is None or not message["id"]:
                continue
            collected[str(message["id"])] = message
            if len(collected) >= max_messages:
                truncated = True
                break
        if truncated or not result.get("hasMore") or not page_times:
            break
        oldest = min(page_times)
        if oldest <= start:
            break
        cursor_time = oldest.strftime("%Y-%m-%d %H:%M:%S")

    messages = sorted(collected.values(), key=lambda message: message["_createdAt"])
    return messages, truncated


def apply_char_budget(messages: list[dict[str, Any]], max_chars: int) -> tuple[list[dict[str, Any]], bool]:
    kept: list[dict[str, Any]] = []
    used = 0
    for message in reversed(messages):
        cost = len(message["content"]) + len(message["sender"]) + len(message["time"]) + 16
        if kept and used + cost > max_chars:
            return list(reversed(kept)), True
        kept.append(message)
        used += cost
    return list(reversed(kept)), False


def emit(payload: dict[str, Any], out_path: str | None) -> None:
    for message in payload.get("messages", []):
        message.pop("_createdAt", None)
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    if out_path:
        Path(out_path).write_text(text + "\n", encoding="utf-8")
        print(f"written {out_path}")
    else:
        print(text)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Read a bounded DingTalk group or direct chat window")
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--group", help="Exact group name")
    target.add_argument("--group-id", help="Group openConversationId")
    target.add_argument("--user", help="Exact person name")
    target.add_argument("--user-id", help="Person userId")
    parser.add_argument("--start", help="Window start, yyyy-MM-dd HH:mm:ss (default 7 days before end)")
    parser.add_argument("--end", help="Window end, yyyy-MM-dd HH:mm:ss (default now)")
    parser.add_argument("--max-messages", type=int, default=100, help="Maximum messages, capped at 300")
    parser.add_argument("--page-size", type=int, default=50, help="Messages per dws page, 20 to 100")
    parser.add_argument("--max-chars", type=int, default=DEFAULT_MAX_CHARS, help="Maximum total content characters")
    parser.add_argument("--resolve-only", action="store_true", help="Resolve the target without reading messages")
    parser.add_argument("--out", help="Write JSON to this UTF-8 file instead of stdout")
    return parser.parse_args()


def main() -> int:
    configure_output()
    args = parse_args()
    try:
        if args.group:
            target = resolve_group(args.group)
        elif args.group_id:
            target = {"type": "GROUP", "id": args.group_id, "name": args.group_id}
        elif args.user:
            target = resolve_user(args.user)
        else:
            target = {"type": "USER", "id": args.user_id, "name": args.user_id}

        if args.resolve_only:
            emit({"status": "ok", "resolveOnly": True, "target": target}, args.out)
            return 0

        end = parse_time(args.end) if args.end else datetime.now().replace(microsecond=0)
        start = parse_time(args.start) if args.start else end - timedelta(days=7)
        if start >= end:
            emit({"status": "error", "message": "--start must be earlier than --end"}, args.out)
            return 1
        if end - start > timedelta(days=MAX_WINDOW_DAYS):
            emit({"status": "error", "message": f"window exceeds {MAX_WINDOW_DAYS} days; narrow it"}, args.out)
            return 1
        max_messages = max(1, min(args.max_messages, MAX_MESSAGES_LIMIT))
        page_size = max(20, min(args.page_size, 100))
        max_chars = max(2000, args.max_chars)

        messages, truncated = fetch_messages(target, start, end, max_messages, page_size)
        messages, content_truncated = apply_char_budget(messages, max_chars)
        emit({
            "status": "ok",
            "target": target,
            "window": {
                "start": start.strftime("%Y-%m-%d %H:%M:%S"),
                "end": end.strftime("%Y-%m-%d %H:%M:%S"),
            },
            "messageCount": len(messages),
            "truncated": truncated,
            "contentTruncated": content_truncated,
            "messages": messages,
        }, args.out)
        return 0
    except LookupError as error:
        payload = json.loads(str(error))
        emit(payload, args.out)
        return 3 if payload.get("status") == "ambiguous" else 2
    except (ChatReadError, subprocess.TimeoutExpired, OSError) as error:
        emit({"status": "error", "message": str(error)}, args.out)
        return 1


if __name__ == "__main__":
    sys.exit(main())
