import { reactive } from 'vue'

export const toastState = reactive({ message: '', kind: 'info' as 'info' | 'error', visible: false })
let hideTimer: number | undefined

export function notify(message: string, kind: 'info' | 'error' = 'info') {
  toastState.message = message
  toastState.kind = kind
  toastState.visible = true
  window.clearTimeout(hideTimer)
  hideTimer = window.setTimeout(() => { toastState.visible = false }, 3000)
}
