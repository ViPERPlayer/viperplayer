# ViPER Player — project conventions

## Kotlin style (hard rules)

- **Never use inline fully-qualified names. Always import.** Add an `import` and use the
  simple name — never write out the package inline (e.g. `android.util.Log.i(...)`,
  `androidx.compose.ui.platform.LocalContext.current`, `private fun android.database.Cursor.x()`).
  The only exception is a genuine name conflict within the file, and then only the *less-used*
  symbol stays fully qualified.
- **No data/business logic in the UI layer.** Composables and Activities only render state and
  forward events; scanners, repositories, managers, network/DB access live in a ViewModel (plugins
  use `AndroidViewModel` + `viewModel()`, the host app uses Hilt).

## Build

- Build with the Android Studio JBR (system JDK 26 breaks AGP):
  `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew ...`
- Plugins (`testsource`, `othersource`, `testsource`, ...) are **separate installable APKs**; only
  `local` is embedded in the host. Changing a plugin requires rebuilding + reinstalling *that*
  plugin APK for the change to take effect on-device.
