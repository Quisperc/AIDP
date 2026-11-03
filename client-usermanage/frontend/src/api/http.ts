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

/**
 * 后端接口基址。
 *
 * 前端部署在 EdgeOne Pages（独立域名）时与后端 um.civer.cn 不同源，
 * 相对路径会打到前端自己域名上，因此必须显式配置绝对地址。
 * 同源部署（jar 内置 static）时不设置该变量即可。
 */
export const API_BASE: string = (import.meta.env.VITE_API_BASE as string | undefined) ?? ''

export function readCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

type Options = Omit<RequestInit, 'body'> & { body?: unknown }

async function request<T>(path: string, options: Options = {}): Promise<T> {
  const { body, headers, ...rest } = options

  const init: RequestInit = {
    /*
     * 必须是 include 而不是 same-origin：
     * 跨域场景下 same-origin 不会携带会话 Cookie，请求会全部变成未登录。
     * 后端需配合：CORS 允许该来源且 Allow-Credentials，
     * Cookie 设 SameSite=None; Secure。
     */
    credentials: 'include',
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

  const res = await fetch(API_BASE + path, init)

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
