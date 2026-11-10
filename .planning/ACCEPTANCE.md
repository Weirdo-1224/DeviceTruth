# DeviceTruth 项目验收执行策略

生效日期：2026-11-10。用户授权将技术性验收交由代理实际执行，保留GSD规划、执行、验收与修复机制；每阶段尽量只集中一次人工确认。本文件补充项目执行策略，不修改全局GSD、不关闭阶段出口。

## 1. 已核实的 GSD 1.20.1 能力

实际执行`node C:/Users/11929/.codex/gsd-core/bin/gsd-tools.cjs runtime-identity --raw`返回`@opengsd/gsd-core / 1.20.1`。本机安装目录仅供核对，其他机器按其实际安装根路径替换。

已读取安装目录中的：

- `workflows/verify-work.md`的`extract_tests`、`create_uat_file`与阶段完成门禁。
- `templates/summary.md`的`coverage_guidance`与`bin/lib/coverage.cjs`实际分类实现。
- `templates/config.json`、`bin/lib/config.cjs`及`bin/shared/config-schema.manifest.json`的配置能力。

原生`coverage`分类条件是：`human_judgment: false`、`verification`非空、每条`status: pass`且无schema错误，才进入`auto_passed`；原生流程要求这些条目写UAT为`result: pass / source: automated`，不再逐项提示。需要主观判断的进入`present`。缺少coverage则走legacy文字提取；当前Phase 1原先正是这种情况。全自动覆盖时，原生流程仍保留一份集中确认摘要。

**分类器不会运行测试，也不会验证所填pass是否来自真实执行。** 因此下节是项目必须执行的证据规则。没有发现可将“技术测试自动运行并只集中人工确认”作为整体切换的原生配置；`workflow.auto_advance`控制后续推进，不替代验收。`parallelization.skip_checkpoints`属于并行执行检查点，不能作为自动验收开关。本项目继续保留interactive、auto_advance=false和现有gates，不添加假GSD配置键。

## 2. 每阶段执行顺序

1. 读STATE、PLAN、需求和本策略，确认本次授权及实施范围。按实际目标列出交付物、可执行验证与需要人的业务判断。
2. **实际运行**单元、集成、CLI、状态和安全负例；A–E这种有明确预期的场景自动比对结果、规则、来源、事件与状态。PG、打包启动/重启、真实模型、真实embedding和UI分别执行和记录。
3. 记录本轮命令、退出码、时间/批次、测试统计和原始产物位置；核对测试输出与当前实施文件一致。变更影响到已验证行为时重新运行受影响验证。历史报告只能作参考；同一活动验收轮次内已亲自执行且实施文件未变的证据可复用，必须标明执行批次，不能写成又运行了一次。
4. 失败先记录、修复、复验；环境缺失记blocked/unknown，不以跳过当通过。独立Review、必要安全和覆盖核验继续执行。源码存在或分类器通过都不能替代行为测试。
5. 将本轮结果填入SUMMARY的原生coverage。通过技术项`human_judgment: false`并列真实测试引用；未执行或失败填unknown/fail，不得清空coverage或伪造pass来减少提示。属于技术工作而证据不足的，由代理继续补验/修复，不把它变成让用户凭感觉批准的技术题。
6. 运行`query uat.classify-coverage --summary <SUMMARY>`核对分类。把自动通过项和实际证据写入UAT；保留已经发生的人工通过、失败与修复历史。只将尚需人判断的产品/业务/设计事项汇成**一个Phase级确认项**，包含所有需要确认的要点及原因；不按每个PLAN或每项技术测试重复询问。
7. 生成下节简洁报告，再提示集中人工确认。若没有新业务判断且已有授权足以覆盖，只使用原生所需的一次汇总确认，不人为增加确认项；外部凭据/实际设备等确需用户协助的事项可在必要时单独说明。
8. VERIFICATION保持`human_needed`直到所需人工确认完成；有实质问题则`gaps_found`。按真实结果更新覆盖指纹和GSD状态，执行`phase uat-passed <phase> --require-verification`等原生出口检查，通过后才关闭Phase。后续计划和Git操作继续遵守用户授权。

## 3. Coverage 写法

```yaml
coverage:
  - id: P1-CLI
    description: "继承CLI结果及安全状态符合断言"
    requirement: BASE-02
    verification:
      - kind: e2e
        ref: "python scripts/verify_cli.py（本轮已实际执行的批次）"
        status: pass
    human_judgment: false
  - id: P1-PRODUCT
    description: "本阶段交付边界与下一步设计集中确认"
    verification: []
    human_judgment: true
    rationale: "属于产品交付与设计取舍认可，自动测试只能证明实现符合既定断言。"
```

这是格式示例，不自动赋予任何未执行计划pass。新增Phase按实际交付物逐条列出，不使用`coverage: []`隐藏交付物。原生分类遇到unknown/fail/缺证会进入present；项目先处理其技术缺口，最终需要人的事项集中呈现，不将它们批量签成通过。

## 4. 阶段简洁报告与证据口径

每阶段写`<phase>-ACCEPTANCE.md`，至少包含：当前阶段状态；本轮测试总数、通过、失败、错误、跳过；额外CLI/打包/PG等检查结果；关键问题及修复/复验；未验证项；唯一集中人工确认（如有）。JUnit测试数、CLI检查组、静态Review威胁条目和UAT记录行数分别统计，不相加制造测试数量。

- `SIMULATED`夹具证明模拟场景与规则；本机HTTP替身证明协议，不代表外部真实LLM效果。
- 真实PG证明迁移/事务/持久化，不代表真实设备或生产执行恢复。
- 测试原型的快照重建不代表生产恢复；未实际执行的在线/现场验证明确记未验证。
- 日志、运行报告与凭据保持忽略；可提交规划记录只保留脱敏摘要和可重跑测试引用。不得读出、提交或透传真实凭据。

## 5. 当前 Phase 1 的迁移

保留用户已确认的第1项CLI B通过，以及首次clean临时移除JAR导致的操作失败与恢复历史。原第2项A–E结果、第4项自由消息保存、第5项重启与非法输入均改为基于本轮实际执行结果自动记录；没有收到的人工确认不冒称已收到。取消原逐项技术提示，第3项收敛为一次产品交付/Runtime选型/后续范围集中确认。对应记录见`phases/01-baseline-contracts/01-UAT.md`、`01-ACCEPTANCE.md`。

用户原有授权继续有效：Phase 1出口全部满足后提交当前Git，再执行02-01；暂不执行02-02/02-03。无需在集中验收后重复申请这一实施授权。
