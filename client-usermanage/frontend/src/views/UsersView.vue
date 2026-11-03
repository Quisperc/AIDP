<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import ModalDialog from '../components/ModalDialog.vue'
import { UserDto, api } from '../api'
import { ApiError } from '../api/http'
import { useTable } from '../composables/useTable'

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

function initialOf(name?: string) {
  return (name ?? '?').slice(0, 1)
}

/** 搜索命中范围：ID、用户名、角色中文名、启用状态 */
const { query, page, pageSize, filtered, paged, pageCount, rangeFrom, rangeTo, toggleSort, ariaSort, goTo } =
  useTable(users, {
    search: (u) => `${u.id ?? ''} ${u.username} ${roleLabel(u.role)} ${u.enabled ? '启用' : '停用'}`,
    sorters: {
      id: (u) => u.id ?? 0,
      username: (u) => u.username,
      role: (u) => roleLabel(u.role),
      enabled: (u) => u.enabled ?? false,
    },
    initialSort: 'id',
  })

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
        <p class="page-subtitle">
          共 {{ users.length }} 个账号<span v-if="!loading">，含启用与停用</span>
        </p>
      </div>
      <button class="btn btn-primary" type="button" @click="openCreate">新增用户</button>
    </div>

    <div v-if="error" class="alert alert-error" role="alert" style="margin-bottom: var(--sp-4)">
      <span>
        <span class="alert-title">{{ error }}</span>
        <br />可尝试刷新页面；若提示无权限，请确认当前账号具备管理员角色。
      </span>
    </div>

    <div v-if="loading" class="table-wrap skeleton-table" aria-busy="true" aria-live="polite">
      <div v-for="i in 6" :key="i" class="skeleton-row">
        <span class="skeleton" style="width: 20px; height: 12px"></span>
        <span class="skeleton" style="flex: 1; max-width: 160px; height: 14px"></span>
        <span class="skeleton" style="width: 64px; height: 14px"></span>
        <span class="skeleton" style="width: 52px; height: 14px"></span>
      </div>
    </div>

    <div v-else-if="users.length === 0" class="table-wrap">
      <div class="empty-state">
        <div class="empty-state-title">还没有用户</div>
        <p class="empty-state-desc">新增第一个账号后，即可在此管理角色与启用状态。</p>
        <button class="btn btn-primary" type="button" @click="openCreate">新增用户</button>
      </div>
    </div>

    <div v-else class="table-wrap">
      <div class="table-toolbar">
        <label class="search-field">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="11" cy="11" r="7" />
            <path d="M20 20l-3.6-3.6" />
          </svg>
          <input
            v-model="query"
            class="search-input"
            type="search"
            placeholder="搜索用户名、角色或状态"
            aria-label="搜索用户"
          />
          <button
            v-if="query"
            class="search-clear"
            type="button"
            aria-label="清除搜索"
            @click="query = ''"
          >
            ×
          </button>
        </label>
        <span v-if="query" class="table-toolbar-meta">
          匹配 {{ filtered.length }} / {{ users.length }} 条
        </span>
      </div>

      <div class="table-scroll">
        <table class="table">
          <thead>
            <tr>
              <th class="col-id" style="width: 72px" :aria-sort="ariaSort('id')">
                <button class="th-sort" type="button" @click="toggleSort('id')">
                  ID<span class="sort-caret" aria-hidden="true"></span>
                </button>
              </th>
              <th style="width: 300px" :aria-sort="ariaSort('username')">
                <button class="th-sort" type="button" @click="toggleSort('username')">
                  用户名<span class="sort-caret" aria-hidden="true"></span>
                </button>
              </th>
              <!-- 空白吸收列：把角色/状态/操作压到右侧，避免用户名与角色之间出现大片空隙 -->
              <th aria-hidden="true"></th>
              <th style="width: 120px" :aria-sort="ariaSort('role')">
                <button class="th-sort" type="button" @click="toggleSort('role')">
                  角色<span class="sort-caret" aria-hidden="true"></span>
                </button>
              </th>
              <th style="width: 110px" :aria-sort="ariaSort('enabled')">
                <button class="th-sort" type="button" @click="toggleSort('enabled')">
                  状态<span class="sort-caret" aria-hidden="true"></span>
                </button>
              </th>
              <th style="width: 132px"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in paged" :key="u.id ?? u.username">
              <td class="mono text-tertiary col-id">{{ u.id }}</td>
              <td>
                <div class="cell-user">
                  <span class="avatar" aria-hidden="true">{{ initialOf(u.username) }}</span>
                  <span class="cell-user-name">{{ u.username }}</span>
                </div>
              </td>
              <td aria-hidden="true"></td>
              <td>
                <span class="role-text" :class="{ 'is-admin': u.role === 'ROLE_ADMIN' }">
                  {{ roleLabel(u.role) }}
                </span>
              </td>
              <td>
                <span class="status" :class="u.enabled ? 'is-on' : 'is-off'">
                  <span class="status-dot"></span>{{ u.enabled ? '启用' : '停用' }}
                </span>
              </td>
              <td>
                <div class="table-actions">
                  <button class="btn btn-secondary btn-sm" type="button" @click="openEdit(u)">编辑</button>
                  <button
                    class="btn btn-plain btn-sm btn-danger-text"
                    type="button"
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
            <tr v-if="paged.length === 0">
              <td colspan="6">
                <div class="table-empty empty-state">
                  <div class="empty-state-title">没有匹配的用户</div>
                  <p class="empty-state-desc">换个关键词，或清空搜索查看全部账号。</p>
                  <button class="btn btn-secondary" type="button" @click="query = ''">清空搜索</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="table-pager">
        <span class="pager-range">{{ rangeFrom }}–{{ rangeTo }} / 共 {{ filtered.length }} 条</span>
        <div class="pager-controls">
          <select v-model.number="pageSize" class="select select-sm" aria-label="每页条数">
            <option :value="10">10 条/页</option>
            <option :value="20">20 条/页</option>
            <option :value="50">50 条/页</option>
          </select>
          <button
            class="btn btn-plain btn-sm"
            type="button"
            :disabled="page <= 1"
            @click="goTo(page - 1)"
          >
            上一页
          </button>
          <span class="pager-page">{{ page }} / {{ pageCount }}</span>
          <button
            class="btn btn-plain btn-sm"
            type="button"
            :disabled="page >= pageCount"
            @click="goTo(page + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <ModalDialog
      v-if="dialog.open"
      :title="dialog.mode === 'create' ? '新增用户' : '编辑用户'"
      :busy="busy"
      @close="dialog.open = false"
      @confirm="submit"
    >
      <div class="stack">
        <div class="field">
          <label class="field-label" for="u-name">用户名</label>
          <input
            id="u-name"
            v-model="form.username"
            class="input"
            :disabled="dialog.mode === 'edit'"
            autocomplete="off"
            placeholder="登录时使用的账号"
          />
          <span v-if="dialog.mode === 'edit'" class="field-hint">用户名创建后不可修改</span>
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
        确定删除「{{ confirmDelete.target?.username }}」吗？
      </p>
      <p class="text-secondary" style="margin-top: var(--sp-2)">
        该操作为停用账号，历史授权记录会保留。
      </p>
    </ModalDialog>
  </div>
</template>

<style scoped>
.cell-user-name {
  font-size: var(--fs-subhead);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 角色是属性而不是状态：用字重区分管理员，不再加一层彩色胶囊 */
.role-text {
  font-size: var(--fs-subhead);
  color: var(--label-secondary);
}

.role-text.is-admin {
  color: var(--label);
  font-weight: var(--fw-medium);
}

/* 窄屏优先保留「谁 / 什么角色 / 是否启用 / 能做什么」，ID 让位 */
@media (max-width: 620px) {
  .col-id {
    display: none;
  }
}

/* 骨架与真实行同节奏：6 行、同样 40px 行高，加载完成时不会有跳变 */
.skeleton-table {
  padding: var(--sp-2) 0;
}

.skeleton-row {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  height: 40px;
  padding: 0 var(--sp-4);
  border-bottom: 1px solid var(--line);
}

.skeleton-row:last-child {
  border-bottom: none;
}
</style>
