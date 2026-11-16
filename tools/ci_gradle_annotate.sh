#!/usr/bin/env bash
set -euo pipefail

LOG_FILE="${1:-gradle.log}"
if [[ ! -f "$LOG_FILE" ]]; then
  echo "Log file not found: $LOG_FILE"
  exit 0
fi

# Emit GitHub Actions ::error annotations for Kotlin (1.9 / K2) and javac diagnostics
python3 - "$LOG_FILE" << 'PYEOF'
import re
import sys

log_path = sys.argv[1]
patterns = [
    # e: file:///path/to/File.kt:12:5 message
    re.compile(r"^e:\s+(?:file://)?([^:]+):(\d+):(\d+)\s+(.*)$"),
    # /path/to/File.kt:12: error: message
    re.compile(r"^(/[^:]+\.(?:kt|java)):(\d+):\s+error:\s+(.*)$"),
]

seen = set()
count = 0
with open(log_path, "r", encoding="utf-8", errors="replace") as f:
    for line in f:
        line = line.rstrip("\r\n")
        for idx, pat in enumerate(patterns):
            m = pat.match(line)
            if m:
                if idx == 0:
                    file_path, ln, col, msg = m.groups()
                    key = (file_path, ln, msg)
                    if key not in seen and count < 25:
                        seen.add(key)
                        print(f"::error file={file_path},line={ln},col={col}::{msg}")
                        count += 1
                else:
                    file_path, ln, msg = m.groups()
                    key = (file_path, ln, msg)
                    if key not in seen and count < 25:
                        seen.add(key)
                        print(f"::error file={file_path},line={ln}::{msg}")
                        count += 1
                break
PYEOF
