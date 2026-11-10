import type { AuthResponse } from './types'

/** Error body from the backend (RFC 9457 problem detail plus `code` and, for validation, `errors`). */
export class ApiError extends Error {
  readonly status: number
  readonly code?: string
  readonly fieldErrors?: Record<string, string>

  constructor(status: number, message: string, code?: string, fieldErrors?: Record<string, string>) {
    super(message)
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

// The access token lives only in memory. The refresh token is an HttpOnly cookie the browser
// sends to /api/auth/* by itself, so a page reload restores the session through refreshSession().
let accessToken: string | null = null
let sessionListener: (session: AuthResponse | null) => void = () => {}
let refreshInFlight: Promise<AuthResponse | null> | null = null

/** The current access token (null when signed out); the WebSocket sends it in its first frame. */
export function currentAccessToken(): string | null {
  return accessToken
}

export function onSessionChange(listener: (session: AuthResponse | null) => void) {
  sessionListener = listener
}

export function applySession(session: AuthResponse | null) {
  accessToken = session?.accessToken ?? null
  sessionListener(session)
}

/**
 * Exchanges the refresh cookie for a new session. Concurrent callers share one request: the backend
 * rotates refresh tokens and treats a reused one as stolen, so two parallel refreshes would sign the
 * user out (React StrictMode runs startup effects twice, which would trigger exactly that).
 */
export function refreshSession(): Promise<AuthResponse | null> {
  refreshInFlight ??= fetch('/api/auth/refresh', { method: 'POST' })
    .then(async (res) => (res.ok ? ((await res.json()) as AuthResponse) : null))
    .catch(() => null)
    .then((session) => {
      applySession(session)
      return session
    })
    .finally(() => {
      refreshInFlight = null
    })
  return refreshInFlight
}

/** `body` is sent as JSON, or as multipart when it is a FormData (file uploads). */
type RequestOptions = { method?: string; body?: unknown }

/** Calls the backend. Retries once after refreshing the session when the access token has expired. */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  // Public endpoints: an expired token sent along would make them fail with 401.
  const isPublic = path.startsWith('/api/auth/') || path.startsWith('/api/public/')
  const send = () => {
    const headers: Record<string, string> = {}
    const isForm = options.body instanceof FormData
    if (options.body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
    if (accessToken && !isPublic) headers.Authorization = `Bearer ${accessToken}`
    return fetch(path, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : isForm ? (options.body as FormData) : JSON.stringify(options.body),
    })
  }

  let res = await send()
  if (res.status === 401 && accessToken && !isPublic && (await refreshSession())) {
    res = await send()
  }
  if (res.status === 204) return undefined as T

  const data = await res.json().catch(() => null)
  if (!res.ok) {
    throw new ApiError(res.status, data?.detail ?? `HTTP ${res.status}`, data?.code, data?.errors)
  }
  return data as T
}

/** Downloads a file that needs the bearer token (so a plain link cannot): fetch it, then save the blob. */
/** `fileName` defaults to the one the server suggests in Content-Disposition. */
export async function downloadFile(path: string, fileName?: string): Promise<void> {
  const send = () => fetch(path, { headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {} })
  let res = await send()
  if (res.status === 401 && accessToken && (await refreshSession())) res = await send()
  if (!res.ok) {
    const data = await res.json().catch(() => null)
    throw new ApiError(res.status, data?.detail ?? `HTTP ${res.status}`, data?.code, data?.errors)
  }
  const suggested = /filename="?([^";]+)"?/.exec(res.headers.get('Content-Disposition') ?? '')?.[1]
  const url = URL.createObjectURL(await res.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = fileName ?? suggested ?? 'download'
  link.click()
  URL.revokeObjectURL(url)
}
