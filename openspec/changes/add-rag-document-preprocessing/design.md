## Context

`enterprise-knowledge-ai-service` 当前文档摄取主链路为：文件上传落库为 `PENDING`，`start-chunk` 后异步执行 `DocumentChunkingServiceImpl`，通过 `TikaDocumentParser` 抽取正文与 metadata，再使用 `PARAGRAPH` 或 `FIXED_SIZE` 策略分块，最后写入 `kb_document_chunk` 和可选 Milvus 向量。

这个链路已经能处理非 Markdown 输入，但 Tika 输出的是统一纯文本，缺少稳定的业务上下文结构。制度文档、FAQ、Excel、PPT、技术文档的最佳 chunk 入口格式不同，如果直接把差异塞进 chunk 策略，会导致策略膨胀、metadata 不一致、召回结果缺少标题/章节/来源解释。

本变更在解析和 chunk 之间增加预处理层，先落默认统一模板，后续通过类型适配器扩展。

## Goals / Non-Goals

**Goals:**

- 在 Tika 解析后、chunk 前引入 `DocumentPreprocessor` 服务。
- 定义标准化预处理结果，包含用于 chunk 的正文、用于 embedding 的上下文文本，以及用于 DB/Milvus 的 metadata。
- 默认预处理器适用于所有文档类型，保证当前实现不依赖用户预输入 Markdown。
- 让 `DocumentChunkingServiceImpl` 继续复用现有 `ChunkingStrategy`，避免把文档类型逻辑写入 `PARAGRAPH` / `FIXED_SIZE`。
- 将新增上下文字段写入 `kb_document.metadata` 和 `kb_document_chunk.metadata_json`，并合并到 Milvus metadata。

**Non-Goals:**

- 不实现 OCR、复杂版面恢复、表格结构识别、PPT 图表理解。
- 不引入新的 embedding 模型、rerank 模型或检索排序策略。
- 不改变上传、分块、chunk CRUD、权限过滤的现有 API 合约。
- 不新增强制数据库字段；本阶段优先使用 JSON metadata 扩展。

## Decisions

### Decision 1: 使用预处理层而不是改造 chunk 策略

新增 `DocumentPreprocessor`，输入为 Tika 抽取的文本、Tika metadata、`KbDocument` 基础信息，输出标准化结果。`DocumentChunkingServiceImpl` 只消费预处理后的文本并继续调用现有 chunk 策略。

原因：chunk 策略只应负责“如何切”，文档类型预处理负责“切什么文本、带什么上下文”。这样制度、FAQ、表格等业务差异不会污染通用分块策略。

备选方案是为每个文档类型新增 chunk 策略。该方案短期直观，但会把解析、模板、metadata、切分边界混在同一类里，后续维护成本高。

### Decision 2: 默认预处理器先统一模板

第一版提供 `DefaultDocumentPreprocessor`，所有文档都生成同一类上下文头：

```text
文档：<title/fileName>
文档类型：<docType or unknown>
来源：<sourceLocation>

正文：
<tikaText>
```

正文为空仍按现有逻辑失败。上下文头字段缺失时跳过或使用默认值，不能因为 metadata 不完整导致分块失败。

原因：用户当前明确接受“预处理默认一样”，先统一文本入口能快速提升非 Markdown 文档的上下文一致性，同时为后续类型化预处理留接口。

### Decision 3: 预留类型适配器 SPI

定义 `DocumentPreprocessor` 接口和选择器。默认实现永远可用；后续可以基于 `document.fileType`、`document.categoryId`、`document.metadata` 或上传请求中的文档类型选择专用实现。

选择逻辑按“显式文档类型优先，文件类型其次，默认处理器兜底”。本变更只要求默认处理器生效，不要求实现自动分类。

### Decision 4: metadata 以 JSON 扩展为主

新增字段优先写入 `kb_document.metadata` 和 `kb_document_chunk.metadata_json`，例如：

- `doc_type`
- `title`
- `section_path`
- `source_page`
- `source_location`
- `preprocess_strategy`

Milvus metadata 继续通过 `VectorDocChunk.metadata` 合并，不改变集合字段定义。

原因：当前表已经有 JSON metadata，且 Milvus schema 已支持 metadata JSON。使用 JSON 扩展能降低数据库迁移成本，避免为尚未稳定的业务字段提前固化列。

## Risks / Trade-offs

- 统一模板会增加 embedding 文本长度 → 控制上下文头只放高价值短字段，并继续使用 `maxChars` 限制 chunk。
- JSON metadata 不利于高频条件查询 → 本阶段只用于召回解释和上下文补充；若后续需要按字段检索，再补充索引列和迁移脚本。
- Tika 对复杂 PDF/Excel/PPT 的结构恢复有限 → 本变更只解决预处理入口和 metadata 契约，不承诺复杂版面理解。
- 预处理后文本参与 chunk 可能影响 offset 含义 → chunk metadata 应明确 offset 基于预处理后的 chunk 输入文本；如需原文页码，后续由专用预处理器填充 `source_page`。

## Migration Plan

1. 新增预处理接口、默认实现和结果模型。
2. 修改 `DocumentChunkingServiceImpl`：Tika 解析后调用预处理器，chunk 使用预处理输出文本。
3. 合并预处理 metadata 到文档 metadata 和 chunk metadata。
4. 增加单元测试，覆盖默认模板、metadata 传递和现有 chunk 策略兼容。
5. 部署后新分块任务使用预处理输出；历史已成功文档不自动重建 chunk，可通过人工重新 `execute-chunk` 补偿。

回滚策略：移除预处理调用或配置为仅返回原始 Tika 文本；已写入的 JSON metadata 字段不影响旧逻辑读取。

## Open Questions

- 文档类型来源最终由上传接口显式传入、知识库分类推断，还是后台管理维护映射？
- 是否需要为 FAQ、Excel、PPT 分别定义专用中间结构和手动校验页面？
- 后续检索阶段是否要基于 `section_path` 做 parent-child 上下文扩展？
