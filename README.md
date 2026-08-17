# ViPER Player

An Android music player built around two ideas: **your sources are plugins**, and **the audio path is
yours to shape**. The host owns the library, the queue and the player; separate plugin APKs feed it
content, and a native DSP engine — a reconstruction of ViPER4Android's effect chain — sits in the
output path.

> **Status:** in active development. Expect rough edges and breaking changes.

## What's in it

**Playback and library** — a local library scanned from `MediaStore`, playlists with M3U import and
export, downloads for offline listening, play history and listening statistics, gapless playback,
ReplayGain (track and album), a sleep timer, and Android Auto support.

**ViPER FX** — the DSP chain runs natively on every buffer: a parametric IIR equalizer, convolver
(load your own impulse responses), ViPER Bass, ViPER Clarity, Headphone Surround+, Reverberation,
Dynamic System, FET Compressor, Spectrum Extension, Analog X, Tube Simulator, Playback Gain, DDC
support, and a software limiter.

**On-device recommendations** — a CLAP audio model embeds your library locally and drives
"more like this", a discovery feed and a generated daylist. Nothing about your listening leaves the
device.

**Lyrics** — synced word-by-word lyrics with an LRCLIB fallback, and romanization for non-Latin
scripts.

**Extras** — Last.fm scrobbling, a home-screen widget, alarms, and account-backed library sync.

## Plugins

The host discovers plugins by intent — any installed APK exposing
`com.viperplayer.plugin.ViperPluginService` is offered in Settings. A plugin can be a music source, a
DSP effect, a lyrics provider, a scrobble sink, a metadata enricher, or several at once.

Build one against the [plugin SDK](https://github.com/ViPERPlayer/plugin-sdk):

```kotlin
implementation("io.github.viperplayer:plugin-sdk:<version>")
```

Worked examples: [plugin-example](https://github.com/ViPERPlayer/plugin-example) (a music source) and
[dsp-example](https://github.com/ViPERPlayer/dsp-example) (an audio effect).

The only plugin bundled with the app is `local`, which serves the on-device library.

## Building

```bash
git clone --recurse-submodules https://github.com/ViPERPlayer/viperplayer.git
cd viperplayer
./gradlew :app:assembleDebug
```

Requires JDK 17 and the Android SDK (compileSdk 37, minSdk 26). The build uses a
[fork of Jetpack Media3](https://github.com/ViPERPlayer/media) as a git submodule and a composite
build, so `--recurse-submodules` matters.

Several integrations are placeholders until you supply your own credentials — see the
`buildConfigField` entries in `app/build.gradle.kts` for the Last.fm API key, the backend URL, the
updater manifest and the recommendation model manifest. The app runs without them; the corresponding
features report that they are not configured.

To develop against a local checkout of the SDK instead of the published artifact:

```bash
./gradlew :app:assembleDebug -Pviper.pluginSdk.dir=../plugin-sdk
```

## Architecture

Standard MVVM over a clean-architecture split: `domain` holds models and repository interfaces and
depends on nothing else, `data` implements them (Room, DataStore, Ktor, Media3, the plugin IPC
client), and `presentation` is Compose UI plus ViewModels with no data access of its own. Hilt wires
it together. `CLAUDE.md` documents the conventions the codebase is held to.

The DSP is C++ under `app/src/main/cpp`, reached through a thin JNI layer. Effect parameters are
staged by the control thread and applied on the audio thread, so nothing mutates DSP state
underneath a render. It has its own host-side test suite (`./gradlew :app:nativeDspTest`) that runs
on the build machine without a device.
