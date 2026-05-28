# Chunk 级元数据实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 分块后给每个 chunk 打上元数据标签（文档标识、权限控制、位置追溯），随向量存入 Milvus，支持敏感词自动检测和后台人工确认。

**Architecture:** 扩展 `TextChunk` record 增加 offset 字段 → 分块后构建 `ChunkMetadata` → 敏感词检测标记 → 通过已有 `VectorDocChunk.metadata` 传入 Milvus，同时写回 `KbDocumentChunk.metadataJson`。新增两个 API 供后台查看和管理敏感标记。敏感词库通过 YAML 配置加载。

**Tech Stack:** Java 17, MyBatis-Plus, Milvus v2 SDK, Spring Boot 3.3.5

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `chunk/TextChunk.java` | Modify | 增加 `startOffset`, `endOffset` 字段 |
| `chunk/FixedSizeChunkingStrategy.java` | Modify | 分块时跟踪字符偏移 |
| `chunk/ParagraphChunkingStrategy.java` | Modify | 分块时跟踪字符偏移 |
| `milvus/SearchResult.java` | Modify | 增加 `metadata` 字段 |
| `milvus/MilvusVectorWriter.java` | Modify | search 方法返回完整 metadata |
| `metadata/ChunkMetadata.java` | **Create** | chunk 元数据模型 |
| `metadata/SensitivityKeywordService.java` | **Create** | 敏感词加载和匹配 |
| `config/SensitivityProperties.java` | **Create** | 敏感词 YAML 配置绑定 |
| `service/impl/DocumentChunkingServiceImpl.java` | Modify | 组装 metadata、敏感词检测、写 metadataJson |
| `service/impl/VectorSyncServiceImpl.java` | Modify | 将 chunk metadata 传入 VectorDocChunk |
| `service/impl/KbChunkServiceImpl.java` | Modify | update 时处理 metadataJson，新增敏感级别更新 |
| `dto/chunk/KbChunkVO.java` | Modify | 已有 metadataJson，确认无需改动 |
| `service/KbChunkService.java` | Modify | 新增 updateSensitivity 方法 |
| `web/KbChunkController.java` | Modify | 新增两个敏感级别接口 |
| `src/main/resources/application.yml` | Modify | 添加默认敏感词配置 |

---

### Task 1: ChunkMetadata 模型

**Files:**
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/metadata/ChunkMetadata.java`

- [ ] **Step 1: 创建 ChunkMetadata 类**

```java
package com.zjl.knowledge.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 单个 chunk 的元数据，序列化后存入 {@code kb_document_chunk.metadata_json} 和 Milvus metadata JSON
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChunkMetadata {

    /** 文档 ID */
    private Long docId;

    /** 原始文件名 */
    private String fileName;

    /** 文件访问 URL（source_url 别名） */
    private String sourceUrl;

    /** 敏感级别：ALL / ADMIN_ONLY */
    private String sensitivityLevel;

    /** 允许访问的角色编码 */
    private List<String> accessRoles;

    /** 允许访问的部门 ID */
    private List<Long> accessDepartments;

    /** 块序号 */
    private int chunkIndex;

    /** 在原文中的起始字符位置 */
    private int startOffset;

    /** 在原文中的结束字符位置 */
    private int endOffset;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 序列化为 JSON 字符串
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * 从 JSON 字符串反序列化
     */
    public static ChunkMetadata fromJson(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, ChunkMetadata.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 转为 Map，供 VectorDocChunk.metadata() 使用
     */
    public Map<String, Object> toMap() {
        try {
            String json = MAPPER.writeValueAsString(this);
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/metadata/ChunkMetadata.java
git commit -m "feat: 添加 ChunkMetadata 模型类"
```

---

### Task 2: TextChunk 扩展 offset

**Files:**
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/chunk/TextChunk.java`

- [ ] **Step 1: TextChunk 增加 offset 字段**

```java
package com.zjl.knowledge.chunk;

/**
 * 分块后的文本片段
 *
 * @param index       切片序号，从 0 开始递增
 * @param content     切片文本内容
 * @param startOffset 在原文中的起始字符位置（含）
 * @param endOffset   在原文中的结束字符位置（不含）
 */
public record TextChunk(int index, String content, int startOffset, int endOffset) {
}
```

- [ ] **Step 2: 更新 FixedSizeChunkingStrategy**

**File:** `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/chunk/FixedSizeChunkingStrategy.java`

将 `chunk()` 方法中的 `new TextChunk(idx++, slice)` 替换为：
```java
out.add(new TextChunk(idx++, slice, pos, end));
```

完整改动：
```java
@Override
public List<TextChunk> chunk(String text, ChunkingOptions options) {
    if (!StringUtils.hasText(text)) {
        return List.of();
    }
    int max = options.maxChars();
    int overlap = Math.min(options.overlapChars(), max - 1);
    List<TextChunk> out = new ArrayList<>();
    int idx = 0;
    int pos = 0;
    while (pos < text.length()) {
        int end = Math.min(text.length(), pos + max);
        String slice = text.substring(pos, end).trim();
        if (StringUtils.hasText(slice)) {
            out.add(new TextChunk(idx++, slice, pos, end));
        }
        if (end >= text.length()) {
            break;
        }
        pos = end - overlap;
        if (pos <= 0) {
            pos = end;
        }
    }
    return out;
}
```

- [ ] **Step 3: 更新 ParagraphChunkingStrategy**

**File:** `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/chunk/ParagraphChunkingStrategy.java`

段落分块需要跟踪每个段落在原文中的偏移。使用 Matcher 来获取偏移：

```java
@Override
public List<TextChunk> chunk(String text, ChunkingOptions options) {
    if (!StringUtils.hasText(text)) {
        return List.of();
    }
    int max = options.maxChars();
    List<TextChunk> out = new ArrayList<>();
    int idx = 0;

    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\R\\R+");
    java.util.regex.Matcher matcher = pattern.matcher(text);

    int lastEnd = 0;
    while (matcher.find()) {
        String block = text.substring(lastEnd, matcher.start()).trim();
        if (StringUtils.hasText(block)) {
            idx = appendBlock(block, lastEnd, max, options, out, idx);
        }
        lastEnd = matcher.end();
    }

    if (lastEnd < text.length()) {
        String block = text.substring(lastEnd).trim();
        if (StringUtils.hasText(block)) {
            idx = appendBlock(block, lastEnd, max, options, out, idx);
        }
    }

    if (out.isEmpty()) {
        for (TextChunk sub : fixedSizeChunkingStrategy.chunk(text, options)) {
            out.add(sub);
        }
    }
    return out;
}

private int appendBlock(String block, int offset, int max, ChunkingOptions options, List<TextChunk> out, int idx) {
    if (block.length() <= max) {
        out.add(new TextChunk(idx++, block, offset, offset + block.length()));
    } else {
        for (TextChunk sub : fixedSizeChunkingStrategy.chunk(block, options)) {
            out.add(new TextChunk(idx++, sub.content(), offset + sub.startOffset(), offset + sub.endOffset()));
        }
    }
    return idx;
}
```

- [ ] **Step 4: 更新 DocumentChunkingServiceImpl 中的 TextChunk 使用**

**File:** `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/DocumentChunkingServiceImpl.java`

当前 `runChunkTask()` 中访问 `chunk.content()` 和 `chunk.index()` 的地方保持不变（record 的 accessor 方法名不变）。

- [ ] **Step 5: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/chunk/TextChunk.java \
        enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/chunk/FixedSizeChunkingStrategy.java \
        enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/chunk/ParagraphChunkingStrategy.java
git commit -m "feat: TextChunk 增加 startOffset/endOffset，分块器跟踪字符偏移"
```

---

### Task 3: 敏感词检测服务

**Files:**
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/config/SensitivityProperties.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/metadata/SensitivityKeywordService.java`
- Modify: `enterprise-knowledge-ai-service/src/main/resources/application.yml`

- [ ] **Step 1: 创建配置属性类**

```java
package com.zjl.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 敏感词配置，绑定 {@code app.knowledge.sensitivity} 前缀
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.knowledge.sensitivity")
public class SensitivityProperties {

    /** 敏感关键词列表 */
    private List<String> keywords = List.of();

    /** 是否启用自动检测，默认 true */
    private boolean enabled = true;
}
```

- [ ] **Step 2: 创建敏感词匹配服务**

```java
package com.zjl.knowledge.metadata;

import com.zjl.knowledge.config.SensitivityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 敏感词检测：子串包含匹配，不区分大小写
 */
@Service
@RequiredArgsConstructor
public class SensitivityKeywordService {

    private final SensitivityProperties properties;

    /**
     * 检测文本是否命中敏感词
     *
     * @param text 待检测文本
     * @return 命中的关键词列表，未命中返回空列表
     */
    public List<String> match(String text) {
        if (!properties.isEnabled() || properties.getKeywords().isEmpty() || text == null) {
            return List.of();
        }
        String lower = text.toLowerCase();
        return properties.getKeywords().stream()
                .filter(kw -> lower.contains(kw.toLowerCase()))
                .toList();
    }

    /**
     * 判断是否命中任何敏感词
     */
    public boolean isSensitive(String text) {
        return !match(text).isEmpty();
    }
}
```

- [ ] **Step 3: 在 application.yml 中添加默认敏感词配置**

在 `enterprise-knowledge-ai-service/src/main/resources/application.yml` 末尾追加：

```yaml
app:
  knowledge:
    sensitivity:
      enabled: true
      keywords:
        - 薪资
        - 财务数据
        - 战略
        - 未公开
        - 机密
        - 预算
        - 股权
```

- [ ] **Step 4: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/config/SensitivityProperties.java \
        enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/metadata/SensitivityKeywordService.java \
        enterprise-knowledge-ai-service/src/main/resources/application.yml
git commit -m "feat: 添加敏感词检测服务和配置"
```

---

### Task 4: 分块链路 — 组装 ChunkMetadata 并传入向量

**Files:**
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/DocumentChunkingServiceImpl.java`

- [ ] **Step 1: 在 runChunkTask 中构建 ChunkMetadata**

在 `runChunkTask()` 方法中，`for (int i = 0; i < parts.size(); i++)` 循环构建 VectorDocChunk 时，同时构建 ChunkMetadata：

```java
// 在 runChunkTask() 中，替换 for 循环块（约 line 194-206）

List<VectorDocChunk> chunkResults = new ArrayList<>(parts.size());
for (int i = 0; i < parts.size(); i++) {
    long chunkPk = IdWorker.getId();
    TextChunk part = parts.get(i);

    ChunkMetadata meta = new ChunkMetadata();
    meta.setDocId(docId);
    meta.setFileName(document.getFileName());
    meta.setSourceUrl(document.getSourceLocation());
    meta.setChunkIndex(part.index());
    meta.setStartOffset(part.startOffset());
    meta.setEndOffset(part.endOffset());

    // 权限默认继承文档级
    meta.setSensitivityLevel("ALL");

    // 敏感词检测
    if (sensitivityKeywordService.isSensitive(part.content())) {
        meta.setSensitivityLevel("ADMIN_ONLY");
    }

    VectorDocChunk.VectorDocChunkBuilder builder = VectorDocChunk.builder()
            .chunkId(String.valueOf(chunkPk))
            .content(part.content())
            .index(part.index())
            .metadata(meta.toMap());

    if (shouldEmbed && vectors != null) {
        builder.embedding(toArray(vectors.get(i)));
    }
    chunkResults.add(builder.build());
}
```

- [ ] **Step 2: 在 persistChunksAndVectorsAtomically 中写入 metadataJson**

在 `persistChunksAndVectorsAtomically()` 方法中，将 `row.setMetadataJson("{}")` 替换为从 `VectorDocChunk.metadata` 中取回 ChunkMetadata：

```java
// 将 row.setMetadataJson("{}"); 替换为：
Map<String, Object> chunkMeta = vc.getMetadata();
if (chunkMeta != null && !chunkMeta.isEmpty()) {
    try {
        row.setMetadataJson(objectMapper.writeValueAsString(chunkMeta));
    } catch (Exception e) {
        row.setMetadataJson("{}");
    }
} else {
    row.setMetadataJson("{}");
}
```

- [ ] **Step 3: 注入 SensitivityKeywordService**

在 `DocumentChunkingServiceImpl` 的字段声明中添加：

```java
private final SensitivityKeywordService sensitivityKeywordService;
```

- [ ] **Step 4: 添加 import**

```java
import com.zjl.knowledge.metadata.ChunkMetadata;
import com.zjl.knowledge.metadata.SensitivityKeywordService;
```

- [ ] **Step 5: 更新 VectorSyncServiceImpl — 已自动处理**

`VectorSyncServiceImpl.syncChunks()` 中的 `buildVectorChunks()` 方法构建 `VectorDocChunk` 时已复制 `VectorDocChunk.builder()` 的所有字段。但由于 `VectorSyncServiceImpl` 自己构建 `VectorDocChunk`（通过 `buildVectorChunks`），它不经过 `DocumentChunkingServiceImpl` 的元数据组装。

需要在 `VectorSyncServiceImpl.buildVectorChunks()` 中也添加元数据。但 `VectorSyncServiceImpl` 里没有 `KbDocument` 的 file_name、source_url 等信息，只有 chunk 对象。

**方案：** 同步写入（`syncChunk`、`syncChunks`、`updateChunk`）通常是增删改 chunk 后用到的，这些场景可以复用已有的 `metadataJson`。修改 `buildVectorChunks` 从 `KbDocumentChunk` 取已有元数据。

实际上，重新审视后：`VectorSyncServiceImpl` 的方法没有直接绑到 `KbDocument` 结构的访问，它通过接口定义。最简单的做法是让 `syncChunks` 等方法的调用方提前组装好 metadata，而 `VectorSyncServiceImpl` 只负责透传。

当前 `syncChunks` 的签名是 `void syncChunks(KbDocument document, List<KbDocumentChunk> chunks)`，它在内部构建 `VectorDocChunk`。我们应该让它从 `KbDocumentChunk.metadataJson` 读取已存的元数据。

修改 `VectorSyncServiceImpl.buildVectorChunks()`:

```java
private List<VectorDocChunk> buildVectorChunks(KbDocument document, List<KbDocumentChunk> chunks) {
    List<String> texts = chunks.stream().map(KbDocumentChunk::getChunkText).collect(Collectors.toList());
    List<List<Float>> vectors = embedBatch(texts, document);
    if (vectors.size() != chunks.size()) {
        throw new BizException(ErrorCode.SYSTEM_ERROR, "向量结果数量与 Chunk 数不一致");
    }
    List<VectorDocChunk> result = new ArrayList<>(chunks.size());
    for (int i = 0; i < chunks.size(); i++) {
        KbDocumentChunk c = chunks.get(i);
        Map<String, Object> meta = ChunkMetadata.fromJson(c.getMetadataJson());
        Map<String, Object> metaMap = meta != null ? meta.toMap() : Collections.emptyMap();
        result.add(VectorDocChunk.builder()
                .chunkId(String.valueOf(c.getId()))
                .content(c.getChunkText())
                .index(c.getChunkIndex())
                .metadata(metaMap)
                .embedding(toArray(vectors.get(i)))
                .build());
    }
    return result;
}
```

同时给 `VectorSyncServiceImpl` 添加 import：
```java
import com.zjl.knowledge.metadata.ChunkMetadata;
import java.util.Collections;
```

- [ ] **Step 6: 更新 VectorSyncServiceImpl.syncChunk() 和 updateChunk()**

这两个方法也各自构建 VectorDocChunk，需要从 KbDocumentChunk 取 metadataJson：

对于 `syncChunk()`（line 62-72），从 `chunk.getMetadataJson()` 读取：
```java
@Override
public void syncChunk(KbDocument document, KbDocumentChunk chunk) {
    float[] vector = toArray(embed(chunk.getChunkText(), document));
    Map<String, Object> metaMap = buildMetaMap(chunk.getMetadataJson());
    VectorDocChunk vc = VectorDocChunk.builder()
            .chunkId(String.valueOf(chunk.getId()))
            .content(chunk.getChunkText())
            .index(chunk.getChunkIndex())
            .metadata(metaMap)
            .embedding(vector)
            .build();
    String collection = resolveCollection(document);
    chunkVectorStore.indexDocumentChunks(collection, document.getId(), List.of(vc));
}
```

对于 `updateChunk()`（line 85-94），同理：
```java
@Override
public void updateChunk(KbDocument document, KbDocumentChunk chunk) {
    float[] vector = toArray(embed(chunk.getChunkText(), document));
    Map<String, Object> metaMap = buildMetaMap(chunk.getMetadataJson());
    String collection = resolveCollection(document);
    chunkVectorStore.updateChunk(collection, document.getId(),
            VectorDocChunk.builder()
                    .chunkId(String.valueOf(chunk.getId()))
                    .content(chunk.getChunkText())
                    .index(chunk.getChunkIndex())
                    .metadata(metaMap)
                    .embedding(vector)
                    .build());
}
```

并添加辅助方法：
```java
private Map<String, Object> buildMetaMap(String metadataJson) {
    ChunkMetadata meta = ChunkMetadata.fromJson(metadataJson);
    return meta != null ? meta.toMap() : Collections.emptyMap();
}
```

- [ ] **Step 7: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/DocumentChunkingServiceImpl.java \
        enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorSyncServiceImpl.java
git commit -m "feat: 分块链路组装 ChunkMetadata 并传入 VectorDocChunk/Milvus"
```

---

### Task 5: SearchResult 扩展 metadata 透传

**Files:**
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/SearchResult.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/MilvusVectorWriter.java`

- [ ] **Step 1: SearchResult 增加 metadata 字段**

```java
package com.zjl.knowledge.milvus;

import java.util.Map;

/**
 * Milvus 向量检索结果
 */
public record SearchResult(String chunkId, String docId, float score, Map<String, Object> metadata) {
}
```

- [ ] **Step 2: MilvusVectorWriter.search() 返回完整 metadata**

在 `search()` 方法中（line 280-312），修改 SearchResult 构造：

```java
// 将 results.add(new SearchResult(chunkId, docId, sr.getScore()));
// 替换为：
results.add(new SearchResult(chunkId, docId, sr.getScore(), metaObj));
```

- [ ] **Step 3: 更新 RagQaTool 消费方**

**File:** `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/tool/RagQaTool.java`

在 `buildMatchedChunks()` 中，当前使用 `r.chunkId()` 和 `r.score()` 的地方保持不变。新增从 SearchResult 读取 metadata：

```java
// 在 buildMatchedChunks 的 map 中添加 metadata
chunkInfo.put("metadata", r.metadata());
```

完整修改 `buildMatchedChunks` 方法：

```java
private List<Map<String, Object>> buildMatchedChunks(Long docId, List<SearchResult> results) {
    List<Long> chunkIds = results.stream()
            .map(r -> Long.parseLong(r.chunkId()))
            .collect(Collectors.toList());

    List<KbDocumentChunk> chunks = kbDocumentChunkMapper.selectBatchIds(chunkIds);

    Map<Long, Float> scoreMap = new LinkedHashMap<>();
    Map<Long, Map<String, Object>> metaMap = new LinkedHashMap<>();
    for (SearchResult r : results) {
        scoreMap.put(Long.parseLong(r.chunkId()), r.score());
        metaMap.put(Long.parseLong(r.chunkId()), r.metadata());
    }

    return chunks.stream()
            .map(c -> {
                Map<String, Object> chunkInfo = new LinkedHashMap<>();
                chunkInfo.put("chunkIndex", c.getChunkIndex());
                chunkInfo.put("text", c.getChunkText());
                chunkInfo.put("score", scoreMap.getOrDefault(c.getId(), 0f));
                chunkInfo.put("metadata", metaMap.getOrDefault(c.getId(), Map.of()));
                return chunkInfo;
            })
            .collect(Collectors.toList());
}
```

- [ ] **Step 4: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/SearchResult.java \
        enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/MilvusVectorWriter.java \
        enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/tool/RagQaTool.java
git commit -m "feat: SearchResult 增加 metadata 字段，搜索返回完整元数据"
```

---

### Task 6: 敏感级别 API

**Files:**
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/KbChunkService.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/KbChunkServiceImpl.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/chunk/ChunkSensitivityUpdateRequest.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/chunk/ChunkSensitivityVO.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/web/KbChunkController.java`

- [ ] **Step 1: 创建请求/响应 DTO**

`ChunkSensitivityUpdateRequest.java`:
```java
package com.zjl.knowledge.dto.chunk;

import lombok.Data;

import java.util.List;

/**
 * 批量更新 chunk 敏感级别请求
 */
@Data
public class ChunkSensitivityUpdateRequest {

    private List<ChunkSensitivityItem> updates;

    @Data
    public static class ChunkSensitivityItem {
        private Long chunkId;
        private String sensitivityLevel;
    }
}
```

`ChunkSensitivityVO.java`:
```java
package com.zjl.knowledge.dto.chunk;

import lombok.Data;

import java.util.List;

/**
 * 被自动标记为敏感的 chunk 视图
 */
@Data
public class ChunkSensitivityVO {

    private Long chunkId;
    private Integer chunkIndex;
    private List<String> matchedKeywords;
    private String sensitivityLevel;
    private String textPreview;
}
```

- [ ] **Step 2: KbChunkService 接口新增方法**

在 `KbChunkService` 中添加：

```java
import com.zjl.knowledge.dto.chunk.ChunkSensitivityUpdateRequest;
import com.zjl.knowledge.dto.chunk.ChunkSensitivityVO;

/**
 * 查询文档中被自动标记为敏感的 chunk 列表
 *
 * @param docId 文档 ID
 * @param user  当前用户
 * @return 敏感 chunk 视图列表
 */
List<ChunkSensitivityVO> listSensitiveChunks(Long docId, UserContext user);

/**
 * 批量更新 chunk 敏感级别
 *
 * @param docId   文档 ID
 * @param request 更新请求
 * @param user    当前用户
 */
void updateChunkSensitivity(Long docId, ChunkSensitivityUpdateRequest request, UserContext user);
```

- [ ] **Step 3: KbChunkServiceImpl 实现敏感查询和更新**

在 `KbChunkServiceImpl` 中注入：
```java
private final VectorSyncService vectorSyncService;
private final KbMilvusRoutingService kbMilvusRoutingService;
```

实现 `listSensitiveChunks`:
```java
@Override
public List<ChunkSensitivityVO> listSensitiveChunks(Long docId, UserContext user) {
    KbDocument document = loadDocOrThrow(docId);
    assertReadable(document, user);

    List<KbDocumentChunk> chunks = baseMapper.selectList(
            Wrappers.lambdaQuery(KbDocumentChunk.class)
                    .eq(KbDocumentChunk::getDocumentId, docId)
                    .orderByAsc(KbDocumentChunk::getChunkIndex));

    return chunks.stream()
            .filter(c -> {
                ChunkMetadata meta = ChunkMetadata.fromJson(c.getMetadataJson());
                return meta != null && "ADMIN_ONLY".equals(meta.getSensitivityLevel());
            })
            .map(c -> {
                ChunkMetadata meta = ChunkMetadata.fromJson(c.getMetadataJson());
                ChunkSensitivityVO vo = new ChunkSensitivityVO();
                vo.setChunkId(c.getId());
                vo.setChunkIndex(c.getChunkIndex());
                vo.setSensitivityLevel(meta != null ? meta.getSensitivityLevel() : "ALL");
                String text = c.getChunkText();
                vo.setTextPreview(text != null && text.length() > 100 ? text.substring(0, 100) + "..." : text);
                return vo;
            })
            .collect(Collectors.toList());
}
```

实现 `updateChunkSensitivity`（更新 MySQL metadataJson 后，通过 `vectorSyncService.updateChunk` 同步 Milvus）：
```java
@Override
@Transactional(rollbackFor = Exception.class)
public void updateChunkSensitivity(Long docId, ChunkSensitivityUpdateRequest request, UserContext user) {
    KbDocument document = loadDocOrThrow(docId);
    assertWritable(document, user);

    if (request.getUpdates() == null || request.getUpdates().isEmpty()) {
        return;
    }

    boolean shouldEmbed = kbMilvusRoutingService.shouldEmbed(document);

    for (var item : request.getUpdates()) {
        KbDocumentChunk chunk = baseMapper.selectById(item.getChunkId());
        if (chunk == null || !chunk.getDocumentId().equals(docId)) {
            continue;
        }

        ChunkMetadata meta = ChunkMetadata.fromJson(chunk.getMetadataJson());
        if (meta == null) {
            meta = new ChunkMetadata();
            meta.setDocId(docId);
            meta.setChunkIndex(chunk.getChunkIndex());
        }
        meta.setSensitivityLevel(item.getSensitivityLevel());
        chunk.setMetadataJson(meta.toJson());
        baseMapper.updateById(chunk);

        if (shouldEmbed) {
            vectorSyncService.updateChunk(document, chunk);
        }
    }
}
```

`vectorSyncService.updateChunk` 内部会重新 embedding 并 upsert 到 Milvus（`VectorSyncServiceImpl` 的 `buildMetaMap` 会从 `chunk.getMetadataJson()` 取回更新后的 metadata 写入）。需要注入 `KbMilvusRoutingService` 和 `VectorSyncService`，并 import `ChunkMetadata`。

- [ ] **Step 4: KbChunkController 新增接口**

在 `KbChunkController` 中添加：

```java
import com.zjl.knowledge.dto.chunk.ChunkSensitivityUpdateRequest;
import com.zjl.knowledge.dto.chunk.ChunkSensitivityVO;

@GetMapping("/sensitivity")
public Result<List<ChunkSensitivityVO>> listSensitiveChunks(@PathVariable("docId") Long docId) {
    return Results.success(kbChunkService.listSensitiveChunks(docId, UserContextHolder.get()));
}

@PutMapping("/sensitivity")
public Result<Void> updateSensitivity(
        @PathVariable("docId") Long docId,
        @RequestBody ChunkSensitivityUpdateRequest request
) {
    kbChunkService.updateChunkSensitivity(docId, request, UserContextHolder.get());
    return Results.success();
}
```

两个接口 URL 为：
- `GET /api/kb/documents/{docId}/chunks/sensitivity`
- `PUT /api/kb/documents/{docId}/chunks/sensitivity`

- [ ] **Step 5: 更新 KbChunkServiceImpl 已有的 update 方法**

当前的 `update()` 方法处理 content 更新，也需要把 metadataJson 的更新处理掉。但当前 `KbChunkUpdateRequest` 只有 `content` 字段。metadataJson 的更新通过新增的 `updateChunkSensitivity` 方法独立处理，不需要改 update。

- [ ] **Step 6: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/KbChunkService.java \
        enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/KbChunkServiceImpl.java \
        enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/chunk/ChunkSensitivityUpdateRequest.java \
        enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/chunk/ChunkSensitivityVO.java \
        enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/web/KbChunkController.java
git commit -m "feat: 添加 chunk 敏感级别查询和批量更新 API"
```

---

### Task 7: 编译验证

**Files:** None (verification only)

- [ ] **Step 1: 编译整个 knowledge-ai-service 模块**

```bash
mvn compile -pl enterprise-knowledge-ai-service -DskipTests
```

- [ ] **Step 2: 确认编译通过，修复编译错误**

若有编译错误，根据错误信息修复后重新编译。

- [ ] **Step 3: Commit（如有修复）**

```bash
git add --all
git commit -m "fix: 编译修复"
```

---

## Self-Review

1. **Spec coverage:**
   - ChunkMetadata 模型（9个字段）→ Task 1
   - 敏感词自动检测 → Task 3
   - 分块链路改动（offset、元数据组装、metadataJson、Milvus写入）→ Tasks 2, 4
   - 搜索返回 metadata → Task 5
   - 查看自动检测结果 API → Task 6
   - 批量更新敏感级别 API → Task 6
   - 已有接口 metadataJson 从 "{}" 变真实数据 → Task 4

2. **No placeholders:** 所有代码块都有具体实现

3. **Type consistency:**
   - `ChunkMetadata` 类型在 Task 1 定义，Task 4, 5, 6 中使用
   - `TextChunk` 的 `startOffset/endOffset` 在 Task 2 添加，Task 4 使用
   - `SearchResult.metadata` 在 Task 5 添加
   - `ChunkSensitivityVO` 和 `ChunkSensitivityUpdateRequest` 在 Task 6 定义和使用
