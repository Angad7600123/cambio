# Third-party notices

Cambio is distributed under the MIT License. It depends on the third-party
components listed below, each under its own licence.

## Exchange-rate data

**ExchangeRate-API** — <https://www.exchangerate-api.com>

Exchange rates are retrieved from ExchangeRate-API's free open endpoint
(`https://open.er-api.com/v6/latest/USD`). No API key or account is required.

> **Attribution requirement.** ExchangeRate-API's terms of use require
> applications using the free open endpoint to display visible attribution
> linking back to <https://www.exchangerate-api.com>.
>
> Cambio satisfies this with a tappable "Rates by ExchangeRate-API" link in the
> app's Settings sheet (see
> [`SettingsSheet.kt`](app/src/main/java/io/github/angad7600123/cambio/ui/sheets/SettingsSheet.kt)),
> plus the attribution in this file and in the README.
>
> **If you fork this project, keep that link.** Removing it puts your build out
> of compliance with the provider's terms.

Rate data itself is provided by ExchangeRate-API and is not covered by this
project's MIT licence. Review their [terms of use](https://www.exchangerate-api.com/terms)
before relying on the data commercially.

## Software dependencies

| Component | Licence |
| --- | --- |
| Android Gradle Plugin | Apache License 2.0 |
| Kotlin, Kotlin Coroutines, kotlinx.serialization (JetBrains) | Apache License 2.0 |
| AndroidX Core, Activity, Lifecycle, DataStore, Glance (Google) | Apache License 2.0 |
| Jetpack Compose, Material 3, Material Icons (Google) | Apache License 2.0 |
| OkHttp, MockWebServer (Square) | Apache License 2.0 |
| Turbine (Cash App) | Apache License 2.0 |
| JUnit 4 | Eclipse Public License 1.0 |
| Robolectric | MIT License |
| ktlint (Pinterest) | MIT License |
| Spotless (DiffPlug) | Apache License 2.0 |

Every runtime dependency is either Apache 2.0 or MIT licensed; both are
compatible with this project's MIT licence. JUnit (EPL 1.0) and Robolectric are
test-only and are not distributed in the application binary.

Full licence texts are published by each project and are also bundled inside the
respective artifacts in the Gradle cache.

## Fonts and assets

Cambio bundles no fonts and no binary image assets. It uses the system font
provided by the device, and its launcher icon is drawn as a vector
(`ic_launcher_foreground.xml`). Currency flags are Unicode regional-indicator
emoji rendered by the device's own emoji font.

Currency names, symbols and minor-unit counts come from the Android platform's
ISO 4217 data via `java.util.Currency` rather than from a bundled table.
