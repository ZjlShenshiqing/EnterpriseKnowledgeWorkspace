# SKILL.md

## 1. 技能名称

企业内部智能协同平台开发技能

## 2. 技能目的

本文件定义在设计、实现和扩展“企业内部智能协同平台”时应遵循的能力边界和工作规范。

适用场景包括：

1. 产品需求编写。
2. SDD 软件设计文档编写。
3. 系统架构设计。
4. 数据库设计。
5. API 设计。
6. 后端开发。
7. 前端开发。
8. 基于 RAG 的知识问答。
9. 会议预约。
10. 待办与任务流程。
11. 权限与安全设计。
12. 部署与测试。

## 3. 必读上下文文件

进行重大修改前，应优先阅读以下文件：

1. constitution.md
2. spec.md
3. plan.md
4. AGENTS.md
5. SKILL.md
6. development-flow.md（当前开发进度与分步流程）
7. step3-summary.md（知识库最小闭环实现总览）

可选但建议阅读：

1. sdd.md
2. database.md
3. api.md
4. knowledge-service-architecture.md（知识库微服务架构文档）
5. deployment.md
6. test-plan.md

## 4. 核心能力

### 4.1 知识库能力

系统必须支持（✅=Step3已完成，🔧=Step4规划）：

1. ✅ 文档上传（multipart + meta）。
2. ✅ 文档解析（Apache Tika：PDF/Word/Excel/PPT/HTML/Markdown/TXT）。
3. ✅ 文档分类。
4. ✅ 文档权限控制（ALL / DEPARTMENT / PROJECT / USER / ADMIN + kb_document_permission）。
5. ✅ 逻辑知识库管理（`kb_knowledge_base` + Milvus 集合绑定 + 嵌入模型路由）。
6. ✅ 文档预处理（Tika 后统一上下文模板 + metadata 扩展）。
7. ✅ 文档切片（FIXED_SIZE / PARAGRAPH 策略 + 异步分块事件）。
8. ✅ 向量写入（Milvus：默认集合 + 每知识库独立集合；可配置跳过）。
9. ✅ 分块任务日志（`kb_document_chunk_log` 记录各阶段耗时）。
10. 🔧 文档版本管理（`current_version` 字段已预留，版本流转逻辑规划中）。
11. 🔧 全文检索（ES/OpenSearch，Step4 接入）。
12. 🔧 语义检索（Milvus 相似度搜索，Step4 接入）。
13. 🔧 智能问答来源引用（Step4 RAG 问答）。

### 4.2 智能问答能力（Step4 规划）

系统将支持（当前全部为规划能力）：

1. 🔧 自然语言提问。
2. 🔧 基于权限过滤的检索。
3. 🔧 基于来源的答案生成。
4. 🔧 无答案兜底（"未找到明确依据"）。
5. 🔧 用户反馈收集。
6. 🔧 多轮对话。

### 4.3 会议预约能力

系统必须支持：

1. 会议室管理。
2. 会议创建。
3. 会议室冲突检测。
4. 参会人冲突检测。
5. 会议修改。
6. 会议取消。
7. 会议通知。
8. 会议纪要。

### 4.4 时间管理能力

系统必须支持：

1. 个人日历。
2. 会议展示。
3. 待办展示。
4. 任务截止时间展示。
5. 提醒规则。
6. 时间冲突检测。

### 4.5 待办事项能力

系统必须支持：

1. 创建待办。
2. 修改待办。
3. 完成待办。
4. 删除待办。
5. 设置优先级。
6. 设置截止时间。
7. 设置提醒时间。
8. 支持周期性待办。

### 4.6 任务协同能力

系统必须支持：

1. 创建任务。
2. 分配任务。
3. 添加参与人。
4. 更新状态。
5. 添加评论。
6. 上传附件。
7. 提交结果。
8. 确认完成。
9. 查看任务看板。

### 4.7 消息通知能力

系统必须支持：

1. 保存站内通知。
2. 标记已读。
3. 统计未读数量。
4. 跳转到对应业务页面。
5. 可选外部通知渠道。

## 5. 推荐技术栈

### 5.1 前端

1. React 或 Vue。
2. TypeScript。
3. Ant Design 或 Element Plus。
4. 日历组件。
5. Markdown 预览组件。
6. 图表组件。

### 5.2 后端（一期已落地）

1. Spring Boot 3（`KnowledgeAiApplication`）。
2. MyBatis-Plus（BaseMapper + 分页插件 + 逻辑删除）。
3. RESTful API + 统一 `Result` 响应（`frameworks-common`）。
4. JWT 认证（`enterprise-gateway-service`）。
5. RBAC 权限模型。
6. Spring `@TransactionalEventListener` + `@Async` 替代消息队列（最小实现）。
7. 全局异常处理（`BizException` + `ErrorCode` 枚举）。
8. 操作日志。

### 5.3 数据库与存储（一期已落地 + 规划）

| 组件 | 状态 | 说明 |
|------|------|------|
| MySQL | ✅ 已落地 | `enterprise_knowledge_ai` 库，6 张表（见 `schema.sql`） |
| Milvus | ✅ 已落地 | 向量存储：默认集合 + 每知识库独立集合 |
| 本地磁盘 | ✅ 已落地 | 文件上传目录 `./data/kb-uploads/` |
| Apache Tika | ✅ 已落地 | 文档解析（PDF/Word/Excel/PPT/HTML/TXT） |
| Redis | 🔧 规划 | 缓存、分布式锁 |
| MinIO / 对象存储 | 🔧 规划 | 替代本地磁盘 |
| Elasticsearch / OpenSearch | 🔧 Step4 | 全文检索 |
| RabbitMQ / Kafka | 🔧 规划 | 替代 Spring Event 异步

## 6. 项目结构（一期已落地）

### 后端微服务

```text
EnterpriseKnowledgeWorkspace/
├── frameworks/                            # 公共基础框架
│   └── common/
│       └── common-spring-boot-starter/    # Result、ErrorCode、BizException
│       └── web-spring-boot-starter/       # 全局异常、traceId（规划）
├── enterprise-gateway-service/            # 网关 + 认证权限（:8080）
│   └── src/main/java/com/zjl/
│       ├── config/        # 网关/安全配置
│       ├── domain/        # SysUser、SysRole、SysDept 等
│       ├── filter/        # JWT、限流、TraceId 过滤器
│       ├── repository/    # MyBatis-Plus Mapper
│       ├── security/      # JWT 工具、RBAC、密码加密
│       └── web/           # AuthController、SystemAdminController
├── enterprise-knowledge-ai-service/       # 知识库 + AI（:8081）
│   └── src/main/java/com/zjl/knowledge/
│       ├── chunk/         # 分块策略（FIXED_SIZE / PARAGRAPH）
│       ├── config/        # Milvus/Kb/MyBatis-Plus 配置
│       ├── domain/        # DocumentStatus、PermissionType 等枚举
│       ├── dto/           # 请求/响应 DTO
│       ├── embedding/     # 向量化服务（PlaceholderEmbedding）
│       ├── entity/        # KbDocument、KbKnowledgeBase 等 ORM 实体
│       ├── event/         # DocumentChunkRequestedEvent + 异步监听器
│       ├── mapper/        # MyBatis-Plus Mapper + XML SQL
│       ├── milvus/        # MilvusVectorWriter、CollectionHelper 等
│       ├── service/       # 服务接口 + impl
│       ├── token/         # Token 计数
│       ├── util/          # SHA-256 哈希工具
│       └── web/           # Controller + UserContext 拦截器
├── enterprise-workbench-service/          # 工作台聚合（骨架）
└── enterprise-collaboration-service/      # 协同业务（骨架）
```

### 前端结构

```text
frontend
  pages
    auth
    workbench
    knowledge
    ai-qa
    calendar
    meeting
    todo
    task
    notification
    dashboard
    system
  components
  services
  stores
  utils
```

## 7. 核心数据库表

核心表包括：

1. sys_user
2. sys_department
3. sys_role
4. sys_user_role
5. sys_permission
6. sys_role_permission
7. kb_category
8. kb_knowledge_base
9. kb_document
10. kb_document_version
11. kb_document_chunk
12. kb_document_chunk_log
13. kb_document_permission
14. meeting_room
15. meeting
16. meeting_attendee
17. meeting_minutes
18. todo_item
19. task
20. task_member
21. task_comment
22. notification
23. operation_log

## 8. 重要业务流程

### 8.1 文档上传与分块流程（与当前后端一致）

1. 用户上传文档（`multipart`：`meta` + `file`），可选 `meta.kbId` 绑定知识库。
2. 系统保存原始文件到本地目录，文档状态为 **PENDING**。
3. 用户调用 **start-chunk**，状态变为 **RUNNING**，事务提交后异步执行分块。
4. 系统使用 **Tika** 从磁盘解析正文与 metadata，先经过默认文档预处理生成统一上下文文本，再按策略分块并调用 **EmbeddingService** 生成向量。
5. 系统在事务内删除旧切片、写入新 `kb_document_chunk`、更新文档 **SUCCESS** 与摘要等。
6. 系统向 **Milvus** 写入向量（集合由 `kb_id` 路由，否则默认集合），chunk metadata 会携带预处理字段；失败则文档 **FAILED** 并记录 **kb_document_chunk_log**。
7. `vector_id` 与 Milvus 行主键 `id`（chunk 主键）对齐。
8. 全文检索与 RAG 问答为后续步骤（见 `docs/step3-summary.md` / Step4）。

### 8.2 智能问答流程

1. 用户提出问题。
2. 系统校验用户身份。
3. 系统确定用户可访问的知识范围。
4. 系统检索有权限的文档切片。
5. 系统构造提示词。
6. 大模型生成答案。
7. 系统返回答案和来源。
8. 系统保存用户反馈。

### 8.3 会议预约流程

1. 用户创建会议。
2. 系统检查会议室是否可用。
3. 系统检查参会人是否冲突。
4. 系统保存会议。
5. 系统保存参会人。
6. 系统发送通知。
7. 后续可修改或取消会议。

### 8.4 任务流程

1. 用户创建任务。
2. 负责人收到通知。
3. 负责人更新任务进度。
4. 负责人提交结果。
5. 创建人确认完成。
6. 任务关闭。

## 9. 测试清单

### 9.1 权限测试

1. 用户不能查看无权限文档。
2. 用户不能通过智能问答获取无权限内容。
3. 用户不能修改其他人的私有待办。
4. 普通用户不能访问管理员接口。

### 9.2 会议测试

1. 已占用会议室不能重复预约。
2. 参会人冲突可以被检测到。
3. 会议取消后会发送通知。
4. 会议修改后会发送通知。

### 9.3 知识库测试

1. 文档上传正常（`PENDING`）。
2. `start-chunk` / `execute-chunk` 与异步分块正常（`SUCCESS`/`FAILED`、chunk 日志）。
3. 文档标题搜索与列表权限过滤正常。
4. 逻辑知识库 CRUD、Milvus 多集合路由正常（见 `docs/step3-summary.md`）。
5. 文档版本管理（若表已落地）与权限扩展用例。

### 9.4 AI 测试

1. 答案包含来源。
2. 无答案兜底正常。
3. 提示词注入得到处理。
4. 无权限切片不会进入上下文。

## 10. 完成定义

一个功能满足以下条件才算完成：

1. 后端 API 已实现。
2. 前端页面已实现。
3. 权限校验已完成。
4. 必要日志已记录。
5. 异常处理已覆盖。
6. 基础测试已通过。
7. 文档已更新。
8. 不存在硬编码敏感信息。
