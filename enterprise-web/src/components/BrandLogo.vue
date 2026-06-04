<template>
  <div class="brand-logo" :class="[`brand-logo--${variant}`, { 'brand-logo--wide': isWide }]">
    <template v-if="variant === 'svg-books'">
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M5 6.5h11a1.5 1.5 0 0 1 1.5 1.5V18H6.5A1.5 1.5 0 0 1 5 16.5V6.5Z" stroke="currentColor" stroke-width="1.6"/>
        <path d="M8 4.5h11A1.5 1.5 0 0 1 20.5 6v11.5H9.5A1.5 1.5 0 0 1 8 16V4.5Z" stroke="currentColor" stroke-width="1.6" opacity="0.85"/>
        <path d="M11 7h8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
      </svg>
    </template>

    <template v-else-if="variant === 'svg-workspace'">
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M7 5h8a2 2 0 0 1 2 2v10H9a2 2 0 0 1-2-2V5Z" stroke="currentColor" stroke-width="1.6"/>
        <path d="M9 9h6M9 12h4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
        <path d="M14.5 14.5c2.2 0 4 1.4 4 3.1 0 .9-.8 1.4-2 1.4h-4.8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>
      </svg>
    </template>

    <template v-else-if="variant === 'gradient-ring'">
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <defs>
          <linearGradient :id="ringGradId" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stop-color="#ff6b9d"/>
            <stop offset="50%" stop-color="#c084fc"/>
            <stop offset="100%" stop-color="#22d3ee"/>
          </linearGradient>
        </defs>
        <circle cx="12" cy="12" r="7.5" :stroke="`url(#${ringGradId})`" stroke-width="3" stroke-linecap="round"/>
      </svg>
    </template>

    <template v-else>
      <span class="brand-logo__text">{{ displayText }}</span>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: {
    type: String,
    default: 'enterprise',
  },
  initial: {
    type: String,
    default: '企',
  },
})

const ringGradId = `brandRingGrad-${Math.random().toString(36).slice(2, 9)}`

const isWide = computed(() => props.variant === 'zh-short')

const displayText = computed(() => {
  switch (props.variant) {
    case 'e':
      return 'E'
    case 'zh-short':
      return '知企'
    case 'user-initial':
      return (props.initial || '企').charAt(0).toUpperCase()
    case 'enterprise':
    default:
      return '企'
  }
})
</script>

<style scoped>
.brand-logo {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: linear-gradient(135deg, #ff6b9d 0%, #c084fc 50%, #22d3ee 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
}

.brand-logo--wide {
  width: 40px;
}

.brand-logo__text {
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
}

.brand-logo--zh-short .brand-logo__text {
  font-size: 11px;
  letter-spacing: 0.02em;
}

.brand-logo--user-initial .brand-logo__text {
  font-size: 15px;
}

.brand-logo svg {
  width: 20px;
  height: 20px;
}

.brand-logo--gradient-ring {
  background: #fff;
  border: 1px solid #f3f4f6;
}
</style>
