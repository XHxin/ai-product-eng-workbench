<script setup lang="ts">
const props = defineProps<{ visible: boolean; title: string; message: string; confirmText?: string; busy?: boolean }>()
const emit = defineEmits<{ (event: 'update:visible', value: boolean): void; (event: 'confirm'): void }>()

function close() {
  emit('update:visible', false)
}
</script>

<template>
  <div v-if="props.visible" class="modal-mask">
    <div class="modal" style="width:460px">
      <div class="modal-h">
        <h2>{{ props.title }}</h2>
        <button class="x-btn" @click="close">✕</button>
      </div>
      <div class="modal-b">
        <div class="notice" style="background:#fef2f2;border-color:#fecaca;color:#991b1b">{{ props.message }}</div>
      </div>
      <div class="modal-f">
        <button class="btn btn-ghost" @click="close">取消</button>
        <button class="btn btn-primary" :disabled="props.busy" @click="emit('confirm')">{{ props.confirmText || '确认' }}</button>
      </div>
    </div>
  </div>
</template>
