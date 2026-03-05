Perform a comprehensive security review of the ENTIRE AuraChat codebase — not just staged or recent changes.

## Steps

1. **Map the full source tree**
   ```bash
   tree app/src/main/java/com/personal/aurachat/ -I "*.class"
   git ls-files --others --exclude-standard   # untracked files that shouldn't exist
   wc -l app/src/main/AndroidManifest.xml     # sanity check manifest size
   ```

2. **Audit git history for sensitive changes**
   ```bash
   git log --oneline -30
   git diff HEAD~10 HEAD -- app/src/main/AndroidManifest.xml
   git log --oneline --diff-filter=A -- "*.kt" | head -20   # newly added Kotlin files
   git log -S "AIza" --oneline                               # any commit that touched an API key string
   git log -S "api_key\|password\|secret\|token" --oneline -i
   ```

3. **Manifest hardening checklist**
   ```bash
   cat app/src/main/AndroidManifest.xml
   rg "android:exported" app/src/main/AndroidManifest.xml   # every component must declare it
   rg "android:debuggable|usesCleartextTraffic|allowBackup" app/src/main/AndroidManifest.xml
   ```
   Verify: no exported components missing explicit `android:exported`, `usesCleartextTraffic="false"`,
   `allowBackup` is intentional, no `debuggable="true"` outside debug build type.

4. **Logging safety scan**
   ```bash
   rg "Log\.[dviwef]\(" app/src/main/java/ --type kt -n
   rg "Log\.[dv]\(" app/src/main/java/com/personal/aurachat/data/ --type kt -n
   ```
   Flag any `Log.d`/`Log.v` in: DefaultSettingsRepository, GoogleAiService, DefaultConversationRepository
   that could expose API keys, message content, or user data.

5. **API key & secret exposure scan**
   ```bash
   rg "AIza" . --type kt -n
   rg "api_key|apiKey|API_KEY" . --type kt -n -i
   rg "BuildConfig\." app/src/main/java/ --type kt -n
   rg "hardcoded|TODO.*key\|FIXME.*key" . --type kt -n -i
   diff <(rg "EncryptedSharedPreferences" app/src/main/java/ --type kt -l) \
        <(echo "app/src/main/java/com/personal/aurachat/data/repository/DefaultSettingsRepository.kt")
   ```
   API key must only live in EncryptedSharedPreferences — nowhere else.

6. **Dependency vulnerability check**
   ```bash
   cat gradle/libs.versions.toml
   rg "version" gradle/libs.versions.toml
   ```
   Flag: generativeai, security-crypto, room, compose-bom, kotlin — check for known CVEs.

7. **Fix all issues found** — apply changes directly with Edit/Write tools.

8. **Validate**
   ```bash
   ./gradlew lint --quiet 2>&1 | tail -20
   ```

9. **Verify changes before committing**
   ```bash
   git diff --stat
   git diff
   ```

10. **Commit** all fixes: `security: [brief description of what was hardened]`
