<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import ModalDialog from '../components/ModalDialog.vue'
import { ClientDto, ClientSummaryDto, api } from '../api'
import { ApiError } from '../api/http'
import { useTable } from '../composables/useTable'

const clients = ref<ClientSummaryDto[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const busy = ref(false)

const dialog = reactive<{ open: boolean; isEdit: boolean }>({ open: false, isEdit: false })
const confirmDelete = reactive<{ open: boolean; target: ClientSummaryDto | null }>({
  open: false,
  target: null,
})

const form = reactive({
  clientId: '',
  clientSecret: '',
  clientName: '',
  redirectUri: '',
  postLogoutRedirectUri: '',
  requirePkce: true,
})

/** 列表里只展示第一条回调地址，其余用「+n」提示，避免撑爆列宽 */
function firstUri(raw?: string | null) {
  return (raw ?? '').split('\n')[0] ?? ''
}

function extraUriCount(raw?: string | null) {
  const lines = (raw ?? '').split('\n')
  return lines.length > 1 ? lines.length - 1 : 0
}

/** 搜索命中范围：Client ID、名称、回调地址（贴一条 URL 也能找到对应客户端） */
const { query, page, pageSize, filtered, paged, pageCount, rangeFrom, rangeTo, toggleSort, ariaSort, goTo } =
  useTable(clients, {
    search: (c) =>
      `${c.clientId} ${c.clientName ?? ''} ${c.redirectUri ?? ''} ${c.requirePkce ? '强制 PKCE' : ''}`,
    sorters: {
      clientId: (c) => c.clientId,
      clientName: (c) => (c.clientName ?? '').toLowerCase(),
      requirePkce: (c) => c.requirePkce ?? false,
    },
    initialSort: 'clientId',
  })

async function refresh() {
  loading.value = true
  error.value = null
  try {
    clients.value = await api.clients.list()
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '加载客户端列表失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialog.isEdit = false
  Object.assign(form, {
    clientId: '',
    clientSecret: '',
    clientName: '',
    redirectUri: '',
    postLogoutRedirectUri: '',
    requirePkce: true,
  })
  dialog.open = true
}

async function openEdit(summary: ClientSummaryDto) {
  dialog.isEdit = true
  // 列表接口是 SQL 投影，拿不到 PKCE 与 Secret，编辑前需取详情
  try {
    const detail = await api.clients.get(summary.clientId)
    Object.assign(form, {
      clientId: detail.clientId,
      clientSecret: '',
      clientName: detail.clientName ?? '',
      redirectUri: detail.redirectUri ?? '',
      postLogoutRedirectUri: detail.postLogoutRedirectUri ?? '',
      requirePkce: detail.requirePkce ?? true,
    })
    dialog.open = true
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '加载客户端详情失败'
  }
}

async function submit() {
  busy.value = true
  error.value = null
  try {
    const payload: ClientDto = {
      clientId: form.clientId.trim(),
      clientSecret: form.clientSecret || null,
      clientName: form.clientName.trim() || null,
      redirectUri: form.redirectUri,
      postLogoutRedirectUri: form.postLogoutRedirectUri || null,
      requirePkce: form.requirePkce,
    }
    if (dialog.isEdit) {
      await api.clients.update(form.clientId, payload)
    } else {
      await api.clients.create(payload)
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
  if (!confirmDelete.target) return
  busy.value = true
  error.value = null
  try {
    await api.clients.remove(confirmDelete.target.clientId)
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
        <h1 class="page-title">客户端</h1>
        <p class="page-subtitle">
          已接入认证中心的子系统，共 {{ clients.length }} 个
        </p>
      </div>
      <button class="btn btn-primary" type="button" @click="openCreate">注册客户端</button>
    </div>

    <div v-if="error" class="alert alert-error" role="alert" style="margin-bottom: var(--sp-4)">
      <span>
        <span class="alert-title">{{ error }}</span>
        <br />可尝试刷新页面；若提示无权限，请确认当前账号具备管理员角色。
      </span>
    </div>

    <div v-if="loading" class="table-wrap skeleton-table" aria-busy="true" aria-live="polite">
      <div v-for="i in 5" :key="i" class="skeleton-row">
        <span class="skeleton" style="width: 120px; height: 14px"></span>
        <span class="skeleton" style="width: 88px; height: 14px"></span>
        <span class="skeleton" style="flex: 1; max-width: 260px; height: 14px"></span>
        <span class="skeleton" style="width: 60px; height: 14px"></span>
      </div>
    </div>

    <div v-else-if="clients.length === 0" class="table-wrap">
      <div class="empty-state">
        <div class="empty-state-title">还没有注册客户端</div>
        <p class="empty-state-desc">
          新子系统接入前必须先在这里登记，否则认证中心会拒绝它的授权请求。
        </p>
        <button class="btn btn-primary" type="button" @click="openCreate">注册客户端</button>
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
            placeholder="搜索 Client ID、名称或回调地址"
            aria-label="搜索客户端"
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
          匹配 {{ filtered.length }} / {{ clients.length }} 个
        </span>
      </div>

      <div class="table-scroll">
        <table class="table">
          <thead>
            <tr>
              <th style="width: 200px" :aria-sort="ariaSort('clientId')">
                <button class="th-sort" type="button" @click="toggleSort('clientId')">
                  Client ID<span class="sort-caret" aria-hidden="true"></span>
                </button>
              </th>
              <th style="width: 150px" :aria-sort="ariaSort('clientName')">
                <button class="th-sort" type="button" @click="toggleSort('clientName')">
                  名称<span class="sort-caret" aria-hidden="true"></span>
                </button>
              </th>
              <th>回调地址</th>
              <th style="width: 110px" :aria-sort="ariaSort('requirePkce')">
                <button class="th-sort" type="button" @click="toggleSort('requirePkce')">
                  PKCE<span class="sort-caret" aria-hidden="true"></span>
                </button>
              </th>
              <th style="width: 132px"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in paged" :key="c.id">
              <td class="mono client-id">{{ c.clientId }}</td>
              <td>
                <span v-if="c.clientName">{{ c.clientName }}</span>
                <span v-else class="text-tertiary">未命名</span>
              </td>
              <td>
                <!-- 列表接口只返回投影字段；长 URL 必须可换行，否则会压到右侧 PKCE 列 -->
                <div class="uri-cell" :title="c.redirectUri ?? ''">
                  <span class="uri-text mono">{{ firstUri(c.redirectUri) || '—' }}</span>
                  <span v-if="extraUriCount(c.redirectUri) > 0" class="uri-more">
                    +{{ extraUriCount(c.redirectUri) }}
                  </span>
                </div>
              </td>
              <td>
                <!-- 列表现在能拿到真实值：SQL 投影已带出 client_settings -->
                <span v-if="c.requirePkce" class="pkce-text is-on">强制</span>
                <span v-else class="pkce-text is-off" title="不符合当前安全基线，建议开启">未强制</span>
              </td>
              <td>
                <div class="table-actions">
                  <button class="btn btn-secondary btn-sm" type="button" @click="openEdit(c)">编辑</button>
                  <button
                    class="btn btn-plain btn-sm btn-danger-text"
                    type="button"
                    @click="
                      confirmDelete.target = c;
                      confirmDelete.open = true
                    "
                  >
                    删除
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="paged.length === 0">
              <td colspan="5">
                <div class="empty-state table-empty">
                  <div class="empty-state-title">没有匹配的客户端</div>
                  <p class="empty-state-desc">换个关键词，或清空搜索查看全部已接入的系统。</p>
                  <button class="btn btn-secondary" type="button" @click="query = ''">清空搜索</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="table-pager">
        <span class="pager-range">{{ rangeFrom }}–{{ rangeTo }} / 共 {{ filtered.length }} 个</span>
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
      :title="dialog.isEdit ? '编辑客户端' : '注册客户端'"
      :busy="busy"
      @close="dialog.open = false"
      @confirm="submit"
    >
      <div class="stack">
        <div class="field">
          <label class="field-label" for="c-id">Client ID</label>
          <input
            id="c-id"
            v-model="form.clientId"
            class="input mono"
            :disabled="dialog.isEdit"
            placeholder="与子系统配置保持一致"
          />
          <span class="field-hint">必须与子系统中配置的 client-id 完全一致</span>
        </div>

        <div class="field">
          <label class="field-label" for="c-secret">
            Client Secret
            <span v-if="dialog.isEdit" class="text-tertiary">（留空则不修改）</span>
          </label>
          <input id="c-secret" v-model="form.clientSecret" class="input mono" autocomplete="off" />
        </div>

        <div class="field">
          <label class="field-label" for="c-name">名称</label>
          <input id="c-name" v-model="form.clientName" class="input" placeholder="展示在授权同意页上" />
          <span class="field-hint">用户授权时会看到这个名字，建议写业务系统全称</span>
        </div>

        <div class="field">
          <label class="field-label" for="c-redirect">回调地址（redirect_uri）</label>
          <textarea id="c-redirect" v-model="form.redirectUri" class="textarea" rows="3" />
          <span class="field-hint">
            多个地址每行一个。必须与子系统发起登录时的 redirect_uri
            <strong>完全一致，含查询参数</strong>。
          </span>
        </div>

        <div class="field">
          <label class="field-label" for="c-logout">登出后回跳地址</label>
          <input id="c-logout" v-model="form.postLogoutRedirectUri" class="input mono" />
        </div>

        <div class="field">
          <label class="switch">
            <input v-model="form.requirePkce" type="checkbox" />
            <span class="switch-track"><span class="switch-thumb" /></span>
            <span>强制 PKCE</span>
          </label>
          <span class="field-hint">公网应用请保持开启；仅当第三方系统确实不支持时才关闭。</span>
        </div>
      </div>
    </ModalDialog>

    <ModalDialog
      v-if="confirmDelete.open"
      title="删除客户端"
      confirm-label="删除"
      destructive
      :busy="busy"
      @close="confirmDelete.open = false"
      @confirm="doDelete"
    >
      <p>确定删除「{{ confirmDelete.target?.clientId }}」吗？</p>
      <p class="text-secondary" style="margin-top: var(--sp-2)">
        会一并清除该客户端的授权与同意记录，其用户需要重新授权。
      </p>
    </ModalDialog>
  </div>
</template>

<style scoped>
.client-id {
  font-size: var(--fs-footnote);
  font-weight: var(--fw-medium);
  color: var(--label);
}

.uri-cell {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
  max-width: 420px;
}

.uri-text {
  font-size: var(--fs-caption1);
  color: var(--label-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.uri-more {
  flex-shrink: 0;
  font-size: var(--fs-caption1);
  color: var(--label-tertiary);
}

/* PKCE 是安全基线：关闭状态值得用警示色标出来，而不是一片灰色事实 */
.pkce-text {
  font-size: var(--fs-footnote);
}

.pkce-text.is-on {
  color: var(--label);
  font-weight: var(--fw-medium);
}

.pkce-text.is-off {
  color: var(--warn-ink);
}

.skeleton-table {
  padding: var(--sp-2) 0;
}

.skeleton-row {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  height: 44px;
  padding: 0 var(--sp-4);
  border-bottom: 1px solid var(--line);
}

.skeleton-row:last-child {
  border-bottom: none;
}
</style>
