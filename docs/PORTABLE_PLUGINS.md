# Portable source plugins — initial prototype

This branch adds `portable-plugin-core` (QuickJS + a JSON host contract) and
`portable-plugin-android` (an adapter to the existing plugin SDK). No WASM is used.
The implementation uses `io.github.dokar3:quickjs-kt:1.0.15`.

The source is a plain UTF-8 `plugin.js` plus `manifest.json`, bundled in an APK's
assets for this prototype. JavaScript exports `globalThis.viperPlugin`, an object
of async methods. It receives JSON arguments and a host object; it returns JSON.
No Kotlin classes, Android APIs, Node APIs, DOM objects or QuickJS bytecode appear
in the plugin contract. See `portable-plugin-core/api.d.ts`.

## Android integration

The player continues to discover installed plugin APKs through the existing
SDK/Binder transport. The plugin service constructs
`PortableSourceProvider.fromAssets(context, "my-plugin")` and registers that
provider and its `capabilities`. Catalog methods now execute JavaScript in the
plugin process. The player UI and media playback service need no parallel API.

For a plugin checkout alongside this app checkout, add this to its settings:

```kotlin
includeBuild("../viperplayer/portable-plugins") {
    dependencySubstitution {
        substitute(module("io.github.viperplayer:portable-plugin-android"))
            .using(project(":portable-plugin-android"))
        substitute(module("io.github.viperplayer:portable-plugin-core"))
            .using(project(":portable-plugin-core"))
    }
}
```

Then depend on `io.github.viperplayer:portable-plugin-android:0.1.0-prototype`.
The composite build configures only the runtime modules, avoiding the player's
Media3 checkout. Android supplies QuickJS's Android variant; desktop JNI is used
only for JVM testing.

The companion YouTube prototype migrates catalog operations to JavaScript and
keeps its existing Android stream resolver behind a `SourceProvider` wrapper.
Its WebView player challenge/cipher path has **not** been made portable. The
YouTube repository documents exactly which operations are migrated.

## Contract v1

The manifest declares `id`, `name`, `version`, `apiVersion: 1`, `entry`, `methods`
and `allowedHosts`. Hosts must be exact lowercase DNS names. Only declared
methods may be invoked. Optional SDK capabilities are derived from these methods.

Method arguments and results follow SDK 1.0.0 JSON, including `#t` discriminators
(`song`, `video`, `album`, `artist`, `playlist`, `carousel`, etc.). Concrete
results such as `getSong` may omit the discriminator; mixed item arrays require
it. Paging cursors are opaque strings, and the provider owns pagination.

- `search`: `{query, types, page}` → SearchResult
- `getSearchSuggestions`: `{query}` → SearchSuggestions
- `getSong/getAlbum/getArtist/getPlaylist`: `{id}` → corresponding model
- `getArtistSongs/getArtistAlbums/getPlaylistSongs/getCategoryContents`: `{id,page}`
- `getBrowseCategories`: `{page}`; `getHome`: `{}`
- `getRelatedSongs`: `{id}` → Page<Song>
- `resolveStream`: `{id,type,maxBitrateKbps}` → StreamSource (`url`, `hls`, `dash`)

A plugin may throw `{code, message, retryable}`. Known codes map to the SDK's
errors; unknown codes become INTERNAL. Cancellation propagates to pending host
calls. HTTP errors are status/body values for the plugin to interpret; transport
and permission errors reject the promise.

## Runtime and permissions

Each invocation creates and disposes a runtime. Globals do not survive calls.
Two invocations per provider may run at once. Defaults: 32 MiB JS heap, 512 KiB
stack, 1 second active JS execution, 20 seconds total operation time. HTTP permits
GET/POST to manifest HTTPS hosts on port 443, rejects redirects and credential
headers, and caps request/response bodies at 256 KiB/2 MiB. Returned JSON is
limited to 512 KiB. There is no filesystem, native reflection, storage, cookie jar,
login bridge, module loader, or arbitrary native-call API.

These controls are defense in depth for **trusted bundled plugins**. This is not
an untrusted-code installer or a complete network sandbox: DNS/private-address
filtering, package signatures, permission UI, upgrades, and process isolation for
arbitrary downloaded plugins are future work. Do not expose an unrestricted
network proxy in a web host.

## iOS and web

The JavaScript source and JSON contract are reusable. The Android adapter and JVM
HTTP transport are not. An iOS host can implement the contract with QuickJS or
JavaScriptCore and its own HTTP/cancellation machinery. A web host can run the
same source using the browser's JavaScript engine in a dedicated Worker, with a
message-based host API and termination deadline. No QuickJS/WASM is needed there.

YouTube HTTP access from a browser is subject to CORS, cookies, and forbidden
headers (including User-Agent/Referer). A constrained backend or other permitted
transport may be needed. Its remaining Android playback resolver also requires a
separate solution. This branch is not a working iOS/web player.

## Validation

With JDK 17:

```sh
./gradlew -p portable-plugin-core/standalone :portable-plugin-core:test
./gradlew -p portable-plugins :portable-plugin-android:assembleDebug
```

The first command needs no Android SDK or Media3 submodule. It tests real
QuickJS/JNI execution, async HTTP, fresh globals, typed errors, permission denial,
infinite-loop interruption and cancellation. The second requires Android SDK 37.
Device installation/playback remains a separate manual check.

Initial validation (2026-10-20): all seven runtime tests passed locally with the
repository's Kotlin 2.4.10 toolchain. The first clean GitHub runner could not
resolve `org.jetbrains.kotlin:kotlin-gradle-plugins-bom:2.4.10`, a version already
selected by the app's version catalog. This dependency-resolution failure occurs
before compilation; CI is not green. Android compilation and on-device playback
are not yet verified. The prototype does not change the app's existing toolchain
versions to work around this repository/dependency issue.
