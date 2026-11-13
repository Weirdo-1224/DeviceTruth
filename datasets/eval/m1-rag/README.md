# M1 固定 RAG 小样本

本目录在调参前冻结 12 个样本，只证明最小检索与引用合同，不替代 M4 的 14 个 E2E、规模或性能评测。两份语料均标记 `SIMULATED`；当前实施按用户决定仅交付 Markdown 上传，PDF 未实现且保持 `NOT_RUN`。

## 样本构成

- 中文语义 4 例、精确部件号 2 例：正向 6 例要求混合检索 Hit@5 至少 5/6，部件号 2/2 命中。
- 跨 owner/project 2 例、空检索 1 例、旧版本/冲突过滤 1 例：不得返回禁止文档，旧版本样本必须命中 2026.2。
- 伪造 referenceId 1 例、伪造 documentId/chunkId 定位 1 例：服务端接受数必须为 0。

`cases.json` 固定查询、身份、过滤条件、期望文档/版本/章节、禁止引用和错误码；`manifest.json` 固定语料 SHA-256。向量、关键词和混合通道分别记录同一批正向样本的 Hit@5、定位正确率和耗时，不混用不同样本比较。

## 执行

真实 PG 配置使用 `DEVICETRUTH_TEST_PG_BASE_URL/USERNAME/PASSWORD`；在线 embedding 还需要服务端 `DEVICETRUTH_EMBEDDING_API_KEY` 或 `DASHSCOPE_API_KEY`。真实模型使用 `DEVICETRUTH_AGENT_REASONER=qwen` 或 `openai`，禁止回退 scripted/deterministic 冒充在线通过。

```bash
python scripts/verify_general_agent.py --suite=m1-retrieval --embedding=real
python scripts/verify_general_agent.py --suite=rag-smoke --embedding=real
python scripts/verify_general_agent.py --suite=multi-turn-basic --embedding=real
```

结果写入 `outputs/validation/general-agent/<suite>-<timestamp>/result.json`，不包含密钥。缺 Key、网络或额度分别记录 `BLOCKED_BY_ENV/NOT_RUN`；浏览器 UI、PDF、跨重启和并发不属于这些命令的通过范围。
