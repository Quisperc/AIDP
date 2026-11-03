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

    <div class="profile-grid">
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">账号信息</div>
            <div class="card-subtitle">凭据由认证中心统一存储，本系统不保存密码</div>
          </div>
        </div>

        <div class="card-body">
          <div v-if="error" class="alert alert-error" role="alert" style="margin-bottom: var(--sp-4)">
            <span>{{ error }}</span>
          </div>
          <div v-if="success" class="alert alert-success" role="status" style="margin-bottom: var(--sp-4)">
            <span>{{ success }}</span>
          </div>

          <div class="stack">
            <div class="readout">
              <span class="readout-label">当前用户名</span>
              <span class="readout-value mono">{{ loading ? '…' : current || '—' }}</span>
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
                placeholder="留空表示不修改"
              />
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
        </div>

        <div class="card-footer">
          <button class="btn btn-primary" type="button" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存修改' }}
          </button>
        </div>
      </div>

      <aside class="card notes">
        <div class="card-header">
          <div class="card-title">修改后会怎样</div>
        </div>
        <div class="card-body">
          <ul class="note-list">
            <li>用户名与密码由统一认证中心管理，改动对所有已接入的子系统立即生效。</li>
            <li>保存成功后会自动退出当前会话，需要用新凭据重新登录一次。</li>
            <li>密码留空表示不修改；填写时两次输入必须一致。</li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(260px, 1fr);
  gap: var(--sp-5);
  align-items: start;
}

/* 只读值：与输入框同高，但用底色区分「不可编辑」，避免用户误点 */
.readout {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  min-height: 38px;
  padding: 0 var(--sp-3);
  background: var(--fill-tertiary);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
}

.readout-label {
  font-size: var(--fs-caption1);
  color: var(--label-tertiary);
}

.readout-value {
  font-size: var(--fs-subhead);
  color: var(--label);
}

.notes {
  background: var(--bg-secondary);
}

.note-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  counter-reset: note;
}

.note-list li {
  position: relative;
  padding-left: var(--sp-4);
  font-size: var(--fs-footnote);
  color: var(--label-secondary);
  line-height: var(--lh-relaxed);
}

.note-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--label-quaternary);
}

@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
