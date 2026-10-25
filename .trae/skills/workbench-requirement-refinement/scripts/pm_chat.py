#!/usr/bin/env python3
"""Resolve a DingTalk product manager, send grill-me questions, and poll replies."""

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
MAX_MESSAGE_CHARS = 20000


class PmChatError(RuntimeError):
    pass


def configure_output() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")


def run_dws(arguments: list[str]) -> dict[str, Any]:
    executable = shutil.which("dws")
    if executable is None:
        raise PmChatError("dws CLI not found in PATH")
    completed = subprocess.run(
        [executable, *arguments, "--format", "json"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=DWS_TIMEOUT_SECONDS,
        check=False,
    )
    if completed.returncode != 0:
        raise PmChatError(completed.stderr.strip() or completed.stdout.strip() or "dws command failed")
    try:
        payload = json.loads(completed.stdout)
    except json.JSONDecodeError as error:
        raise PmChatError(f"dws returned non-JSON output: {error}") from error
    if not isinstance(payload, dict) or not payload.get("success", True):
        message = payload.get("errorMsg") if isinstance(payload, dict) else None
        raise PmChatError(message or "dws returned unsuccessful result")
    return payload


def normalized(value: str) -> str:
    return " ".join(value.split())


def find_value(payload: Any, keys: set[str]) -> Any:
    if isinstance(payload, dict):
        for key, value in payload.items():
            if key in keys and value not in (None, ""):
                return value
        for value in payload.values():
            found = find_value(value, keys)
            if found not in (None, ""):
                return found
    elif isinstance(payload, list):
        for item in payload:
            found = find_value(item, keys)
            if found not in (None, ""):
                return found
    return None


def resolve_pm(name: str) -> dict[str, Any]:
    payload = run_dws(["contact", "user", "search", "--query", name])
    result = payload.get("result")
    users = result if isinstance(result, list) else (payload.get("result") or [])
    wanted = normalized(name)
    exact = [user for user in users if normalized(str(user.get("name", ""))) == wanted]
    if len(exact) == 1:
        user = exact[0]
        return {
            "name": user.get("name"),
            "userId": user.get("userId"),
            "openDingTalkId": user.get("openDingTalkId"),
            "title": user.get("title"),
        }
    if len(exact) > 1:
        raise LookupError(json.dumps({
            "status": "ambiguous",
            "query": name,
            "candidates": [{
                "name": user.get("name"),
                "userId": user.get("userId"),
                "title": user.get("title"),
            } for user in exact],
        }, ensure_ascii=False))
    raise LookupError(json.dumps({
        "status": "not_found",
        "query": name,
        "candidates": [{
            "name": user.get("name"),
            "userId": user.get("userId"),
            "title": user.get("title"),
        } for user in users[:10]],
    }, ensure_ascii=False))


def read_text(path: str) -> str:
    try:
        text = Path(path).read_text(encoding="utf-8")
    except OSError as error:
        raise PmChatError(f"Cannot read message file: {error}") from error
    if not text.strip():
        raise PmChatError("message file is empty")
    if len(text) > MAX_MESSAGE_CHARS:
        raise PmChatError(f"message exceeds {MAX_MESSAGE_CHARS} characters")
    return text


def load_state(path: str) -> dict[str, Any]:
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except OSError as error:
        raise PmChatError(f"Cannot read state file: {error}") from error
    except json.JSONDecodeError as error:
        raise PmChatError(f"State file is not valid JSON: {error}") from error


def write_state(path: str, state: dict[str, Any]) -> None:
    Path(path).write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def command_resolve(args: argparse.Namespace) -> dict[str, Any]:
    return {"status": "ok", "target": resolve_pm(args.name)}


def command_send(args: argparse.Namespace) -> dict[str, Any]:
    text = read_text(args.text_file)
    target = resolve_pm(args.pm_name) if not args.user_id else {
        "name": args.pm_name, "userId": args.user_id,
    }
    user_id = args.user_id or target.get("userId")
    if not user_id:
        raise PmChatError("PM userId is missing")
    if not args.confirm:
        return {
            "status": "dry_run",
            "action": "send_to_pm",
            "pmName": target.get("name") or args.pm_name,
            "userId": user_id,
            "title": args.title,
            "message": text,
            "note": "Re-run with --confirm after the user approves this message.",
        }
    baseline = (datetime.now() - timedelta(seconds=1)).strftime("%Y-%m-%d %H:%M:%S")
    send_arguments = ["chat", "message", "send", "--user", str(user_id), "--text", text]
    if args.title:
        send_arguments.extend(["--title", args.title])
    payload = run_dws(send_arguments)
    message_id = find_value(payload, {"openMessageId", "messageId", "processQueryKey"})
    state_path = args.state_file
    previous = load_state(state_path) if Path(state_path).exists() else {}
    state = {
        "pm": {"name": target.get("name") or args.pm_name, "userId": user_id},
        "round": int(previous.get("round", 0)) + 1,
        "baseline": baseline,
        "sentAt": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "questionMessageId": message_id,
        "questionText": text,
        "status": "WAITING_FOR_REPLY",
        "lastCheckedAt": None,
        "lastReplyAt": previous.get("lastReplyAt"),
        "replyCount": previous.get("replyCount", 0),
        "replies": previous.get("replies", []),
    }
    write_state(state_path, state)
    return {
        "status": "sent",
        "pmName": state["pm"]["name"],
        "userId": user_id,
        "round": state["round"],
        "sentAt": state["sentAt"],
        "messageId": message_id,
        "stateFile": state_path,
    }


def command_poll(args: argparse.Namespace) -> dict[str, Any]:
    state = load_state(args.state_file)
    pm = state.get("pm") or {}
    user_id = pm.get("userId")
    pm_name = pm.get("name")
    if not user_id or not pm_name:
        raise PmChatError("state file has no PM identity")
    payload = run_dws([
        "chat", "message", "list-direct", "--user", str(user_id),
        "--time", str(state.get("baseline", "")), "--forward=true", "--limit", str(args.max_messages),
    ])
    result = payload.get("result") if isinstance(payload.get("result"), dict) else payload
    messages = result.get("messages") or []
    wanted_name = normalized(str(pm_name))
    known_ids = {reply.get("id") for reply in state.get("replies", []) if reply.get("id")}
    question_id = state.get("questionMessageId")
    question_text = state.get("questionText")
    new_replies = []
    for message in messages:
        if not isinstance(message, dict):
            continue
        if message.get("openMessageId") in known_ids or message.get("openMessageId") == question_id:
            continue
        if normalized(str(message.get("sender", ""))) != wanted_name:
            continue
        content = message.get("content")
        if not isinstance(content, str) or not content.strip():
            continue
        if isinstance(question_text, str) and content.strip() == question_text.strip():
            continue
        new_replies.append({
            "id": message.get("openMessageId"),
            "time": message.get("createTime"),
            "sender": message.get("sender"),
            "content": content.strip(),
        })
    new_replies.sort(key=lambda item: str(item.get("time", "")))
    all_replies = state.get("replies", []) + new_replies
    state["replies"] = all_replies
    state["replyCount"] = len(all_replies)
    state["lastCheckedAt"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    if new_replies:
        state["lastReplyAt"] = new_replies[-1].get("time")
    state["status"] = "ANSWERED" if new_replies else "WAITING_FOR_REPLY"
    write_state(args.state_file, state)
    return {
        "status": state["status"],
        "pmName": pm_name,
        "round": state.get("round"),
        "sentAt": state.get("sentAt"),
        "newReplyCount": len(new_replies),
        "totalReplyCount": len(all_replies),
        "newReplies": new_replies,
        "stateFile": args.state_file,
    }


def command_status(args: argparse.Namespace) -> dict[str, Any]:
    state = load_state(args.state_file)
    return {
        "status": state.get("status"),
        "pm": state.get("pm"),
        "round": state.get("round"),
        "sentAt": state.get("sentAt"),
        "lastCheckedAt": state.get("lastCheckedAt"),
        "lastReplyAt": state.get("lastReplyAt"),
        "replyCount": state.get("replyCount"),
        "stateFile": args.state_file,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="PM dialogue via DingTalk for requirement refinement")
    sub = parser.add_subparsers(dest="command", required=True)

    resolve = sub.add_parser("resolve", help="Resolve a PM name to a DingTalk account")
    resolve.add_argument("--name", required=True)

    send = sub.add_parser("send", help="Send one round of grill-me questions")
    send.add_argument("--pm-name", required=True)
    send.add_argument("--user-id")
    send.add_argument("--text-file", required=True)
    send.add_argument("--title")
    send.add_argument("--state-file", required=True)
    send.add_argument("--confirm", action="store_true", help="Actually send; omit for a preview")

    poll = sub.add_parser("poll", help="Poll for new PM replies")
    poll.add_argument("--state-file", required=True)
    poll.add_argument("--max-messages", type=int, default=50)

    status = sub.add_parser("status", help="Show dialogue state")
    status.add_argument("--state-file", required=True)
    return parser.parse_args()


def main() -> int:
    configure_output()
    args = parse_args()
    try:
        if args.command == "resolve":
            payload = command_resolve(args)
        elif args.command == "send":
            payload = command_send(args)
        elif args.command == "poll":
            payload = command_poll(args)
        else:
            payload = command_status(args)
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 0
    except LookupError as error:
        payload = json.loads(str(error))
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 3 if payload.get("status") == "ambiguous" else 2
    except (PmChatError, subprocess.TimeoutExpired, OSError) as error:
        print(json.dumps({"status": "error", "message": str(error)}, ensure_ascii=False, indent=2))
        return 1


if __name__ == "__main__":
    sys.exit(main())
