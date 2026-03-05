Audit AuraChat dependencies for outdated versions and known issues.

## Steps

1. **Read and parse the versions catalog**
   ```bash
   cat gradle/libs.versions.toml
   rg "^\w" gradle/libs.versions.toml              # just the version declarations
   wc -l gradle/libs.versions.toml                 # total size
   diff gradle/libs.versions.toml <(sort gradle/libs.versions.toml) || true   # detect duplicate keys
   ```

2. **Check what's actually used vs declared**
   ```bash
   rg "libs\." app/build.gradle.kts               # all library references in build file
   rg "libs\." build.gradle.kts 2>/dev/null       # root build file
   ```

3. **Check current versions against latest stable** for each key dependency:
   - `generativeai` (Google AI Client SDK) — check GitHub releases for google/generative-ai-android
   - `kotlin` — check kotlinlang.org
   - `compose-bom` — check developer.android.com/jetpack/compose/bom/bom-mapping
   - `room` — check developer.android.com/jetpack/androidx/releases/room
   - `navigation-compose` — check developer.android.com/jetpack/androidx/releases/navigation
   - `security-crypto` (EncryptedSharedPreferences) — check Maven Central `androidx.security:security-crypto`
   - `datastore` — check developer.android.com/jetpack/androidx/releases/datastore

4. **Report findings** in a clear table:
   | Dependency | Current | Latest Stable | Status | Notes |
   |---|---|---|---|---|

5. **For any dependency outdated by a major or minor version**:
   - Explain the migration effort and breaking changes
   - Flag if there are known CVEs or security advisories

6. **If updates are straightforward** (patch or minor with no breaking changes):
   - Update `gradle/libs.versions.toml` directly — versions are centralized there, never inline in build files
   - Verify the change looks right before building:
     ```bash
     git diff gradle/libs.versions.toml
     ```
   - Run: `./gradlew assembleDebug 2>&1 | tail -20`
   - Commit: `chore(deps): bump [lib] from X to Y`

7. **If a major version update needs investigation**: report what needs manual review and skip the automated update.
