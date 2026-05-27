# 淘宝 AI 智能客服系统设计文档

> 2026-05-27 | 需求调研与分析阶段 | 技术栈：Java + Spring Boot 3 + MyBatis-Plus + DeepSeek + Milvus

---

## 1. 项目概述

### 1.1 项目目标

为淘宝自有店铺接入 AI 智能客服，通过旺旺 + Web 双通道提供 RAG（检索增强生成）级别的智能问答服务。

### 1.2 核心需求

| 维度 | 选择 |
|------|------|
| 角色 | 淘宝卖家，服务自有店铺 |
| 接入渠道 | 旺旺（淘宝开放平台 TMC）+ Web 客服页面 |
| 智能程度 | RAG 智能客服（语义理解 + 多轮对话） |
| 数据源 | 商品信息、店铺政策、订单数据、历史对话 |
| 后端框架 | Spring Boot 3 + MyBatis-Plus |
| 大模型 | DeepSeek |
| 向量数据库 | Milvus |
| 消息接入 | TMC WebSocket 收消息 + TOP API 发消息 + 业务 API 查数据 |
| 后台管理 | 意图配置、知识库上传、会话监控、数据看板、商品索引管理 |

---

## 2. 系统架构

### 2.1 整体架构（模块化单体）

```
┌──────────────────────────────────────────────────────────────────┐
│                    AI 客服单体服务 (Spring Boot)                    │
│                                                                  │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌───────────┐ │
│  │  消息接入层  │  │  RAG 引擎   │  │  对话管理   │  │  后台管理  │ │
│  │ TMC + WS   │  │ 检索 + 生成  │  │ 状态机 +    │  │ CRUD +    │ │
│  │            │  │            │  │  多轮对话   │  │  看板      │ │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └─────┬─────┘ │
│        │               │               │               │        │
│  ┌─────┴───────────────┴───────────────┴───────────────┴─────┐  │
│  │                  淘宝 API 客户端层                          │  │
│  │    TOP 签名 + Token 管理 + 订单/商品/物流/退款 API          │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │               MySQL + Redis + Milvus                        │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

外部系统交互：

```
                    淘宝开放平台
                   ┌──────────────┐
                   │  TMC 消息推送  │ ←── WebSocket 收买家消息
                   │  TOP API      │ ←── HTTP 查订单/商品/物流
                   │  旺旺消息发送   │ ←── HTTP 发送 AI 回复
                   └──────────────┘
                         ↑
                         │
               ┌─────────┴─────────┐
               │   AI 客服系统      │
               └─────────┬─────────┘
                         │
     ┌───────────────────┼───────────────────┐
     ↓                   ↓                   ↓
  DeepSeek            Milvus              MySQL
  (LLM生成)        (向量检索)         (业务数据)
```

### 2.2 项目结构

```
taobao-ai-customer-service/
├── pom.xml
├── src/main/java/com/yourdomain/customerservice/
│   ├── CustomerServiceApplication.java
│   │
│   ├── gateway/                # 消息接入层（双通道）
│   │   ├── tmc/
│   │   │   ├── TmcClientManager.java      # TmcClient 生命周期管理
│   │   │   └── TmcMessageHandler.java     # TMC 消息回调处理
│   │   └── web/
│   │       ├── ChatWebSocketHandler.java  # Spring WebSocket 处理
│   │       └── ChatWebSocketConfig.java   # WebSocket 配置
│   │
│   ├── chat/                   # 对话管理
│   │   ├── SessionManager.java           # 会话状态机
│   │   ├── MessageRouter.java            # 消息分发
│   │   ├── ReplySender.java              # 回复发送
│   │   ├── ContextManager.java           # 多轮对话上下文管理
│   │   ├── SlotMemory.java               # 槽位记忆
│   │   ├── ConversationSummarizer.java   # 对话摘要生成
│   │   ├── ReferenceResolver.java        # 指代消解
│   │   ├── OutputGuard.java              # 输出安全过滤
│   │   └── model/
│   │       ├── Conversation.java
│   │       ├── CustomerMessage.java
│   │       └── SessionState.java
│   │
│   ├── rag/                    # RAG 检索引擎
│   │   ├── Retriever.java               # 检索协调器
│   │   ├── IntentRecognizer.java        # 意图识别
│   │   ├── HybridSearcher.java          # 混合检索（稠密+稀疏）
│   │   ├── Reranker.java                # 重排序
│   │   ├── KnowledgeSearchService.java  # 知识库检索
│   │   ├── PromptBuilder.java           # 分层 Prompt 构建
│   │   └── SearchCacheManager.java      # 检索结果缓存
│   │
│   ├── taobao/                 # 淘宝开放平台客户端
│   │   ├── TaobaoApiClient.java         # TOP API 统一调用
│   │   ├── TopSignUtil.java             # HMAC-SHA256 签名
│   │   ├── TokenManager.java            # access_token 管理与自动续期
│   │   ├── RateLimiter.java             # API 令牌桶限流
│   │   └── dto/
│   │       ├── TradeInfo.java
│   │       ├── LogisticsTrace.java
│   │       └── ItemInfo.java
│   │
│   ├── knowledge/              # 知识库管理
│   │   ├── StorePolicyService.java      # 店铺政策 CRUD
│   │   ├── ProductIndexService.java     # 商品信息索引同步
│   │   ├── DocumentUploadService.java   # 文档上传 + Tika 解析
│   │   ├── DocumentChunkingService.java # 切片策略
│   │   ├── KnowledgeIndexService.java   # Milvus 向量写入
│   │   └── entity/
│   │       ├── StorePolicy.java
│   │       ├── KbDocument.java
│   │       ├── KbDocumentChunk.java
│   │       └── ProductKnowledge.java
│   │
│   ├── admin/                  # 后台管理
│   │   ├── controller/
│   │   │   ├── IntentConfigController.java
│   │   │   ├── PolicyManageController.java
│   │   │   ├── KnowledgeUploadController.java
│   │   │   ├── ConversationMonitorController.java
│   │   │   └── DashboardController.java
│   │   ├── service/
│   │   │   ├── IntentConfigService.java
│   │   │   ├── ConversationMonitorService.java
│   │   │   └── DashboardService.java
│   │   └── dto/
│   │       ├── IntentConfigRequest.java
│   │       ├── PolicyUpsertRequest.java
│   │       └── DashboardStatsVO.java
│   │
│   ├── llm/                    # LLM 调用层
│   │   └── DeepSeekClient.java
│   │
│   ├── config/
│   │   ├── AsyncConfig.java
│   │   ├── MilvusConfig.java
│   │   └── RedisConfig.java
│   │
│   └── common/
│       ├── Result.java
│       ├── BizException.java
│       ├── ErrorCode.java
│       └── TraceIdHolder.java
│
└── src/main/resources/
    ├── application.yml
    └── db/migration/
        ├── V1__init_schema.sql
        └── V2__init_data.sql
```

---

## 3. 核心流程设计

### 3.1 消息处理主流程

```
                         消息到达
                            │
                            ▼
                  ┌─────────────────┐
                  │  消息幂等检查      │
                  │  (Redis SETNX)   │
                  └───────┬─────────┘
                          │ 通过
                          ▼
                  ┌─────────────────┐
                  │  写入 message 表   │
                  │  会话状态 → ACTIVE │
                  └───────┬─────────┘
                          │
                          ▼
                  ┌─────────────────┐
                  │  指代消解         │
                  │  (轻量LLM调用)   │
                  │  补全省略信息     │
                  └───────┬─────────┘
                          │
                          ▼
                  ┌─────────────────┐
                  │  意图识别         │
                  │  (规则 + LLM)     │
                  └───────┬─────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    ORDER_QUERY     PRODUCT_ASK       AFTER_SALE
    / POLICY        / CHAT            / COMPLAINT
          │               │               │
          └───────────────┼───────────────┘
                          │
                          ▼
              ┌─────────────────────┐
              │   意图路由 → 检索编排  │
              └─────────┬───────────┘
                        │
          ┌─────────────┼─────────────┐
          ▼             ▼             ▼
    查淘宝API      Milvus检索    仅LLM生成
    (订单/物流)   (知识库/RAG)   (闲聊)
          │             │             │
          └─────────────┼─────────────┘
                        │
                        ▼
              ┌─────────────────────┐
              │   组装分层 Prompt    │
              │   System + Intent   │
              │   + RAG + History   │
              └─────────┬───────────┘
                        │
                        ▼
              ┌─────────────────────┐
              │   DeepSeek 生成回答  │
              └─────────┬───────────┘
                        │
                        ▼
              ┌─────────────────────┐
              │   输出安全过滤        │
              │   (正则 + 规则)      │
              └─────────┬───────────┘
                        │
                        ▼
              ┌─────────────────────┐
              │   发送回复            │
              │   旺旺API/WebSocket  │
              └─────────┬───────────┘
                        │
                        ▼
              ┌─────────────────────┐
              │   更新会话时间/状态    │
              │   写入 message 表     │
              └─────────────────────┘
```

### 3.2 意图路由与检索编排

```
用户问题（已消解）
    │
    ▼
┌────────────────────────────────────────────────────┐
│                 意图识别（规则 + LLM）                │
│                                                    │
│  规则层（优先匹配）：                                │
│  ├─ 含订单号正则 → ORDER_QUERY                     │
│  ├─ "退款/退货/换货/投诉" → AFTER_SALE              │
│  ├─ "发货/快递/物流" → LOGISTICS                    │
│  ├─ "优惠/满减/折扣" → PROMOTION                    │
│  ├─ 含商品属性词 → PRODUCT_ASK                      │
│  └─ "转人工" → TRANSFER_HUMAN                      │
│                                                    │
│  LLM 兜底（规则未命中时）：                          │
│  输入: 当前消息 + 对话摘要 + 槽位记忆                │
│  输出: 意图类型枚举值                                │
└────────────────────┬───────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────┐
│                 检索编排                             │
│                                                    │
│  ORDER_QUERY / LOGISTICS:                          │
│  → 解析实体（订单号）                                │
│  → 调用淘宝 API (taobao.trade.fullinfo.get /       │
│       taobao.logistics.trace.search)               │
│  → 结构化数据直接注入 Prompt                         │
│                                                    │
│  PRODUCT_ASK:                                      │
│  → 先做结构化查询 (MySQL SKU 表)                     │
│  → 查不到再走 Milvus 向量检索（商品知识库）           │
│                                                    │
│  POLICY:                                           │
│  → Milvus 混合检索（店铺政策知识库）                  │
│  → Rerank 取 top-5                                 │
│                                                    │
│  AFTER_SALE:                                       │
│  → 混合检索（政策知识库 + 历史 FAQ）                  │
│  → 同时调淘宝退款 API 获取当前退款状态                 │
│                                                    │
│  CHAT / 闲聊:                                       │
│  → 不检索，仅用对话历史注入 LLM                       │
│                                                    │
│  TRANSFER_HUMAN:                                    │
│  → 不检索，直接推送转人工通知                         │
└────────────────────────────────────────────────────┘
```

### 3.3 知识入库流水线

```
后台管理 → 用户上传文档 (PDF/Word/Excel/文本)
                    │
                    ▼
          ┌─────────────────────┐
          │  Tika 解析文档        │
          │  提取纯文本内容        │
          └──────────┬──────────┘
                     │
                     ▼
          ┌─────────────────────┐
          │  文档记录入库         │
          │  status = PENDING    │
          └──────────┬──────────┘
                     │
                     ▼
          ┌─────────────────────┐
          │  策略切片             │
          │                     │
          │  FIXED_SIZE:        │
          │  - chunk 500-800字   │
          │  - overlap 100字     │
          │  - 按固定大小截断     │
          │                     │
          │  PARAGRAPH:         │
          │  - 按段落边界切分     │
          │  - 保持语义完整性     │
          │  - 最小段落合并       │
          └──────────┬──────────┘
                     │
                     ▼
          ┌─────────────────────┐
          │  status → RUNNING    │
          │  事务提交后异步执行    │
          │  @TransactionalEvent │
          │  Listener + @Async   │
          └──────────┬──────────┘
                     │
                     ▼
          ┌─────────────────────┐
          │  逐 chunk 向量化       │
          │  DeepSeek Embedding  │
          │  写入 Milvus          │
          └──────────┬──────────┘
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
  全部写入成功              部分写入失败
  status → SUCCESS         status → FAILED
  chunk_count = N           记录失败原因
                            部分数据回滚
```

状态流转：`PENDING → RUNNING → SUCCESS / FAILED`

### 3.4 会话生命周期

```
                    买家发送消息
                         │
                         ▼
                    ┌─────────┐
           ┌───────→│  ACTIVE  │←──────────┐
           │        └────┬─────┘           │
           │             │                 │
           │    ┌────────┼────────┐        │
           │    ▼        ▼        ▼        │
           │  AI回复  转人工   超时30分钟    │
           │    │        │        │        │
           │    │        ▼        ▼        │
           │    │  WAITING_HUMAN CLOSED    │
           │    │        │                 │
           │    │   人工回复                │
           │    └────────┼─────────────────┘
           │             │
           │         买家发新消息
           │         恢复 ACTIVE
           │             │
           │         问题解决
           │             │
           │             ▼
           │        ┌──────────┐
           └────────│ RESOLVED  │
                    └──────────┘
                    (30分钟后自动 CLOSED)
```

---

## 4. 多轮对话设计

### 4.1 上下文分层管理

不做简单截断，采用三层架构：

```
┌──────────────────────────────────────────┐
│            对话上下文窗口                  │
│                                          │
│  Layer 1: 槽位记忆（结构化）               │
│  ┌────────────────────────────────────┐  │
│  │ cur_product: A款T恤                 │  │
│  │ cur_sku: M码/黑色                   │  │
│  │ cur_intent: PRODUCT_ASK            │  │
│  │ slots_filled: {color, size}        │  │
│  │ buyer_nick: tb_xxx                 │  │
│  │ last_order_id: 12345678            │  │
│  └────────────────────────────────────┘  │
│                                          │
│  Layer 2: 对话摘要（AI 压缩生成）          │
│  ┌────────────────────────────────────┐  │
│  │ 买家在咨询A款T恤，已确认黑色M码有货， │  │
│  │ AI回复价格99元库存23件，问了快递，    │  │
│  │ AI回复中通2-3天。买家尚未下单。       │  │
│  └────────────────────────────────────┘  │
│                                          │
│  Layer 3: 最近 N 轮原始对话 (N=6)          │
│  ┌────────────────────────────────────┐  │
│  │ user: 这个T恤有黑色的吗？            │  │
│  │ assistant: 有的，黑色M码还有货哦～    │  │
│  │ user: 那M码呢？                     │  │
│  │ assistant: 黑色M码还有的，要帮你下单吗│  │
│  │ user: 发什么快递？                   │  │
│  │ assistant: 默认中通，一般2-3天到~     │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

| 层级 | 作用 | 注入 Prompt 方式 |
|------|------|-----------------|
| 槽位记忆 | 追踪当前商品、SKU、意图等结构信息 | 注入为结构化 JSON |
| 对话摘要 | 超过 6 轮后，把历史压缩成摘要 | 覆盖旧对话，替换在 Recent N 之前 |
| 最近 N 轮 | 当前话题细节不丢失 | 直接作为 messages 数组 |

### 4.2 摘要生成策略

超过 6 轮对话时触发摘要生成：

```
触发条件: 对话轮次 > 6
输入: 全部历史消息
LLM 任务: "将以下客服对话压缩为1-2句摘要，保留关键事实：
        买家问了什么、AI回答了哪些信息、是否有未解决的问题"

示例输出: "买家在咨询A款T恤，已确认黑色M码有货价格99元，
         问了快递信息AI回复中通2-3天。买家尚未下单。"

摘要替换: 前N-6轮 → 摘要，后6轮保留原文
```

### 4.3 指代消解

每次收到用户消息后，发一次轻量 LLM 调用做指代消解：

```
输入:
  槽位: {商品: "A款T恤", 颜色: "黑色"}
  最近3轮对话
  当前消息: "那M码呢？"

LLM 输出: "A款T恤黑色M码有货吗？"

消解后的消息 → 用于后续意图识别和 RAG 检索
```

指代消解只做一次轻量调用，不走完整 RAG 链路，延迟可控。

### 4.4 槽位管理

```
槽位更新规则:
├─ 累积: 买家补充新信息 → 追加到槽位 (如颜色→黑色, 再加尺码→M码)
├─ 覆盖: 同类信息新值替换旧值 (尺码M → 尺码L)
├─ 清空: 买家明确切换商品 ("那另一款呢") → 重置商品相关槽位
└─ 超时: 会话 CLOSED → 所有槽位清空

槽位模板:
{
  "product": { "name": null, "id": null },
  "sku": { "color": null, "size": null, "spec": null },
  "order": { "id": null },
  "intent": null,
  "pending_questions": []  // 买家问过但未回答的问题队列
}
```

### 4.5 自然回复风格

System Prompt 注入风格指令：

```
你是淘宝"XXX旗舰店"的客服小玲，一个热情细心的女生。回复规则：

1. 口语化，带适当语气词（"呢"、"哦"、"哈"），但不过度卖萌
2. 每条消息控制在2-3句话内，不发长篇大论
3. 主动提供关联信息（如问颜色时顺带说尺码）
4. 需要等待时先说"稍等我查一下哈~"，不要沉默
5. 用户下单后给正向反馈
6. 不知道的事情不要编造，说"我帮你问问"
```

不同场景的风格基调：

| 场景 | 风格 | 示例 |
|------|------|------|
| 商品咨询 | 专业 + 热情 | "这款有黑色的哦，M码也还有，要帮你留一件吗？" |
| 订单查询 | 准确 + 安抚 | "您的快递已经到杭州集散中心啦，预计明天就能收到~" |
| 售后问题 | 共情 + 高效 | "确实是我们发货的问题，已经帮你申请换货了" |
| 政策说明 | 清晰 + 温和 | "退换货需要在签收后7天内申请哈，包装完好就行~" |
| 闲聊 | 自然 + 适度 | "哈哈谢谢喜欢，有什么问题随时找我哦~" |

---

## 5. 向量检索策略与召回优化

### 5.1 检索链路

```
用户问题（指代消解后）
    │
    ▼
┌─────────────────────────────────────────────┐
│  ① 意图路由 → 确定检索范围                    │
│     ORDER_QUERY / LOGISTICS → 不走检索，调API │
│     PRODUCT_ASK → 结构化查询优先 + 向量兜底    │
│     POLICY / AFTER_SALE → Milvus 混合检索    │
│     CHAT → 不检索                            │
│                                             │
│  ② 混合检索（稠密向量 + 稀疏向量/关键词）       │
│                                             │
│  ③ 粗排 → 向量相似度 + 关键词得分融合          │
│                                             │
│  ④ 精排 → 规则 Rerank（轻量）                 │
│                                             │
│  ⑤ 后处理 → 去重 / 截断 / 组装 RAG context     │
└─────────────────────────────────────────────┘
```

### 5.2 混合检索方案

```
用户问题
    │
    ├──→ Milvus 稠密向量检索
    │     embedding = DeepSeek.embed(question)
    │     top_k = 10
    │     score_v = cosine_similarity
    │
    ├──→ 关键词匹配（Milvus 稀疏向量 / BM25）
    │     分词 + 倒排索引
    │     top_k = 10
    │     score_k = BM25_score
    │
    └──→ 融合: score = α × score_v + (1-α) × score_k
          α 默认 0.7，后台可调
```

### 5.3 切片策略

| 知识类型 | 切片大小 | 重叠 | 方法 |
|----------|---------|------|------|
| 商品描述 | 200-400 字 | 0 | 结构化存储优先，向量作为兜底 |
| 政策文档 | 500-800 字 | 100 字 | PARAGRAPH 策略 |
| FAQ | 单条 FAQ | 0 | 一个问答对一个 chunk |
| 历史对话摘要 | 300-500 字 | 50 字 | FIXED_SIZE |

### 5.4 重排序（Rerank）

轻量方案，不额外调用 LLM：

```
score_final = α × 向量相似度 (0.4)
            + β × 关键词命中数量 (0.2)
            + γ × 知识类型权重 (0.3)   — 政策 > FAQ > 商品
            + δ × 时效性衰减 (0.1)     — 近期更新加分

取 top-5 组装 RAG context
```

### 5.5 结构化查询（商品类）

商品知识不依赖向量检索，走 MySQL 结构化查询：

```
用户问题: "A款T恤M码黑色多少钱？"
    ↓
指代消解 + 意图识别 → INTENT=PRODUCT_ASK
    ↓
实体抽取: product_name="A款T恤", sku_color="黑色", sku_size="M"
    ↓
MySQL:
  SELECT p.title, p.description, p.price, s.stock
  FROM product_knowledge p
  JOIN product_sku s ON p.id = s.product_id
  WHERE p.title LIKE '%A款T恤%'
    AND s.color = '黑色' AND s.size = 'M'
    ↓
结构化结果 → 直接注入 Prompt
  → "A款T恤黑色M码价格99元，库存23件，纯棉材质..."
```

### 5.6 检索缓存

```
缓存键: SHA256(问题文本 + 意图类型)
缓存值: [top-5 chunk_id]
TTL: 1小时

流程:
  收到问题 → 计算缓存键 → 查 Redis
    ├─ 命中: 直接拿 chunk_id 查 MySQL 取内容 → 组装
    └─ 未命中: 正常检索 → 写缓存 → 组装

不缓存的场景:
  - 商品库存/价格 → 走淘宝 API 实时查
  - 订单状态 → 走淘宝 API 实时查
```

### 5.7 检索失败兜底

| 情况 | 策略 |
|------|------|
| 所有 chunk score < 阈值(0.3) | 不强行用低质量结果，仅用 LLM 回答 + 建议转人工 |
| 检索结果互相矛盾 | 降级为仅 LLM 回答，打日志标记运营排查 |
| Milvus 不可用 | 降级为 MySQL LIKE 关键词查询 |
| 连续 3 轮检索分 < 阈值 | 触发自动转人工 |

---

## 6. LLM 调用策略

### 6.1 分层 Prompt 架构

```
┌─────────────────────────────────────┐
│ Layer 1: System Prompt              │
│ 角色定义 + 店铺信息 + 风格指令 + 边界约束 │
├─────────────────────────────────────┤
│ Layer 2: Intent Context             │
│ 当前识别意图 + 槽位记忆               │
├─────────────────────────────────────┤
│ Layer 3: RAG Context + 实时数据      │
│ 检索到的知识片段 + API 返回的结构化数据  │
│ 上限 3000 tokens，超出截断           │
├─────────────────────────────────────┤
│ Layer 4: Conversation History       │
│ 摘要 (长对话压缩) + 最近 6 轮原文      │
└─────────────────────────────────────┘
```

### 6.2 不同场景的调用参数

| 场景 | temperature | max_tokens | 说明 |
|------|------------|------------|------|
| 知识问答（命中 RAG） | 0.1 | 512 | 严谨，基于知识回答 |
| 数据告知（API 查结果） | 0.2 | 512 | 准确为主，不发挥 |
| 闲聊/问候 | 0.5 | 256 | 自然随意 |
| 意图不明确（追问） | 0.3 | 128 | 简洁追问 |

### 6.3 Token 预算分配

| 项目 | token 预算 |
|------|-----------|
| System Prompt | ~300 |
| Intent Context | ~100 |
| RAG Context | ~3000 (max) |
| Conversation History | ~2000 |
| 回答内容 | 512 ~ 1024 |
| **总预算** | ~6000 |

---

## 7. 数据库设计

### 7.1 ER 图

```
┌──────────────────┐       ┌──────────────────┐
│  conversation    │       │  message          │
├──────────────────┤       ├──────────────────┤
│ id (PK)          │──1:N──│ id (PK)           │
│ buyer_nick       │       │ conversation_id   │
│ session_state    │       │ role              │
│ channel          │       │ content           │
│ last_message_at  │       │ intent            │
│ summary          │       │ sources (JSON)    │
│ slot_data (JSON) │       │ feedback           │
│ created_at       │       │ created_at        │
└──────────────────┘       └──────────────────┘

┌──────────────────┐       ┌──────────────────┐
│ store_policy     │       │ product_knowledge │
├──────────────────┤       ├──────────────────┤
│ id (PK)          │       │ id (PK)           │
│ title            │       │ item_id (淘宝ID)   │
│ content          │       │ title             │
│ category         │       │ description       │
│ vector_id        │       │ price             │
│ enabled          │       │ stock_status       │
│ created_at       │       │ embedding_vector  │
│ updated_at       │       │ updated_at        │
└──────────────────┘       └──────────────────┘

┌──────────────────┐       ┌──────────────────┐
│ intent_config    │       │ kb_document       │
├──────────────────┤       ├──────────────────┤
│ id (PK)          │       │ id (PK)           │
│ intent_type      │       │ title             │
│ keywords (JSON)  │       │ content           │
│ knowledge_scopes │       │ category          │
│ reply_template   │       │ file_type         │
│ pipeline_config  │       │ chunk_count        │
│ priority         │       │ status            │
│ enabled          │       │ enabled           │
│ created_at       │       │ created_at        │
│ updated_at       │       │ updated_at        │
└──────────────────┘       └──────────────────┘
                                   │
                                   │ 1:N
                                   ▼
                            ┌──────────────────┐
                            │ kb_document_chunk │
                            ├──────────────────┤
                            │ id (PK)           │
                            │ document_id (FK)  │
                            │ chunk_index       │
                            │ chunk_text        │
                            │ vector_id         │
                            │ created_at        │
                            └──────────────────┘
```

### 7.2 表结构明细

**conversation（会话表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 雪花 ID |
| buyer_nick | VARCHAR(64) | 旺旺买家昵称 / Web session_id |
| channel | VARCHAR(16) | WANGWANG / WEB |
| session_state | VARCHAR(32) | ACTIVE / WAITING_HUMAN / RESOLVED / CLOSED |
| last_message_at | DATETIME | 最后消息时间 |
| summary | TEXT | AI 生成的对话摘要 |
| slot_data | JSON | 槽位记忆数据 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**message（消息表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 雪花 ID |
| conversation_id | BIGINT FK | 会话 ID |
| message_id | VARCHAR(64) UQ | TMC 消息唯一 ID（幂等去重） |
| role | VARCHAR(16) | CUSTOMER / ASSISTANT / SYSTEM |
| content | TEXT | 消息内容 |
| intent | VARCHAR(32) | 意图类型 |
| sources | JSON | 引用的知识来源 |
| feedback | VARCHAR(16) | 反馈：NULL / HELPFUL / UNHELPFUL |
| created_at | DATETIME | 创建时间 |

**store_policy（店铺政策表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 雪花 ID |
| title | VARCHAR(256) | 政策标题 |
| content | TEXT | 政策内容 |
| category | VARCHAR(64) | 分类：退换货/发货/售后/优惠 |
| enabled | TINYINT | 是否启用 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**product_knowledge（商品知识表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 雪花 ID |
| item_id | BIGINT | 淘宝商品 ID |
| title | VARCHAR(256) | 商品标题 |
| description | TEXT | 商品描述 |
| price | DECIMAL(10,2) | 价格 |
| stock_status | TINYINT | 库存状态 |
| embedding_vector | - | Milvus 中存储向量，MySQL 不存 |
| updated_at | DATETIME | 更新时间 |

**intent_config（意图配置表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 雪花 ID |
| intent_type | VARCHAR(32) UQ | 意图类型枚举 |
| keywords | JSON | 触发关键词列表 |
| knowledge_scopes | JSON | 关联知识库范围 |
| reply_template | TEXT | 兜底回复模板 |
| pipeline_config | JSON | 流水线步骤配置 |
| priority | INT | 匹配优先级 |
| enabled | TINYINT | 是否启用 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**kb_document（知识库文档表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 雪花 ID |
| title | VARCHAR(256) | 文档标题 |
| content | LONGTEXT | 原始内容 |
| category | VARCHAR(64) | 分类 |
| file_type | VARCHAR(32) | 来源：MANUAL / FILE |
| chunk_count | INT | 切片数量 |
| status | VARCHAR(32) | PENDING / RUNNING / SUCCESS / FAILED |
| enabled | TINYINT | 是否启用 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**kb_document_chunk（文档切片表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 雪花 ID |
| document_id | BIGINT FK | 文档 ID |
| chunk_index | INT | 切片序号 |
| chunk_text | TEXT | 切片文本 |
| vector_id | VARCHAR(64) | Milvus 向量 ID |
| created_at | DATETIME | 创建时间 |

---

## 8. API 设计

### 8.1 Web 客服通道

| 端点 | 方法 | 说明 |
|------|------|------|
| `/ws/chat` | WebSocket | Web 客服聊天连接 |
| `/api/chat/session` | POST | Web 端创建会话 → session_id |
| `/api/chat/history` | GET | 当前会话对话历史 |

### 8.2 后台管理 API

**意图配置：**

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/admin/intents` | GET | 意图配置列表 |
| `/api/admin/intents` | POST | 新增意图配置 |
| `/api/admin/intents/{id}` | PUT | 修改意图配置 |
| `/api/admin/intents/{id}` | DELETE | 删除意图配置 |

**店铺政策管理：**

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/admin/policies` | GET | 政策列表 |
| `/api/admin/policies` | POST | 新增政策 |
| `/api/admin/policies/{id}` | PUT | 修改政策 |
| `/api/admin/policies/{id}` | DELETE | 删除政策 |
| `/api/admin/policies/{id}/enable` | PUT | 启用/停用 |

**知识库管理：**

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/admin/knowledge/upload` | POST | 上传文档（multipart） |
| `/api/admin/knowledge/list` | GET | 文档列表 |
| `/api/admin/knowledge/{id}` | DELETE | 删除文档 |
| `/api/admin/knowledge/{id}/reindex` | POST | 重新切片+向量化 |

**会话监控：**

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/admin/conversations` | GET | 会话列表（支持按状态/渠道筛选） |
| `/api/admin/conversations/{id}` | GET | 会话详情 + 全部消息 |
| `/api/admin/conversations/{id}/intervene` | POST | 人工介入回复 |

**数据看板：**

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/admin/dashboard/stats` | GET | 关键指标统计 |
| `/api/admin/dashboard/trends` | GET | 趋势数据（按天/小时） |
| `/api/admin/dashboard/intents` | GET | 意图分布统计 |

### 8.3 内部回调

| 端点 | 方法 | 说明 |
|------|------|------|
| `/callback/tmc` | - | TMC WebSocket 客户端连接（内部，不对外 HTTP） |
| `/api/internal/orders/{id}` | GET | 内部查单接口 |

### 8.4 统一响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": { },
  "traceId": "a1b2c3d4"
}
```

### 8.5 WebSocket 消息协议

```
客户端 → 服务端:
{
  "type": "message",
  "session_id": "xxx",
  "content": "这个T恤有黑色的吗？"
}

服务端 → 客户端:
{
  "type": "reply",
  "session_id": "xxx",
  "content": "有的哦，黑色M码还有货呢～",
  "sources": ["商品A款T恤详情", "SKU库存查询"],
  "intent": "PRODUCT_ASK",
  "timestamp": "2026-05-27T10:30:00"
}

{
  "type": "typing",
  "session_id": "xxx"
}

{
  "type": "transfer",
  "session_id": "xxx",
  "message": "已为您转接人工客服，请稍等~"
}
```

---

## 9. 后台管理设计

### 9.1 功能模块

| 页面 | 功能 |
|------|------|
| **意图配置** | 定义意图类型及触发规则；配置每个意图的关键词、知识库范围、兜底模板、处理流水线；支持优先级排序 |
| **政策管理** | 店铺政策 CRUD（退换货规则、发货说明、售后流程等）；支持分类、启用/停用 |
| **知识库管理** | 上传政策/FAQ 文档（PDF/Word/Excel/文本）；查看文档列表与处理状态；支持重新切片索引；管理商品知识索引 |
| **会话监控** | 实时查看进行中对话；支持人工接管回复；查看历史对话与满意度评价 |
| **数据看板** | 会话量趋势、AI 处理率、转人工率、意图分布、满意度统计 |

### 9.2 后台页面交互流程

```
意图配置页:
  ┌─────────────────────────────────────────────┐
  │  意图列表                                     │
  │  ┌──────┬──────────┬──────┬──────┬──────┐   │
  │  │ 意图  │ 关键词     │ 优先级 │ 状态  │ 操作  │   │
  │  ├──────┼──────────┼──────┼──────┼──────┤   │
  │  │ 查订单 │ 订单/快递   │  1   │ 启用  │ 编辑  │   │
  │  │ 商品咨询│ 有没有/多少钱│  2   │ 启用  │ 编辑  │   │
  │  └──────┴──────────┴──────┴──────┴──────┘   │
  │                                             │
  │  编辑意图 → 弹窗:                              │
  │  ┌─────────────────────────────────────┐    │
  │  │  意图类型: ORDER_QUERY               │    │
  │  │  关键词: ["订单","快递","物流","发货"] │    │
  │  │  知识库范围: [订单查询FAQ]             │    │
  │  │  兜底模板: "我帮你查了一下..."          │    │
  │  │  流水线: [意图匹配→API查询→组装→生成]   │    │
  │  │  状态: ● 启用                         │    │
  │  └─────────────────────────────────────┘    │
  └─────────────────────────────────────────────┘

数据看板:
  ┌─────────────────────────────────────────────┐
  │  今日概览                                     │
  │  ┌────────┬────────┬────────┬────────┐      │
  │  │ 总会话  │ AI处理率 │ 转人工率 │ 满意度  │      │
  │  │  1,234  │  78%    │  12%    │  92%   │      │
  │  └────────┴────────┴────────┴────────┘      │
  │                                             │
  │  意图分布饼图        会话量趋势折线图           │
  │  [PRODUCT_ASK 45%]  [     ╱╲    ]            │
  │  [ORDER_QUERY 25%]  [    ╱  ╲╱  ]            │
  │  [AFTER_SALE  18%]  [╱╲╱       ]             │
  │  [POLICY       7%]  [           ]            │
  │  [CHAT         5%]  [           ]            │
  └─────────────────────────────────────────────┘
```

---

## 10. 流水线配置化设计

### 10.1 流水线步骤定义

后台意图配置中，每个意图可以配置"处理流水线"，新增意图时无需改代码：

```
意图: ORDER_QUERY (查订单)

流水线配置 (pipeline_config JSON):
[
  { "step": "INTENT_MATCH",    "enabled": true },
  { "step": "ENTITY_EXTRACT",  "enabled": true,
    "rules": ["order_id_regex"] },
  { "step": "TAOBAO_API",      "enabled": true,
    "api": "taobao.trade.fullinfo.get" },
  { "step": "PROMPT_BUILD",    "enabled": true,
    "template": "order_query_template" },
  { "step": "LLM_GENERATE",    "enabled": true,
    "model": "deepseek-chat", "temperature": 0.2 },
  { "step": "OUTPUT_GUARD",    "enabled": true },
  { "step": "REPLY_SEND",      "enabled": true }
]
```

```
意图: POLICY (政策咨询)

流水线配置:
[
  { "step": "INTENT_MATCH",     "enabled": true },
  { "step": "MILVUS_SEARCH",    "enabled": true,
    "collection": "store_policies", "top_k": 5 },
  { "step": "RERANK",           "enabled": true },
  { "step": "PROMPT_BUILD",     "enabled": true,
    "template": "policy_qa_template" },
  { "step": "LLM_GENERATE",     "enabled": true,
    "model": "deepseek-chat", "temperature": 0.1 },
  { "step": "OUTPUT_GUARD",     "enabled": true },
  { "step": "REPLY_SEND",       "enabled": true }
]
```

### 10.2 流水线执行引擎

```java
// 伪代码示意
public PipelineResult execute(IntentConfig intent, MessageContext ctx) {
    PipelineConfig config = intent.getPipelineConfig();
    PipelineContext pipeCtx = new PipelineContext(ctx);

    for (StepConfig step : config.getSteps()) {
        if (!step.isEnabled()) continue;

        StepResult result = stepExecutor.execute(step, pipeCtx);
        pipeCtx.update(result);

        if (result.isFailed() && step.isCritical()) {
            return PipelineResult.failed(result.getError());
        }
    }
    return PipelineResult.success(pipeCtx.getFinalReply());
}
```

---

## 11. 错误处理与边界情况

### 11.1 异常分级处理

**L1 - 可恢复（自动重试 1 次）**

| 异常 | 处理 |
|------|------|
| 意图识别超时 | 重试后失败 → 使用通用意图(CHAT) |
| 淘宝 API 超时 | 重试后失败 → "稍等，我帮您查查~" |
| DeepSeek 返回异常 | 重试后失败 → 兜底话术 |

**L2 - 降级处理**

| 异常 | 降级策略 |
|------|---------|
| Milvus 不可用 | MySQL LIKE 关键词检索 |
| 向量检索无结果 | 仅用 LLM 回答，不注入 RAG context |
| access_token 过期 | TokenManager 自动续期，请求不中断 |

**L3 - 转人工**

| 触发条件 | 动作 |
|------|------|
| 连续 3 轮检索分 < 阈值 | 自动转人工 |
| 用户明确说"转人工" | 立即转人工 |
| 检测到投诉倾向关键词 | 主动转人工 + 标记高优先级 |

### 11.2 关键边界场景

**大促流量洪峰：**

```
线程池配置:
├─ corePoolSize: 20
├─ maxPoolSize: 50
├─ queueCapacity: 1000
└─ rejectedPolicy: CallerRunsPolicy

限流:
├─ 淘宝 API: 令牌桶，不超过 TOP API QPS 上限
├─ DeepSeek: 令牌桶，避免打爆 API Key
└─ 大促模式开关: 可降级为纯 FAQ 模式
```

**消息重复投递：**

```
Redis SETNX message_id TTL=1h
  → 已存在: 跳过处理，打日志
  → 不存在: 正常处理 + 标记已处理
```

**买家连续快速发送多条消息：**

```
80ms 内收到 3 条消息:
  → 全部落库保存
  → 只取最后 1 条进行意图识别和回复
  → 前面的消息作为对话历史 context
```

**会话超时：**

```
30 分钟无新消息 → CLOSED
下次同一买家发消息 → 新会话
  → 继承上次对话摘要（不回追超过 30 分钟的历史）
```

**双通道同一买家：**

```
旺旺 buyer_nick ≠ Web session_id
  → 各自独立会话
  → 不做跨通道合并
```

### 11.3 输出安全过滤

```
AI 生成回复 → OutputGuard 检查:

拦截规则（正则匹配）:
├─ 大段连续数字 → 疑似手机号/银行卡号 → 改写为 ***
├─ 金额承诺 → "赔您XX元" → 拦截，改为"我帮您记录反馈"
├─ 外部链接 → 非店铺域名的 URL → 移除链接
├─ 发货承诺 → "明天一定发货" → 改为"正常1-3天内发货"
└─ 内部信息 → "我是AI机器人" → 改为"我是客服小玲"

拦截后替换为兜底话术，不直接把原始 LLM 输出发给买家。
```

### 11.4 数据一致性

| 场景 | 处理 |
|------|------|
| 订单状态 | 以淘宝 API 实时返回为准，不做本地缓存 |
| 知识库更新中 | 文档标记 `status=RUNNING`，检索时查旧版本 |
| 会话状态变更 | 数据库事务 + optimistic locking (version 字段) |

---

## 12. 部署与运维

### 12.1 部署架构

```
┌──────────────────────────────────────────────┐
│  生产环境 (云服务器 4C8G)                       │
│                                              │
│  ┌────────────────┐  ┌────────────────────┐  │
│  │  AI 客服服务     │  │  MySQL 8.0          │  │
│  │  java -jar ... │  │  :3306              │  │
│  │  :8080         │  └────────────────────┘  │
│  └────────────────┘                          │
│           │                                   │
│  ┌────────┴────────┐  ┌────────────────────┐  │
│  │  Milvus Standalone│  │  Redis             │  │
│  │  :19530         │  │  :6379              │  │
│  └─────────────────┘  └────────────────────┘  │
└──────────────────────────────────────────────┘
```

### 12.2 关键配置项

```yaml
app:
  taobao:
    app-key: ${TAOBAO_APP_KEY}
    app-secret: ${TAOBAO_APP_SECRET}
    tmc-group: default
    sandbox: true  # 沙箱环境调试

  deepseek:
    api-key: ${DEEPSEEK_API_KEY}
    model: deepseek-chat
    embedding-model: text-embedding-ada-002

  milvus:
    uri: http://localhost:19530
    collections:
      policies: store_policies
      products: product_knowledge
      faq: store_faq

  chat:
    session-timeout-minutes: 30
    max-history-rounds: 6
    rag-max-tokens: 3000
    fallback-message: "这个问题我需要问一下店长，稍等哈～"
```

### 12.3 监控指标

| 指标 | 说明 |
|------|------|
| `chat.session.count` | 当前活跃会话数 |
| `chat.message.rate` | 消息处理速率 |
| `chat.ai_resolve_rate` | AI 解决率（未转人工的会话占比） |
| `chat.transfer_rate` | 转人工率 |
| `rag.retrieval.latency` | 向量检索延迟 P99 |
| `taobao.api.latency` | 淘宝 API 调用延迟 P99 |
| `llm.latency` | DeepSeek 调用延迟 P99 |
| `pipeline.error.rate` | 流水线异常率 |

---

## 13. 测试策略

### 13.1 单元测试

| 测试对象 | 覆盖重点 |
|------|------|
| IntentRecognizer | 各意图关键词命中、LLM 兜底逻辑 |
| TopSignUtil | 签名生成正确性、时间戳校验 |
| SessionManager | 状态机流转、超时处理 |
| ReferenceResolver | 指代消解准确性 |
| OutputGuard | 各拦截规则命中 |
| Retriever | 混合检索融合逻辑 |

### 13.2 集成测试

| 场景 | 验证点 |
|------|------|
| 完整消息流 | TMC 消息 → AI 回复 → 发送成功 |
| 多轮对话 | 上下文保持、指代消解、摘要生成 |
| 知识入库 | 上传 → 切片 → 向量化 → 可检索 |
| 人工接管 | 转人工 → 人工回复 → 恢复正常 |
| Token 续期 | 过期前自动续期，请求不中断 |

### 13.3 压力测试

| 指标 | 目标 |
|------|------|
| TMC 消息处理 QPS | 支持 200 QPS |
| WebSocket 并发连接数 | 500 并发 |
| 单条消息端到端延迟 P99 | < 3 秒 |
| RAG 检索延迟 P99 | < 500ms |
| 系统恢复时间 | 故障恢复 < 30 秒 |

---

## 14. 开发分期

| 阶段 | 内容 | 预估工作量 |
|------|------|-----------|
| **Phase 1: MVP** | TMC 消息接入 + 旺旺收发消息 + 基础意图识别 + FAQ 问答 + 后台管理基础 CRUD | 3-4 周 |
| **Phase 2: RAG** | 知识库上传/切片/向量化 + Milvus 混合检索 + 分层 Prompt + Rerank | 2-3 周 |
| **Phase 3: 智能** | 多轮对话上下文管理 + 指代消解 + 槽位记忆 + 对话摘要 + 自然回复风格 | 2 周 |
| **Phase 4: Web** | Web 客服页面 + WebSocket 接入 + 双通道会话管理 | 2 周 |
| **Phase 5: 完善** | 数据看板 + 会话监控 + 人工接管 + 输出安全过滤 + 监控告警 | 2 周 |

**预估总工作量：11-13 周（单人力）**

---

## 15. 风险与应对

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| 淘宝开放平台 API 变更或权限限制 | 中 | 高 | 适配器模式封装，接口变更只改 adapter 层 |
| 旺旺消息发送 API 不可用或受限 | 中 | 高 | 前期用沙箱环境验证接口可用性，准备备选方案 |
| DeepSeek 服务不稳定 | 低 | 中 | 配置超时 + 重试 + 兜底话术 |
| 大促期间流量远超预期 | 中 | 中 | 限流 + 降级开关 + 监控告警 |
| 知识库内容质量差导致回答不准 | 高 | 中 | 后台配置意图兜底话术，持续优化知识库内容 |
| 用户输入恶意 Prompt 注入 | 中 | 高 | 输出安全过滤 + System Prompt 加固 |
