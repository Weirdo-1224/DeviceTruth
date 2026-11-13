---
phase: 04-skills-dual-domain
plan: "02"
status: complete
completed: 2026-11-13
requirements-completed: [DATA-04, DATA-05, DATA-06, SKILL-03]
coverage:
  - id: P4-FACTORY-SOURCES
    description: "工厂出厂、改造、旧模板及现场核验按来源和有效期分别判定"
    requirement: DATA-04
    verification:
      - kind: integration
        ref: "DualDomainAgentTest；真实 PostgreSQL，四组工厂样本及冲突、时点断言"
        status: pass
    human_judgment: false
  - id: P4-OBSERVATION
    description: "按资产、归属、时点和质量读取模拟观测，缺记录时明确未读取现场数据"
    requirement: DATA-05
    verification:
      - kind: integration
        ref: "DualDomainAgentTest；模拟记录引用落库回读、质量、未来时点和归属负例"
        status: pass
    human_judgment: false
  - id: P4-DUAL-HTTP
    description: "汽车和工厂使用同一产品 HTTP、Runtime 与网关，分别核对领域指引与事实"
    requirement: DATA-06
    verification:
      - kind: e2e
        ref: "verify_general_agent.py --suite=dual-domain --embedding=deterministic --reasoner=scripted；汽车 9 项、工厂 8 项"
        status: pass
    human_judgment: false
  - id: P4-ONLINE
    description: "真实模型和真实向量服务双域对照"
    requirement: SKILL-03
    verification:
      - kind: e2e
        ref: "DeepSeek + DashScope，实际 HTTP 和 PostgreSQL；保留原始报告，对唯一措辞误判用更正后的规则复核，同批汽车 9/9、工厂 8/8"
        status: pass
    human_judgment: false
---

V15 增加模拟观测，产品网关登记 `get_observations`。汽车样例能在同一任务追问、补答、检索并回读手册章节。工厂样例通过现有资产工具回读三个来源与有效期，并保留现场核验边界。Skill 指引、知识引用、配置与观测状态分开传给同一运行循环。产品可回读已登记的模拟观测引用。

真实 PostgreSQL 上 `DualDomainAgentTest` 4/4。打包离线双域 HTTP 通过。在线批次使用 DeepSeek 和 DashScope，在同一批次跑完两域。保留原始 `FAIL`，用修正后的工厂未核实措辞断言复核同一份答案，其余断言保持原值，复核 17/17。在线汽车首轮明确请求问清两项信息，补答明确要求检索手册与检查观测；离线使用固定原始问题。详见 04-ACCEPTANCE。
