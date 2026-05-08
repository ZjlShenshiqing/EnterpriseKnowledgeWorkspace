# enterprise-knowledge-ai-service 代码解析

> 基于 2026-05-08 重构后的代码，逐层解析知识库微服务的完整流程、组件职责与数据流。

---

## 1. 启动入口与组件扫描

### KnowledgeAiApplication

```java
@SpringBootApplication(scanBasePackages = {"com.zjl.knowledge", "com.zjl.common"})
@ConfigurationPropertiesScan
@MapperScan("com.zjl.knowledge.mapper")
@EnableAsync
@EnableTransactionManagement
public class KnowledgeAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeAiApplication.class, args);
    }
}
```

- `scanBasePackages` 扫描两个包：`com.zjl.knowledge`（本服务）和 `com.zjl.common`（frameworks 公共组件），使得 `GlobalExceptionHandler`、`TraceIdFilter` 等自动生效。
- `@ConfigurationPropertiesScan` 自动注册 `@ConfigurationProperties` 类（`MilvusProperties`、`KnowledgeAiProperties`、`KbStorageProperties`）。
- `@MapperScan` 扫描 MyBatis-Plus Mapper 接口。
- `@EnableAsync` + `@EnableTransactionManagement` 支持异步分块事件。

---

## 2. 服务内模块总览

```
web/          → Controller 层：接收 HTTP 请求，参数校验，委托 Service
service/      → Service 接口 + 实现（核心业务逻辑）
event/        → 事件驱动：DocumentChunkRequestedEvent + 异步监听器
domain/       → 枚举：DocumentStatus, DocumentPermissionType, ChunkingMode 等
entity/       → ORM 实体：KbDocument, KbDocumentChunk, KbKnowledgeBase 等
dto/          → 请求/响应 DTO（按功能分子包：chunk/, kb/）
mapper/       → MyBatis-Plus Mapper 接口（含自定义 XML SQL）
milvus/       → Milvus 向量存储层
chunk/        → 分块策略（策略模式）
embedding/    → 向量化服务（EmbeddingService 接口 + PlaceholderEmbeddingService）
token/        → Token 计数
config/       → Spring 配置（MyBatis-Plus, Milvus, WebMvc, 属性类）
util/         → 工具类（ContentHashUtil）
```

---

## 3. HTTP 请求处理全链路

### 3.1 用户身份解析 — UserContextInterceptor

每个请求进入 Controller 之前，`UserContextInterceptor` 解析请求头并构造 `UserContext`：

```
请求头                          → UserContext 字段
X-User-Id      (Long, 必填)    → userId
X-Department-Id (Long, 可选)   → departmentId
X-Project-Id   (Long, 可选)    → projectId
X-Is-Admin     (String, 可选)  → isAdmin (equalsIgnoreCase("true"))
```

`UserContext` 是不可变 POJO，通过 `UserContextHolder`（ThreadLocal）存储，Controller 通过 `UserContextHolder.get()` 获取。

**源码位置**：`web/UserContextInterceptor.java:28-44`

### 3.2 统一响应格式

所有 Controller 返回 `Result<T>`，通过 `Results` 工厂类构造：

```java
// 成功
Results.success(data);        // Result { code: "200", message: "success", data: T, traceId: "..." }
Results.success();            // Result { code: "200", message: "success", data: null }

// 分页
Results.success(PageResult.of(current, size, total, records));

// 异常 → 由 GlobalExceptionHandler 统一转换为 Result
throw new BizException(ErrorCode.PARAM_INVALID);
throw new BizException(ErrorCode.PARAM_INVALID, "自定义消息");
```

`traceId` 由 `TraceIdFilter`（frameworks-web）在请求入口写入 MDC，`Results` 工厂方法从 MDC 读取。

**源码位置**：`frameworks/common/.../Result.java`, `Results.java`, `ErrorCode.java`

### 3.3 全局异常处理

`GlobalExceptionHandler`（frameworks-web）拦截三类异常：

| 异常类型 | 映射 |
|----------|------|
| `BizException` | `Result { code: ex.getErrorCode(), message: ex.getMessage() }` |
| 校验异常 (`MethodArgumentNotValidException` 等) | `Result { code: "40000", message: "请求参数不合法" }` |
| `Exception` | `Result { code: "50000", message: "系统异常" }` |

---

## 4. Controller 层 — 四个控制器

### 4.1 KbCategoryController → `/api/kb/categories`

| 方法 | 路径 | Service 调用 | 说明 |
|------|------|-------------|------|
| GET | `/categories` | `list()` | 全量列表 |
| GET | `/categories/{id}` | `getById(id)` | 详情 |
| POST | `/categories` | `save(KbCategory)` | 新建 |
| PUT | `/categories/{id}` | `update(id, request)` | 更新 |
| DELETE | `/categories/{id}` | `delete(id)` | 逻辑删除 |

实现类 `KbCategoryServiceImpl` 继承 `ServiceImpl<KbCategoryMapper, KbCategory>`，CRUD 均委托 MyBatis-Plus 内置方法，逻辑简单。

### 4.2 KbKnowledgeBaseController → `/api/kb/bases`

| 方法 | 路径 | 核心逻辑 |
|------|------|----------|
| POST | `/bases` | 校验名称/集合名唯一 → INSERT kb_knowledge_base → `MilvusCollectionHelper.ensureCollectionLoaded()` 建 Milvus 集合 |
| GET | `/bases` | 分页，非管理员只看自己的库，批量聚合 documentCount |
| GET | `/bases/{id}` | 详情 + documentCount |
| PUT | `/bases/{id}` | 更新名称/嵌入模型（有向量化文档时禁止改模型） |
| PUT | `/bases/{id}/rename` | 仅重命名 |
| DELETE | `/bases/{id}` | 有关联文档时禁止删除 |

**关键约束**：
- 修改嵌入模型前，检查 `kb_document` 中是否存在 `chunk_count > 0` 的文档（`KbKnowledgeBaseServiceImpl.java:103-111`）
- 创建知识库时，`MilvusCollectionHelper.ensureCollectionLoaded()` 创建 Milvus 集合（Schema：id/VarChar + content/VarChar(65535) + metadata/JSON + embedding/FloatVector），设置 AUTOINDEX + COSINE 度量，然后 load 到内存

### 4.3 KbDocumentController → `/api/kb/documents`

核心文档接口，方法委托关系：

```
Controller                          → Service（KbDocumentService 接口）
upload(meta, file)                  → DocumentUploadService.upload()
startChunk(id)                      → KbDocumentServiceImpl.startChunk() → DocumentChunkingService.startChunk()
executeChunk(id)                    → KbDocumentServiceImpl.executeChunkAsUser() → DocumentChunkingService.executeChunkAsUser()
page(current, size)                 → KbDocumentServiceImpl.pageVisible()
detail(id)                          → KbDocumentServiceImpl.getVisible()
updateDocument(id, request)          → KbDocumentServiceImpl.updateDocument()
enableDocument(id, enabled)          → KbDocumentServiceImpl.enableDocument()
chunkLogs(id)                       → KbDocumentServiceImpl.pageChunkLogs()
search(keyword)                     → KbDocumentServiceImpl.searchDocuments()
delete(id)                          → KbDocumentServiceImpl.deleteVisible() → DocumentDeleteService.deleteVisible()
download(id)                        → 直接使用 FileStorageService.read() + ResponseEntity
```

### 4.4 KbChunkController → `/api/kb/documents/{docId}/chunks`

| 方法 | 路径 | 核心逻辑 |
|------|------|----------|
| GET | `/chunks` | 分页，可选 enabled 过滤 |
| GET | `/chunks/list` | 全量列表 |
| POST | `/chunks` | 单条创建 → INSERT chunk → chunk_count+1 → syncChunk 写 Milvus |
| POST | `/chunks/batch?writeVector=` | 批量创建 |
| PUT | `/chunks/{chunkId}` | 更新内容 → 重算哈希/token → Upsert Milvus |
| DELETE | `/chunks/{chunkId}` | 删除 → chunk_count-1 → 删 Milvus 向量 |
| PATCH | `/chunks/{chunkId}/enabled?on=` | 启用：重建向量；禁用：删向量 |
| POST | `/chunks/batch-enabled?on=` | 批量启用/禁用，上限 500 条 |

---

## 5. Service 层 — 核心业务逻辑

### 5.1 文档上传 — DocumentUploadService

**入口**：`DocumentUploadService.upload(UserContext, KbDocumentUploadRequest, MultipartFile)`

```
upload()
  ├── 校验文件非空
  ├── 校验 sourceType（仅 FILE，URL 未支持）
  ├── 校验 permissionType + grantUserIds/grantProjectId
  ├── 校验 categoryId 存在
  ├── 校验 kbId（可选，存在则验证知识库存在）
  ├── 构建 KbDocument 实体（30+ 字段填充，status=PENDING）
  ├── kbDocumentMapper.insert(doc)
  ├── FileStorageService.store(docId, fileName, stream)
  │     └── LocalFileStorageService：baseDir/{docId}/{safeName}
  ├── Tika 探测 MIME 类型
  ├── kbDocumentMapper.updateById(doc)  // 更新 fileUrl, fileType
  ├── savePermissionRows() → kbDocumentPermissionMapper.insert()
  └── 返回 docId
```

**权限行写入**（`savePermissionRows`）：
- `USER` 权限：为 `grantUserIds` 中每个用户写入 `(documentId, "USER", uid, "READ")`
- `PROJECT` 权限：写入 `(documentId, "PROJECT", grantProjectId, "READ")`
- `ALL` / `DEPARTMENT` / `ADMIN`：不需要权限行，依赖文档自身的 `permission_type` 字段

### 5.2 异步分块 — DocumentChunkingService

**完整流程图**：

```
用户调用 POST /documents/{id}/start-chunk
  │
  ├── [同步] startChunk(id, user)
  │     ├── 权限校验（仅 PENDING 或 FAILED 状态可提交）
  │     ├── CAS 乐观锁更新 status=PENDING→RUNNING
  │     │     UPDATE kb_document SET status='RUNNING'
  │     │     WHERE id=? AND status<>'RUNNING'
  │     ├── 若 rows=0 → 并发冲突，抛异常
  │     └── 发布 DocumentChunkRequestedEvent
  │
  └── [异步, AFTER_COMMIT] DocumentChunkEventListener.onChunkRequested()
        │   @Async + @TransactionalEventListener(phase=AFTER_COMMIT)
        │   事务提交后才触发，保证 DB 状态已更新
        │
        └── executeChunk(docId, operatorUserId)
              │
              ├── ① INSERT kb_document_chunk_log (status=RUNNING, started_at=now)
              │
              ├── ② Tika 解析
              │     TikaDocumentParser.extractText(磁盘文件流, fileName, fileType)
              │     支持 PDF/Word/Excel/PPT/HTML/Markdown/TXT/纯文本
              │
              ├── ③ 策略分块
              │     ChunkingStrategyFactory.requireStrategy(mode).chunk(text, options)
              │       ├── FIXED_SIZE: 按 token 数固定切分
              │       └── PARAGRAPH: 按 \n\n 段落切分
              │     返回 List<TextChunk>（index + content）
              │
              ├── ④ 条件向量化
              │     if (vectorSyncService.shouldEmbed(document)):
              │       vectorSyncService.embedBatch(texts, document)
              │         → KbMilvusRoutingService.embeddingModelOrDefault()
              │           → 知识库配置优先 → 全局配置
              │         → EmbeddingService.embedBatch(texts, model)
              │       List<List<Float>> → float[] → VectorDocChunk.embedding
              │
              ├── ⑤ 原子持久化（TransactionTemplate）
              │     persistChunksAndVectorsAtomically(document, userId, chunks, fullText, shouldEmbed)
              │       ├── DELETE kb_document_chunk WHERE document_id=?
              │       ├── INSERT kb_document_chunk × N（雪花ID, content_hash, token_count, vector_id 等）
              │       ├── [if shouldEmbed] vectorSyncService.deleteDocumentVectors(doc)
              │       ├── [if shouldEmbed] vectorSyncService.indexDocumentChunks(doc, vectorChunks)
              │       │     → MilvusVectorWriter.indexDocumentChunks()
              │       │       → MilvusClientV2.insert(InsertReq(collection, rows))
              │       │       每行：{id, content, metadata{collection_name, doc_id, chunk_index}, embedding}
              │       └── UPDATE kb_document SET status=SUCCESS, chunk_count=N, content_text=全文, summary=前200字
              │
              └── ⑥ UPDATE kb_document_chunk_log (status=SUCCESS, 各阶段耗时)

若任何步骤异常：
  → markChunkFailed(docId, reason): UPDATE kb_document SET status=FAILED
  → updateChunkLog(logId, FAILED, ...): 记录错误信息
```

### 5.3 文档启用/禁用 — KbDocumentServiceImpl.enableDocument()

```
enableDocument(id, enabled, user)
  ├── 权限校验（可读 + 可写）
  ├── 状态校验（RUNNING 禁止操作）
  ├── if (当前状态 == 目标状态) return
  ├── 查询所有 chunk
  ├── if (启用 && shouldEmbed):
  │     ├── 将 chunk 转为 VectorDocChunk
  │     ├── embedBatch 生成向量
  │     └── vectorChunks[i].embedding = VectorSyncService.toArray(vecs[i])
  └── TransactionTemplate 内：
        ├── doc.setEnabled(enabled) → updateById(doc)
        ├── kbChunkService.updateEnabledByDocId(docId, enabled, userId)
        └── if (shouldEmbed):
              if (禁用): vectorSyncService.deleteDocumentVectors(doc)
              if (启用 && 有chunk): vectorSyncService.indexDocumentChunks(doc, chunks)
```

### 5.4 文档删除 — DocumentDeleteService.deleteVisible()

```
deleteVisible(id, user)
  ├── 权限校验（owner 或 admin）
  ├── 状态校验（RUNNING 禁止删除）
  ├── [关键顺序] 先删向量：
  │     vectorSyncService.deleteDocumentVectors(doc)
  │       → MilvusVectorWriter.deleteByDocumentId()
  │       → DELETE FROM collection WHERE metadata["doc_id"] == "id"
  │     失败 → 抛 VECTOR_WRITE_FAILED，文档不删
  ├── DELETE kb_document_chunk WHERE document_id=?
  ├── DELETE kb_document_chunk_log WHERE document_id=?
  ├── DELETE kb_document_permission WHERE document_id=?
  └── kbDocumentMapper.deleteById(id)  // MyBatis-Plus 逻辑删除（deleted=1）
```

**设计要点**：先删向量再删 DB。向量删除失败时文档数据保留，避免 DB 已删但 Milvus 有孤儿向量的不一致状态。

### 5.5 文档权限过滤

**列表查询**（`KbDocumentMapper.xml` 中的 `selectPageVisible`）：

```sql
WHERE d.deleted = 0
  AND (
    d.owner_id = #{userId}                          -- 本人上传
    OR d.permission_type = 'ALL'                    -- 全员可见
    OR (d.permission_type = 'DEPARTMENT'            -- 同部门
        AND d.department_id = #{deptId})
    OR (d.permission_type = 'ADMIN' AND #{admin}=1) -- 管理员
    OR EXISTS (SELECT 1 FROM kb_document_permission  -- 项目成员
               WHERE ... AND p.permission_target_type='PROJECT')
    OR EXISTS (SELECT 1 FROM kb_document_permission  -- 指定用户
               WHERE ... AND p2.permission_target_type='USER')
  )
```

**详情查询**（`DocumentVisibilityService.canView()`）：逐一判断权限类型：

```java
switch (permissionType) {
    case ALL       → true
    case DEPARTMENT → doc.departmentId == user.departmentId
    case ADMIN     → false (管理员在上面已提前放行)
    case PROJECT   → permissions 中存在 PROJECT 行且 projectId 匹配
    case USER      → permissions 中存在 USER 行且 userId 匹配
}
```

---

## 6. 向量存储层（重构后 3 层）

### 调用链

```
VectorSyncService (业务层统一入口)
  → ChunkVectorStore (接口)
    → MilvusChunkVectorStore (实现，Long→String 类型适配)
      → MilvusVectorWriter (底层，直接操作 MilvusClientV2 gRPC)
```

### Milvus Collection Schema

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | VarChar(128) PK | 与 `kb_document_chunk.id` 字符串一致 |
| `content` | VarChar(65535) | 切片正文，超长截断 |
| `metadata` | JSON | `collection_name`, `doc_id`, `chunk_index` + 业务扩展 |
| `embedding` | FloatVector(dim) | AUTOINDEX + COSINE 度量 |

### MilvusVectorWriter 操作对照

```
ChunkVectorStore 方法                 → MilvusVectorWriter 方法
indexDocumentChunks(collection, id, chunks) → indexDocumentChunks(collection, String.valueOf(id), chunks)
                                                → milvusClient.insert(InsertReq)
updateChunk(collection, id, chunk)          → upsertChunk(collection, String.valueOf(id), chunk)
                                                → milvusClient.upsert(UpsertReq)
deleteDocumentVectors(collection, id)       → deleteByDocumentId(collection, String.valueOf(id))
                                                → milvusClient.delete(filter: metadata["doc_id"] == "...")
deleteChunkById(collection, chunkId)        → deleteByChunkId(collection, chunkId)
                                                → milvusClient.delete(filter: id == "...")
deleteChunksByIds(collection, chunkIds)     → deleteByChunkIds(collection, chunkIds)
                                                → milvusClient.delete(filter: id in [...])
```

### 知识库多集合路由

`KbMilvusRoutingService` 负责：

```
文档.kb_id == null  → 使用默认集合（app.milvus.collection）
文档.kb_id != null  → 查询 kb_knowledge_base → 返回 collection_name
知识库不存在        → 抛异常（写入场景）或回退默认（删除场景）
```

嵌入模型选择优先级：
```
文档.kb_id → kb_knowledge_base.embedding_model（非空时）
           → app.knowledge.embedding-model（全局配置）
```

---

## 7. 分块策略 — 策略模式

```
ChunkingStrategy (接口)
  ├── FixedSizeChunkingStrategy  — 按固定 token 数切分
  └── ParagraphChunkingStrategy  — 按 \n\n 段落切分

ChunkingStrategyFactory
  └── 自动注入所有 ChunkingStrategy 实现
      requireStrategy(mode) → 按 ChunkingMode 路由
```

`ChunkingOptions` 通过 Map 传递参数（如 `chunkSize`、`overlap`），从文档的 `chunk_config` JSON 字段反序列化。

---

## 8. 事件驱动异步

```
KbDocumentServiceImpl.startChunk()
  └── DocumentChunkingService.startChunk()
        ├── CAS 更新 status=RUNNING
        └── applicationEventPublisher.publishEvent(
              new DocumentChunkRequestedEvent(documentId, userId))

事务提交后 (AFTER_COMMIT):
  DocumentChunkEventListener.onChunkRequested(event)  [异步线程]
    └── DocumentChunkingService.executeChunk(event.documentId(), event.operatorUserId())
```

这是替代消息队列的最小实现。设计要点：
- `@TransactionalEventListener(phase=AFTER_COMMIT)`：事务提交后才触发，保证 DB 状态已更新（status=RUNNING）
- `fallbackExecution=true`：无事务时也能触发（补偿调用）
- `@Async`：不阻塞 HTTP 响应

---

## 9. 数据库表关系

```
kb_knowledge_base (1) ──o (N) kb_document (通过 kb_id, nullable)
kb_category (1) ──o (N) kb_document (通过 category_id)
kb_document (1) ──o (N) kb_document_permission
kb_document (1) ──o (N) kb_document_chunk
kb_document (1) ──o (N) kb_document_chunk_log
```

### 文档状态机

```
PENDING ──startChunk──→ RUNNING ──成功──→ SUCCESS
  │                        │                  │
  └──失败──→ FAILED        └──失败──→ FAILED   └──再次startChunk──→ RUNNING
                             ↑
                       FAILED ──修正后startChunk──→ RUNNING
```

---

## 10. 配置一览

```yaml
app:
  kb:
    upload-dir: ./data/kb-uploads          # FileStorageService 使用
  knowledge:
    embedding-model: ""                     # 为空 → shouldEmbed=false，跳过向量化
    vector-write-enabled: true              # 全局开关
  milvus:
    uri: http://localhost:19530             # Milvus gRPC 地址
    collection: kb_chunk_embedding          # 默认集合名
    vector-dimension: 128                   # 必须与 EmbeddingService 输出一致
    fail-on-init: false                     # 启动时 Milvus 不可用是否中止

mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

---

## 11. 目录结构速查

```
enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/
├── KnowledgeAiApplication.java              # 启动类
├── chunk/                                    # 分块策略
├── config/                                   # 配置 + 属性类
├── domain/                                   # 枚举（状态、权限类型、分块模式等）
├── dto/                                      # 请求/响应 DTO
│   ├── chunk/                                # Chunk 相关
│   └── kb/                                   # 知识库相关
├── embedding/                                # 向量化接口 + PlaceholderEmbedding
├── entity/                                   # ORM 实体
├── event/                                    # 事件 + 异步监听器
├── mapper/                                   # MyBatis-Plus Mapper
├── milvus/                                   # Milvus 向量存储层（3层）
├── service/                                  # 服务接口 + 实现
│   └── impl/                                 # 6 个独立职责服务:
│       ├── KbCategoryServiceImpl             # 分类 CRUD
│       ├── KbKnowledgeBaseServiceImpl        # 知识库管理（含 Milvus 集合创建）
│       ├── DocumentUploadService             # 文档上传
│       ├── DocumentChunkingService           # 异步分块
│       ├── DocumentDeleteService             # 文档删除
│       ├── KbChunkServiceImpl               # Chunk CRUD + 向量同步
│       ├── LocalFileStorageService           # 本地文件存储
│       └── KbDocumentServiceImpl             # 查询/更新 + 委托门面
├── token/                                    # Token 计数
├── util/                                     # ContentHashUtil
└── web/                                      # Controller + UserContext
```

---

**文档版本**：v2.0（重构后）  
**最后更新**：2026-05-08
