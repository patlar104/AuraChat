#!/usr/bin/env bash
# PostToolUse hook: runs ChatViewModelTest automatically after any ViewModel file is edited.
# Reads tool input JSON from stdin.

INPUT=$(cat)
FILE=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
inp = d.get('tool_input', {})
print(inp.get('file_path', inp.get('notebook_path', '')))
" 2>/dev/null)

# Only trigger on ViewModel source files (not the test file itself)
if ! echo "$FILE" | grep -qE "ViewModel\.kt$"; then
  exit 0
fi
if echo "$FILE" | grep -qE "Test\.kt$"; then
  exit 0
fi

# Verify file exists and has real content
if [ ! -s "$FILE" ]; then
  echo "Skipping tests — ViewModel file appears empty." >&2
  exit 0
fi

FILE_LINES=$(awk 'END { print NR + 0 }' "$FILE" 2>/dev/null || echo 0)

# Show a quick diff summary of what changed before running tests
echo "" >&2
echo "ViewModel edited: $FILE ($FILE_LINES lines)" >&2
git diff --stat "$FILE" 2>/dev/null | head -5 >&2
echo "" >&2

# Count test functions to give context
TEST_COUNT=$(rg -c "@Test|fun test" app/src/test/ --type kt 2>/dev/null | awk -F: '{sum+=$2} END {print sum}')
echo "Running ChatViewModelTest ($TEST_COUNT total test functions in suite)..." >&2
cd "$CLAUDE_PROJECT_DIR" || exit 0

./gradlew testDebugUnitTest \
  --tests "com.personal.aurachat.presentation.chat.ChatViewModelTest" \
  --quiet 2>&1 | tail -15 >&2

TEST_EXIT=${PIPESTATUS[0]}

if [ "$TEST_EXIT" -ne 0 ]; then
  echo "" >&2
  echo "ChatViewModelTest FAILED after ViewModel edit." >&2
  echo "Run: ./gradlew testDebugUnitTest --tests \"com.personal.aurachat.presentation.chat.ChatViewModelTest\" for full output." >&2
else
  echo "ChatViewModelTest passed." >&2
fi

exit 0
