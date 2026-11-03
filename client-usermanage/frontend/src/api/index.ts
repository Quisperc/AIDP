import { http } from './http'

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

/** OAuth2 登录入口（由 Spring Security 提供） */
export const OAUTH_LOGIN_URL = '/oauth2/authorization/oidc-client'
