#!/usr/bin/env python3
"""Read a DingTalk AI minutes by exact title through the authenticated dws CLI."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any

DEFAULT_MAX_CHARS = 12000
DEFAULT_TRANSCRIPT_CHARS = 20000
DWS_TIMEOUT_SECONDS = 120
MAX_TRANSCRIPT_PAGES = 20


class MinutesError(RuntimeError):
    pass


def configure_output() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")


def run_dws(arguments: list[str]) -> dict[str, Any]:
    executable = shutil.which("dws")
    if executable is None:
        raise MinutesError("dws CLI not found in PATH")
    completed = subprocess.run(
        [executable, *arguments, "--format", "json"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=DWS_TIMEOUT_SECONDS,
        check=False,
    )
    if completed.returncode != 0:
        message = completed.stderr.strip() or completed.stdout.strip() or "dws command failed"
        raise MinutesError(message)
    try:
        payload = json.loads(completed.stdout)
    except json.JSONDecodeError as error:
        raise MinutesError(f"dws returned non-JSON output: {error}") from error
    if not payload.get("success"):
        raise MinutesError(payload.get("errorMsg") or "dws returned unsuccessful result")
    result = payload.get("result")
    if not isinstance(result, dict):
        raise MinutesError("dws result payload is missing")
    return result


def normalized_title(value: str) -> str:
    return " ".join(value.split())


def list_candidates(title: str) -> list[dict[str, Any]]:
    result = run_dws(["minutes", "list", "all", "--query", title, "--limit", "20"])
    items = result.get("itemList") or []
    wanted = normalized_title(title)
    return [item for item in items if normalized_title(str(item.get("title", ""))) == wanted]


def format_clock(offset_millis: Any) -> str:
    if not isinstance(offset_millis, (int, float)):
        return "--:--:--"
    total = max(0, int(offset_millis / 1000))
    return f"{total // 3600:02d}:{(total % 3600) // 60:02d}:{total % 60:02d}"


def speaker_name(paragraph: dict[str, Any]) -> str:
    display = paragraph.get("speakerDisplay")
    if isinstance(display, dict) and display.get("nickName"):
        return str(display["nickName"])
    if isinstance(display, str) and display.strip():
        return display.strip()
    nickname = paragraph.get("nickName")
    return str(nickname) if nickname else "发言"


def fetch_transcript(uuid: str, max_chars: int) -> tuple[str, bool]:
    lines: list[str] = []
    collected = 0
    next_token = ""
    for _ in range(MAX_TRANSCRIPT_PAGES):
        arguments = ["minutes", "get", "transcription", "--id", uuid]
        if next_token:
            arguments.extend(["--next-token", next_token])
        result = run_dws(arguments)
        paragraphs = result.get("paragraphList") or []
        for paragraph in paragraphs:
            text = paragraph.get("paragraph")
            if not isinstance(text, str) or not text.strip():
                continue
            line = f"[{format_clock(paragraph.get('startTime'))}] {speaker_name(paragraph)}: {text.strip()}"
            lines.append(line)
            collected += len(line) + 1
            if collected >= max_chars:
                return "\n".join(lines)[:max_chars], True
        next_token = result.get("nextToken")
        if not result.get("hasNext") or not next_token:
            break
    return "\n".join(lines), False


def fetch_summary(uuid: str) -> str:
    result = run_dws(["minutes", "get", "summary", "--id", uuid])
    summary = result.get("fullSummary")
    if not isinstance(summary, str) or not summary.strip():
        raise MinutesError("minutes summary is empty or still being generated")
    return summary


def build_minutes(item: dict[str, Any]) -> dict[str, Any]:
    duration_micros = item.get("durationMicros")
    duration_minutes = None
    if isinstance(duration_micros, int):
        duration_minutes = round(duration_micros / 60_000_000, 1)
    organizer = (item.get("flashUserInfo") or {}).get("name", "")
    keywords = (item.get("keywordsInfo") or {}).get("keywords") or []
    return {
        "uuid": item.get("uuid"),
        "title": item.get("title"),
        "startTimeISO": item.get("startTimeISO"),
        "endTimeISO": item.get("endTimeISO"),
        "durationMinutes": duration_minutes,
        "organizer": organizer,
        "shareUrl": item.get("shareUrl"),
        "keywords": keywords,
    }


def emit(payload: dict[str, Any], out_path: str | None) -> None:
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    if out_path:
        Path(out_path).write_text(text + "\n", encoding="utf-8")
        print(f"written {out_path}")
    else:
        print(text)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fetch one DingTalk AI minutes by exact title")
    parser.add_argument("--title", help="Exact minutes title")
    parser.add_argument("--id", help="DingTalk minutes task UUID")
    parser.add_argument("--max-chars", type=int, default=DEFAULT_MAX_CHARS, help="Maximum summary characters")
    parser.add_argument("--include-transcript", action="store_true", help="Include a bounded speaker transcript")
    parser.add_argument("--transcript-chars", type=int, default=DEFAULT_TRANSCRIPT_CHARS,
                        help="Maximum transcript characters")
    parser.add_argument("--out", help="Write JSON to this UTF-8 file instead of stdout")
    return parser.parse_args()


def main() -> int:
    configure_output()
    args = parse_args()
    if not args.title and not args.id:
        emit({"status": "error", "message": "provide --title or --id"}, args.out)
        return 1
    if args.max_chars < 1000:
        emit({"status": "error", "message": "--max-chars must be at least 1000"}, args.out)
        return 1
    if args.transcript_chars < 1000:
        emit({"status": "error", "message": "--transcript-chars must be at least 1000"}, args.out)
        return 1

    try:
        if args.title and not args.id:
            candidates = list_candidates(args.title)
            if not candidates:
                emit({"status": "not_found", "title": args.title,
                      "message": "No minutes matched the exact title"}, args.out)
                return 2
            if len(candidates) > 1:
                emit({"status": "ambiguous", "title": args.title,
                      "candidates": [build_minutes(item) for item in candidates]}, args.out)
                return 3
            item = candidates[0]
        else:
            result = run_dws(["minutes", "list", "all", "--limit", "100"])
            items = result.get("itemList") or []
            item = next((entry for entry in items if entry.get("uuid") == args.id), None)
            if item is None:
                emit({"status": "not_found", "id": args.id,
                      "message": "UUID was not found in accessible minutes"}, args.out)
                return 2

        minutes = build_minutes(item)
        summary = fetch_summary(str(minutes["uuid"]))
        truncated = len(summary) > args.max_chars
        payload = {
            "status": "ok",
            "minutes": minutes,
            "summary": summary[:args.max_chars],
            "summaryChars": len(summary),
            "summaryTruncated": truncated,
        }
        if args.include_transcript:
            transcript, transcript_truncated = fetch_transcript(str(minutes["uuid"]), args.transcript_chars)
            payload["transcript"] = transcript
            payload["transcriptTruncated"] = transcript_truncated
        emit(payload, args.out)
        return 0
    except (MinutesError, subprocess.TimeoutExpired, OSError) as error:
        emit({"status": "error", "message": str(error)}, args.out)
        return 1


if __name__ == "__main__":
    sys.exit(main())
