# docs/research — AgentScope / Spring Boot 调研资料

本目录是早期选型调研的留档（V0.1 阶段，2026-11 前后），不参与构建。

- `notes/` — 调研笔记（Markdown）
  - `agentscope-spike.md`：API 编译验证与单步边界结论（被 `README.md`、V0.1 spec/state 引用）
  - `agent.md`、`model.md`：AgentScope Core 能力梳理
- `references/` — 固定版本的 SDK 源码快照与元数据，供溯源核对
  - `agentscope-core-2.0.4-sources.jar`、`agentscope-extensions-model-dashscope-2.0.4-sources.jar`
  - `*.java`（核心类源码快照）、`agentscope-metadata.xml`、`spring-boot-metadata.xml`

## 历史路径对照（2026-11-14 整理）

整理前这些文件直接放在 `docs/research/` 下：

- `docs/research/agentscope-spike.md` → `docs/research/notes/agentscope-spike.md`
- `docs/research/agent.md` → `docs/research/notes/agent.md`
- `docs/research/model.md` → `docs/research/notes/model.md`
- `docs/research/agentscope-core-2.0.4-sources.jar` → `docs/research/references/agentscope-core-2.0.4-sources.jar`

`.planning/` 下阶段文档（如 `phases/01-baseline-contracts/RUNTIME-SPIKE.md`）是历史验收记录，其中引用的旧路径未回改，以上表为准。
