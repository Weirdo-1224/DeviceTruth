---
phase: 02-open-rag
plan: "01"
status: passed
reviewed: 2026-11-11
scope: "02-01及先前P1/P2修复；未审查或执行02-02/02-03"
---

# 02-01 独立复审

结论：通过。未解决P0/P1/P2均0；审查者未修改业务代码，未commit/push。

## 历史问题与关闭证据

| 问题 | 原结论 | 当前证据 | 状态 |
|---|---|---|---|
| fake/real档案重名、重导绕过兼容性 | 首轮P1，阻止通过 | 结构化档案、独立离线模型、导入维度前置及完整档案校验；知识PG13/配置6项通过 | 关闭 |
| 单行切片超过配置上限 | 首轮P2 | KnowledgeChunkerTest 5项，含长单行/换行/补充Unicode | 关闭 |
| 待审SUMMARY被GSD计为完成 | 首轮P2 | 修复期间blocked实际使原生完成数回到2/13；本轮仅在复审通过后改complete并原生推进 | 关闭 |
| V3猜provider，把旧假向量改标为真实档案 | 首次复审P1，继续阻止通过 | V4将既有向量统一隔离NEEDS_REBUILD；真实PG升级回归及独立已有V3探针全部通过 | 关闭 |

原始失败证据分别保留在`outputs/validation/02-01-review/`与`outputs/validation/02-01-rereview/`；本轮证据在`outputs/validation/02-01-rereview-v4/`。这些目录保持Git忽略，不提交日志。

## 当前修复为何有效

`V4__knowledge_legacy_quarantine.sql`不推断旧向量的供应商，统一置为`legacy-unverified`及`NEEDS_REBUILD`；导入服务返回409 `DOCUMENT_NEEDS_REBUILD`，检索仅接受当前档案且READY的文档。原文读取仍核验owner/project/run引用集合，未删除旧正文、切片ID或引用。

例如旧充电手册曾被误标成真实千问档案：升级后同版本重导被拒绝且不调用embedding；换新文档版本重新向量化后可检索；旧引用仍打开原版本。再次普通Flyway启动不会重跑V4，因此新重建版本继续READY。

## 实际验证

- `mvn -o test`：185/0/0/0，35 suites；真实PG知识13、升级1、会话8，全部通过。
- `mvn -o -DskipTests package`成功；`python scripts/verify_cli.py`全部通过；打包没有重复执行测试。
- 独立`ExistingV3UpgradeProbe`：迁移前插入引用，V3确认错误标识，升级V4，再验证旧版拒绝/零embedding、禁止召回、旧引用可读、新版重建、重复启动不重新隔离及新版可召回。9断言通过，随机库已清理。

## 范围限制

V4隔离迁移时全部已有向量，恢复检索须用新版本重建。真实PG证明事务/迁移与引用合同；确定性及本机HTTP替身不证明真实千问或语义质量。真实embedding、PDF/混合检索属于02-03，Runtime/模型答案引用校验及Vue属于02-02；Phase2产品确认仍集中到阶段出口。
