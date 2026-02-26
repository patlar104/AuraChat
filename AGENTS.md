# AuraChat

Android AI chat app built with Kotlin, Jetpack Compose, and Google Gemini API.

## Cursor Cloud specific instructions

### Environment prerequisites

- **JDK 21** (pre-installed on Cloud VM)
- **Android SDK** at `/opt/android-sdk` with `ANDROID_HOME` exported in `~/.bashrc`
  - Required packages: `platforms;android-36`, `build-tools;36.0.0`, `platform-tools`
  - Licenses are pre-accepted

### Key commands

| Task | Command |
|------|---------|
| Build debug APK | `./gradlew :app:assembleDebug` |
| Run unit tests | `./gradlew :app:testDebugUnitTest` |
| Run lint | `./gradlew :app:lint` |
| Clean build | `./gradlew clean` |

### Notes

- This is a single-module Android project (`:app`). No backend server is needed.
- The first Gradle build downloads dependencies and may take ~5 minutes. Subsequent builds use cache and are much faster (~10s).
- Instrumented tests (`connectedDebugAndroidTest`) require an Android emulator or device and cannot run in headless Cloud VMs.
- The app requires a Google Gemini API key at runtime (entered in the Settings screen). This is not needed for building or running unit tests.
- Lint report is written to `app/build/reports/lint-results-debug.html`.
- Unit test results are in `app/build/test-results/testDebugUnitTest/`.
