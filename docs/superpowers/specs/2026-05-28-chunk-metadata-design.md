# Chunk 级元数据设计方案

## 目标

分块完成后给每个 chunk 打上元数据标签，随向量一起存入向量库。元数据不参与向量化，但可用来做检索过滤、排序和引用生成。

## 一、ChunkMetadata 模型

每个 chunk 生成时构建一份 `ChunkMetadata`，序列化后存入 `kb_document_chunk.metadata_json` 和 Milvus metadata JSON。

| 分类 | 字段 | 类型 | 说明 |
|---|---|---|---|
| 文档标识 | `docId` | Long | 文档 ID |
| | `fileName` | String | 原始文件名 |
| | `sourceUrl` | String | 文件访问 URL |
| 权限控制 | `sensitivityLevel` | String | `ALL` / `ADMIN_ONLY`，默认继承文档级权限 |
| | `accessRoles` | List\<String\> | 允许访问的角色编码，默认继承文档权限 |
| | `accessDepartments` | List\<Long\> | 允许访问的部门 ID，默认继承文档权限 |
| 位置追溯 | `chunkIndex` | int | 块序号，从 0 开始 |
| | `startOffset` | int | 在原文中的起始字符位置 |
| | `endOffset` | int | 在原文中的结束字符位置 |

权限字段默认值由 `KbDocument.permissionType` 换算。敏感词检测命中后 `sensitivityLevel` 升级为 `ADMIN_ONLY`。

## 二、敏感词自动检测

分块完成后，对每个 chunk 文本跑关键词匹配：

- 命中 → `sensitivityLevel = ADMIN_ONLY`
- 未命中 → 保持默认（继承文档权限）

**敏感词库：** 存在 `sys_config` 表中，key 为 `chunk_sensitivity_keywords`，值为 JSON 字符串数组，admin 可在后台维护。匹配规则为子串包含，不区分大小写。

**人工确认：** 分块完成后，后台可查看自动检测结果，对标记进行确认或撤销。确认后的标记生效写入 Milvus。

**重新分块：** 重新走自动检测，之前的标记不保留（chunk 边界可能已变化）。

## 三、分块链路改动

当前链路：
```
读文件 → Tika解析(文本+文档元数据) → 分块 → 向量化(可选) → 持久化(chunk行 + Milvus写入)
```

改动点：

1. **分块器扩展** — `ChunkStrategy` 返回 `(text, startOffset, endOffset)` 三元组，不再仅返回 text
2. **分块后、向量化前** — 遍历 chunk 列表构建 `ChunkMetadata`，跑敏感词检测
3. **向量化** — 不变，元数据不参与 embedding
4. **持久化** — `KbDocumentChunk.metadataJson` 写入序列化的 ChunkMetadata，不再写 `"{}"`
5. **Milvus 写入** — 构建 `VectorDocChunk` 时 `.metadata(chunkMetadata.toMap())`，通过已有的 `buildMetadata()` 自动合并
6. **搜索返回** — `SearchResult` 增加 `metadata` 字段透传

核心改动文件：
- `ChunkStrategy` 及实现类 — 分块结果增加 offset
- `DocumentChunkingServiceImpl` — 组装 metadata、敏感词检测、写入 metadataJson
- `VectorSyncServiceImpl` — 传递 metadata 到 VectorDocChunk
- `MilvusVectorWriter` / `SearchResult` — 搜索返回 metadata

## 四、API 变更

### 新增接口

**查看自动检测结果**
```
GET /api/kb/documents/{docId}/chunk-sensitivity
```
返回被自动标记为 `ADMIN_ONLY` 的 chunk 列表，含命中关键词和文本摘要。

**批量更新敏感级别**
```
PUT /api/kb/documents/{docId}/chunk-sensitivity
{
  "updates": [
    { "chunkId": 123, "sensitivityLevel": "ALL" },
    { "chunkId": 456, "sensitivityLevel": "ADMIN_ONLY" }
  ]
}
```
更新后同步 MySQL `metadataJson` 和 Milvus 对应 chunk 的 metadata。

### 已有接口行为变更

`GET /api/kb/documents/{docId}/chunks` 返回的 `metadataJson` 字段从 `"{}"` 变为真实元数据 JSON。

## 五、不涉及的部分

- 不新增数据库表，复用 `kb_document_chunk.metadata_json`（VARCHAR → LONGTEXT）
- 检索时的元数据过滤能力留到后续迭代
- 不改变现有文档级权限模型，chunk 级权限为叠加限制
