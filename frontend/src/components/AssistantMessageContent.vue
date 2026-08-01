<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ content: string }>()

type Part = { text: string; strong: boolean }
type Line = { kind: 'blank' | 'heading' | 'bullet' | 'number' | 'text'; parts: Part[] }

const lines = computed<Line[]>(() => props.content.split(/\r?\n/).map((raw) => {
  const text = raw.trim()
  if (!text) return { kind: 'blank', parts: [] }
  if (/^#{1,3}\s+/.test(text)) return { kind: 'heading', parts: parseInline(text.replace(/^#{1,3}\s+/, '')) }
  if (/^[-·]\s*/.test(text)) return { kind: 'bullet', parts: parseInline(text.replace(/^[-·]\s*/, '')) }
  if (/^\d+[.、]\s*/.test(text)) return { kind: 'number', parts: parseInline(text) }
  return { kind: 'text', parts: parseInline(text) }
}))

function parseInline(text: string): Part[] {
  const parts: Part[] = []
  const regex = /\*\*(.+?)\*\*/g
  let cursor = 0
  let match: RegExpExecArray | null
  while ((match = regex.exec(text))) {
    if (match.index > cursor) parts.push({ text: text.slice(cursor, match.index), strong: false })
    parts.push({ text: match[1], strong: true })
    cursor = match.index + match[0].length
  }
  if (cursor < text.length) parts.push({ text: text.slice(cursor), strong: false })
  return parts.length ? parts : [{ text, strong: false }]
}
</script>

<template>
  <div class="assistant-content">
    <template v-for="(line, index) in lines" :key="index">
      <div v-if="line.kind === 'blank'" class="h-3"></div>
      <div v-else :class="[
        'assistant-line',
        `assistant-line--${line.kind}`,
        line.kind === 'heading' && 'font-semibold text-[1.05em] text-slate-900',
        line.kind === 'bullet' && 'pl-4 relative',
        line.kind === 'number' && 'pl-1 font-medium',
      ]">
        <span v-if="line.kind === 'bullet'" class="absolute left-0 text-indigo-500">•</span>
        <template v-for="(part, partIndex) in line.parts" :key="partIndex">
          <strong v-if="part.strong" class="font-semibold">{{ part.text }}</strong>
          <span v-else>{{ part.text }}</span>
        </template>
      </div>
    </template>
  </div>
</template>

<style scoped>
.assistant-content {
  line-height: 1.75;
}

.assistant-line + .assistant-line {
  margin-top: 0.35rem;
}

.assistant-line--heading {
  margin-top: 0.9rem !important;
  margin-bottom: 0.15rem;
}

.assistant-line--bullet + .assistant-line--bullet {
  margin-top: 0.25rem;
}

.assistant-line--number {
  margin-top: 0.65rem !important;
}

.assistant-line:first-child {
  margin-top: 0 !important;
}
</style>
