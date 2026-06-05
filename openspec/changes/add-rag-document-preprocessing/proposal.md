## Why

当前知识库文档摄取链路已支持 Tika 抽取多格式文件并按 `PARAGRAPH` / `FIXED_SIZE` 分块，但所有文档都直接进入统一纯文本 chunk，无法稳定保留制度条款、FAQ 问答、表格行、PPT 页等上下文结构。随着 RAG 文档类型增多，需要在 chunk 前引入统一预处理层，让非 Markdown 输入也能转换为可检索、可追溯、可扩展的标准中间文本。

## What Changes

- 新增 RAG 文档预处理能力：在 Tika 解析之后、chunk 策略之前，对抽取文本和 metadata 做统一规范化。
- 新增标准化 chunk 输入模型：包含正文、上下文头、文档类型、标题、章节路径、页码/来源等 metadata。
- 新增默认预处理器：所有文档先采用同一套默认模板，满足当前“预处理默认一样”的实现边界。
- 预留文档类型适配器机制：后续可按制度、FAQ、表格、PPT、技术文档等类型扩展专用预处理器。
- 扩展 chunk metadata：chunk 入库和向量写入时携带 `doc_type`、`section_path`、`source_page` 等可选字段，提升召回后上下文解释能力。
- 不改变现有上传、`start-chunk`、`execute-chunk` API 的主流程和文档状态机。

## Capabilities

### New Capabilities

- `rag-document-preprocessing`: RAG 文档预处理与标准化 chunk 输入，覆盖多格式文档解析后进入 chunk 前的统一文本模板、metadata 契约、默认处理器和可扩展类型适配器。

### Modified Capabilities

无。

## Impact

- **enterprise-knowledge-ai-service**：新增预处理服务接口与默认实现；调整 `DocumentChunkingServiceImpl` 在 Tika 解析后调用预处理层；扩展 chunk metadata 构建逻辑。
- **数据模型**：优先复用 `kb_document.metadata` 与 `kb_document_chunk.metadata_json` 存储新增 metadata；如后续需要索引查询，再单独补充迁移脚本。
- **向量库**：Milvus metadata JSON 合并新增字段，不改变集合核心 schema。
- **兼容性**：现有文档状态流转、分块策略、权限过滤、向量写入开关保持兼容；缺失文档类型时走默认预处理器。
- **测试**：需要覆盖默认预处理、metadata 传递、现有 `PARAGRAPH` / `FIXED_SIZE` 行为兼容，以及非 Markdown 文件抽取后的标准化输入。
