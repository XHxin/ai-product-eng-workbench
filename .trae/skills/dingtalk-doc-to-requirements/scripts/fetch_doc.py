#!/usr/bin/env python3
"""Read a specified DingTalk online document or AI table as Markdown."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any

DWS_TIMEOUT_SECONDS = 120
DEFAULT_MAX_CHARS = 30000
DEFAULT_MAX_RECORDS = 200
MAX_RECORDS_LIMIT = 1000


class DocReadError(RuntimeError):
    pass


def configure_output() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")


def run_dws(arguments: list[str]) -> dict[str, Any]:
    executable = shutil.which("dws")
    if executable is None:
        raise DocReadError("dws CLI not found in PATH")
    completed = subprocess.run(
        [executable, *arguments, "--format", "json"],
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=DWS_TIMEOUT_SECONDS,
        check=False,
    )
    if completed.returncode != 0:
        raise DocReadError(completed.stderr.strip() or completed.stdout.strip() or "dws command failed")
    try:
        payload = json.loads(completed.stdout)
    except json.JSONDecodeError as error:
        raise DocReadError(f"dws returned non-JSON output: {error}") from error
    if not isinstance(payload, dict) or not payload.get("success", True):
        message = payload.get("errorMsg") if isinstance(payload, dict) else None
        raise DocReadError(message or "dws returned unsuccessful result")
    return payload


def normalized(value: str) -> str:
    return " ".join(value.split())


def search_documents(title: str) -> list[dict[str, Any]]:
    payload = run_dws(["drive", "search", "--query", title, "--limit", "20"])
    result = payload.get("result") or {}
    documents = ((result.get("doc_results") or {}).get("content") or {}).get("documents")
    if isinstance(documents, list) and documents:
        return documents
    items = ((result.get("drive_results") or {}).get("content") or {}).get("items")
    return items if isinstance(items, list) else []


def resolve_by_title(title: str) -> dict[str, Any]:
    documents = search_documents(title)
    wanted = normalized(title)
    exact = [document for document in documents if normalized(str(document.get("name", ""))) == wanted]
    if len(exact) == 1:
        return exact[0]
    if len(exact) > 1:
        raise LookupError(json.dumps({
            "status": "ambiguous",
            "query": title,
            "candidates": [{
                "name": document.get("name"),
                "nodeId": document.get("nodeId") or document.get("fileId"),
                "extension": document.get("extension"),
            } for document in exact],
        }, ensure_ascii=False))
    raise LookupError(json.dumps({
        "status": "not_found",
        "query": title,
        "candidates": [{
            "name": document.get("name"),
            "nodeId": document.get("nodeId") or document.get("fileId"),
            "extension": document.get("extension"),
        } for document in documents[:10]],
    }, ensure_ascii=False))


def probe_document(node: str) -> dict[str, Any]:
    if "/i/p/" in node:
        raise DocReadError("share short links containing /i/p/ are not supported; provide a node URL, node ID, or exact title")
    payload = run_dws(["doc", "info", "--node", node])
    if "contentType" not in payload and "result" in payload:
        payload = payload["result"]
    contentType = payload.get("contentType")
    extension = payload.get("extension")
    if contentType != "ALIDOC" or extension not in {"adoc", "able"}:
        raise DocReadError(
            f"unsupported type (contentType={contentType}, extension={extension}); "
            "this skill supports online documents (adoc) and AI tables (able)"
        )
    node_id = payload.get("nodeId")
    if not isinstance(node_id, str) or not node_id:
        raise DocReadError("document nodeId is missing")
    source_kind = "AITABLE" if extension == "able" else "DOCUMENT"
    return {
        "nodeId": node_id,
        "title": payload.get("name") or node_id,
        "docUrl": payload.get("docUrl") or f"https://alidocs.dingtalk.com/i/nodes/{node_id}",
        "extension": extension,
        "contentType": contentType,
        "sourceKind": source_kind,
        "updateTime": payload.get("updateTime"),
    }


def read_markdown(node_id: str) -> tuple[str, str, str | None]:
    payload = run_dws(["doc", "read", "--node", node_id])
    markdown = payload.get("markdown")
    if not isinstance(markdown, str) or not markdown.strip():
        raise DocReadError("document content is empty or unreadable")
    return markdown, str(payload.get("title") or ""), payload.get("docUrl")


def aitable_data(payload: dict[str, Any]) -> dict[str, Any]:
    data = payload.get("data")
    return data if isinstance(data, dict) else payload


def read_aitable(base_id: str, table_filter: str | None, max_records: int) -> tuple[dict[str, Any], str, list[dict[str, Any]], bool]:
    info = aitable_data(run_dws(["aitable", "info", "--base-id", base_id]))
    tables = info.get("tables") or []
    if not tables:
        raise DocReadError("AI table has no data tables")
    selected = [table for table in tables if not table_filter or table.get("tableId") == table_filter]
    if table_filter and not selected:
        raise DocReadError(f"tableId {table_filter} was not found in this AI table")

    lines = [f"# 多维表: {info.get('baseName') or base_id}", ""]
    record_map: list[dict[str, Any]] = []
    truncated = False
    remaining = max_records

    for table in selected:
        table_id = table.get("tableId")
        table_name = table.get("tableName") or table_id
        if not isinstance(table_id, str) or not table_id:
            continue
        field_payload = aitable_data(run_dws([
            "aitable", "field", "get", "--base-id", base_id, "--table-id", table_id,
        ]))
        fields = field_payload.get("fields") or []
        field_names = {field.get("fieldId"): field.get("fieldName") for field in fields if field.get("fieldId")}

        record_payload = run_dws([
            "aitable", "record", "query", "--base-id", base_id, "--table-id", table_id,
            "--limit", str(max(1, min(remaining, MAX_RECORDS_LIMIT))),
        ])
        record_data = record_payload.get("data") if isinstance(record_payload.get("data"), dict) else record_payload
        records = record_data.get("records") or []
        if record_data.get("hasMore"):
            truncated = True

        lines.append(f"## 数据表: {table_name}")
        lines.append(f"- tableId: {table_id}")
        lines.append(f"- 记录数（本次读取）: {len(records)}")
        lines.append("")

        for index, record in enumerate(records):
            if remaining <= 0:
                truncated = True
                break
            remaining -= 1
            cells = record.get("cells") or {}
            values = []
            for field in fields:
                field_id = field.get("fieldId")
                field_name = field.get("fieldName")
                if not field_id or not field_name:
                    continue
                value = format_cell_value(cells.get(field_id))
                if value:
                    values.append((field_name, value))
            title = next((value for name, value in values if name in {"需求标题", "标题", "名称"}), f"记录 {index + 1}")
            record_id = record.get("recordId")
            lines.append(f"### {title} · {record_id or index + 1}")
            if record_id:
                lines.append(f"- 记录ID: {record_id}")
            for name, value in values:
                lines.append(f"- {name}: {value}")
            lines.append("")
            record_map.append({"recordId": record_id, "tableId": table_id, "title": title})

    document_tables = [{
        "tableId": table.get("tableId"),
        "tableName": table.get("tableName"),
    } for table in selected]
    document = {
        "nodeId": base_id,
        "title": info.get("baseName") or base_id,
        "docUrl": f"https://alidocs.dingtalk.com/i/nodes/{base_id}",
        "extension": "able",
        "contentType": "ALIDOC",
        "sourceKind": "AITABLE",
        "tables": document_tables,
    }
    return document, "\n".join(lines).strip(), record_map, truncated


def format_cell_value(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, str):
        text = value.strip()
        return "" if text in {"", "#REF!", "#VALUE!", "#N/A"} else text
    if isinstance(value, dict):
        if value.get("name") is not None:
            return str(value["name"])
        if value.get("text") is not None:
            return str(value["text"])
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, list):
        parts = [format_cell_value(item) for item in value]
        return ", ".join(part for part in parts if part)
    if isinstance(value, (int, float, bool)):
        return str(value)
    return json.dumps(value, ensure_ascii=False)


def emit(payload: dict[str, Any], out_path: str | None) -> None:
    text = json.dumps(payload, ensure_ascii=False, indent=2)
    if out_path:
        Path(out_path).write_text(text + "\n", encoding="utf-8")
        print(f"written {out_path}")
    else:
        print(text)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Read a DingTalk online document or AI table")
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--url", help="Full alidocs URL")
    target.add_argument("--node-id", help="Document nodeId or AI table baseId")
    target.add_argument("--title", help="Exact document or AI table title")
    parser.add_argument("--table-id", help="AI table data table ID (optional)")
    parser.add_argument("--max-chars", type=int, default=DEFAULT_MAX_CHARS, help="Maximum Markdown characters")
    parser.add_argument("--max-records", type=int, default=DEFAULT_MAX_RECORDS,
                        help="Maximum AI table records, capped at 1000")
    parser.add_argument("--resolve-only", action="store_true", help="Resolve metadata without reading content")
    parser.add_argument("--out", help="Write JSON to this UTF-8 file instead of stdout")
    return parser.parse_args()


def main() -> int:
    configure_output()
    args = parse_args()
    try:
        if args.title:
            candidate = resolve_by_title(args.title)
            node = candidate.get("nodeId") or candidate.get("fileId")
            if not isinstance(node, str) or not node:
                raise DocReadError("search result has no nodeId")
        else:
            node = args.url or args.node_id

        document = probe_document(str(node))
        max_chars = max(2000, args.max_chars)
        max_records = max(1, min(args.max_records, MAX_RECORDS_LIMIT))

        if document["sourceKind"] == "AITABLE":
            document, markdown, record_map, records_truncated = read_aitable(
                document["nodeId"], args.table_id, max_records)
            if args.resolve_only:
                emit({"status": "ok", "resolveOnly": True, "document": document, "recordMap": []}, args.out)
                return 0
            truncated = len(markdown) > max_chars
            emit({
                "status": "ok",
                "document": document,
                "markdown": markdown[:max_chars],
                "chars": len(markdown),
                "truncated": truncated or records_truncated,
                "recordMap": record_map,
            }, args.out)
            return 0

        if args.resolve_only:
            emit({"status": "ok", "resolveOnly": True, "document": document}, args.out)
            return 0

        markdown, title, doc_url = read_markdown(document["nodeId"])
        if title:
            document["title"] = title
        if doc_url:
            document["docUrl"] = doc_url
        truncated = len(markdown) > max_chars
        emit({
            "status": "ok",
            "document": document,
            "markdown": markdown[:max_chars],
            "chars": len(markdown),
            "truncated": truncated,
        }, args.out)
        return 0
    except LookupError as error:
        payload = json.loads(str(error))
        emit(payload, args.out)
        return 3 if payload.get("status") == "ambiguous" else 2
    except (DocReadError, subprocess.TimeoutExpired, OSError) as error:
        emit({"status": "error", "message": str(error)}, args.out)
        return 1


if __name__ == "__main__":
    sys.exit(main())
