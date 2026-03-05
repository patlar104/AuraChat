---
name: aurachat-build-test
description: Build and test AuraChat. Use when building, running tests, linting, or when the user mentions assembleDebug, test, lint, connectedAndroidTest, or gradle.
---

# AuraChat Build & Test

## Build Commands

```bash
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease       # Release APK
./gradlew clean                 # Clean outputs
```

## Test Commands

```bash
./gradlew test                  # All unit tests
./gradlew testDebugUnitTest --tests "com.personal.aurachat.MyTest"  # Single test class
./gradlew connectedAndroidTest   # Instrumented tests (requires device/emulator)
```

## Lint

```bash
./gradlew lint
```

## Dependency Updates

All versions are in `gradle/libs.versions.toml`. Update versions there only; never inline in build files.
