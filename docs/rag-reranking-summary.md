# RAG Reranking 实现总结

## 背景

RAG 召回结果原来直接使用 Milvus 向量排序，即使后续接入 hybrid search 也仅解决"候选覆盖"问题，不能保证最终排在前面的 chunk 就是最能回答问题的证据。本次实现增加独立的 rerank 层，对候选 chunk 进行二次排序。

## 架构

```
RagQaTool
  -> RagRetrievalService
       -> 检索（VECTOR_ONLY / HYBRID_MILVUS）
       -> DB 查询文档 + chunk
       -> 权限终检
       -> RagRerankService（二次排序）    ← 新增
       -> topK 截断
       -> 返回 matchedChunks
```

Rerank 接在 DB 权限终检之后、topK 截断之前，确保无权限内容不会进入 rerank。

## 新增文件

```
enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/
├── config/
│   └── RagRerankProperties.java          # 配置属性
└── service/rerank/
    ├── RerankStrategy.java               # 策略枚举（NONE / LOCAL_FEATURE）
    ├── RerankedCandidate.java            # 候选模型（保留原始召回字段 + rerank 元数据）
    ├── RerankRequest.java                # 请求模型
    ├── RagRerankService.java             # 服务接口
    ├── RagReranker.java                  # 策略接口（扩展点，后续可接模型 reranker）
    ├── RagRerankServiceImpl.java         # 服务实现：策略分发、超时、失败回退
    └── LocalFeatureRagReranker.java      # 本地特征 reranker
```

## 配置

```yaml
app:
  rag:
    rerank:
      enabled: true                       # 开关，关闭时保持原召回顺序
      strategy: LOCAL_FEATURE             # 策略：NONE / LOCAL_FEATURE
      candidate-limit: 50                 # 候选数量上限
      timeout-ms: 800                     # 超时时间
      fallback-to-original-order: true    # 失败时回退原顺序
```

## 本地特征 Reranker 评分公式

每个候选 chunk 计算 5 个维度的分数加权求和：

```
rerankScore =
  0.35 × retrievalScoreNorm    （原始召回分数归一化）
+ 0.30 × keywordCoverage        （query 关键词在 chunk 正文中的覆盖率）
+ 0.15 × titleOrSectionHit      （query 关键词在标题/章节路径中的命中）
+ 0.10 × sourceWeight           （召回来源加权：HYBRID > DENSE/SPARSE > 其他）
+ 0.10 × lengthQuality          （chunk 长度质量：过短/过长惩罚）
```

各维度说明：

| 维度 | 权重 | 说明 |
|---|---|---|
| retrievalScoreNorm | 0.35 | 原始分数除以最大分数归一化 |
| keywordCoverage | 0.30 | query token 在 chunk 文本中的去重命中比例 |
| titleOrSectionHit | 0.15 | metadata 中 title/section/chapter/heading 字段命中 query token 的比例 |
| sourceWeight | 0.10 | HYBRID=1.0, DENSE=0.7, SPARSE=0.7, 其他=0.5 |
| lengthQuality | 0.10 | <50字符按比例惩罚，50-300字符线性增长至满分，300-2000字符线性衰减至0.5 |

## 修改文件

| 文件 | 改动 |
|---|---|
| `RetrievalResult.java` | `ChunkResult` 新增 `rerankScore`、`rerankStrategy`、`rerankReason` 字段，兼容旧构造 |
| `RagRetrievalServiceImpl.java` | 检索链路重构：先查询所有 chunk → 构建候选 → 调用 rerank → 按 rerank 顺序分组文档 → topK 截断 |
| `RagQaTool.java` | 输出 JSON 中补充 rerank 调试字段 |
| `application.yml` | 新增 `app.rag.rerank.*` 配置块 |

## 关键设计决策

1. **rerank 作为独立服务层**：拆出 `RagRerankService`，与 `RagQaTool` 和检索逻辑解耦，后续无论 vector-only 还是 hybrid 都可复用。

2. **第一阶段使用本地轻量 rerank**：不依赖外部模型或 API，使用可解释的特征评分建立排序基线和回退机制。后续可扩展 `CrossEncoderRagReranker` / `LlmRagReranker`。

3. **rerank 在权限过滤后执行**：防止模型 rerank 将无权限内容发送给外部服务。

4. **失败回退安全**：rerank 超时或异常时默认回退原始召回顺序，不影响 RAG 可用性；也可配置 `fallbackToOriginalOrder=false` 返回明确错误。

## 策略扩展点

`RagReranker` 接口支持后续新增策略，无需修改检索编排代码：

```java
boolean supports(RerankStrategy strategy);
List<RerankedCandidate> rerank(RerankRequest request);
```

后续可接入：
- `CrossEncoderRagReranker` — 本地 cross-encoder 模型
- `LlmRagReranker` — LLM 打分排序
- `RemoteApiRagReranker` — 第三方 rerank API

## 测试

新增 21 个测试（`LocalFeatureRagRerankerTest` 13 个 + `RagRerankServiceImplTest` 8 个），覆盖：

- 关键词覆盖率排序
- 标题/章节路径命中提升
- 关闭模式保持原顺序
- 失败回退启用和禁用
- 候选数量上限截断
- 空 query / 空白 query 处理
- 候选字段完整性保留
- 调试元数据输出
- 无匹配 reranker 回退

全部 48 个模块测试通过，无回归。
