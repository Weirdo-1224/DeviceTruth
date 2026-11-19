# 调查附件、空间知识与来源读取

## 上传检查记录，不等于共享为知识

在已登录调查里向 `POST /api/conversations/{id}/attachments` 发送 multipart `file`，可附 title、domain（默认 general）、deviceModel、version（默认 1.0）、dataNature（默认 SIMULATED）。返回 202 FileView，包含 fileId、解析状态、documentId/documentVersion、uploadedByUserId、conversationId、usageScope 和 dataNature。路径决定真实空间，不能在请求里绑定另一空间或调查。

支持 MD 和 UTF-8 TXT；MD 使用 Markdown MIME，TXT 使用 text/plain。复用受控路径、大小上限、异步有界解析和失败重试。PDF 只保存原始记录并显示 OCR_REQUIRED/PDF_NOT_SUPPORTED，不生成正文、切片或页码。上传、解析和发布都不创建 Run。

- `GET /api/conversations/{id}/attachments?offset=0&limit=20`：本调查附件，按创建时间/id 排序，每页 1–100 条。
- `GET /api/files/{fileId}`：状态和元数据。
- `GET /api/files/{fileId}/content`：授权读取原始 UTF-8 文本，包含上传者、调查、用途及数据性质，不暴露存储路径。PDF 返回 415；历史记录没有可用原始文件时返回 FILE_CONTENT_UNAVAILABLE，不伪造原文。
- `POST /api/files/{fileId}/retry`：沿原失败重试合同；不自动发布，PDF 重试仍明确未实现。

旧 `POST /api/files` 和 `POST /api/knowledge/documents` 仍是显式空间知识导入，不要求调查 ID。若明确知识导入与某附件同内容同版本，文档可复用，但新增知识用途关联，不改原附件的归属和上传人；既有知识重复导入不新增关联。

## 确认名称和适用范围后发布

`POST /api/files/{fileId}/publications` 输入：

```json
{"title":"控制信号检查记录","applicability":"适用 ABC-123 型号；现场现象仍未核实"}
```

标题 1–500 字符，适用范围 1–2000 字符，均不能为空。当前空间 ADMIN/COLLABORATOR 可发布，VIEWER 禁止。发布要求调查附件已经 PARSED、文档 READY 且有切片；解析或索引未就绪返回 409，不显示发布成功。发布不再调用 embedding 或模型，不提升 SIMULATED/AUTHORIZED 等原有数据性质。

空间锁和 `(workspace_id,document_id)` 唯一约束裁决重复发布；两成员同时发布得到同一 publicationId，不重写首次名称、适用范围或作者。Publication 返回文档版本、原作者、发布者、适用范围和时间；原附件继续保留。

`GET /api/knowledge/documents?offset=0&limit=20` 返回当前空间 READY 的显式知识导入和已发布资料，每页 1–100 条。字段为 documentId、title、documentVersion、dataNature、ingestStatus、applicability、publishedByUserId、originalAuthorUserId、publishedAt。旧显式导入没有可确认作者/发布时间时保持 null，不冒充当前账号。

## Agent 使用的是执行段材料，不是成员能看到的所有文件

正式提交或同轮补答确认时，沿既有会话锁将当时已解析的本调查附件 documentId/version/fileId 固定进 input_snapshot.materialVersions，并追加 dt_run_input_segment。运行后新增或后来才解析成功的附件不进入当前段，下一次明确提交/补答才纳入。显式 attachments 中提供其他调查的 fileId 会被拒绝；空间知识旧引用继续可用。

`MaterialScope` 统一限定：该 Run 同存储归属下的显式空间知识、已发布知识，或该执行段快照里的附件文档。文档 UUID 对应不可变版本，不能用当前文件的最新关联替换快照。

资格条件同时进入向量候选、关键词候选、RRF 的输入、引用登记、get_document_excerpt、referenceId 原文和 contextRefs 继承。其他调查的未发布附件不能通过直接 documentId/chunkId/referenceId 或旧引用继承绕过。既有历史 source_file_id 的空间知识关联仍有效，不强迫旧内部数据补写新关联表。

成员可以主动打开同空间调查 A、阅读 A 的附件和持久来源；这不授权调查 B 的模型使用 A 的未发布材料。历史来源/报告继续按原 Run 回读，保留版本和数据性质，不再次调用模型。Skill 和资产仍使用原 ToolContext 的空间授权，不从附件或模型参数授予额外权限。

全部接口要求真实登录，写请求要求 CSRF；非成员/跨空间资源 404，只读写入 403。新接口尚未接入前端，浏览器协作验收仍属第 6 组。
