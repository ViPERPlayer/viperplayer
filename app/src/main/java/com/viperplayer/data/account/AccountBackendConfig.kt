package com.viperplayer.data.account

/**
 * Parsed ViPER backend gRPC endpoint, derived from the `VIPER_BACKEND_GRPC` BuildConfig field.
 *
 * The app talks gRPC/protobuf over HTTP/2. The config value is a `host:port` string. Transport is
 * TLS by default (prod); a dev/h2c server with no TLS is opted into with a `plaintext://` prefix
 * (e.g. `plaintext://10.0.2.2:9090`). While the field holds the build placeholder — or is blank or
 * malformed — [isConfigured] is false and the account UI stays gated (auth is impossible).
 *
 * Pure value type with no Android/network dependencies so the parsing is unit-testable.
 */
data class AccountBackendConfig(
    val host: String,
    val port: Int,
    val useTls: Boolean,
) {
    companion object {
        /** BuildConfig placeholder that means "no backend wired in". */
        const val PLACEHOLDER = "REPLACE_WITH_REAL_VALUE"

        private const val PLAINTEXT_PREFIX = "plaintext://"
        private const val DEFAULT_PORT = 9090

        /**
         * Parses a `host:port` (optionally `plaintext://host:port`) endpoint. Returns null when the
         * value is the placeholder, blank, or not a parseable host:port — in which case the account
         * feature is treated as not configured.
         *
         * Host is expected to be a DNS name or IPv4 literal (prod is e.g. `api.viper.player:9090`).
         * Bracketed IPv6 literals (`[::1]:9090`) are NOT supported — the last-colon split would mangle
         * them; use a hostname instead.
         */
        fun parse(raw: String?): AccountBackendConfig? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty() || trimmed == PLACEHOLDER) return null

            val plaintext = trimmed.startsWith(PLAINTEXT_PREFIX)
            val authority = if (plaintext) trimmed.removePrefix(PLAINTEXT_PREFIX) else trimmed
            if (authority.isBlank()) return null

            val colon = authority.lastIndexOf(':')
            val host: String
            val port: Int
            if (colon <= 0 || colon == authority.length - 1) {
                // No explicit port (or a leading/trailing colon) — accept a bare host with the default.
                host = authority.removeSuffix(":")
                port = DEFAULT_PORT
            } else {
                host = authority.substring(0, colon)
                port = authority.substring(colon + 1).toIntOrNull() ?: return null
            }
            if (host.isBlank() || port !in 1..65535) return null

            return AccountBackendConfig(host = host, port = port, useTls = !plaintext)
        }
    }
}
