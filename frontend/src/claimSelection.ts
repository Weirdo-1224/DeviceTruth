/**
 * 判断选中状态：只改变查看焦点，不触发任何网络请求或模型调用。
 * 选中键由 ViewModel 的 claim.key（runId:claimIndex）承载；本模块只管当前快照内的下标。
 * Run 或结果替换时调用 sync 重置：默认选中第一条判断，无判断时为 null。
 */
import { ref, type Ref } from 'vue'

export interface ClaimSelection {
  readonly selectedIndex: Ref<number | null>
  /** 选中某条判断（index 越界时忽略）。 */
  select(index: number): void
  /** 清空选中（切 Run/结果时）。 */
  reset(): void
  /** 与当前 claims 数量对齐：无 claims 置 null；选中下标越界时回退到第一条。 */
  sync(claimCount: number): void
}

export function createClaimSelection(): ClaimSelection {
  const selectedIndex = ref<number | null>(null)
  return {
    selectedIndex,
    select(index: number) {
      if (index >= 0) selectedIndex.value = index
    },
    reset() {
      selectedIndex.value = null
    },
    sync(claimCount: number) {
      if (claimCount <= 0) {
        selectedIndex.value = null
        return
      }
      const current = selectedIndex.value
      if (current === null || current >= claimCount) selectedIndex.value = 0
    }
  }
}
