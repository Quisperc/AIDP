import { computed, reactive } from 'vue'
import { FRONTEND_URL, MeResponse, OAUTH_LOGIN_URL, api } from '../api'
import { API_BASE, ApiError } from '../api/http'

/**
 * 极简认证状态。
 *
 * 没有引入 Pinia：全站只有「当前是谁、是不是管理员」这一个跨页状态，
 * 一个 reactive 对象足够，多一层状态库反而增加心智负担。
 */
const state = reactive<{ me: MeResponse | null; loaded: boolean }>({
  me: null,
  loaded: false,
})

export function useAuth() {
  const authenticated = computed(() => state.me?.authenticated === true)
  const username = computed(() => state.me?.username ?? null)
  const roles = computed(() => state.me?.roles ?? [])
  const isAdmin = computed(() => roles.value.includes('ROLE_ADMIN'))

  /** 拉取当前用户。401 时不做跳转，交给调用方决定（避免每个页面都被强制重定向）。 */
  async function load(): Promise<MeResponse | null> {
    try {
      state.me = await api.me()
    } catch (err) {
      if (err instanceof ApiError && err.isUnauthorized) {
        state.me = { authenticated: false, username: null, roles: [] }
      } else {
        // 网络或后端故障：保留未知态，让页面显示错误而不是误判为未登录
        state.me = null
        throw err
      }
    } finally {
      state.loaded = true
    }
    return state.me
  }

  function login(redirectTo?: string): void {
    /*
     * 必须传完整 URL 而不是 pathname：
     * 跨域部署时后端要重定向回前端域名，只给 /users 它会拼成自己域名下的地址。
     * 后端会对该地址做 host 白名单校验，避免变成开放重定向。
     */
    const target = redirectTo ?? window.location.href
    window.location.href = `${OAUTH_LOGIN_URL}?redirect=${encodeURIComponent(target)}`
  }

  /**
   * 退出：先清本端会话，再让认证中心撤销授权并广播全局登出。
   * 直接访问认证中心的 logout-all 才能把其它子系统一并踢下线。
   */
  function logout(): void {
    const authServer = import.meta.env.VITE_AUTH_SERVER_URL ?? ''
    // 明确回跳到前端地址；跨域部署时 window.location.origin 就是 EdgeOne 域名
    const returnTo = `${FRONTEND_URL || window.location.origin}/login`
    const localLogout = `${API_BASE}/logout`
    if (authServer) {
      // 走认证中心的全局登出：撤销 consent 并向各子系统广播
      window.location.href =
        `${authServer}/oauth2/logout-all?redirect_uri=${encodeURIComponent(returnTo)}`
    } else {
      window.location.href = localLogout
    }
  }

  return { state, authenticated, username, roles, isAdmin, load, login, logout }
}
