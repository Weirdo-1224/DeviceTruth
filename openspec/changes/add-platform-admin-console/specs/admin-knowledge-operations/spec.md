## ADDED Requirements

### Requirement: 空间知识与私有材料分层

管理知识查询 SHALL 限显式授权空间的 WORKSPACE_KNOWLEDGE，区分文件处理、入库、可用、发布和 release 状态。调查未发布附件 MUST NOT 自动进入管理空间库或因 READY 提升来源许可。

#### Scenario: 同团队调查附件未发布
- **WHEN** 团队调查上传材料但尚未发布为空间知识
- **THEN** 管理空间知识列表不自动收录该附件，不把团队阅读资格当成全库发布

### Requirement: 导入与失败重试复用现有流程

导入 SHALL 复用 FileUploadService/KnowledgeImportService，Markdown 首期可用，TXT 保留旧入口，PDF/OCR 不宣称解析。来源/许可 SHALL 由服务端登记校验。失败任务 SHALL 可分页定位及按原 fileId 重试；异步处理 SHALL 检查当前授权。

#### Scenario: 导入受理后处理失败
- **WHEN** 已返回202的导入随后失败，页面刷新或管理员请求重试
- **THEN** 导入任务列表仍可定位状态/稳定原因码，只有失败可重试版本可再次 enqueue，不创建覆盖成功正文的操作

#### Scenario: 导入执行前管理授权撤销
- **WHEN** 导入受理后管理员账号或目标 grant 被撤销
- **THEN** 后续处理在安全边界拒绝，保留已发生费用与状态记录，不借旧请求无限执行

#### Scenario: 已有 corpus 知识接入管理空间
- **WHEN** 已有 corpus 文档、原文件及 import_item 记录归属于显式授权的管理空间
- **THEN** 知识列表、文档详情及导入任务 SHALL 展示真实记录，历史入库标识 CORPUS_IMPORT 且不可由网页重试；不创建虚构管理导入任务，不展示其他空间或未发布调查附件

### Requirement: 停用覆盖所有新的使用路径

文档停用 SHALL 在向量/关键词/混合检索、附件关联、contextRefs 继承和新引用登记生效，与状态/发布/release 并发裁决协调。重复上传 MUST NOT 重置停用。

#### Scenario: 新登记与停用竞争
- **WHEN** 引用登记和停用并发且停用先提交
- **THEN** 新登记被拒绝，不出现已停用资料进入新 Run 的窗口

#### Scenario: 重复内容重传
- **WHEN** 停用文档以相同内容再次上传并被复用
- **THEN** 原可用状态和版本保护保留，不能通过上传重新启用

### Requirement: 历史引用保留与恢复前提

普通停用 SHALL 保留既有登记证据在原授权内可读；恢复 SHALL 检查处理、来源、空间权限、发布及 release 条件。管理详情 SHALL 仅返回 admin-api.md 元数据，不开放正文下载。

#### Scenario: 历史证据与新调查使用
- **WHEN** 文档停用后既有 Run 读取其原登记来源，新 Run 试图继承同一引用
- **THEN** 历史读取按现授权允许，新使用被拒绝；恢复不能自动激活 release 或提升许可
