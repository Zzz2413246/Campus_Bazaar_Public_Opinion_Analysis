import { ref } from 'vue'
import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const currentPageTitle = ref('舆情总览')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setPageTitle(title: string) {
    currentPageTitle.value = title
    document.title = `${title} - 校园安全舆情平台`
  }

  return { sidebarCollapsed, currentPageTitle, toggleSidebar, setPageTitle }
})
