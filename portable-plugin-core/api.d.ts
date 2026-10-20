/** Contract v1; no runtime dependency. Model payloads use plugin-sdk 1.0.0 JSON. */
export type Json = null | boolean | number | string | Json[] | { [key: string]: Json };
export interface PortableManifest {
  id: string;
  name: string;
  version: string;
  apiVersion: 1;
  entry: string;
  methods: string[];
  allowedHosts: string[];
}
export interface ViperHost {
  readonly apiVersion: 1;
  http(request: {
    url: string;
    method?: 'GET' | 'POST';
    headers?: Record<string, string>;
    body?: string;
  }): Promise<{ status: number; body: string }>;
}
export interface PluginError {
  code: 'NETWORK' | 'AUTH_REQUIRED' | 'NOT_FOUND' | 'NOT_AVAILABLE' | 'RATE_LIMITED' |
    'INVALID_REQUEST' | 'PERMISSION_DENIED' | 'UNSUPPORTED' | 'PLAYBACK' | 'INTERNAL';
  message: string;
  retryable?: boolean;
}
export type PortablePlugin = Record<string, (args: Json, host: ViperHost) => Promise<Json> | Json>;
