#!/usr/bin/env bash
# PostToolUse hook: warns if verbose Log.d/Log.v calls appear in security-sensitive files.
# Reads tool input JSON from stdin. Always exits 0 (warning only, never blocks).

INPUT=$(cat)
FILE=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
inp = d.get('tool_input', {})
print(inp.get('file_path', inp.get('notebook_path', '')))
" 2>/dev/null)

# Only check security-sensitive files
if ! echo "$FILE" | grep -qE "(DefaultSettingsRepository|GoogleAiService|DefaultConversationRepository)\.kt$"; then
  exit 0
fi

# File must exist and be non-empty
if [ ! -s "$FILE" ]; then
  exit 0
fi

# Use rg for fast, accurate pattern matching with line numbers and context
VERBOSE_LOGS=$(rg -n "Log\.[dv]\(" "$FILE" 2>/dev/null)
ALL_LOGS=$(rg -n "Log\.[dviwe]\(" "$FILE" 2>/dev/null)
LOG_COUNT=$(echo "$ALL_LOGS" | grep -c "Log\." 2>/dev/null || echo 0)
FILE_LINES=$(wc -l < "$FILE" 2>/dev/null || echo 0)

if [ -n "$VERBOSE_LOGS" ]; then
  echo "" >&2
  echo "WARNING: verbose logs in sensitive file — verify no API keys or message content are exposed:" >&2
  echo "$VERBOSE_LOGS" >&2
  echo "" >&2
  echo "  File: $FILE ($FILE_LINES lines, $LOG_COUNT total log calls)" >&2
  echo "  All log calls in this file:" >&2
  echo "$ALL_LOGS" | sed 's/^/    /' >&2
  echo "" >&2
fi

exit 0
