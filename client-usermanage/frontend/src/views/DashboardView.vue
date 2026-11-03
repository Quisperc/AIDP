<script setup lang="ts">
import { computed } from 'vue'
import { useAuth } from '../stores/auth'

const { username, roles, isAdmin } = useAuth()

const roleText = computed(() => {
  if (roles.value.length === 0) return '无'
  return roles.value
    .map((r) => (r === 'ROLE_ADMIN' ? '管理员' : r === 'ROLE_USER' ? '普通用户' : r))
    .join('、')
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">概览</h1>
        <p class="page-subtitle">当前登录者与会话状态</p>
      </div>
    </div>

    <div class="grid">
      <section class="card">
        <div class="card-header">
          <div>
            <div class="card-title">当前账号</div>
            <div class="card-subtitle">来自认证中心签发的令牌</div>
          </div>
          <span class="badge" :class="isAdmin ? 'badge-success' : 'badge-neutral'">
            {{ isAdmin ? '管理员' : '普通用户' }}
          </span>
        </div>
        <div class="card-body">
          <div class="list-group">
            <div class="list-row">
              <span class="list-row-label">用户名</span>
              <span class="list-row-value mono">{{ username ?? '—' }}</span>
            </div>
            <div class="list-row">
              <span class="list-row-label">角色</span>
              <span class="list-row-value">{{ roleText }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="card-header">
          <div>
            <div class="card-title">可用功能</div>
            <div class="card-subtitle">按当前权限展示</div>
          </div>
        </div>
        <div class="card-body">
          <div class="list-group">
            <div class="list-row">
              <span class="list-row-label">用户管理</span>
              <span class="list-row-value">
                <span class="badge" :class="isAdmin ? 'badge-success' : 'badge-neutral'">
                  {{ isAdmin ? '可用' : '需管理员' }}
                </span>
              </span>
            </div>
            <div class="list-row">
              <span class="list-row-label">客户端管理</span>
              <span class="list-row-value">
                <span class="badge" :class="isAdmin ? 'badge-success' : 'badge-neutral'">
                  {{ isAdmin ? '可用' : '需管理员' }}
                </span>
              </span>
            </div>
            <div class="list-row">
              <span class="list-row-label">修改自己的账号</span>
              <span class="list-row-value"><span class="badge badge-success">可用</span></span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: var(--sp-5);
}
</style>
