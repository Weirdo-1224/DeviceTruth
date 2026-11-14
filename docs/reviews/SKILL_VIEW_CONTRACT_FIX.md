# Skill 视图合同不一致修复记录（P2-3）

日期：2026-11-13

## 问题

审查清单 P2 第 3 条：前后端 Skill 视图合同不一致，前端展示的领域包名称为空。

核实证据：

- 后端 `GET /api/runs/{id}/skill` 返回 `SkillRuntime.SelectionView(skillId, version, devices, tasks)`（`src/main/java/io/devicetruth/skill/SkillRuntime.java:34`）。
- 前端 `frontend/src/api.ts` 的 `SkillSelection` 却定义为 `{skillId, name, version, scope}`，`App.vue` 展示 `selectedSkill.name` —— `name`/`scope` 后端从不返回，页面显示空白。
- `docs/reviews/DEVICETRUTH_INTERFACE_INVENTORY.md` 接口清单同样误写为 `{skillId,name,version,scope[]}`，前端是按这份文档实现的。

## 决策

改前端对齐后端，不改后端：

- `name` 与 `skillId` 冗余——Skill 规范要求目录名与 `name` 同名（`SkillMetadata.java:17`），持久化的 `skill_id` 就是 Skill 名。
- 后端 `devices`/`tasks` 比单一 `scope` 数组信息更全（适用范围分设备/任务两类），且后端合同已被 `dt_run_skill` 持久化结构与 Java 侧测试固定。
- 前端是该接口唯一消费者，前端修复爆炸半径最小。

## 修改内容

- `frontend/src/api.ts`：`SkillSelection` 改为 `{skillId, version, devices, tasks}`，注释注明与 `SkillRuntime.SelectionView` 一致。
- `frontend/src/App.vue`：领域包展示改用 `selectedSkill.skillId`。
- `frontend/src/api.test.ts`：Skill 合同单测的桩数据改为真实后端形状，并补 `devices`/`tasks` 断言。
- `docs/reviews/DEVICETRUTH_INTERFACE_INVENTORY.md`：接口清单第 17 行更正为实际合同。

## 验证

- `cd frontend && npm run test -- --run`：全部通过（含更新后的 Skill 合同用例）。
- `cd frontend && npm run build`：`vue-tsc --noEmit` 类型检查与 Vite 构建通过。
