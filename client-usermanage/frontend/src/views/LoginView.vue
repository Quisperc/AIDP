<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuth } from '../stores/auth'

const route = useRoute()
const { login } = useAuth()

const redirect = computed(() => (route.query.redirect as string) ?? '/')

function startLogin() {
  login(redirect.value)
}
</script>

<template>
  <div class="login-screen">
    <div class="login-panel">
      <div class="login-brand">
        <span class="brand-mark" aria-hidden="true">A</span>
        <span>AIDP 统一身份认证</span>
      </div>

      <h1 class="login-title">管理后台</h1>
      <p class="login-desc">
        本系统不单独保存密码。点击下方按钮后，将由认证中心校验你的身份，再回到这里继续操作。
      </p>

      <button class="btn btn-primary btn-block" type="button" @click="startLogin">
        使用 SSO 登录
      </button>

      <p class="login-foot">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <rect x="4.5" y="10.5" width="15" height="9.5" rx="2" />
          <path d="M8.5 10.5V7.8a3.5 3.5 0 0 1 7 0v2.7" />
        </svg>
        登录页面由认证中心提供，凭据不会经过本系统
      </p>
    </div>
  </div>
</template>

<style scoped>
.login-screen {
  /* 配合 App.vue 的 flex column：占满 main 剩余空间，让页脚自然落在底部而不需要滚动 */
  flex: 1;
  display: grid;
  place-items: center;
  padding: var(--sp-6);
  background: var(--bg-grouped);
}

.login-panel {
  width: 100%;
  max-width: 400px;
  padding: var(--sp-8);
  background: var(--bg-primary);
  border: 1px solid var(--line);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-md);
}

.login-brand {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-caption1);
  color: var(--label-tertiary);
  margin-bottom: var(--sp-6);
}

.login-brand .brand-mark {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  background: var(--brand);
  color: #ffffff;
  font-size: var(--fs-caption1);
  font-weight: var(--fw-semibold);
  line-height: 1;
}

.login-title {
  font-size: var(--fs-title2);
  font-weight: var(--fw-semibold);
  letter-spacing: -0.01em;
}

.login-desc {
  margin-top: var(--sp-2);
  margin-bottom: var(--sp-6);
  font-size: var(--fs-footnote);
  color: var(--label-secondary);
  line-height: var(--lh-relaxed);
}

.login-foot {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--line);
  font-size: var(--fs-caption1);
  color: var(--label-tertiary);
  line-height: var(--lh-body);
}

.login-foot svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
</style>
