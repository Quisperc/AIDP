<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import ModalDialog from '../components/ModalDialog.vue'
import { ClientDto, ClientSummaryDto, api } from '../api'
import { ApiError } from '../api/http'

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
        <p class="page-subtitle">已接入认证中心的子系统，共 {{ clients.length }} 个</p>
      </div>
      <button class="btn btn-primary" @click="openCreate">注册客户端</button>
    </div>

    <div v-if="error" class="alert alert-error" style="margin-bottom: var(--sp-4)">
      <span>{{ error }}</span>
    </div>

    <div v-if="loading" class="table-wrap" style="padding: var(--sp-5)">
      <div v-for="i in 4" :key="i" class="skeleton" style="height: 22px; margin-bottom: var(--sp-3)" />
    </div>

    <div v-else-if="clients.length === 0" class="table-wrap">
      <div class="empty-state">
        <div class="empty-state-title">还没有注册客户端</div>
        <p class="empty-state-desc">新子系统接入前必须先在这里登记，否则认证中心会拒绝授权请求。</p>
        <button class="btn btn-primary" @click="openCreate">注册客户端</button>
      </div>
    </div>

    <div v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>Client ID</th>
            <th>名称</th>
            <th>回调地址</th>
            <th style="width: 90px">PKCE</th>
            <th style="width: 150px"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in clients" :key="c.id">
            <td class="mono">{{ c.clientId }}</td>
            <td>{{ c.clientName || '—' }}</td>
            <td class="mono text-secondary" style="max-width: 320px">
              {{ (c.redirectUri || '').split('\n')[0] }}
              <span v-if="(c.redirectUri || '').includes('\n')" class="text-tertiary">
                +{{ (c.redirectUri || '').split('\n').length - 1 }}
              </span>
            </td>
            <td>
              <!-- 列表为 SQL 投影，取不到 PKCE；以徽标提示需查看详情 -->
              <span class="badge badge-neutral">见详情</span>
            </td>
            <td>
              <div class="table-actions">
                <button class="btn btn-secondary btn-sm" @click="openEdit(c)">编辑</button>
                <button
                  class="btn btn-plain btn-sm btn-danger-text"
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
        </tbody>
      </table>
    </div>

    <ModalDialog
      v-if="dialog.open"
      :title="dialog.isEdit ? '编辑客户端' : '注册客户端'"
      :busy="busy"
      @close="dialog.open = false"
      @confirm="submit"
    >
      <div style="display: flex; flex-direction: column; gap: var(--sp-4)">
        <div class="field">
          <label class="field-label" for="c-id">Client ID</label>
          <input id="c-id" v-model="form.clientId" class="input" :disabled="dialog.isEdit" />
          <span class="field-hint">与子系统中配置的 client-id 完全一致</span>
        </div>

        <div class="field">
          <label class="field-label" for="c-secret">
            Client Secret
            <span v-if="dialog.isEdit" class="text-tertiary">（留空则不修改）</span>
          </label>
          <input id="c-secret" v-model="form.clientSecret" class="input" autocomplete="off" />
        </div>

        <div class="field">
          <label class="field-label" for="c-name">名称</label>
          <input id="c-name" v-model="form.clientName" class="input" />
          <span class="field-hint">展示在授权同意页上</span>
        </div>

        <div class="field">
          <label class="field-label" for="c-redirect">回调地址（redirect_uri）</label>
          <textarea id="c-redirect" v-model="form.redirectUri" class="textarea" rows="3" />
          <span class="field-hint">
            多个地址请换行填写。必须与子系统实际发起登录时的 redirect_uri
            <strong>完全一致，含查询参数</strong>。
          </span>
        </div>

        <div class="field">
          <label class="field-label" for="c-logout">登出后回跳地址</label>
          <input id="c-logout" v-model="form.postLogoutRedirectUri" class="input" />
        </div>

        <label class="switch">
          <input v-model="form.requirePkce" type="checkbox" />
          <span class="switch-track"><span class="switch-thumb" /></span>
          <span>强制 PKCE</span>
        </label>
        <span class="field-hint" style="margin-top: calc(-1 * var(--sp-2))">
          公网应用请保持开启；仅当第三方系统确实不支持时才关闭。
        </span>
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
      <p>
        确定删除「{{ confirmDelete.target?.clientId }}」吗？<br />
        <span class="text-secondary">
          会一并清除该客户端的授权与同意记录，其用户需要重新授权。
        </span>
      </p>
    </ModalDialog>
  </div>
</template>
