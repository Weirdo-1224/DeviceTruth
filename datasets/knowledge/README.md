# DeviceTruth 知识数据 v1.0

正式语料为 343 篇 Markdown（汽车 130、工业 160、Carrier 23、证据规则 30）。默认 E3 / METADATA / 512 切片产生 343 Parent、682 Child。技术资料与项目证据规则区分保存；公开文档不代表当前设备的现场事实。

## 可获取的数据包

所需输入随 Git 仓库保存，独立数据包由 `scripts/knowledge_data.py pack` 生成，无需访问开发者机器或下载历史报告。包内仅包含版本清单、正文、来源登记及被正文引用的原件，排除评测题、答案、历史报告、数据库备份和运行缓存。

```bash
python3 scripts/knowledge_data.py verify
python3 scripts/knowledge_data.py pack
```

默认输出 `outputs/packages/devicetruth-knowledge-v1.tar.gz` 与 SHA-256 文件。接收者可以将包解压到独立目录，运行 `python3 scripts/knowledge_data.py verify --root <解压目录>`，再用 `knowledge_admin.py --knowledge-root <解压目录>/datasets/knowledge` 导入。原始目录结构应完整保留。

## 必要输入

- `package-manifest.json`：发布输入的路径、大小与 SHA-256。
- `reports/knowledge_v1_manifest.json`：冻结的 343 篇版本清单，是导入输入。
- `sources/source_registry.jsonl`：来源登记，文件哈希与冻结清单绑定。
- `curated/`：343 篇正文与 JSONL 索引。
- `raw/`：被正文引用的 55 个来源原件；另有 8 个来源指向仓库内的 Skill 正文。

详细的数据库配置、费用上限、导入、激活、回滚和索引重建见 [知识库运维](../../docs/knowledge-operations.md)。生产向量由部署者使用自己的 embedding 配置生成，不包含在数据包中。
