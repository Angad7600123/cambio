# Contributing to Cambio

Thanks for taking an interest. This is a small project, so the process is light.

## Before you start

For anything beyond a bug fix or a typo, please open an issue first so we can agree
on the approach. That saves you building something that turns out not to fit.

## Getting set up

1. Fork and clone the repository.
2. Create `local.properties` pointing at your Android SDK:
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```
3. Open in Android Studio, or build from the command line with `./gradlew`.

You need JDK 21 and Android SDK platform 36.

## Before you open a pull request

Run all four:

```bash
./gradlew spotlessApply
./gradlew lintDebug
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

If you have a device or emulator connected, also run the UI tests:

```bash
./gradlew connectedDebugAndroidTest
```

CI runs the same checks, so a green local run usually means a green PR.

## What we look for

- **Keep the calculator engine free of Android dependencies.** Everything in
  `calculator/` is pure Kotlin, and that is what makes it testable and shareable
  between the app and the widget. Please keep it that way.
- **Add tests for behaviour, not for coverage.** A test that pins down a real rule —
  a precedence case, a rounding edge, a failure mode — is welcome. A test that
  exercises a getter is not.
- **No new dependencies without a reason.** The dependency list is deliberately
  short. If a few lines of Kotlin will do, prefer the few lines.
- **Errors must be explicit.** The engine returns typed results rather than throwing,
  and the data layer returns typed failures. Please follow that.
- **Match the surrounding style.** ktlint via Spotless is the arbiter; run
  `spotlessApply` and it will sort out formatting.

## Things that will be declined

- Removing or hiding the ExchangeRate-API attribution link. It is a requirement of
  the provider's terms, not a decoration.
- Adding analytics, telemetry, crash reporting or advertising. The app collects
  nothing, and that is a deliberate promise made in the README.
- Committing secrets, keystores, or `local.properties`.

## Reporting bugs

Please include your Android version, device, and the exact key sequence or currency
pair that reproduces the problem. For calculator bugs, the expression matters — a
lot of the interesting cases live in precedence and percent handling.

## Licence

By contributing, you agree that your contributions will be licensed under the
[MIT License](LICENSE) that covers this project.
