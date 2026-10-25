<script setup lang="ts">
import { typeClass, typeLabel } from '../ui/format'
defineProps<{ type: string }>()
</script>
<template><span :class="['chip', typeClass(type)]">{{ typeLabel(type) }}</span></template>
