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
    <div class="card login-card">
      <div class="card-header">
        <div>
          <div class="card-title">AIDP 管理后台</div>
          <div class="card-subtitle">使用统一认证中心账号登录</div>
        </div>
      </div>
      <div class="card-body">
        <p class="text-secondary" style="font-size: var(--fs-subhead)">
          本系统不单独保存密码，登录由认证中心完成。点击下方按钮跳转。
        </p>
      </div>
      <div class="card-footer">
        <button class="btn btn-primary btn-block" @click="startLogin">使用 SSO 登录</button>
      </div>
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

.login-card {
  width: 100%;
  max-width: 400px;
  box-shadow: var(--shadow-md);
}
</style>
