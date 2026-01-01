# Repository Guidelines

## Project Structure & Module Organization
- `composeApp/src/commonMain/kotlin` holds shared Kotlin and Compose UI code.
- `composeApp/src/androidMain` contains Android-specific Kotlin, `AndroidManifest.xml`, and `res/` assets.
- `composeApp/src/iosMain` contains iOS-specific Kotlin used by the shared UI.
- `iosApp/iosApp` is the SwiftUI entry point and iOS assets (`Assets.xcassets`).
- `docs/` contains project notes and prototypes.

## Build, Test, and Development Commands
- `./gradlew :composeApp:assembleDebug` builds the Android debug APK.
- `./gradlew :composeApp:allTests` runs multiplatform unit tests when present.
- iOS: open `iosApp` in Xcode and run the `iosApp` scheme (Gradle does not produce the iOS bundle here).

## Coding Style & Naming Conventions
- Kotlin: 4-space indentation; keep packages under `t.l.budget.loop`.
- Compose UI: `@Composable` functions in PascalCase (`App()`, `Greeting()`), matching file names like `App.kt`.
- Swift: follow Xcode defaults (types in PascalCase, methods in camelCase).
- Formatting: no formatter/linter configured; use IDE auto-format before committing.

## Testing Guidelines
- Shared tests go in `composeApp/src/commonTest/kotlin` using `kotlin.test`.
- Android-specific tests can go in `composeApp/src/androidTest/kotlin` as needed.
- Test file names should end with `*Test.kt`, and new logic should include unit tests.

## Commit & Pull Request Guidelines
- Git history currently has only `initial commit`, so no formal convention exists; use short, imperative summaries (e.g., “Add budget model”).
- PRs should include a concise summary, testing notes, and screenshots for UI changes; link related issues when available.

## Configuration & Secrets
- `local.properties` stores local SDK paths; keep it machine-specific and out of review.
