/** Contract v2 (v1 packages remain supported). Model payloads use plugin-sdk 1.0.0 JSON. */
export type Json = null | boolean | number | string | Json[] | { [key: string]: Json };
export interface PortableManifest {
  id: string;
  name: string;
  version: string;
  apiVersion: 1 | 2;
  entry: string;
  methods: string[];
  /** Exact HTTPS hostnames. */
  allowedHosts: string[];
  /** v2: HEAD only, includes the named domain and its subdomains. */
  headOnlyDomains?: string[];
  /** v2: optional DOM capability; host may still report browser: null. */
  capabilities?: ('browser')[];
}
export interface ScriptRequest {
  /** Function expression, e.g. "async args => ({value: args.value + 1})". */
  script: string;
  args?: Json;
}
export interface BrowserHost {
  /** HTTPS origin must be in allowedHosts. No navigation or network capability. */
  open(request: {origin: string; userAgent?: string}): Promise<number>;
  /** Sessions retain globals between evaluations, within this plugin invocation only. */
  evaluate(request: ScriptRequest & {id: number}): Promise<Json>;
  close(id: number): Promise<void>;
}
export interface ViperHost {
  readonly apiVersion: 1 | 2;
  /** v2: fresh ECMAScript realm per call, no DOM or host bindings. */
  readonly javascript: {evaluate(request: ScriptRequest): Promise<Json>} | null;
  /** v2: optional DOM evaluator. Unavailable or ungranted capabilities are null. */
  readonly browser: BrowserHost | null;
  http(request: {
    url: string;
    method?: 'GET' | 'POST' | 'HEAD';
    headers?: Record<string, string>;
    body?: string;
    /** Default 2 MiB; maximum 8 MiB. */
    maxResponseBytes?: number;
    /** Default and maximum 10 seconds. */
    timeoutMs?: number;
  }): Promise<{ status: number; body: string }>;
}
export interface PluginError {
  code: 'NETWORK' | 'TIMEOUT' | 'AUTH_REQUIRED' | 'NOT_FOUND' | 'NOT_AVAILABLE' | 'RATE_LIMITED' |
    'INVALID_REQUEST' | 'PERMISSION_DENIED' | 'UNSUPPORTED' | 'PLAYBACK' | 'INTERNAL';
  message: string;
  retryable?: boolean;
}
export type PortablePlugin = Record<string, (args: Json, host: ViperHost) => Promise<Json> | Json>;
