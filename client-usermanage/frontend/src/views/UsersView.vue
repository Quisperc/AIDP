<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import ModalDialog from '../components/ModalDialog.vue'
import { UserDto, api } from '../api'
import { ApiError } from '../api/http'

const users = ref<UserDto[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const busy = ref(false)

type EditMode = 'create' | 'edit'
const dialog = reactive<{ open: boolean; mode: EditMode; target: UserDto | null }>({
  open: false,
  mode: 'create',
  target: null,
})
const confirmDelete = reactive<{ open: boolean; target: UserDto | null }>({
  open: false,
  target: null,
})

const form = reactive({ username: '', password: '', role: 'ROLE_USER', enabled: true })

const ROLES = [
  { value: 'ROLE_ADMIN', label: '管理员' },
  { value: 'ROLE_USER', label: '普通用户' },
]

function roleLabel(role?: string) {
  return ROLES.find((r) => r.value === role)?.label ?? (role || '—')
}

async function refresh() {
  loading.value = true
  error.value = null
  try {
    users.value = await api.users.list()
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '加载用户列表失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialog.mode = 'create'
  dialog.target = null
  form.username = ''
  form.password = ''
  form.role = 'ROLE_USER'
  form.enabled = true
  dialog.open = true
}

function openEdit(user: UserDto) {
  dialog.mode = 'edit'
  dialog.target = user
  form.username = user.username
  form.password = ''
  form.role = user.role ?? 'ROLE_USER'
  form.enabled = user.enabled ?? true
  dialog.open = true
}

async function submit() {
  busy.value = true
  error.value = null
  try {
    if (dialog.mode === 'create') {
      await api.users.create({
        username: form.username.trim(),
        password: form.password,
        role: form.role,
      })
    } else if (dialog.target?.id != null) {
      await api.users.update(dialog.target.id, {
        role: form.role,
        enabled: form.enabled,
        // 留空表示不修改密码
        password: form.password || null,
      })
    }
    dialog.open = false
    await refresh()
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '保存失败'
  } finally {
    busy.value = false
  }
}

async function doDelete() {
  if (confirmDelete.target?.id == null) return
  busy.value = true
  error.value = null
  try {
    await api.users.remove(confirmDelete.target.id)
    confirmDelete.open = false
    await refresh()
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '删除失败'
  } finally {
    busy.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h1 class="page-title">用户</h1>
        <p class="page-subtitle">共 {{ users.length }} 个账号</p>
      </div>
      <button class="btn btn-primary" @click="openCreate">新增用户</button>
    </div>

    <div v-if="error" class="alert alert-error" style="margin-bottom: var(--sp-4)">
      <span>{{ error }}</span>
    </div>

    <div v-if="loading" class="table-wrap" style="padding: var(--sp-5)">
      <div v-for="i in 4" :key="i" class="skeleton" style="height: 22px; margin-bottom: var(--sp-3)" />
    </div>

    <div v-else-if="users.length === 0" class="table-wrap">
      <div class="empty-state">
        <div class="empty-state-title">还没有用户</div>
        <p class="empty-state-desc">新增第一个账号后即可在此管理角色与启用状态。</p>
        <button class="btn btn-primary" @click="openCreate">新增用户</button>
      </div>
    </div>

    <div v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th style="width: 72px">ID</th>
            <th>用户名</th>
            <th>角色</th>
            <th>状态</th>
            <th style="width: 160px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.id ?? u.username">
            <td class="mono text-tertiary">{{ u.id }}</td>
            <td>{{ u.username }}</td>
            <td>{{ roleLabel(u.role) }}</td>
            <td>
              <span class="badge" :class="u.enabled ? 'badge-success' : 'badge-neutral'">
                {{ u.enabled ? '启用' : '停用' }}
              </span>
            </td>
            <td>
              <div class="table-actions">
                <button class="btn btn-secondary btn-sm" @click="openEdit(u)">编辑</button>
                <button
                  class="btn btn-plain btn-sm btn-danger-text"
                  @click="
                    confirmDelete.target = u;
                    confirmDelete.open = true
                  "
                >
                  删除
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <ModalDialog
      v-if="dialog.open"
      :title="dialog.mode === 'create' ? '新增用户' : '编辑用户'"
      :busy="busy"
      @close="dialog.open = false"
      @confirm="submit"
    >
      <div style="display: flex; flex-direction: column; gap: var(--sp-4)">
        <div class="field">
          <label class="field-label" for="u-name">用户名</label>
          <input
            id="u-name"
            v-model="form.username"
            class="input"
            :disabled="dialog.mode === 'edit'"
            autocomplete="off"
          />
          <span v-if="dialog.mode === 'edit'" class="field-hint">用户名不可在此修改</span>
        </div>

        <div class="field">
          <label class="field-label" for="u-pwd">
            密码<span v-if="dialog.mode === 'edit'" class="text-tertiary">（留空则不修改）</span>
          </label>
          <input
            id="u-pwd"
            v-model="form.password"
            class="input"
            type="password"
            autocomplete="new-password"
          />
        </div>

        <div class="field">
          <label class="field-label" for="u-role">角色</label>
          <select id="u-role" v-model="form.role" class="select">
            <option v-for="r in ROLES" :key="r.value" :value="r.value">{{ r.label }}</option>
          </select>
        </div>

        <label v-if="dialog.mode === 'edit'" class="switch">
          <input v-model="form.enabled" type="checkbox" />
          <span class="switch-track"><span class="switch-thumb" /></span>
          <span>{{ form.enabled ? '启用' : '停用' }}</span>
        </label>
      </div>
    </ModalDialog>

    <ModalDialog
      v-if="confirmDelete.open"
      title="删除用户"
      confirm-label="删除"
      destructive
      :busy="busy"
      @close="confirmDelete.open = false"
      @confirm="doDelete"
    >
      <p>
        确定删除「{{ confirmDelete.target?.username }}」吗？<br />
        <span class="text-secondary">该操作为停用账号，历史授权记录会保留。</span>
      </p>
    </ModalDialog>
  </div>
</template>
