/**
 * Thin wrapper around fetch.
 *
 * - Always sends the session cookie (same origin).
 * - Echoes the XSRF-TOKEN cookie in the X-XSRF-TOKEN header on state
 *   changing requests (double submit CSRF protection).
 * - Turns problem+json error bodies into ApiError so screens can show the
 *   server's message and highlight fields.
 */

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fields: Record<string, string>

  constructor(status: number, code: string, message: string, fields: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.fields = fields
  }
}

type Listener = (error: ApiError) => void
const authListeners = new Set<Listener>()

/** Lets the auth layer react to expired sessions and forced password changes. */
export function onAuthProblem(listener: Listener): () => void {
  authListeners.add(listener)
  return () => authListeners.delete(listener)
}

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(name + '='))
  return match ? decodeURIComponent(match.substring(name.length + 1)) : null
}

let csrfPrimed = false

/** Makes sure the CSRF cookie exists before the first write. */
async function ensureCsrf(): Promise<void> {
  if (csrfPrimed && readCookie('XSRF-TOKEN')) return
  await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  csrfPrimed = true
}

async function toError(response: Response): Promise<ApiError> {
  let body: { code?: string; detail?: string; fields?: Record<string, string> } = {}
  try {
    body = await response.json()
  } catch {
    // Non JSON error body; fall back to a generic message
  }
  return new ApiError(
    response.status,
    body.code ?? 'HTTP_' + response.status,
    body.detail ?? 'The request failed (' + response.status + ')',
    body.fields ?? {},
  )
}

async function request<T>(method: string, url: string, body?: unknown, isForm = false, quiet = false): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const writes = method !== 'GET'
  if (writes) {
    await ensureCsrf()
    const token = readCookie('XSRF-TOKEN')
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'

  const response = await fetch(url, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
  })

  if (!response.ok) {
    const error = await toError(response)
    // A quiet request (the initial "who am I" check) must not trigger the signed out flow
    if (!quiet && url !== '/api/auth/login' && (error.status === 401 || error.code === 'PASSWORD_CHANGE_REQUIRED')) {
      authListeners.forEach((l) => l(error))
    }
    // An expired CSRF token: refresh once so the next attempt works
    if (error.code === 'CSRF_INVALID') csrfPrimed = false
    throw error
  }
  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  get: <T>(url: string) => request<T>('GET', url),
  /** GET that reports 401 to the caller only. */
  probe: <T>(url: string) => request<T>('GET', url, undefined, false, true),
  post: <T>(url: string, body?: unknown) => request<T>('POST', url, body ?? {}),
  put: <T>(url: string, body: unknown) => request<T>('PUT', url, body),
  del: <T>(url: string) => request<T>('DELETE', url),
  upload: <T>(url: string, form: FormData) => request<T>('POST', url, form, true),
}

/**
 * Downloads a protected file with the session cookie and hands it to the
 * browser. The object URL is revoked shortly after to free memory.
 */
export async function openDocument(url: string, fileName: string, download = false): Promise<void> {
  const response = await fetch(url, { credentials: 'same-origin' })
  if (!response.ok) throw await toError(response)
  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  if (download) {
    const a = document.createElement('a')
    a.href = objectUrl
    a.download = fileName
    a.rel = 'noopener'
    a.click()
  } else {
    window.open(objectUrl, '_blank', 'noopener')
  }
  setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000)
}

/** Builds a query string, skipping empty values. */
export function qs(params: Record<string, string | number | undefined | null>): string {
  const p = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v))
  })
  const s = p.toString()
  return s ? '?' + s : ''
}
