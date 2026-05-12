# Repository Guidelines

## Project Structure & Module Organization

Muninn-Weather is a single-module Android Kotlin app. Application code lives in `app/src/main/java/com/studiosleepygiraffe/muninnweather/`, grouped by role: `data/` for weather models and encrypted storage, `network/` for Home Assistant API access, `receiver/` for boot handling, and `worker/` for WorkManager scheduling and sync execution. Android resources are in `app/src/main/res/`, including layouts under `layout/`, drawables under `drawable/`, and shared values under `values/`. Build configuration is split between root `build.gradle.kts`, `settings.gradle.kts`, and `app/build.gradle.kts`.

## Build, Test, and Development Commands

- `docker compose run --rm android-build` builds the debug APK in a reproducible Android container; output appears in `app/build/outputs/apk/debug/`.
- `./gradlew assembleDebug` builds a local debug APK when a compatible Android SDK and Gradle wrapper are available.
- `./gradlew assembleRelease` builds a release APK; signing requires `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.
- `./gradlew lint` runs Android lint checks if local Gradle tooling is configured.

## Coding Style & Naming Conventions

Use Kotlin with Java 17 compatibility. Follow existing Android/Kotlin conventions: 4-space indentation, `PascalCase` for classes and adapters, `camelCase` for functions and properties, and package names under `com.studiosleepygiraffe.muninnweather`. Keep UI code in activities/adapters, network calls in `network/`, persistence in `data/`, and background behavior in `worker/`. Prefer small, focused functions and avoid broad rewrites unless they simplify the design.

## Testing Guidelines

There is currently no dedicated test source set in this repository. When adding tests, use Android’s standard layout: `app/src/test/` for JVM unit tests and `app/src/androidTest/` for instrumentation tests. Name tests after the behavior under test, for example `WeatherStorageTest` or `HaClientTest`. Run targeted tests with `./gradlew testDebugUnitTest` and instrumentation tests with `./gradlew connectedDebugAndroidTest` when a device or emulator is available.

## Commit & Pull Request Guidelines

Recent history uses concise Conventional Commit-style prefixes, especially `fix:`. Use short, imperative commit subjects such as `fix: improve worker scheduling` or `feat: add sensor refresh action`. Pull requests should describe the behavior change, list validation performed, link related issues when available, and include screenshots or screen recordings for UI changes. Note any Home Assistant, Gadgetbridge, battery optimization, or signing implications explicitly.

## Security & Configuration Tips

Do not commit Home Assistant URLs, long-lived access tokens, passwords, or new signing keys. Keep release signing values in environment variables, not Gradle files. Treat `munnin-weather-keys.jks` and any replacement keystores as sensitive material.
