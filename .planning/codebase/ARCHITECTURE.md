# 架构地图

## 双入口与依赖

CLI 的 DeviceTruthApplication 在非 Web Spring 上下文手工装配 Core。Server 的 DeviceTruthServerApplication 扫描 server 包，ServerConfiguration 仅额外扫描 api，并提供 application/adapter/core Bean 或工厂。Core 主要是普通 Java 类，未作为 Spring 组件分散扫描。

HTTP：ConversationController → ConversationFlowService → Router/Validator → QUERY(只读仓储) 或 DIAGNOSE(映射旧 Request → DiagnosisRunService → Core)。QUERY 不走 CapabilityGateway，这一点不能概括成“所有请求都过同一网关”。

Core：Reasoner.propose → TaskOrchestrator 验证/调度 → CapabilityGateway → Repositories → EvidenceManager → StateManager；决策经 DecisionGuard。模型没有执行工具或直接改事实的权限。

## 状态及终止

StateManager 是 synchronized 内存乐观版本控制。TaskOrchestrator.run 内新建 manager，生成最终 RunReport。运行 ACTIVE/COMPLETED/FAILED 与业务 Outcome 分开；应用运行另有 QUEUED/RUNNING/COMPLETED/FAILED。

RequestInformation 是结束动作，无 WAITING/恢复。事件全部收集在 run 局部列表，结束后通过 RunSnapshot 发布。文件输出不能恢复原线程或重建新任务。

## 耦合点

DiagnosisRequest 四必填字段；Reasoner.Context 绑定 DiagnosisState；Gateway scope 绑定 DiagnosisRequest、参数全部 ID；Guard 绑定规则和全量历史；公开摘要绑定规则成功。这些点都限制自由知识问答。

扩展方案优先保留模型连接、Gateway 权限、来源/时间、版本提交、错误码与事件过滤；新主循环要以原型 ADR 选定，legacy 与通用策略不得套两层 Agent 循环。

