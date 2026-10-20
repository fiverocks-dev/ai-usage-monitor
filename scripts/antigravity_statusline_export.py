#!/usr/bin/env python3
"""Antigravity CLI statusLine stdin -> quota-only local JSON. No network or credentials.

This script is intended for the officially documented statusLine command hook.
It never copies the original payload, which contains email, workspace paths, and
potentially other private metadata.
"""
import json
import math
import os
from datetime import datetime, timezone
from pathlib import Path
import sys

MAX_INPUT = 256 * 1024
MAX_BUCKETS = 64
MAX_ID_LENGTH = 80


def normalized(payload: dict) -> dict:
    quotas = payload.get("quota")
    if not isinstance(quotas, dict):
        quotas = {}
    if len(quotas) > MAX_BUCKETS:
        raise ValueError("too many quota buckets")
    result = []
    for key, value in sorted(quotas.items()):
        if not isinstance(key, str) or len(key) > MAX_ID_LENGTH or not key:
            continue
        if not isinstance(value, dict):
            continue
        remaining = value.get("remaining_fraction")
        if isinstance(remaining, bool) or not isinstance(remaining, (float, int)):
            continue
        if not math.isfinite(remaining) or not 0 <= remaining <= 1:
            continue
        reset = value.get("reset_time")
        if reset is not None and (not isinstance(reset, str) or len(reset) > 50):
            reset = None
        result.append({
            "bucket_id": key,
            "remaining_fraction": remaining,
            "reset_time": reset,
        })
    return {
        "schema_version": 1,
        "source": "antigravity-cli-statusline",
        "captured_at": datetime.now(timezone.utc).isoformat(),
        "buckets": result,
    }


def main() -> int:
    try:
        raw = sys.stdin.buffer.read(MAX_INPUT + 1)
        if len(raw) > MAX_INPUT:
            raise ValueError("input too large")
        state = json.loads(raw)
        if not isinstance(state, dict):
            raise ValueError("not an object")
        result = normalized(state)
        base = os.getenv("LOCALAPPDATA")
        if not base:
            raise RuntimeError("LOCALAPPDATA missing; Windows-only prototype")
        target_dir = Path(base) / "AIUsageMonitor"
        target_dir.mkdir(parents=True, exist_ok=True)
        target = target_dir / "antigravity-quota.json"
        temp = target.with_suffix(".tmp")
        with temp.open("w", encoding="utf-8", newline="\n") as fp:
            json.dump(result, fp, ensure_ascii=False, separators=(",", ":"))
            fp.write("\n")
        os.replace(temp, target)
        # stdout is what Antigravity shows in the status line.
        print(f"Quota export: {len(result['buckets'])} buckets")
        return 0
    except Exception:
        # Never print the original statusLine JSON or its sensitive metadata.
        print("Quota export unavailable")
        return 1


if __name__ == "__main__":
    sys.exit(main())
