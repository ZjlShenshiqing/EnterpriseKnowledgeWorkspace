## 上下文

当前 RAG 检索入口 `RagQaTool` 直接调用 `VectorSyncService.searchSimilar(question, topK * 3, contextDoc)`，随后查询 DB 文档和 chunk、执行权限终检，并按原始召回顺序返回 matched chunks。该链路没有二次排序：Milvus dense vector 的相似度分数直接决定候选顺序。

即使后续接入 `milvus-hybrid-search`，hybrid 主要解决“候选覆盖”问题：dense 捕捉语义相似，sparse/BM25 捕捉精确词。候选覆盖变好后，仍需要 rerank 判断哪些 chunk 最能回答当前问题，否则语义相近、关键词命中但上下文不完整的片段仍可能排在前面。

本变更增加独立 rerank 层，接在 retrieval candidate 产生之后、最终结果组装之前。

## 目标 / 非目标

**目标：**

- 新增 `RagRerankService` 抽象，支持对召回候选进行二次排序。
- 第一阶段提供本地轻量 rerank 策略，不强依赖外部 reranker。
- rerank 必须兼容当前 vector-only 检索和后续 Milvus hybrid 检索。
- rerank 失败时可配置回退原始召回顺序，避免影响 RAG 可用性。
- matched chunk 返回中增加可选 rerank debug metadata，便于调试排序原因。
- 保证 rerank 不绕过文档状态、chunk 启用状态和 DB 权限终检。

**非目标：**

- 本变更不实现 LLM 答案生成质量评估。
- 本变更不实现 cross-encoder 模型部署。
- 本变更不引入新的搜索中间件。
- 本变更不替代 hybrid search；rerank 是 hybrid/vector 之后的排序层。

## 设计决策

### 决策 1：rerank 作为独立服务层

新增 `RagRerankService`：

```java
List<RerankedCandidate> rerank(RerankRequest request);
```

输入包含 query、候选 chunk、原始 rank、retrieval source、vector score、keyword score 和 metadata。输出保留候选身份并补充 `rerankScore`、`rerankReason`、`rerankStrategy`。

原因：把 rerank 从 `RagQaTool` 和 `VectorSyncService` 中拆出，后续无论 vector-only、Milvus hybrid、OpenSearch hybrid 还是模型 rerank，都可以复用统一排序接口。

备选方案是在 `RagQaTool` 内直接排序。该方案改动小，但会继续扩大 Tool 职责，不利于后续加 hybrid、debug 页面和评测。

### 决策 2：第一阶段使用本地轻量 rerank

第一版实现 `LocalFeatureRagRerankService`，使用可解释特征排序：

1. 原始召回 rank。
2. 原始向量分数或 hybrid fused score。
3. query 关键词在 chunk 文本中的覆盖率。
4. query 关键词在标题、章节路径、metadata 中的命中。
5. chunk 长度惩罚，避免过短或过长片段排太前。
6. retrieval source 加权，例如 HYBRID 命中优于单路命中。

示例公式：

```text
rerankScore =
  0.35 * retrievalScoreNorm
  + 0.30 * keywordCoverage
  + 0.15 * titleOrSectionHit
  + 0.10 * sourceWeight
  + 0.10 * lengthQuality
```

原因：本地 rerank 可以先建立排序框架、调试字段、测试边界和回退机制；后续接模型 rerank 时替换策略即可。

备选方案是直接接 cross-encoder/LLM reranker。该方案效果上限更高，但会引入模型部署、延迟、费用、超时和降级问题，不适合作为第一步。

### 决策 3：rerank 只处理权限过滤后的候选

推荐顺序：

```text
召回候选
  -> 查询 DB chunk/document
  -> 过滤 deleted/status/enabled
  -> DB 权限终检
  -> rerank
  -> topK 截断
  -> 组装 matchedChunks
```

原因：rerank 没必要消耗无权限候选；并且模型 rerank 后续可能把 chunk 文本发送给外部服务，必须先做权限终检。

如果为了性能需要在权限前做 lightweight rerank，只能使用不含敏感正文的 metadata 特征，不能调用外部模型。

### 决策 4：配置化启用和失败回退

新增配置：

```yaml
app:
  rag:
    rerank:
      enabled: true
      strategy: LOCAL_FEATURE
      candidate-limit: 50
      timeout-ms: 800
      fallback-to-original-order: true
```

关闭时保持旧排序；开启后只对 `candidate-limit` 内候选排序；失败时按配置回退原顺序或返回错误。

### 决策 5：预留模型 rerank 扩展点

定义 `RagReranker` 策略接口：

```java
boolean supports(RerankStrategy strategy);
List<RerankedCandidate> rerank(RerankRequest request);
```

后续可新增：

1. `CrossEncoderRagReranker`
2. `LlmRagReranker`
3. `RemoteApiRagReranker`

所有策略都必须遵守超时、候选上限、失败回退和权限前置约束。

## 风险 / 取舍

- **[风险] 本地 rerank 效果有限** → 第一版目标是建立排序框架和可解释基线，后续再接模型 rerank。
- **[风险] rerank 增加延迟** → 限制 candidate 数量，配置超时，支持关闭和回退。
- **[风险] 关键词覆盖对中文分词粗糙** → 第一版使用简单 tokenizer，后续可接中文分词器或从 hybrid sparse 结果复用词项。
- **[风险] 模型 rerank 可能泄露无权限内容** → rerank 必须默认放在 DB 权限终检之后。
- **[风险] 排序变更影响现有结果稳定性** → 保留 `enabled=false` 和 fallback 原始顺序，测试覆盖关闭模式。

## 迁移计划

1. 新增 rerank 配置，默认可先关闭或在测试环境开启。
2. 新增候选模型和 `RagRerankService` 抽象。
3. 实现本地轻量 rerank。
4. 在当前 `RagQaTool` 或后续 `RagRetrievalService` 中接入 rerank。
5. 返回 matched chunks 时补充 rerank debug metadata。
6. 用固定测试样例验证 rerank 能提升精确命中 chunk 排名。
7. 灰度开启 `app.rag.rerank.enabled=true`。

回滚方式：设置 `app.rag.rerank.enabled=false`，恢复原召回顺序。

## 待确认问题

- 第一阶段默认是否开启 rerank，还是仅测试环境开启？
- 本地 tokenizer 是否需要立即支持中文分词库，还是先用字符/正则 token 基线？
- 模型 rerank 后续优先接本地 cross-encoder，还是云端 rerank API？
