# RAG 文档预处理与 Chunk 设计说明

## 1. 背景

知识库当前支持通过 Apache Tika 解析 PDF、Word、Excel、PPT、HTML、Markdown、TXT 等多种文档。Tika 的职责是把不同文件格式抽取成文本和 metadata，但抽取结果通常是纯文本，不一定保留业务上下文。

如果直接把 Tika 文本交给 chunk 策略，会有几个问题：

1. 非 Markdown 文档没有稳定标题、来源、类型等上下文。
2. 不同文档类别的结构差异会被迫混进 chunk 策略。
3. 向量召回后，chunk 片段可能缺少文档名、章节路径、来源等解释信息。
4. 后续要支持制度、FAQ、表格、PPT 等专用处理时，扩展点不清晰。

因此本次在 Tika 解析和 chunk 策略之间增加一层文档预处理。

## 2. 总体链路

当前文档摄取链路如下：

```text
原始文件
  -> Tika 解析正文与 metadata
  -> 文档预处理
  -> Chunk 策略切分
  -> 可选 embedding
  -> 写入 kb_document_chunk
  -> 可选写入 Milvus
```

对应代码入口：

1. `DocumentChunkingServiceImpl` 负责主流程编排。
2. `TikaDocumentParser` 负责文件正文和 metadata 抽取。
3. `DocumentPreprocessorSelector` 负责选择预处理器。
4. `DefaultDocumentPreprocessor` 是默认兜底预处理器。
5. `ChunkingStrategyFactory` 继续选择 `FIXED_SIZE` 或 `PARAGRAPH`。

## 3. 为什么不是要求用户预输入 Markdown

系统不要求用户上传 Markdown。用户可以上传 PDF、Word、Excel、PPT、HTML、TXT 等文件。系统内部先把它们解析为文本，再转换为统一 chunk 输入格式。

Markdown 只是结构化文本的一种形式，不是 RAG 必须的输入格式。更重要的是内部形成稳定的中间表示：

```text
文档：<标题或文件名>
文档类型：<文件类型或 unknown>
来源：<来源地址，可选>

正文：
<Tika 抽取正文>
```

这样无论原始文件是什么格式，进入 chunk 策略前都有一致的上下文头。

## 4. 默认预处理器

默认预处理器位于：

```text
enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/preprocess/DefaultDocumentPreprocessor.java
```

它做三件事：

1. 生成统一上下文头。
2. 保留 Tika 抽取的正文。
3. 生成文档级和 chunk 级 metadata。

默认输出示例：

```text
文档：差旅报销制度
文档类型：application/pdf
来源：https://example.com/travel.pdf

正文：
高铁二等座可报销，飞机经济舱需提前审批...
```

如果标题、来源、文档类型等字段缺失，默认预处理器不会失败：

1. 标题优先使用 `KbDocument.title`。
2. 没有标题时使用 Tika metadata 中的 title。
3. 再没有时使用 `fileName`。
4. 文档类型优先使用 `fileType`。
5. 再没有时使用 Tika 的 `Content-Type`。
6. 仍然没有时使用 `unknown`。

## 5. Metadata 设计

预处理层会生成两类 metadata。

文档级 metadata 会合并到 `kb_document.metadata`：

```json
{
  "doc_type": "application/pdf",
  "title": "差旅报销制度",
  "section_path": ["差旅报销制度"],
  "source_location": "https://example.com/travel.pdf",
  "preprocess_strategy": "DEFAULT"
}
```

chunk 级 metadata 会合并到 `kb_document_chunk.metadata_json` 和 Milvus metadata：

```json
{
  "docId": 1001,
  "fileName": "travel.pdf",
  "sourceUrl": "https://example.com/travel.pdf",
  "chunkIndex": 0,
  "startOffset": 0,
  "endOffset": 1200,
  "sensitivityLevel": "ALL",
  "doc_type": "application/pdf",
  "title": "差旅报销制度",
  "section_path": ["差旅报销制度"],
  "source_location": "https://example.com/travel.pdf",
  "preprocess_strategy": "DEFAULT"
}
```

这样召回 chunk 后，系统可以知道它来自哪个文档、什么类型、来源地址是什么，以及使用了哪种预处理策略。

## 6. Chunk 策略仍保持通用

本次没有把文档类型逻辑写进 chunk 策略。chunk 策略仍然只负责切分文本：

1. `FIXED_SIZE`：按字符窗口切分，支持 overlap。
2. `PARAGRAPH`：按空行分段，单段过长时降级为固定窗口。

预处理层负责把不同文档格式变成统一 chunk 输入；chunk 层只负责切分统一文本。这样后续扩展制度、FAQ、表格、PPT 处理时，不需要改动已有 chunk 策略。

## 7. 后续扩展方式

后续可以新增类型化预处理器，例如：

1. 制度文档预处理器：保留条款编号和章节路径。
2. FAQ 预处理器：按问答对组织上下文。
3. Excel 预处理器：按 sheet、表头、行记录生成结构化文本。
4. PPT 预处理器：按页标题、页码、备注生成上下文。
5. 技术文档预处理器：保留标题层级、代码块说明和章节路径。

扩展方式是实现 `DocumentPreprocessor`：

```text
supports(context) -> 判断是否匹配该文档类型
preprocess(context) -> 输出标准化 chunk 输入和 metadata
```

`DocumentPreprocessorSelector` 会优先选择非 fallback 的匹配预处理器；没有匹配时使用 `DefaultDocumentPreprocessor`。

## 8. 失败路径

失败路径保持原有行为：

1. Tika 抽取正文为空：文档分块失败，状态变为 `FAILED`。
2. 预处理输出为空：文档分块失败，状态变为 `FAILED`。
3. 预处理器抛出异常：文档分块失败，状态变为 `FAILED`。
4. 失败信息写入 `kb_document_chunk_log.error_message`。

这保证新增预处理层不会绕开现有文档状态机和分块日志。

## 9. 当前边界

本次实现的是默认统一预处理，不包含以下能力：

1. OCR。
2. PDF 复杂版面恢复。
3. Excel 表格语义识别。
4. PPT 图表理解。
5. 自动文档类型分类。
6. rerank 或 parent-child 上下文扩展。

这些能力可以基于当前预处理 SPI 继续演进。

## 10. 测试覆盖

已覆盖的测试包括：

1. 默认预处理器上下文头和正文保留。
2. optional metadata 缺失时的 fallback。
3. 预处理器选择器 fallback。
4. `DocumentChunkingServiceImpl` 使用预处理后的文本进入 chunk。
5. chunk metadata 保留原有字段并合并预处理字段。
6. Milvus metadata 合并预处理字段。
7. Tika 空正文失败路径。
8. 预处理异常失败路径。
9. `FIXED_SIZE` 和 `PARAGRAPH` 策略兼容性。
