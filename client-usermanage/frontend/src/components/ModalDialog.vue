<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    title: string
    confirmLabel?: string
    destructive?: boolean
    busy?: boolean
  }>(),
  { confirmLabel: '保存', destructive: false, busy: false },
)

const emit = defineEmits<{ confirm: []; close: [] }>()

const panel = ref<HTMLElement | null>(null)

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && !props.busy) {
    emit('close')
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  panel.value?.querySelector<HTMLElement>('input, select, textarea')?.focus()
})

onUnmounted(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="modal-backdrop" @click.self="!busy && emit('close')">
    <div ref="panel" class="modal" role="dialog" aria-modal="true" :aria-label="title">
      <div class="modal-head">
        <h2 class="modal-header">{{ title }}</h2>
        <button
          class="icon-btn"
          type="button"
          aria-label="关闭"
          :disabled="busy"
          @click="emit('close')"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" />
          </svg>
        </button>
      </div>
      <div class="modal-body">
        <slot />
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" type="button" :disabled="busy" @click="emit('close')">
          取消
        </button>
        <button
          class="btn"
          type="button"
          :class="destructive ? 'btn-destructive' : 'btn-primary'"
          :disabled="busy"
          @click="emit('confirm')"
        >
          {{ busy ? '处理中…' : confirmLabel }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* .modal-head 的布局在 components.css 里定义（与站点样式同源），这里不再重复 */
</style>
