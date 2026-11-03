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
      <div class="modal-header">{{ title }}</div>
      <div class="modal-body">
        <slot />
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" :disabled="busy" @click="emit('close')">取消</button>
        <button
          class="btn"
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
