# 旧合同变更 ledger

## 2026-11-20 用户确认的简化合同

产品新答案使用整体问题协议v2：不逐句分类，不强制每项引用，不按关键词否决。一次复核/至多一次修正，修正后程序硬校验；语义整改由该次修正负责，不再第二次语义复核。成功修正不自动有限。引用权限、来源真实性、资产归属、存量CONFIRMED现场核验含义、取消/预算/迟到仍保留。此前v1的实施和失败记录是历史证据，不是新版验收门槛。旧结果和检查点继续兼容；v1新鲜流程的离线测试显式使用旧协议适配器，另由DeliveryReviewTest覆盖真实新产品路径。


## 当前合同：已接入逐项复核，规程合同仍未开放

真实 StructuredAgentReasoner 走 TaskOrchestrator 的语义复核分支，宿主再次校验证据后，文档明确事实允许保留并投影为 SUPPORTED；不再仅因“已确认”等词整份拒绝。旧内部/离线推理器未声明 supportsAnswerReview 时仍走原入口。引用伪造、越权及来源性质升级的拒绝要求不变；重启后 INVALID_CITATION 的原断言已在当前候选重跑通过，没有改成接受任意错误码。

规程/前提结构尚未完成，当前不得据此开放高风险具体操作。下节为旧行为基线，不代表新复核主链路仍按词拒绝，也不代表原 Run 草案已经找回。真模答案质量与合同/离线回归分开记录，见 acceptance.md。

## 2026-11-19 定向基线（尚未替换产品合同）

新增 `AnswerGuardTest.legacyGuardRejectsDocumentAndHistoryAlongsideCurrentDefect`，使用构造的模拟来源，固定四类语义：文档版本明确转述、历史损坏记录、当前设备损坏、以 KNOWLEDGE 类别伪装当前损坏。另用中性版本表述作对照。

当前旧实现把文档的“已确认”命中 `assertsConfirmedFinding`，把历史的“已经损坏”命中 `assertsDeviceDefect` 中的 `DEFINITE_COMPLETIONS`，两者均返回 UNCONFIRMED_FINDING；当前无依据损坏仍被拒绝。中性版本表述被接受且保留引用和 SIMULATED。

前两项是新规格需要替换的旧正文合同；当前设备无依据升级的拒绝必须保留。此测试暂时刻画旧行为，后续实施应转为新协议下的正反例，不把旧拒绝断言当作新产品验收。

原 Run c96da39d-263f-4b35-84f8-c30fdab28ab0 的忽略目录报告不含被拒绝草案，只含全局拒绝码及最终有限结果。因此以上分支定位只适用于构造样例，无法归因到原 Run；不能补造其 certainty、category 或正文。
