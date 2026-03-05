#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="/Users/patricklarocque/IdeaProjects/AuraChat"
HOOK="$REPO_ROOT/.claude/hooks/post-edit-run-viewmodel-tests.sh"

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

cat > "$TMP_DIR/gradlew" <<'EOF'
#!/usr/bin/env bash
echo "stub gradle invoked" >&2
exit 0
EOF
chmod +x "$TMP_DIR/gradlew"

VM_FILE="$TMP_DIR/TmpViewModel.kt"
printf 'class TmpViewModel' > "$VM_FILE"

OUTPUT=$(printf '{"tool_input":{"file_path":"%s"}}' "$VM_FILE" | CLAUDE_PROJECT_DIR="$TMP_DIR" bash "$HOOK" 2>&1)

if echo "$OUTPUT" | grep -q "appears empty"; then
  echo "FAIL: non-empty ViewModel without trailing newline was treated as empty" >&2
  echo "$OUTPUT" >&2
  exit 1
fi

if ! echo "$OUTPUT" | grep -q "stub gradle invoked"; then
  echo "FAIL: hook did not continue past content check for non-empty file" >&2
  echo "$OUTPUT" >&2
  exit 1
fi

EMPTY_FILE="$TMP_DIR/EmptyViewModel.kt"
: > "$EMPTY_FILE"

EMPTY_OUTPUT=$(printf '{"tool_input":{"file_path":"%s"}}' "$EMPTY_FILE" | CLAUDE_PROJECT_DIR="$TMP_DIR" bash "$HOOK" 2>&1)

if ! echo "$EMPTY_OUTPUT" | grep -q "appears empty"; then
  echo "FAIL: empty ViewModel file was not skipped" >&2
  echo "$EMPTY_OUTPUT" >&2
  exit 1
fi

echo "PASS: non-empty files without trailing newlines run, empty files skip"
