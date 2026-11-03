<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { useAuth } from './stores/auth'

// 登录跳转由 LoginView 与路由守卫负责，这里只需要状态与退出
const { authenticated, username, isAdmin, load, logout } = useAuth()
const route = useRoute()

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

const themeIcon = computed(() => (theme.value === 'dark' ? '☾' : '☀'))

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
      <RouterLink to="/" class="navbar-brand">
        <span class="brand-mark">A</span>
        <span>AIDP</span>
      </RouterLink>

      <nav class="navbar-links">
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
          class="btn btn-plain btn-sm"
          :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          @click="toggleTheme"
        >
          {{ themeIcon }}
        </button>
        <span class="text-secondary" style="font-size: var(--fs-footnote)">{{ username }}</span>
        <button class="btn btn-secondary btn-sm" @click="logout">退出</button>
      </div>
    </header>

    <main>
      <div v-if="bootError" class="page">
        <div class="alert alert-error">
          <span>{{ bootError }}</span>
        </div>
      </div>
      <RouterView v-else />
    </main>
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
}
</style>
