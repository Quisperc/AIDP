<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { useAuth } from './stores/auth'

// 登录跳转由 LoginView 与路由守卫负责，这里只需要状态与退出
const { authenticated, username, isAdmin, load, logout } = useAuth()
const route = useRoute()

// 备案信息：与 auth-server 的 app.footer.* 保持一致，修改时两边要同步
const ICP_TEXT = '渝ICP备2026020534号'
const ICP_URL = 'https://beian.miit.gov.cn/'
const POLICE_TEXT = '渝公网安备50019002505695号'
const POLICE_URL = 'https://www.beian.gov.cn/portal/registerSystemInfo?recordcode=50019002505695'

const bootError = ref<string | null>(null)
const theme = ref<'auto' | 'light' | 'dark'>(
  (localStorage.getItem('aidp-theme') as 'auto' | 'light' | 'dark') ?? 'auto',
)

/** 主题：auto 跟随系统，light/dark 手动覆盖（写入 data-theme） */
function applyTheme(next: 'auto' | 'light' | 'dark') {
  theme.value = next
  localStorage.setItem('aidp-theme', next)
  if (next === 'auto') {
    document.documentElement.removeAttribute('data-theme')
  } else {
    document.documentElement.setAttribute('data-theme', next)
  }
}

function toggleTheme() {
  // auto 时按当前系统偏好决定切换方向
  if (theme.value === 'auto') {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    applyTheme(prefersDark ? 'light' : 'dark')
  } else {
    applyTheme(theme.value === 'dark' ? 'light' : 'dark')
  }
}

/** 深色状态用于切换图标与可访问名称：auto 模式下也要反映「用户实际看到的」 */
const isDark = computed(() => {
  if (theme.value === 'dark') return true
  if (theme.value === 'light') return false
  return typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches
})

const initial = computed(() => (username.value ?? '?').slice(0, 1))

onMounted(async () => {
  applyTheme(theme.value)
  try {
    await load()
  } catch (err) {
    bootError.value = err instanceof Error ? err.message : '无法连接后端服务'
  }
})
</script>

<template>
  <div class="app-shell">
    <header v-if="authenticated" class="navbar">
      <div class="navbar-inner">
        <RouterLink to="/" class="brand" aria-label="AIDP 统一认证 · 返回概览">
          <span class="brand-mark" aria-hidden="true">A</span>
          <span class="brand-text">
            <span class="brand-name">AIDP</span>
            <span class="brand-sub">统一认证</span>
          </span>
        </RouterLink>

        <nav class="navbar-links" aria-label="主导航">
          <RouterLink to="/" class="navbar-link" :class="{ 'is-active': route.name === 'dashboard' }">
            概览
          </RouterLink>
          <RouterLink
            v-if="isAdmin"
            to="/users"
            class="navbar-link"
            :class="{ 'is-active': route.name === 'users' }"
          >
            用户
          </RouterLink>
          <RouterLink
            v-if="isAdmin"
            to="/clients"
            class="navbar-link"
            :class="{ 'is-active': route.name === 'clients' }"
          >
            客户端
          </RouterLink>
          <RouterLink
            to="/profile"
            class="navbar-link"
            :class="{ 'is-active': route.name === 'profile' }"
          >
            账号
          </RouterLink>
        </nav>

        <div class="navbar-trailing">
          <button
            class="icon-btn"
            type="button"
            :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'"
            :title="isDark ? '切换到浅色模式' : '切换到深色模式'"
            @click="toggleTheme"
          >
            <svg v-if="isDark" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="4.2" />
              <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M18.4 5.6L17 7M7 17l-1.4 1.4" />
            </svg>
            <svg v-else viewBox="0 0 24 24" aria-hidden="true">
              <path d="M20 14.4A8.4 8.4 0 0 1 9.6 4a8.4 8.4 0 1 0 10.4 10.4z" />
            </svg>
          </button>

          <span class="user-chip">
            <span class="avatar" aria-hidden="true">{{ initial }}</span>
            <span class="user-name">{{ username }}</span>
          </span>

          <button class="btn btn-secondary btn-sm" type="button" @click="logout">退出</button>
        </div>
      </div>
    </header>

    <main>
      <div v-if="bootError" class="page">
        <div class="alert alert-error" role="alert">
          <span>
            <span class="alert-title">无法连接认证服务。</span>
            {{ bootError }} —— 请确认网络正常后刷新页面；若持续失败，请联系系统管理员。
          </span>
        </div>
      </div>
      <RouterView v-else />
    </main>

    <!-- 备案信息：所有页面（含未登录的登录页）都要展示，与认证中心页脚保持一致 -->
    <footer class="app-footer">
      <div class="app-footer-inner">
        <a :href="ICP_URL" target="_blank" rel="noopener noreferrer">{{ ICP_TEXT }}</a>
        <span class="footer-sep" aria-hidden="true"></span>
        <a :href="POLICE_URL" target="_blank" rel="noopener noreferrer">{{ POLICE_TEXT }}</a>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* ---------------- 顶栏 ----------------
   不做侧边栏：一级入口只有 4 个，侧栏会占掉 200px 却只放 4 个链接，
   信息密度反而下降。改成「一条通栏 + 版心对齐」更省地方，也更容易在窄屏折叠。 */
.navbar {
  position: sticky;
  top: 0;
  z-index: var(--z-nav);
  background: var(--bg-primary);
  border-bottom: 1px solid var(--line);
}

.navbar-inner {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  width: 100%;
  max-width: var(--w-page);
  height: 60px;
  margin: 0 auto;
  padding: 0 var(--sp-6);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  color: var(--label);
  flex-shrink: 0;
}

.brand:hover {
  color: var(--label);
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-md);
  background: var(--brand);
  color: #ffffff;
  font-size: var(--fs-footnote);
  font-weight: var(--fw-semibold);
  line-height: 1;
}

.brand-text {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
}

.brand-name {
  font-size: var(--fs-callout);
  font-weight: var(--fw-semibold);
  letter-spacing: 0.01em;
}

.brand-sub {
  font-size: var(--fs-caption1);
  color: var(--label-tertiary);
}

.navbar-links {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  flex: 1;
  min-width: 0;
}

.navbar-link {
  padding: 6px var(--sp-3);
  border-radius: var(--radius-md);
  font-size: var(--fs-subhead);
  color: var(--label-secondary);
  white-space: nowrap;
  transition: background var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}

.navbar-link:hover {
  color: var(--label);
  background: var(--fill-secondary);
}

.navbar-link.is-active {
  color: var(--brand);
  background: var(--brand-soft);
  font-weight: var(--fw-medium);
}

.navbar-trailing {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-shrink: 0;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  padding-left: var(--sp-3);
  border-left: 1px solid var(--line);
}

.user-name {
  font-size: var(--fs-footnote);
  color: var(--label-secondary);
  max-width: 14ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---------------- 页脚 ---------------- */
.app-footer {
  flex-shrink: 0;
  border-top: 1px solid var(--line);
  background: var(--bg-primary);
}

.app-footer-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  width: 100%;
  max-width: var(--w-page);
  margin: 0 auto;
  padding: var(--sp-4) var(--sp-6);
  font-size: var(--fs-caption1);
}

/* 备案号是合规信息，必须可读：用次级正文色而不是三级灰（后者只有 3:1） */
.app-footer a {
  color: var(--label-secondary);
}

.app-footer a:hover {
  color: var(--brand);
}

.footer-sep {
  width: 1px;
  height: 10px;
  background: var(--line-strong);
}

@media (max-width: 860px) {
  .navbar-inner {
    gap: var(--sp-4);
    padding: 0 var(--sp-4);
  }

  .brand-sub {
    display: none;
  }

  /* 窄屏隐藏用户名，保留头像与退出：身份仍可辨识，但不再挤压导航 */
  .user-name {
    display: none;
  }

  .user-chip {
    padding-left: 0;
    border-left: none;
  }
}

@media (max-width: 620px) {
  .navbar-inner {
    height: 54px;
    gap: var(--sp-2);
  }

  .brand-text {
    display: none;
  }

  /* 窄屏只留「你已登录」的图形线索：头像与用户名会挤掉导航与退出按钮 */
  .user-chip {
    display: none;
  }

  .navbar-links {
    gap: 0;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .navbar-links::-webkit-scrollbar {
    display: none;
  }

  .navbar-link {
    padding: 6px var(--sp-2);
    font-size: var(--fs-footnote);
  }

  .navbar-trailing {
    gap: var(--sp-2);
  }
}
</style>
