import { computed, ref, watch, type Ref } from 'vue'

export type SortDir = 'asc' | 'desc'

interface TableConfig<T> {
  /** 搜索时参与匹配的可读文本（把中文标签也拼进去，用户搜「管理员」也能命中） */
  search: (row: T) => string
  /** 每个可排序列的取值函数 */
  sorters: Record<string, (row: T) => string | number | boolean>
  initialSort: string
  initialDir?: SortDir
  initialPageSize?: number
}

/**
 * 列表的搜索 / 排序 / 分页状态。
 *
 * 当前接口返回全量数组（认证中心的用户、客户端规模都在百级），
 * 因此在前端一次算完是**更快的选择**：翻页、搜索零延迟，排序能覆盖全部行
 * 而不是只排当前页。若将来单表超过数千行，再把这套状态换成服务端分页，
 * 组件层的用法不变（paged 换成接口返回的当前页即可）。
 */
export function useTable<T>(rows: Ref<T[]>, config: TableConfig<T>) {
  const query = ref('')
  const sortKey = ref(config.initialSort)
  const sortDir = ref<SortDir>(config.initialDir ?? 'asc')
  const page = ref(1)
  const pageSize = ref(config.initialPageSize ?? 10)

  const filtered = computed<T[]>(() => {
    const keyword = query.value.trim().toLowerCase()
    const base = keyword
      ? rows.value.filter((row) => config.search(row).toLowerCase().includes(keyword))
      : rows.value.slice()

    const getter = config.sorters[sortKey.value]
    if (!getter) return base

    const dir = sortDir.value === 'asc' ? 1 : -1
    return base.sort((a, b) => compare(getter(a), getter(b)) * dir)
  })

  const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))

  const paged = computed<T[]>(() => {
    const start = (page.value - 1) * pageSize.value
    return filtered.value.slice(start, start + pageSize.value)
  })

  const rangeFrom = computed(() =>
    filtered.value.length === 0 ? 0 : (page.value - 1) * pageSize.value + 1,
  )
  const rangeTo = computed(() => Math.min(page.value * pageSize.value, filtered.value.length))

  // 搜索词变化回到第一页；数据变少导致当前页越界时也回到第一页
  watch(query, () => {
    page.value = 1
  })
  watch([() => filtered.value.length, pageSize], () => {
    if (page.value > pageCount.value) page.value = 1
  })

  function toggleSort(key: string) {
    if (sortKey.value === key) {
      sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
    } else {
      sortKey.value = key
      sortDir.value = 'asc'
    }
    page.value = 1
  }

  function ariaSort(key: string): 'ascending' | 'descending' | 'none' {
    if (sortKey.value !== key) return 'none'
    return sortDir.value === 'asc' ? 'ascending' : 'descending'
  }

  function goTo(target: number) {
    page.value = Math.min(pageCount.value, Math.max(1, target))
  }

  return {
    query,
    sortKey,
    sortDir,
    page,
    pageSize,
    filtered,
    paged,
    pageCount,
    rangeFrom,
    rangeTo,
    toggleSort,
    ariaSort,
    goTo,
  }
}

function compare(a: string | number | boolean, b: string | number | boolean): number {
  const left = typeof a === 'boolean' ? Number(a) : a
  const right = typeof b === 'boolean' ? Number(b) : b
  if (typeof left === 'number' && typeof right === 'number') {
    return left - right
  }
  // numeric:true 让 user2 排在 user10 前面
  return String(left).localeCompare(String(right), 'zh-Hans-CN', { numeric: true })
}
