## 1. 配置与模型

- [x] 1.1 新增 `app.rag.rerank.*` 配置属性，包含启用开关、策略、候选上限、超时和失败回退
- [x] 1.2 新增 rerank 请求、候选、结果和策略枚举模型
- [x] 1.3 在 rerank 候选模型中保留原始召回字段，包括文档 ID、chunk ID、原始分数、召回来源和 metadata

## 2. Rerank 服务抽象

- [x] 2.1 新增 `RagRerankService` 接口，用于重排候选列表
- [x] 2.2 新增 `RagReranker` 策略接口，支持本地策略和未来模型策略
- [x] 2.3 新增 selector 或 dispatcher，用于选择当前配置的 reranker 策略
- [x] 2.4 实现关闭模式，关闭时按原始召回顺序返回候选

## 3. 本地特征 Reranker

- [x] 3.1 实现本地 query token 提取，并安全处理空 query 或过短 query
- [x] 3.2 实现 chunk 文本关键词覆盖率评分
- [x] 3.3 实现 metadata 中标题和章节路径命中评分
- [x] 3.4 实现召回来源、原始分数和原始 rank 的评分贡献
- [x] 3.5 实现 chunk 长度质量评分，对过短或过长 chunk 做惩罚
- [x] 3.6 生成 rerank 调试字段，例如 rerank 分、策略和原因

## 4. 检索链路接入

- [x] 4.1 明确当前 `RagQaTool` 的候选组装位置，以及后续 `RagRetrievalService` 的接入路径
- [x] 4.2 在 rerank 完整 chunk 文本前完成 DB 文档和 chunk 可见性过滤
- [x] 4.3 在最终 topK 截断和 matched chunk 组装前调用 rerank
- [x] 4.4 在 matched chunk 输出中加入 rerank metadata，同时保留既有字段
- [x] 4.5 落实候选上限、超时和失败回退行为

## 5. 测试

- [x] 5.1 新增本地特征 rerank 排序单元测试
- [x] 5.2 新增关闭模式保持原始顺序的测试
- [x] 5.3 新增标题和章节路径命中评分贡献测试
- [x] 5.4 新增失败回退启用和禁用两种行为测试
- [x] 5.5 新增服务级测试，证明无权限候选不会进入 rerank 输出
- [x] 5.6 新增 matched chunk 输出测试，确认既有字段保留且启用 rerank 时包含调试字段

## 6. 文档与验证

- [x] 6.1 更新 RAG 设计文档，说明 rerank 位于 vector/hybrid retrieval 之后
- [x] 6.2 运行 knowledge-ai-service 相关测试套件
- [x] 6.3 运行 `openspec status --change "add-rag-reranking"` 并确认变更可进入实现
