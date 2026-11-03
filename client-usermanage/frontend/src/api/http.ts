/**
 * 统一请求封装。
 *
 * 两件事值得说明：
 * 1. CSRF：后端用 CookieCsrfTokenRepository（HttpOnly=false），
 *    这里从 XSRF-TOKEN cookie 读出并回传 X-XSRF-TOKEN 头；
 *    没有它，所有 POST/PUT/DELETE 都会被 Spring Security 拒绝。
 * 2. 401：后端对数据接口返回 401 而不是登录页，这里据此触发全局跳转。
 */

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }

  get isUnauthorized(): boolean {
    return this.status === 401
  }

  get isForbidden(): boolean {
    return this.status === 403
  }

  get isConflict(): boolean {
    return this.status === 409
  }
}

export function readCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

type Options = Omit<RequestInit, 'body'> & { body?: unknown }

async function request<T>(path: string, options: Options = {}): Promise<T> {
  const { body, headers, ...rest } = options

  const init: RequestInit = {
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json',
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...headers,
    },
    ...rest,
  }

  // 非安全方法带上 CSRF 头
  if (rest.method && !['GET', 'HEAD', 'OPTIONS'].includes(rest.method)) {
    const token = readCsrfToken()
    if (token) {
      ;(init.headers as Record<string, string>)['X-XSRF-TOKEN'] = token
    }
  }

  if (body !== undefined) {
    init.body = JSON.stringify(body)
  }

  const res = await fetch(path, init)

  if (res.status === 204) {
    return undefined as T
  }

  if (!res.ok) {
    let message = `请求失败（${res.status}）`
    try {
      const payload = await res.json()
      if (typeof payload?.message === 'string' && payload.message) {
        message = payload.message
      }
    } catch {
      // 非 JSON 响应（例如网关返回的 HTML 错误页），沿用默认文案
    }
    throw new ApiError(res.status, message)
  }

  return (await res.json()) as T
}

export const http = {
  get: <T>(path: string) => request<T>(path, { method: 'GET' }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
