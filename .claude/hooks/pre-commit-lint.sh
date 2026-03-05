#!/usr/bin/env bash
# Pre-commit lint check: runs ./gradlew lint when a git commit is about to happen.
# Called by Claude Code's PreToolUse hook on Bash commands.
# Exit 2 to block the commit if lint fails; exit 0 to allow it.

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('command',''))" 2>/dev/null)

# Only act on git commit commands
if ! echo "$COMMAND" | grep -qE '^git commit'; then
  exit 0
fi

echo "Running ./gradlew lint before commit..." >&2
cd "$CLAUDE_PROJECT_DIR" || exit 0

./gradlew lint --quiet 2>&1 | tail -10 >&2
LINT_EXIT=${PIPESTATUS[0]}

if [ "$LINT_EXIT" -ne 0 ]; then
  echo "" >&2
  echo "Lint failed. Fix the issues above before committing, or run './gradlew lint' to see the full report." >&2
  exit 2
fi

echo "Lint passed." >&2
exit 0
