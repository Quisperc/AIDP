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

const initial = computed(() => (username.value ?? '?').slice(0, 1))
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">概览</h1>
        <p class="page-subtitle">当前登录身份与可用权限</p>
      </div>
    </div>

    <div class="dashboard">
      <!-- 身份凭证：整页的主物件，先回答「我是谁」，再回答「我能做什么」 -->
      <section class="card identity">
        <div class="identity-head">
          <span class="avatar avatar-lg" aria-hidden="true">{{ initial }}</span>
          <div class="identity-title">
            <div class="identity-name">{{ username ?? '—' }}</div>
            <div class="identity-sub">已通过统一认证中心校验</div>
          </div>
          <span class="badge" :class="isAdmin ? 'badge-success' : 'badge-neutral'">
            {{ isAdmin ? '管理员' : '普通用户' }}
          </span>
        </div>

        <div class="meta-grid">
          <div class="meta-item">
            <div class="meta-label">用户名</div>
            <div class="meta-value mono">{{ username ?? '—' }}</div>
          </div>
          <div class="meta-item">
            <div class="meta-label">角色</div>
            <div class="meta-value">{{ roleText }}</div>
          </div>
          <div class="meta-item">
            <div class="meta-label">认证方式</div>
            <div class="meta-value">统一认证（OIDC）</div>
          </div>
          <div class="meta-item">
            <div class="meta-label">会话状态</div>
            <div class="meta-value">
              <span class="status is-on"><span class="status-dot"></span>已登录</span>
            </div>
          </div>
        </div>

        <p class="identity-foot">
          本次会话由认证中心签发。在此退出会同时注销所有已接入的子系统。
        </p>
      </section>

      <section class="card">
        <div class="card-header">
          <div>
            <div class="card-title">可用功能</div>
            <div class="card-subtitle">按当前权限展开</div>
          </div>
        </div>
        <div class="card-body">
          <ul class="cap-list">
            <li class="cap-row">
              <span class="cap-name">用户管理</span>
              <span class="status" :class="isAdmin ? 'is-on' : 'is-off'">
                <span class="status-dot"></span>{{ isAdmin ? '可用' : '需管理员' }}
              </span>
            </li>
            <li class="cap-row">
              <span class="cap-name">客户端管理</span>
              <span class="status" :class="isAdmin ? 'is-on' : 'is-off'">
                <span class="status-dot"></span>{{ isAdmin ? '可用' : '需管理员' }}
              </span>
            </li>
            <li class="cap-row">
              <span class="cap-name">修改自己的账号</span>
              <span class="status is-on"><span class="status-dot"></span>可用</span>
            </li>
          </ul>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(280px, 1fr);
  gap: var(--sp-5);
  align-items: start;
}

.identity {
  padding: var(--sp-5);
}

.identity-head {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  margin-bottom: var(--sp-5);
}

.identity-title {
  flex: 1;
  min-width: 0;
}

.identity-name {
  font-size: var(--fs-title3);
  font-weight: var(--fw-semibold);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
}

.identity-sub {
  margin-top: 2px;
  font-size: var(--fs-caption1);
  color: var(--label-tertiary);
}

/* 底注用虚线收口：读作「凭证附注」，而不是又一段正文 */
.identity-foot {
  margin-top: var(--sp-4);
  padding-top: var(--sp-3);
  border-top: 1px dashed var(--line-strong);
  font-size: var(--fs-caption1);
  color: var(--label-tertiary);
  line-height: var(--lh-body);
}

.cap-list {
  list-style: none;
  display: flex;
  flex-direction: column;
}

.cap-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  padding: var(--sp-3) 0;
  border-bottom: 1px solid var(--line);
}

.cap-row:first-child {
  padding-top: 0;
}

.cap-row:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.cap-name {
  font-size: var(--fs-subhead);
  color: var(--label);
}

@media (max-width: 900px) {
  .dashboard {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
