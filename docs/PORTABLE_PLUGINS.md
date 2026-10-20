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
provider and its `capabilities`. Catalog and stream-resolution methods execute JavaScript in the
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

The standalone Android composite defaults to AGP 9.3.1, matching the companion
YouTube APK, and needs Gradle 9.5.0. A different consuming APK can select its AGP
with `-Pviper.portable.agp=<version>`; Gradle requires matching AGP versions across
included Android builds. Modules built inside the player use the player's own
version catalog instead.

The companion YouTube prototype moves catalog and playback policy into its JavaScript
package. The public runtime remains provider-neutral: no YouTube URL matching,
player parsing, token protocol, or cipher implementation lives in Kotlin.

## Contract v2

The manifest declares identity, entry, methods and exact lowercase `allowedHosts`.
Version 2 adds `headOnlyDomains` (HEAD requests to that domain and its subdomains)
and optional `capabilities: ["browser"]`. API v1 packages remain supported.
Only declared methods may be invoked. SDK capabilities are derived from methods.

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
Two invocations per provider may run at once. Catalog limits: 32 MiB JS heap,
512 KiB stack, 1 second active JS execution, 20 seconds total. `resolveStream`
gets 96 MiB and 60 seconds total, retaining the 1 second main-script CPU limit.
HTTP permits GET/POST/HEAD to manifest HTTPS hosts on port 443, rejects redirects
and credential headers, and caps requests at 256 KiB. Responses default to 2 MiB;
plugins may request up to 8 MiB for large scripts. HEAD does not read the body.
Returned JSON is limited to 512 KiB. There is no filesystem, native reflection,
persistent storage API, login bridge, module loader, or arbitrary native-call API.

### Isolated JavaScript

`host.javascript.evaluate({script,args})` executes a function expression in a new
QuickJS realm. No host bindings, network, DOM, or state from the caller is exposed.
The JSON result is awaited and the realm disposed. This is the intended place for
provider-supplied parsers and downloaded transformation code. Limits: two calls per
operation, with one active isolated realm per process; 16 MiB input, 256 MiB heap, 1 MiB stack, 10 seconds active
execution, 15 seconds total, 512 KiB result. Main-operation cancellation closes it.
Provider-specific libraries are packaged with the provider, not with this host.

### Optional browser capability

`host.browser` is null unless granted by the manifest and supported by the host.
It provides `open({origin,userAgent})`, `evaluate({id,script,args})`, and `close(id)`.
The function expression receives JSON arguments and can return a Promise. Globals
persist only within a session. `open` accepts an exact permitted HTTPS origin;
there is no arbitrary page navigation, HTML-loading API, or provider-specific method.
At most two sessions may be open per operation. Inputs are bounded to 12 MiB and
outputs to 512 KiB. Sessions are automatically closed on success, error or cancellation.

Android supplies an offscreen WebView with network loading, file/content access,
storage, geolocation and navigation disabled. Provider network requests go through
`host.http`. Creation times out after 5 seconds; each evaluation after 12 seconds.
Per-call nonces discard late results; renderer death fails pending work and destroys
the session. These are temporary DOM tasks, not persistent background workers.
A local browser timeout is a typed failure so the provider can choose another path;
caller cancellation remains cancellation.

These controls are defense in depth for **trusted bundled plugins**. This is not
an untrusted-code installer or a complete network sandbox: DNS/private-address
filtering, package signatures, permission UI, upgrades, and process isolation for
arbitrary downloaded plugins are future work. Do not expose an unrestricted
network proxy in a web host.

## iOS and web

The shared unit is source JavaScript plus the JSON contract, not bytecode. Keep
ordinary provider logic and transforms in the interpreter. For iOS, a native
QuickJS build can provide the same engine semantics and interruption limits;
JavaScriptCore is another possible host implementation. Supply networking with
URLSession and cancellation, and keep audio in the native playback engine.

An optional iOS browser adapter would implement the same session API with
`WKWebView`, a fresh nonpersistent website data store, JSON arguments/results and
`callAsyncJavaScript`. It must enforce network/navigation restrictions and dispose
sessions on timeout, cancellation and process termination. A navigation delegate
alone does not block subresource requests; content rules/CSP and an explicit
network-isolation test are needed. Setting a base URL or user agent is not proof
that a remote attestation service accepts the resulting browser environment.

Offscreen WebKit tasks can be suspended. iOS 17+ exposes
`WKPreferences.inactiveSchedulingPolicy`; use any relaxed policy only during a
bounded active operation and restore it afterward. This is not an entitlement to
run indefinitely when the app is backgrounded. Plan for unavailable browser work,
prefetch upcoming stream resolutions, and test lock-screen track changes, expired
URLs, renderer termination and resume on real devices. The host can report
`browser: null` when the capability cannot run. Provider fallback behavior is still
subject to the upstream service accepting those clients.

A web host can use a Worker for provider logic and a separate Worker for isolated
script execution, with deadlines enforced by termination. No QuickJS/WASM is needed.
Optional DOM work needs an isolated frame and a message bridge. A frame cannot
impersonate an arbitrary HTTPS origin; CORS, cookie rules and forbidden headers
(including User-Agent/Referer) still apply. A constrained transport service may be
needed. This branch implements neither an iOS host nor a web player.

References:
- [Apple async JavaScript API](https://developer.apple.com/documentation/webkit/wkwebview/callasyncjavascript(_:arguments:in:contentworld:))
- [WebKit offscreen scheduling explanation](https://bugs.webkit.org/show_bug.cgi?id=283794)
- [Apple nonpersistent data store](https://developer.apple.com/documentation/webkit/wkwebsitedatastore/nonpersistent())

## Validation

With JDK 17:

```sh
./gradlew -p portable-plugin-core/standalone :portable-plugin-core:test
gradle -p portable-plugins :portable-plugin-android:assembleDebug # Gradle 9.5.0
```

The first command needs no Android SDK or Media3 submodule. It tests real
QuickJS/JNI execution, async HTTP, fresh globals, typed errors, permission denial,
infinite-loop interruption and cancellation. The second requires Android SDK 37.
Device installation/playback remains a separate manual check.

The runtime suite additionally checks optional capability grants, origin validation,
HEAD-only domain boundaries, browser cleanup on error/cancellation, and isolated
JavaScript interruption and lack of host bindings. The companion plugin suite
checks playback policy and challenge orchestration; APK assembly runs in its CI.
Device playback and iOS/WebKit acceptance remain separate manual checks.
