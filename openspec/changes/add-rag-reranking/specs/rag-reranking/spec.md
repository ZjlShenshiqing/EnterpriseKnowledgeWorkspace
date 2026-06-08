## ADDED Requirements

### Requirement: RAG 候选片段支持召回后重排
系统 SHALL 在最终返回 matched chunks 之前提供 rerank 步骤，用于重新排序已召回的 RAG chunk 候选。

#### Scenario: rerank 对候选重新排序
- **WHEN** rerank 已启用，且召回候选具有不同的 rerank 特征分
- **THEN** 系统 MUST 按 rerank 分从高到低返回 matched chunks

#### Scenario: rerank 保留候选身份信息
- **WHEN** 候选片段被 rerank
- **THEN** 系统 MUST 保留其文档 ID、chunk ID、chunk 序号、召回来源、原始分数和 metadata

### Requirement: rerank 支持配置关闭
系统 SHALL 支持通过配置关闭 rerank。

#### Scenario: 关闭 rerank 时保留召回顺序
- **WHEN** `app.rag.rerank.enabled` 为 false
- **THEN** 系统 MUST 保持 matched chunks 的原始召回顺序

### Requirement: 支持本地特征 rerank
系统 SHALL 提供不依赖外部模型或远程 API 的本地特征 rerank 策略。

#### Scenario: 关键词覆盖提升排序
- **WHEN** 一个候选片段相比另一个召回分相近的候选片段包含更多 query 关键词
- **THEN** 本地特征 reranker MUST 将关键词覆盖更高的候选排在更前

#### Scenario: 标题或章节命中提升排序
- **WHEN** 候选 metadata 中的标题或章节路径命中 query 词项
- **THEN** 本地特征 reranker MUST 将该命中纳入 rerank 分计算

### Requirement: rerank 必须遵守权限过滤
系统 SHALL NOT 将无权限文档或 chunk 正文暴露给可能读取完整 chunk 内容的 rerank 策略。

#### Scenario: rerank 在 DB 权限过滤后执行
- **WHEN** 召回候选中包含用户无权访问的文档
- **THEN** 系统 MUST 在 rerank 完整 chunk 正文前移除无权限候选

#### Scenario: 无权限候选不会返回
- **WHEN** 无权限候选拥有最高 rerank 分
- **THEN** 系统 MUST NOT 在 matched chunks 中返回该候选

### Requirement: rerank 失败支持回退原顺序
系统 SHALL 在 rerank 失败或超时时支持可配置的回退行为。

#### Scenario: 启用失败回退
- **WHEN** rerank 抛出异常或超过配置超时时间，且已启用失败回退
- **THEN** 系统 MUST 按原始召回顺序返回候选

#### Scenario: 禁用失败回退
- **WHEN** rerank 抛出异常或超过配置超时时间，且未启用失败回退
- **THEN** 系统 MUST 返回受控服务错误，且不得暴露堆栈信息

### Requirement: rerank 返回调试 metadata
系统 SHALL 在 rerank 启用时为每个 matched chunk 附加可选 rerank metadata。

#### Scenario: matched chunk 包含 rerank 字段
- **WHEN** rerank 已启用并返回 matched chunks
- **THEN** 每个 matched chunk MUST 包含 `rerankScore`、`rerankStrategy`、`rerankReason` 或等价调试字段

#### Scenario: 保留既有 matched chunk 字段
- **WHEN** 添加 rerank metadata
- **THEN** 既有字段如 `chunkIndex`、`text`、`score`、`metadata` MUST 保持存在

### Requirement: rerank 工作量必须有边界
系统 SHALL 通过候选数量和超时配置限制 rerank 工作量。

#### Scenario: 应用候选数量上限
- **WHEN** 召回候选数量超过 `app.rag.rerank.candidate-limit`
- **THEN** 系统 MUST 只对该上限范围内优先级最高的候选执行 rerank

#### Scenario: 应用超时限制
- **WHEN** rerank 策略执行超过 `app.rag.rerank.timeout-ms`
- **THEN** 系统 MUST 执行配置的超时回退行为

### Requirement: rerank 支持未来模型策略
系统 SHALL 定义 rerank 策略接口，使后续模型 reranker 能在不修改检索调用方的情况下接入。

#### Scenario: 新增 reranker 策略
- **WHEN** 新 reranker 实现支持某个已配置策略
- **THEN** 系统 MUST 在不修改 `RagQaTool` 检索编排代码的情况下选择该策略
