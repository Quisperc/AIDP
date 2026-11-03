import { API_BASE, http } from './http'

/** 后端返回的用户（管理后台侧，password 恒为空） */
export interface UserDto {
  id?: number | null
  username: string
  password?: string | null
  role?: string
  enabled?: boolean
}

export interface ClientSummaryDto {
  id: string
  clientId: string
  clientName?: string | null
  redirectUri?: string | null
  postLogoutRedirectUri?: string | null
  requirePkce?: boolean
}

export interface ClientDto {
  clientId: string
  clientSecret?: string | null
  redirectUri?: string | null
  postLogoutRedirectUri?: string | null
  clientName?: string | null
  requirePkce?: boolean | null
}

export interface MeResponse {
  authenticated: boolean
  username: string | null
  roles: string[]
}

export const api = {
  me: () => http.get<MeResponse>('/api/me'),

  users: {
    list: () => http.get<UserDto[]>('/admin/users'),
    get: (id: number) => http.get<UserDto>(`/admin/users/${id}`),
    create: (u: UserDto) => http.post<UserDto>('/admin/users', u),
    update: (id: number, u: Partial<UserDto>) => http.put<UserDto>(`/admin/users/${id}`, u),
    remove: (id: number) => http.del<void>(`/admin/users/${id}`),
  },

  clients: {
    list: () => http.get<ClientSummaryDto[]>('/admin/clients'),
    get: (clientId: string) => http.get<ClientSummaryDto>(`/admin/clients/${encodeURIComponent(clientId)}`),
    create: (c: ClientDto) => http.post<void>('/admin/clients', c),
    update: (clientId: string, c: ClientDto) =>
      http.put<void>(`/admin/clients/${encodeURIComponent(clientId)}`, c),
    remove: (clientId: string) => http.del<void>(`/admin/clients/${encodeURIComponent(clientId)}`),
  },

  profile: {
    get: () => http.get<UserDto>('/admin/profile'),
    update: (u: Partial<UserDto>) => http.put<void>('/admin/profile', u),
  },
}

/**
 * OAuth2 登录入口（Spring Security 提供）。
 * 跨域部署时必须是后端绝对地址 —— 登录是整页跳转，不走 fetch。
 */
export const OAUTH_LOGIN_URL = `${API_BASE}/oauth2/authorization/oidc-client`

/** 前端自身地址：登录后由后端重定向回这里（同源部署时为空，交给相对路径） */
export const FRONTEND_URL: string = (import.meta.env.VITE_FRONTEND_URL as string | undefined) ?? ''
