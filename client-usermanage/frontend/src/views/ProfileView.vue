<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import { ApiError } from '../api/http'
import { useAuth } from '../stores/auth'

const { logout } = useAuth()

const current = ref('')
const loading = ref(true)
const saving = ref(false)
const error = ref<string | null>(null)
const success = ref<string | null>(null)

const form = reactive({ username: '', password: '', confirm: '' })

async function load() {
  loading.value = true
  try {
    const me = await api.profile.get()
    current.value = me.username
    form.username = me.username
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '加载资料失败'
  } finally {
    loading.value = false
  }
}

async function save() {
  error.value = null
  success.value = null

  if (!form.username.trim() && !form.password) {
    error.value = '请填写新用户名或新密码。'
    return
  }
  if (form.password && form.password !== form.confirm) {
    error.value = '两次输入的新密码不一致。'
    return
  }

  saving.value = true
  try {
    // 传空串表示「不修改」，由后端 StringUtils.hasText 判断
    await api.profile.update({
      username: form.username.trim(),
      password: form.password,
    })
    success.value = '已更新。正在退出，请使用新凭据重新登录。'
    form.password = ''
    form.confirm = ''
    // 用户名或密码变了，旧会话必须作废，否则仍以旧身份有效
    window.setTimeout(logout, 1200)
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '更新失败'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">账号</h1>
        <p class="page-subtitle">修改自己的用户名或密码</p>
      </div>
    </div>

    <div style="max-width: 480px">
      <div v-if="error" class="alert alert-error" style="margin-bottom: var(--sp-4)">
        <span>{{ error }}</span>
      </div>
      <div v-if="success" class="alert alert-success" style="margin-bottom: var(--sp-4)">
        <span>{{ success }}</span>
      </div>

      <div class="card">
        <div class="card-body" style="display: flex; flex-direction: column; gap: var(--sp-4)">
          <div class="field">
            <label class="field-label">当前用户名</label>
            <div class="mono">{{ loading ? '…' : current || '—' }}</div>
          </div>

          <div class="field">
            <label class="field-label" for="p-name">新用户名</label>
            <input id="p-name" v-model="form.username" class="input" autocomplete="username" />
            <span class="field-hint">留空表示不修改</span>
          </div>

          <div class="field">
            <label class="field-label" for="p-pwd">新密码</label>
            <input
              id="p-pwd"
              v-model="form.password"
              class="input"
              type="password"
              autocomplete="new-password"
            />
            <span class="field-hint">留空表示不修改</span>
          </div>

          <div class="field">
            <label class="field-label" for="p-confirm">确认新密码</label>
            <input
              id="p-confirm"
              v-model="form.confirm"
              class="input"
              type="password"
              autocomplete="new-password"
            />
          </div>
        </div>

        <div class="card-footer">
          <button class="btn btn-primary" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存修改' }}
          </button>
        </div>
      </div>

      <p class="field-hint" style="margin-top: var(--sp-4)">
        修改成功后会自动触发全局退出，所有已登录的子系统都需要重新登录。
      </p>
    </div>
  </div>
</template>
