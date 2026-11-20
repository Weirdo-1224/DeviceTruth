import { afterEach, describe, expect, it, vi } from 'vitest'
import { accessContextKey, authAction, clearAuthentication, productRequest, waitForRun, openRunEventStream, authenticate, currentAccount, loadCsrf, logout, readConversation, sendMessage, setAccessContext } from './api'
function json(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status }) }
afterEach(() => { setAccessContext(null, null); vi.unstubAllGlobals() })
describe('真实认证前端合同', () => {
  it('登录前取得 CSRF，登录后旋转，口令只在请求体中发送', async () => {
    const calls: {url: string; init?: RequestInit}[] = []
    vi.stubGlobal('fetch', vi.fn(async (url, init) => {
      calls.push({url: String(url), init})
      return String(url).endsWith('/csrf') ? json({headerName: 'X-CSRF-TOKEN', token: calls.length === 1 ? 'before' : 'after'}) : json({userId: 'u', displayName:'工程师', email:'a@example.com'})
    }))
    await authenticate('login', {email:'a@example.com', password:'test-password-123'})
    await loadCsrf()
    expect(calls.map(c => c.url)).toEqual(['/api/auth/csrf', '/api/auth/login', '/api/auth/csrf'])
    expect(new Headers(calls[1].init?.headers).get('X-CSRF-TOKEN')).toBe('before')
    expect(calls.every(c => c.init?.credentials === 'same-origin')).toBe(true)
  })
  it('团队消息带空间与已看到的调查版本，不伪造身份', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({headerName:'X-CSRF-TOKEN',token:'csrf'})))
    await loadCsrf(); setAccessContext('u', 'team-a')
    const fetch = vi.fn(async (_url: RequestInfo | URL, _init?: RequestInit) => json({runId:'r'})); vi.stubGlobal('fetch', fetch)
    await sendMessage('c', '问题', 'same-key', [], null, [], 7)
    const init = fetch.mock.calls[0]![1] as RequestInit
    expect(new Headers(init.headers).get('X-Workspace-ID')).toBe('team-a')
    expect(JSON.parse(String(init.body))).toEqual({schemaVersion:1,userMessage:'问题',expectedConversationVersion:7})
    expect(new Headers(init.headers).get('X-CSRF-TOKEN')).toBe('csrf')
  })
  it('切换后忽略旧空间晚到响应，包括 A 到 B 再回 A', async () => {
    setAccessContext('u','a')
    let resolve!: (r: Response) => void
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(r => {resolve = r})))
    const pending = readConversation('c')
    setAccessContext('u','b'); setAccessContext('u','a'); resolve(json({conversation:{conversationId:'c'}}))
    await expect(pending).rejects.toMatchObject({code:'ACCESS_CONTEXT_CHANGED'})
  })
  it('刷新通过 me 回读身份，退出后清理当前空间', async () => {
    vi.stubGlobal('fetch', vi.fn(async url => String(url).endsWith('/csrf') ? json({headerName:'X-CSRF-TOKEN',token:'c'}) : json({userId:'u'})))
    expect(await currentAccount()).toMatchObject({userId:'u'})
    await loadCsrf(); setAccessContext('u','w'); await logout()
    expect(accessContextKey()).toBe('anonymous:personal')
  })
  it('409 保持原服务端错误，不自动再发或转换新轮', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({headerName:'X-CSRF-TOKEN',token:'c'}))); await loadCsrf()
    const fetch = vi.fn(async () => json({code:'CONVERSATION_VERSION_CONFLICT',message:'调查版本已变化'},409)); vi.stubGlobal('fetch',fetch)
    await expect(sendMessage('c','私人草稿','key',[],null,[],3)).rejects.toMatchObject({status:409,code:'CONVERSATION_VERSION_CONFLICT'})
    expect(fetch).toHaveBeenCalledTimes(1)
  })
})

describe('会话失效与账号错误恢复', () => {
  it('me 的 401 清除旧 CSRF，下一次登录重新获取，不重发原请求', async () => {
    const urls: string[] = []
    vi.stubGlobal('fetch', vi.fn(async (url, init) => {
      urls.push(String(url))
      if (String(url).endsWith('/csrf')) return json({headerName:'X-CSRF-TOKEN',token:'fresh'})
      if (String(url).endsWith('/me')) return json({code:'AUTHENTICATION_REQUIRED'},401)
      expect(new Headers(init.headers).get('X-CSRF-TOKEN')).toBe('fresh')
      return json({userId:'new-user'})
    }))
    await loadCsrf(); setAccessContext('old','team')
    await expect(currentAccount()).rejects.toMatchObject({status:401})
    expect(accessContextKey()).toBe('anonymous:personal')
    await authenticate('login',{email:'new@example.com',password:'password-123456'})
    expect(urls).toEqual(['/api/auth/csrf','/api/auth/me','/api/auth/csrf','/api/auth/login'])
  })
  it('旧 CSRF 读取不能覆盖退出后的身份', async () => {
    let resolve!: (r: Response) => void
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(r => {resolve=r})))
    const pending = loadCsrf(); clearAuthentication(); resolve(json({headerName:'X-CSRF-TOKEN',token:'old'}))
    await expect(pending).rejects.toMatchObject({code:'ACCESS_CONTEXT_CHANGED'})
  })
  it('邮箱不匹配保留403和可读提示，不自动退出或接受邀请', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json({headerName:'X-CSRF-TOKEN',token:'c'}))); await loadCsrf(); setAccessContext('u','w')
    const fetch = vi.fn(async () => json({code:'INVITATION_EMAIL_MISMATCH'},403)); vi.stubGlobal('fetch',fetch)
    await expect(productRequest('invitations/accept','POST',{token:'test'})).rejects.toMatchObject({status:403,code:'INVITATION_EMAIL_MISMATCH',message:expect.stringContaining('受邀邮箱')})
    expect(accessContextKey()).toBe('u:w'); expect(fetch).toHaveBeenCalledTimes(1)
  })
  it.each([['password-reset-requests',503,'MAIL_UNAVAILABLE'],['password-resets',400,'INVALID_OR_EXPIRED_RESET_TOKEN']] as const)('%s 如实保留错误状态', async (action,status,code) => {
    vi.stubGlobal('fetch', vi.fn(async url => String(url).endsWith('/csrf') ? json({headerName:'X-CSRF-TOKEN',token:'c'}) : json({code},status)))
    await loadCsrf()
    await expect(authAction(action,{email:'test@example.com'})).rejects.toMatchObject({status,code})
  })
})

describe('受控持久与订阅边界',()=>{
  it('登录不将凭据写入localStorage或sessionStorage',async()=>{
    const setItem=vi.fn(()=>{throw new Error('credentials must not be stored')})
    vi.stubGlobal('localStorage',{setItem});vi.stubGlobal('sessionStorage',{setItem})
    vi.stubGlobal('fetch',vi.fn(async url=>String(url).endsWith('/csrf')?json({headerName:'X-CSRF-TOKEN',token:'c'}):json({userId:'u'})))
    await authenticate('login',{email:'a@example.com',password:'private-password-123'})
    expect(setItem).not.toHaveBeenCalled()
  })
  it('退出关闭订阅，晚到帧不再输出',()=>{
    const listeners: Record<string,(e:{data:string})=>void>={}
    const close=vi.fn(),onEvent=vi.fn()
    setAccessContext('u','a')
    openRunEventStream('r',{lastSeq:0,onEvent,eventSourceFactory:()=>({onmessage:null,onerror:null,addEventListener:(name,listener)=>{listeners[name]=listener},close})})
    setAccessContext('u','b')
    listeners.RUN_MODEL!({data:JSON.stringify({seq:1,type:'RUN_MODEL',data:{}})})
    expect(close).toHaveBeenCalledTimes(1);expect(onEvent).not.toHaveBeenCalled()
  })
  it('切空间后停止旧轮询，不用新空间Header重读旧Run',async()=>{
    setAccessContext('u','a')
    const fetch=vi.fn(async()=>json({runId:'r',status:'RUNNING',version:1}));vi.stubGlobal('fetch',fetch)
    await expect(waitForRun('r',{sleep:async()=>{setAccessContext('u','b')}})).rejects.toMatchObject({code:'ACCESS_CONTEXT_CHANGED'})
    expect(fetch).toHaveBeenCalledTimes(1)
  })
})
