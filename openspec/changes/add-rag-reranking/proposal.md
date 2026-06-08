## 为什么

当前 RAG 召回结果直接使用 Milvus 向量排序，后续 hybrid search 也只能解决“候选覆盖”，不能保证最终排在前面的 chunk 就是最能回答问题的证据。需要在召回之后增加 rerank 层，对候选 chunk 进行二次排序，降低语义相近但不含答案的片段进入上下文的概率。

## 变更内容

- 新增 RAG reranking 能力：在 vector-only 或 hybrid retrieval 召回候选后、权限终检和结果组装前进行二次排序。
- 新增 `RagRerankService` 抽象，支持配置化启用/禁用和多策略实现。
- 第一阶段实现本地轻量 rerank：结合原始召回 rank、score、关键词覆盖、标题/章节命中、chunk 长度等特征计算 rerank 分。
- 预留模型 rerank 扩展点：后续可接 cross-encoder、LLM reranker 或第三方 rerank API。
- RAG 返回结果增加 rerank 相关调试 metadata，例如 `rerankScore`、`rerankReason`、`retrievalSource`。
- 增加 rerank 配置：`app.rag.rerank.enabled`、`strategy`、`candidateLimit`、`timeoutMs`、`fallbackToOriginalOrder`。
- **BREAKING**: 无。rerank 默认可配置关闭，关闭时保持原召回顺序。

## 能力范围

### 新增能力

- `rag-reranking`: RAG 召回候选二次排序能力，覆盖 rerank 服务抽象、轻量排序策略、模型 rerank 扩展点、失败回退、调试 metadata 和测试要求。

### 修改能力

无。

## 影响范围

- **enterprise-knowledge-ai-service**：新增 rerank domain/model/service；`RagQaTool` 或后续 `RagRetrievalService` 在候选组装前调用 rerank。
- **配置**：新增 `app.rag.rerank.*` 配置块，支持启用、策略、候选上限、超时和失败回退。
- **检索结果**：matched chunk 增加可选 rerank 字段，不改变已有字段。
- **测试**：需要覆盖 rerank 排序、关闭保持原顺序、异常回退、topK 限制、权限过滤不被绕过。
- **后续集成**：与 `milvus-hybrid-search` 兼容；hybrid 提供候选，rerank 负责最终上下文排序。
