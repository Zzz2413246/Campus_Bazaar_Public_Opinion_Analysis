export const FINAL_SAFETY_CATEGORIES = [
  '个人安全',
  '意外伤害',
  '消防与电气安全',
  '建筑与设施安全',
  '食品与公共卫生',
  '交通安全',
  '网络与数据安全',
  '财产安全',
  '心理危机',
  '实验室安全',
  '公共秩序与活动安全',
  '环境安全',
  '自然灾害',
  '政治与国家安全',
  '仇恨与身份歧视',
  '校园谣言与声誉风险',
  '其他校园安全',
  '疑似主题无法确定',
] as const

export const finalSafetyCategories = () => [...FINAL_SAFETY_CATEGORIES]

export const postCategoryOptions = () => [...FINAL_SAFETY_CATEGORIES, '非安全内容']
