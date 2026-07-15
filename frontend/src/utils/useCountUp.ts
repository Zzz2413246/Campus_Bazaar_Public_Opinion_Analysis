import { ref, watch, onMounted } from 'vue'

// 数字计数动画 · 从 0 缓动到目标值
export function useCountUp(target: () => number, duration = 1200) {
  const display = ref(0)
  let raf = 0

  const animate = (to: number) => {
    cancelAnimationFrame(raf)
    const start = display.value
    const startTime = performance.now()
    const step = (now: number) => {
      const t = Math.min((now - startTime) / duration, 1)
      // easeOutCubic
      const eased = 1 - Math.pow(1 - t, 3)
      display.value = Math.round(start + (to - start) * eased)
      if (t < 1) raf = requestAnimationFrame(step)
    }
    raf = requestAnimationFrame(step)
  }

  onMounted(() => animate(target()))
  watch(target, (v) => animate(v))

  return display
}
