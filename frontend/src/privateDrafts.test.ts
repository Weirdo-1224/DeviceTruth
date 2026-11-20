import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, ref } from 'vue'
import { clearAccountDrafts, draftKey, readDraft, listDrafts, usePrivateDraft, writeDraft } from './privateDrafts'
afterEach(() => vi.unstubAllGlobals())
function storage() {
  const values: Record<string,string> = {}
  vi.stubGlobal('sessionStorage',new Proxy({getItem:(key:string)=>values[key]??null,setItem:(key:string,value:string)=>{values[key]=value},removeItem:(key:string)=>{delete values[key]}}, {ownKeys:()=>Object.keys(values),getOwnPropertyDescriptor:()=>({enumerable:true,configurable:true})}))
}
describe('账号/空间/调查/Run私人草稿',()=>{
  it('所有输入目标与账号空间隔离，切回恢复且不自动提交',()=>{
    storage(); const scope=effectScope()
    scope.run(()=>{
      const key=ref(draftKey('a:team','case-a','reply:run-a:q1:1'))
      const draft=usePrivateDraft(key); draft.value='私人待答'
      key.value=draftKey('a:team','case-b','reply:run-a:q1:1'); expect(draft.value).toBe('')
      draft.value='另一个调查'; key.value=draftKey('b:team','case-a','reply:run-a:q1:1'); expect(draft.value).toBe('')
      key.value=draftKey('a:other','case-a','reply:run-a:q1:1'); expect(draft.value).toBe('')
      key.value=draftKey('a:team','case-a','reply:run-a:q1:2'); expect(draft.value).toBe('')
      key.value=draftKey('a:team','case-a','reply:run-a:q1:1'); expect(draft.value).toBe('私人待答')
    });scope.stop()
  })
  it('同事消费追问后旧草稿可回看，不转换成新消息',()=>{
    storage(); const old=draftKey('a:team','c','reply:run:q:1');writeDraft(old,'未接受补答');expect(listDrafts(draftKey('a:team','c','reply:'))).toEqual([{key:old,text:'未接受补答'}]);expect(readDraft(draftKey('a:team','c','free'))).toBe('')
  })
  it('首次提交接受后清除原新调查草稿，不污染下一次新建',()=>{
    storage(); const initial=draftKey('a:team',null);writeDraft(initial,'已提交问题');writeDraft(draftKey('a:team','accepted'),'已提交问题');writeDraft(initial,'');writeDraft(draftKey('a:team','accepted'),'');expect(readDraft(initial)).toBe('')
  })
  it('退出只清当前账号草稿，不保留口令或会话凭证',()=>{
    storage();writeDraft(draftKey('a:team','c'),'a draft');writeDraft(draftKey('b:team','c'),'b draft')
    clearAccountDrafts('a');expect(readDraft(draftKey('a:team','c'))).toBe('');expect(readDraft(draftKey('b:team','c'))).toBe('b draft')
  })
})
