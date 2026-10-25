<script setup lang="ts">
import { statusClass, statusLabel } from '../ui/format'
defineProps<{ status: string }>()
</script>
<template><span :class="['st', statusClass(status)]">{{ statusLabel(status) }}</span></template>
