<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { buildWorkBuddyDeeplink, copyText } from '../ui/prompt'
import { notify } from '../ui/toast'

const props = defineProps<{ visible: boolean; title: string; source: string; prompt: string }>()
const emit = defineEmits<{ (event: 'update:visible', value: boolean): void }>()
const draft = ref('')
const deeplink = computed(() => buildWorkBuddyDeeplink(draft.value))

watch(() => props.prompt, value => { draft.value = value }, { immediate: true })
watch(() => props.visible, value => { if (value) draft.value = props.prompt })

function close() {
  emit('update:visible', false)
}

function openWorkBuddy(event: MouseEvent) {
  if (!draft.value.trim()) { event.preventDefault(); notify('提示词不能为空', 'error'); return }
  copyText(draft.value).then(copied => {
    notify(copied ? '提示词已复制到粘贴板' : '复制失败，请手动选中复制', copied ? 'info' : 'error')
  })
}
</script>

<template>
  <div v-if="visible" class="modal-mask">
    <div class="modal">
      <div class="modal-h">
        <h2>{{ title }}</h2>
        <button class="x-btn" @click="close">✕</button>
      </div>
      <div class="modal-b">
        <div class="notice">提示词可修改。点击“打开WorkBuddy”会复制提示词并唤起本机 WorkBuddy。</div>
        <div class="field"><label>来源</label><span class="chip chip-soft">{{ source }}</span></div>
        <div class="field"><label>提示词</label><textarea v-model="draft" class="in" rows="10" maxlength="8000" /></div>
        <div class="hint">点击“打开WorkBuddy”会复制提示词并唤起本机 WorkBuddy。若未唤起，请确认本机已安装 WorkBuddy，并在浏览器提示中选择“打开”；也可手动启动 WorkBuddy 后粘贴已复制的提示词。</div>
      </div>
      <div class="modal-f">
        <button class="btn btn-ghost" @click="close">取消</button>
        <a class="btn btn-primary" style="text-decoration:none" :href="deeplink" @click="openWorkBuddy">打开WorkBuddy</a>
      </div>
    </div>
  </div>
</template>
