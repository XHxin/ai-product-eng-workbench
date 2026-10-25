<script setup lang="ts">
import { toastState } from '../ui/toast'
</script>
<template>
  <div v-if="toastState.visible" :class="['toast', toastState.kind === 'error' ? 'error' : '']">
    {{ toastState.message }}
  </div>
</template>
