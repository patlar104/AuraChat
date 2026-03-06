#!/usr/bin/env bash
# pre-commit-lint.sh
# Reads a PreToolUse (bash) hook payload from stdin.
# If the command is a git commit that touches security-sensitive files,
# runs ./gradlew lint first and blocks the commit if lint fails.

set -euo pipefail

INPUT=$(cat)

# Extract the command string from the tool input
TOOL_NAME=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('toolName',''))" 2>/dev/null || echo "")
COMMAND=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('toolInput',{}).get('command',''))" 2>/dev/null || echo "")

# Only act on bash/shell tool calls that are git commits
if [[ "$TOOL_NAME" != "bash" ]] || ! echo "$COMMAND" | grep -qE '^\s*git\s+(commit|push)'; then
  exit 0
fi

# Check if any staged files are security-sensitive
SENSITIVE_PATTERN='(AndroidManifest\.xml|EncryptedSharedPreferences|GoogleAiService|AppContainer|network_security_config|backup_rules|data_extraction_rules|\.github/)'

STAGED=$(git diff --cached --name-only 2>/dev/null || echo "")

if ! echo "$STAGED" | grep -qE "$SENSITIVE_PATTERN"; then
  exit 0
fi

echo "[pre-commit-lint] Security-sensitive files staged:" >&2
echo "$STAGED" | grep -E "$SENSITIVE_PATTERN" >&2
echo "[pre-commit-lint] Running ./gradlew lint..." >&2

cd "$(git rev-parse --show-toplevel)"

if ./gradlew lint --quiet 2>&1; then
  echo "[pre-commit-lint] Lint passed ✓" >&2
  # Allow the commit to proceed
  python3 -c "import json; print(json.dumps({'continue': True}))"
  exit 0
else
  echo "[pre-commit-lint] Lint FAILED. Fix lint errors before committing security-sensitive files." >&2
  python3 -c "
import json
print(json.dumps({
  'continue': False,
  'stopReason': 'Lint failed on security-sensitive files. Run ./gradlew lint, fix all errors, then retry the commit.',
  'hookSpecificOutput': {
    'hookEventName': 'PreToolUse',
    'permissionDecision': 'deny',
    'permissionDecisionReason': 'Lint failed on security-sensitive staged files. Fix lint errors first.'
  }
}))
"
  exit 2
fi
