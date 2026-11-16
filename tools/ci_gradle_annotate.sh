#!/usr/bin/env bash
set -euo pipefail

LOG_FILE="${1:-gradle.log}"
if [[ ! -f "$LOG_FILE" ]]; then
  echo "Log file not found: $LOG_FILE"
  exit 0
fi

# Emit GitHub Actions ::error annotations for Kotlin (1.9 / K2), javac, Lint, JUnit, and Gradle failures
python3 - "$LOG_FILE" << 'PYEOF'
import re
import sys

log_path = sys.argv[1]
compiler_patterns = [
    re.compile(r"^e:\s+(?:file://)?([^:]+):(\d+):(\d+)\s+(.*)$"),
    re.compile(r"^e:\s+(?:file://)?([^:]+):(\d+)\s+(.*)$"),
    re.compile(r"^(/[^:]+\.(?:kt|java)):(\d+):\s+error:\s+(.*)$"),
]
lint_pattern = re.compile(r"^([^:\s]+):(\d+):\s+Error:\s+(.*)$")
test_fail_pattern = re.compile(r"^(\S.*)\s+>\s+(\S.*)\s+FAILED$")

with open(log_path, "r", encoding="utf-8", errors="replace") as f:
    lines = [ln.rstrip("\r\n") for ln in f]

seen = set()
count = 0

def emit(annotation: str) -> None:
    global count
    if annotation not in seen and count < 35:
        seen.add(annotation)
        print(annotation)
        count += 1

for i, line in enumerate(lines):
    for idx, pat in enumerate(compiler_patterns):
        m = pat.match(line)
        if m:
            if idx == 0:
                fp, ln, col, msg = m.groups()
                emit(f"::error file={fp},line={ln},col={col}::{msg}")
            else:
                fp, ln, msg = m.groups()
                emit(f"::error file={fp},line={ln}::{msg}")
            break
    else:
        lm = lint_pattern.match(line)
        if lm:
            fp, ln, msg = lm.groups()
            emit(f"::error file={fp},line={ln}::{msg}")
            continue
        tm = test_fail_pattern.match(line)
        if tm:
            cls, method = tm.groups()
            detail = lines[i + 1].strip() if i + 1 < len(lines) else ""
            emit(f"::error::Test FAILED: {cls} > {method} | {detail}")
            continue
        if line.startswith("* What went wrong:"):
            summary = " | ".join(l.strip() for l in lines[i + 1 : i + 8] if l.strip())
            emit(f"::error::Gradle failure: {summary}")
        elif line.startswith("> Task ") and line.endswith(" FAILED"):
            emit(f"::error::{line}")
PYEOF
