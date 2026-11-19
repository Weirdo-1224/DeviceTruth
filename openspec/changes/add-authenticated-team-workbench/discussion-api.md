# 讨论与共同接续调查

## 先讨论，再选择哪些内容交给 Agent

登录后向 `POST /api/conversations/{id}/discussions` 提交 `{content:"怀疑控制信号，尚未检查"}`。返回 201 和 discussionId、version=1、seq、authorUserId、authorName、content、createdAt。作者和时间由服务器生成；没有编辑或删除接口。讨论不写 dt_message，不改变正式调查版本，不产生 Run、摘要、知识切片或证据。

`GET /api/conversations/{id}/discussions?after=0&limit=20` 返回 `{items,nextCursor}`，下一页用 nextCursor 作为 after；每页 1–100 条，按调查内 seq 升序。路径先解析真实空间；非成员 404，只读成员能读但不能发表。写操作仍需 Cookie/CSRF。

## 正式提交引用

原消息保持 schemaVersion=1、userMessage、selectedAssetId、attachments、contextRefs、asOf、replyTo；追加可选 discussionQuotes 和 expectedConversationVersion。示例：

```json
{"schemaVersion":1,"userMessage":"请结合这条讨论继续排查","discussionQuotes":[{"discussionId":"讨论 UUID","version":1}],"expectedConversationVersion":0}
```

discussionQuotes 最多 10 项，只接收 discussionId/version。服务器校验同调查记录与版本，解析作者、原文和时间并保存 USER_REPORTED_UNVERIFIED；额外伪造字段 400，跨调查 404，版本失效 409 DISCUSSION_VERSION_CONFLICT，事务回滚不创建 Run。contextRefs 仍只接受知识 referenceId，不接收讨论 ID。引用讨论不登记知识证据，也不提升数据性质。

## 提交与补答

新认证普通提交在会话行锁内先查原幂等键（409 DUPLICATE_REQUEST），再判期望版本（409 CONVERSATION_VERSION_CONFLICT），再查任何 QUEUED/RUNNING/WAITING_FOR_USER（409 CONVERSATION_BUSY），然后解析引用、写消息、enqueue、绑定作者/快照；提交后 dispatch。团队必须给期望版本，个人可省略，给了则校验。旧内部固定身份入口继续允许多未决轮，已有历史不删除。

补答必须沿原 runId/pendingQuestionId/questionVersion；同一会话锁及原 CAS 只让一次消费成功，其他请求 409 STALE_OR_UNRELATED_REPLY，不变成新消息。补答保留原 Run/排查时点，更新 executionActorUserId 并追加执行段；Run initiatedByUserId 不变。

每段保存本 Run 正式消息、结构化讨论快照、已核验附件 documentId/version、contextRefs、排查对象和时点。worker 从 input_snapshot.executionMessages 读取，恢复不扫描最新讨论/附件。引用内容以结构化 discussionQuotes 交给模型，原文不会被拼接进指令或扩大单消息长度。历史基础快照及旧内部 Run 仍保留原读取兼容路径。调查附件上传/检索资格在第 5 组接通，本组不改变旧空间知识导入入口。

## 取消与作者

取消先复核当前写权，再要求当前执行段发起人或空间 ADMIN；普通同事 403 CANCEL_NOT_ALLOWED。QUEUED/WAITING 立即取消，RUNNING 保留既有协作取消和迟到响应拒绝。新增 RUN_CANCEL_REQUESTED 事件记录实际取消人；系统收尾事件保留执行段作者，两者不混用。事件顶层新增 actorUserId（UUID 或 null），data 继续原逐类型白名单，不返回账号凭据或任意作者对象。

Run 增加 roundNumber、initiatedByUserId、executionActorUserId；正式消息增加 actorUserId 和 discussionQuotes。历史未知作者仍 null。V23 按历史时间/id 给全部尝试编号，新轮在会话锁内 max+1；取消不删除记录或回收轮号。前端接线仍属第 6 组。
