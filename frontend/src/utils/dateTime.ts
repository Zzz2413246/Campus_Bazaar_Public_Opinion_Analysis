/**
 * 帖子时间统一展示为 YYYY-MM-DD HH:mm。
 * 后端的 LocalDateTime 字符串直接截取，带时区的 ISO 时间则按浏览器本地时区转换。
 */
export function formatPostTime(value: unknown, missing = '时间待核实') {
  const text = String(value ?? '').trim()
  if (!text) return missing

  const local = text.match(/^(\d{4}-\d{2}-\d{2})[T\s](\d{2}):(\d{2})/)
  if (local && !/[zZ]|[+-]\d{2}:?\d{2}$/.test(text)) {
    return `${local[1]} ${local[2]}:${local[3]}`
  }

  const date = new Date(text)
  if (Number.isNaN(date.getTime())) return local ? `${local[1]} ${local[2]}:${local[3]}` : text
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
