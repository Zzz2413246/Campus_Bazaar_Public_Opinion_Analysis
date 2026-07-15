<script setup lang="ts">
import {
  LayoutDashboard, Radar, Siren, TrendingUp, Bot, FileText, Settings,
  ShieldCheck, Bell, Search, ChevronLeft, ChevronRight,
  MessageSquare, AlertTriangle, Smile, Meh, Angry, MapPin, Tag, Megaphone,
  ThumbsUp, MessageCircle, Clock, ArrowLeft, ArrowRight, ArrowUp, ArrowDown,
  Copy, Download, Plus, RefreshCw, Check, X, Sparkles, Send,
  type LucideIcon,
} from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  name: string
  size?: number
}>(), { size: 18 })

const map: Record<string, LucideIcon> = {
  'layout-dashboard': LayoutDashboard,
  'radar': Radar,
  'siren': Siren,
  'trending-up': TrendingUp,
  'bot': Bot,
  'file-text': FileText,
  'settings': Settings,
  'shield': ShieldCheck,
  'bell': Bell,
  'search': Search,
  'chevron-left': ChevronLeft,
  'chevron-right': ChevronRight,
  'message-square': MessageSquare,
  'alert-triangle': AlertTriangle,
  'smile': Smile,
  'meh': Meh,
  'anger': Angry,
  'map-pin': MapPin,
  'tag': Tag,
  'megaphone': Megaphone,
  'thumbs-up': ThumbsUp,
  'message-circle': MessageCircle,
  'clock': Clock,
  'arrow-left': ArrowLeft,
  'arrow-right': ArrowRight,
  'arrow-up': ArrowUp,
  'arrow-down': ArrowDown,
  'copy': Copy,
  'download': Download,
  'plus': Plus,
  'refresh': RefreshCw,
  'check': Check,
  'x': X,
  'sparkles': Sparkles,
  'send': Send,
}
</script>

<template>
  <component :is="map[props.name]" :size="props.size" :stroke-width="2" />
</template>
