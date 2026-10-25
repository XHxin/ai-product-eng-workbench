<script setup lang="ts">
defineProps<{ priority: string }>()
</script>
<template><span :class="['pri', priority.toLowerCase()]">{{ priority }}</span></template>
