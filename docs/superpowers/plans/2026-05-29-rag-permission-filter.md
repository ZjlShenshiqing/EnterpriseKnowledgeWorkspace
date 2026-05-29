# RAG Permission Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Milvus metadata permission filtering for RAG, keep DB permission checks as the final safety boundary, and add chunk-level permission metadata sync tasks with admin visibility and retry.

**Architecture:** Introduce dedicated RAG permission services instead of growing `RagQaTool`: metadata generation, filter generation, vector search orchestration, sync task state machine, and admin APIs. MySQL remains the permission source of truth; Milvus metadata is a query optimization copy maintained asynchronously.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Milvus Java SDK, JUnit 5, Mockito, Maven.

---

## Current Context

Read these first:

- Spec: `docs/superpowers/specs/2026-05-29-rag-permission-filter-design.md`
- Existing RAG tool: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/mcp/tool/RagQaTool.java`
- Existing vector writer: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/MilvusVectorWriter.java`
- Existing vector service: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorSyncServiceImpl.java`
- Existing metadata model: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/metadata/ChunkMetadata.java`
- Existing permission service: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/DocumentVisibilityServiceImpl.java`
- Existing mapper XML pattern: `enterprise-knowledge-ai-service/src/main/resources/mapper/KbDocumentMapper.xml`
- Existing DB rules: `docs/AGENTS.md`

Run target tests with:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -Dtest=RagQaToolTest
```

Mockito tests may require running Maven outside the sandbox because the inline mock maker needs JVM attach permissions.

## File Structure

Create:

- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagPermissionMetadataService.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagPermissionMetadataServiceImpl.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagPermissionFilterBuilder.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagVectorSearchService.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagVectorSearchServiceImpl.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagCandidate.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/domain/VectorPermissionSyncStatus.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/domain/VectorPermissionSyncTriggerType.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/entity/KbVectorPermissionSyncTask.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/mapper/KbVectorPermissionSyncTaskMapper.java`
- `enterprise-knowledge-ai-service/src/main/resources/mapper/KbVectorPermissionSyncTaskMapper.xml`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/VectorPermissionSyncTaskService.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorPermissionSyncTaskServiceImpl.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/event/DocumentPermissionChangedEvent.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/event/DocumentPermissionChangedListener.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/vector/VectorPermissionSyncOverviewVO.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/vector/VectorPermissionSyncTaskVO.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/web/VectorPermissionAdminController.java`
- `enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/rag/RagPermissionMetadataServiceImplTest.java`
- `enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/rag/RagPermissionFilterBuilderTest.java`
- `enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/rag/RagVectorSearchServiceImplTest.java`
- `enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/service/impl/VectorPermissionSyncTaskServiceImplTest.java`

Modify:

- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/ChunkVectorStore.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/MilvusChunkVectorStore.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/MilvusVectorWriter.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/VectorSyncService.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorSyncServiceImpl.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/DocumentChunkingServiceImpl.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/KbChunkServiceImpl.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/KbDocumentServiceImpl.java`
- `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/mcp/tool/RagQaTool.java`
- `enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/agent/mcp/tool/RagQaToolTest.java`
- `resouces/enterprise_knowledge_workspace.sql`

## Task 1: Permission Metadata Generator

**Files:**

- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagPermissionMetadataService.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagPermissionMetadataServiceImpl.java`
- Test: `enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/rag/RagPermissionMetadataServiceImplTest.java`

- [ ] **Step 1: Write the failing metadata test**

Create `RagPermissionMetadataServiceImplTest.java`:

```java
package com.zjl.knowledge.rag;

import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.entity.KbDocumentPermission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagPermissionMetadataServiceImplTest {

    private final RagPermissionMetadataService service = new RagPermissionMetadataServiceImpl();

    @Test
    void buildMetadataIncludesFlatPermissionFields() {
        KbDocument doc = new KbDocument();
        doc.setId(1001L);
        doc.setOwnerId(2001L);
        doc.setDepartmentId(3001L);
        doc.setPermissionType("PROJECT");
        doc.setStatus(DocumentStatus.SUCCESS.name());
        doc.setEnabled(1);

        KbDocumentChunk chunk = new KbDocumentChunk();
        chunk.setId(9001L);
        chunk.setChunkIndex(7);
        chunk.setEnabled(1);

        KbDocumentPermission project = new KbDocumentPermission();
        project.setPermissionTargetType("PROJECT");
        project.setPermissionTargetId(4001L);
        KbDocumentPermission user = new KbDocumentPermission();
        user.setPermissionTargetType("USER");
        user.setPermissionTargetId(5001L);

        Map<String, Object> metadata = service.buildMetadata(doc, chunk, List.of(project, user));

        assertThat(metadata).containsEntry("permission_type", "PROJECT");
        assertThat(metadata).containsEntry("owner_id", 2001L);
        assertThat(metadata).containsEntry("department_id", 3001L);
        assertThat(metadata).containsEntry("document_status", "SUCCESS");
        assertThat(metadata).containsEntry("document_enabled", true);
        assertThat(metadata).containsEntry("chunk_enabled", true);
        assertThat(metadata).containsEntry("admin_only", false);
        assertThat(metadata.get("project_ids")).isEqualTo(List.of(4001L));
        assertThat(metadata.get("user_ids")).isEqualTo(List.of(5001L));
    }

    @Test
    void buildMetadataMarksAdminOnlyDocuments() {
        KbDocument doc = new KbDocument();
        doc.setId(1002L);
        doc.setOwnerId(2002L);
        doc.setPermissionType("ADMIN");
        doc.setStatus(DocumentStatus.SUCCESS.name());
        doc.setEnabled(1);

        KbDocumentChunk chunk = new KbDocumentChunk();
        chunk.setId(9002L);
        chunk.setChunkIndex(1);
        chunk.setEnabled(1);

        Map<String, Object> metadata = service.buildMetadata(doc, chunk, List.of());

        assertThat(metadata).containsEntry("permission_type", "ADMIN");
        assertThat(metadata).containsEntry("admin_only", true);
        assertThat(metadata.get("project_ids")).isEqualTo(List.of());
        assertThat(metadata.get("user_ids")).isEqualTo(List.of());
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -Dtest=RagPermissionMetadataServiceImplTest
```

Expected: compilation fails because `RagPermissionMetadataService` does not exist.

- [ ] **Step 3: Create the interface**

Create `RagPermissionMetadataService.java`:

```java
package com.zjl.knowledge.rag;

import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.entity.KbDocumentPermission;

import java.util.List;
import java.util.Map;

/**
 * RAG 权限 metadata 构建服务
 */
public interface RagPermissionMetadataService {

    /**
     * 根据当前 DB 权限快照构建写入 Milvus metadata JSON 的扁平字段
     */
    Map<String, Object> buildMetadata(KbDocument document, KbDocumentChunk chunk,
                                      List<KbDocumentPermission> permissions);
}
```

- [ ] **Step 4: Implement metadata generation**

Create `RagPermissionMetadataServiceImpl.java`:

```java
package com.zjl.knowledge.rag;

import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.entity.KbDocumentPermission;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 权限 metadata 构建服务实现
 */
@Service
public class RagPermissionMetadataServiceImpl implements RagPermissionMetadataService {

    @Override
    public Map<String, Object> buildMetadata(KbDocument document, KbDocumentChunk chunk,
                                             List<KbDocumentPermission> permissions) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        String permissionType = document.getPermissionType();
        metadata.put("permission_type", permissionType);
        metadata.put("owner_id", document.getOwnerId());
        metadata.put("department_id", document.getDepartmentId());
        metadata.put("project_ids", targetIds(permissions, "PROJECT"));
        metadata.put("user_ids", targetIds(permissions, "USER"));
        metadata.put("admin_only", "ADMIN".equals(permissionType));
        metadata.put("document_status", document.getStatus());
        metadata.put("document_enabled", document.getEnabled() == null || document.getEnabled() == 1);
        metadata.put("chunk_enabled", chunk.getEnabled() == null || chunk.getEnabled() == 1);
        return metadata;
    }

    private static List<Long> targetIds(List<KbDocumentPermission> permissions, String targetType) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        return permissions.stream()
                .filter(p -> targetType.equals(p.getPermissionTargetType()))
                .map(KbDocumentPermission::getPermissionTargetId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }
}
```

- [ ] **Step 5: Run the test and verify it passes**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -Dtest=RagPermissionMetadataServiceImplTest
```

Expected: 2 tests pass.

- [ ] **Step 6: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagPermissionMetadataService.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagPermissionMetadataServiceImpl.java \
  enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/rag/RagPermissionMetadataServiceImplTest.java
git commit -m "新增 RAG 权限 metadata 构建服务"
```

## Task 2: Milvus Permission Filter Builder

**Files:**

- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagPermissionFilterBuilder.java`
- Test: `enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/rag/RagPermissionFilterBuilderTest.java`

- [ ] **Step 1: Write the failing filter builder test**

Create `RagPermissionFilterBuilderTest.java`:

```java
package com.zjl.knowledge.rag;

import com.zjl.knowledge.web.UserContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPermissionFilterBuilderTest {

    private final RagPermissionFilterBuilder builder = new RagPermissionFilterBuilder();

    @Test
    void buildForUserIncludesStatusEnabledAndVisibilityConditions() {
        UserContext user = UserContext.builder()
                .userId(1001L)
                .departmentId(2001L)
                .projectId(3001L)
                .admin(false)
                .build();

        String filter = builder.build(user);

        assertThat(filter).contains("metadata[\"document_status\"] == \"SUCCESS\"");
        assertThat(filter).contains("metadata[\"document_enabled\"] == true");
        assertThat(filter).contains("metadata[\"chunk_enabled\"] == true");
        assertThat(filter).contains("metadata[\"owner_id\"] == 1001");
        assertThat(filter).contains("metadata[\"permission_type\"] == \"ALL\"");
        assertThat(filter).contains("metadata[\"permission_type\"] == \"DEPARTMENT\"");
        assertThat(filter).contains("metadata[\"department_id\"] == 2001");
        assertThat(filter).contains("metadata[\"permission_type\"] == \"PROJECT\"");
        assertThat(filter).contains("3001 in metadata[\"project_ids\"]");
        assertThat(filter).contains("metadata[\"permission_type\"] == \"USER\"");
        assertThat(filter).contains("1001 in metadata[\"user_ids\"]");
    }

    @Test
    void buildForAdminIncludesAdminVisibility() {
        UserContext user = UserContext.builder()
                .userId(1001L)
                .admin(true)
                .build();

        String filter = builder.build(user);

        assertThat(filter).contains("metadata[\"permission_type\"] == \"ADMIN\"");
        assertThat(filter).contains("metadata[\"admin_only\"] == true");
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -Dtest=RagPermissionFilterBuilderTest
```

Expected: compilation fails because `RagPermissionFilterBuilder` does not exist.

- [ ] **Step 3: Implement filter builder**

Create `RagPermissionFilterBuilder.java`:

```java
package com.zjl.knowledge.rag;

import com.zjl.knowledge.web.UserContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG Milvus 权限粗过滤表达式构建器
 */
@Component
public class RagPermissionFilterBuilder {

    /**
     * 根据当前用户构建 Milvus metadata JSON filter
     */
    public String build(UserContext user) {
        List<String> visibility = new ArrayList<>();
        visibility.add("metadata[\"owner_id\"] == " + user.getUserId());
        visibility.add("metadata[\"permission_type\"] == \"ALL\"");
        if (user.getDepartmentId() != null) {
            visibility.add("(metadata[\"permission_type\"] == \"DEPARTMENT\" && metadata[\"department_id\"] == "
                    + user.getDepartmentId() + ")");
        }
        if (user.getProjectId() != null) {
            visibility.add("(metadata[\"permission_type\"] == \"PROJECT\" && "
                    + user.getProjectId() + " in metadata[\"project_ids\"])");
        }
        visibility.add("(metadata[\"permission_type\"] == \"USER\" && "
                + user.getUserId() + " in metadata[\"user_ids\"])");
        if (user.isAdmin()) {
            visibility.add("metadata[\"permission_type\"] == \"ADMIN\"");
            visibility.add("metadata[\"admin_only\"] == true");
        }

        String searchable = "metadata[\"document_status\"] == \"SUCCESS\""
                + " && metadata[\"document_enabled\"] == true"
                + " && metadata[\"chunk_enabled\"] == true";
        return searchable + " && (" + String.join(" || ", visibility) + ")";
    }
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -Dtest=RagPermissionFilterBuilderTest
```

Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagPermissionFilterBuilder.java \
  enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/rag/RagPermissionFilterBuilderTest.java
git commit -m "新增 RAG 权限过滤表达式构建器"
```

## Task 3: Add Filtered Vector Search Contract

**Files:**

- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/ChunkVectorStore.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/MilvusChunkVectorStore.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/VectorSyncService.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorSyncServiceImpl.java`
- Test: `enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/rag/RagVectorSearchServiceImplTest.java`

- [ ] **Step 1: Extend interfaces with filtered search methods**

Modify `ChunkVectorStore.java` to keep the existing method and use the existing `filter` parameter:

```java
List<SearchResult> search(String collectionName, float[] vector, int topK, String filter);
```

Modify `VectorSyncService.java` and add:

```java
List<com.zjl.knowledge.milvus.SearchResult> searchSimilar(String query, int topK, KbDocument document, String filter);
```

- [ ] **Step 2: Implement overloaded VectorSyncService method**

Modify `VectorSyncServiceImpl.java`:

```java
@Override
public List<com.zjl.knowledge.milvus.SearchResult> searchSimilar(String query, int topK, KbDocument document, String filter) {
    float[] vector = toArray(embed(query, document));
    String collection = resolveCollectionOrDefault(document);
    return chunkVectorStore.search(collection, vector, topK, filter);
}
```

Keep existing method as a delegating overload:

```java
@Override
public List<com.zjl.knowledge.milvus.SearchResult> searchSimilar(String query, int topK, KbDocument document) {
    return searchSimilar(query, topK, document, null);
}
```

- [ ] **Step 3: Run compile for interface changes**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -DskipTests
```

Expected: compile succeeds.

- [ ] **Step 4: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/ChunkVectorStore.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/MilvusChunkVectorStore.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/VectorSyncService.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorSyncServiceImpl.java
git commit -m "支持 RAG 向量检索传入过滤表达式"
```

## Task 4: RAG Vector Search Service

**Files:**

- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagCandidate.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagVectorSearchService.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagVectorSearchServiceImpl.java`
- Test: `enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/rag/RagVectorSearchServiceImplTest.java`

- [ ] **Step 1: Write failing RAG vector search test**

Create `RagVectorSearchServiceImplTest.java`:

```java
package com.zjl.knowledge.rag;

import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.milvus.SearchResult;
import com.zjl.knowledge.service.VectorSyncService;
import com.zjl.knowledge.web.UserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagVectorSearchServiceImplTest {

    @Mock
    private VectorSyncService vectorSyncService;

    @Test
    void searchBuildsPermissionFilterAndSkipsInvalidIds() {
        RagPermissionFilterBuilder filterBuilder = new RagPermissionFilterBuilder();
        RagVectorSearchService service = new RagVectorSearchServiceImpl(vectorSyncService, filterBuilder);
        UserContext user = UserContext.builder().userId(1001L).departmentId(2001L).projectId(3001L).admin(false).build();

        when(vectorSyncService.searchSimilar(eq("question"), eq(15), any(KbDocument.class), any(String.class)))
                .thenReturn(List.of(
                        new SearchResult("bad", "bad", 0.99f, Map.of()),
                        new SearchResult("9001", "2001", 0.88f, Map.of("doc_id", "2001"))
                ));

        List<RagCandidate> candidates = service.search("question", 15, user);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).docId()).isEqualTo(2001L);
        assertThat(candidates.get(0).chunkId()).isEqualTo(9001L);

        ArgumentCaptor<String> filterCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(vectorSyncService)
                .searchSimilar(eq("question"), eq(15), any(KbDocument.class), filterCaptor.capture());
        assertThat(filterCaptor.getValue()).contains("metadata[\"document_status\"] == \"SUCCESS\"");
        assertThat(filterCaptor.getValue()).contains("metadata[\"owner_id\"] == 1001");
    }
}
```

- [ ] **Step 2: Run and verify failure**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -Dtest=RagVectorSearchServiceImplTest
```

Expected: compilation fails because `RagCandidate` and `RagVectorSearchService` do not exist.

- [ ] **Step 3: Create candidate record**

Create `RagCandidate.java`:

```java
package com.zjl.knowledge.rag;

import java.util.Map;

/**
 * RAG 向量召回候选
 */
public record RagCandidate(Long docId, Long chunkId, float score, Map<String, Object> metadata) {
}
```

- [ ] **Step 4: Create service interface**

Create `RagVectorSearchService.java`:

```java
package com.zjl.knowledge.rag;

import com.zjl.knowledge.web.UserContext;

import java.util.List;

/**
 * RAG 向量检索编排服务
 */
public interface RagVectorSearchService {

    List<RagCandidate> search(String question, int topK, UserContext user);
}
```

- [ ] **Step 5: Implement service**

Create `RagVectorSearchServiceImpl.java`:

```java
package com.zjl.knowledge.rag;

import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.milvus.SearchResult;
import com.zjl.knowledge.service.VectorSyncService;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * RAG 向量检索编排服务实现
 */
@Service
@RequiredArgsConstructor
public class RagVectorSearchServiceImpl implements RagVectorSearchService {

    private final VectorSyncService vectorSyncService;
    private final RagPermissionFilterBuilder filterBuilder;

    @Override
    public List<RagCandidate> search(String question, int topK, UserContext user) {
        String filter = filterBuilder.build(user);
        KbDocument contextDoc = new KbDocument();
        return vectorSyncService.searchSimilar(question, topK, contextDoc, filter).stream()
                .map(this::toCandidate)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<RagCandidate> toCandidate(SearchResult result) {
        Optional<Long> docId = parseLong(result.docId());
        Optional<Long> chunkId = parseLong(result.chunkId());
        if (docId.isEmpty() || chunkId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RagCandidate(docId.get(), chunkId.get(), result.score(), result.metadata()));
    }

    private static Optional<Long> parseLong(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 6: Run test and verify pass**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -Dtest=RagVectorSearchServiceImplTest
```

Expected: 1 test passes.

- [ ] **Step 7: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagCandidate.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagVectorSearchService.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/rag/RagVectorSearchServiceImpl.java \
  enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/rag/RagVectorSearchServiceImplTest.java
git commit -m "新增 RAG 向量检索编排服务"
```

## Task 5: Wire Metadata into Vector Writes

**Files:**

- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/DocumentChunkingServiceImpl.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/KbChunkServiceImpl.java`
- Test: verify with `RagPermissionMetadataServiceImplTest` and module compilation after wiring.

- [ ] **Step 1: Inject metadata service and permission mapper**

In both `DocumentChunkingServiceImpl` and `KbChunkServiceImpl`, inject:

```java
private final RagPermissionMetadataService ragPermissionMetadataService;
private final KbDocumentPermissionMapper kbDocumentPermissionMapper;
```

`KbChunkServiceImpl` already has `private final KbDocumentPermissionMapper permissionMapper;`; reuse that field there. Add a new `KbDocumentPermissionMapper` field only to `DocumentChunkingServiceImpl`.

- [ ] **Step 2: Build permissions list near vector chunk construction**

Where `VectorDocChunk.builder()` is used for a persisted `KbDocumentChunk`, load permissions once per document:

```java
List<KbDocumentPermission> permissions = kbDocumentPermissionMapper.selectList(
        Wrappers.lambdaQuery(KbDocumentPermission.class)
                .eq(KbDocumentPermission::getDocumentId, document.getId())
);
```

- [ ] **Step 3: Merge permission metadata into VectorDocChunk metadata**

For each chunk row:

```java
Map<String, Object> metadata = new LinkedHashMap<>();
metadata.putAll(existingMetadataMap);
metadata.putAll(ragPermissionMetadataService.buildMetadata(document, chunk, permissions));
```

Then pass:

```java
.metadata(metadata)
```

on `VectorDocChunk.builder()`.

- [ ] **Step 4: Compile**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -Dtest=RagPermissionMetadataServiceImplTest
```

Expected: metadata tests pass and production code compiles.

- [ ] **Step 5: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/DocumentChunkingServiceImpl.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/KbChunkServiceImpl.java
git commit -m "写入向量时附加 RAG 权限 metadata"
```

## Task 6: Refactor RagQaTool to Use RagVectorSearchService

**Files:**

- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/mcp/tool/RagQaTool.java`
- Modify: `enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/agent/mcp/tool/RagQaToolTest.java`

- [ ] **Step 1: Update RagQaToolTest to mock RagVectorSearchService**

Replace `VectorSyncService` mock with:

```java
@Mock
private RagVectorSearchService ragVectorSearchService;
```

Construct:

```java
ragQaTool = new RagQaTool(
        ragVectorSearchService,
        kbDocumentMapper,
        kbDocumentChunkMapper,
        kbDocumentPermissionMapper,
        documentVisibilityService
);
```

Replace stubs such as:

```java
when(vectorSyncService.searchSimilar("question", 15, new KbDocument()))
```

with:

```java
when(ragVectorSearchService.search("question", 15, user))
```

and return `List<RagCandidate>`.

- [ ] **Step 2: Run test and verify failure**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -Dtest=RagQaToolTest
```

Expected: compilation fails because `RagQaTool` still expects `VectorSyncService`.

- [ ] **Step 3: Modify RagQaTool constructor dependency**

Replace:

```java
private final VectorSyncService vectorSyncService;
```

with:

```java
private final RagVectorSearchService ragVectorSearchService;
```

- [ ] **Step 4: Replace search call and remove parseLong helper if unused**

Use:

```java
List<RagCandidate> candidates = ragVectorSearchService.search(question, topK * 3, user);
```

Build `docIds`, `resultsByDoc`, `chunkIds`, `scoreMap`, and `metaMap` from `RagCandidate` instead of `SearchResult`.

- [ ] **Step 5: Run test and verify pass**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -Dtest=RagQaToolTest,RagVectorSearchServiceImplTest
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/agent/mcp/tool/RagQaTool.java \
  enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/agent/mcp/tool/RagQaToolTest.java
git commit -m "让 RAG 工具使用权限过滤检索服务"
```

## Task 7: Database Model for Sync Tasks

**Files:**

- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/domain/VectorPermissionSyncStatus.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/domain/VectorPermissionSyncTriggerType.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/entity/KbVectorPermissionSyncTask.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/mapper/KbVectorPermissionSyncTaskMapper.java`
- Create: `enterprise-knowledge-ai-service/src/main/resources/mapper/KbVectorPermissionSyncTaskMapper.xml`
- Modify: `resouces/enterprise_knowledge_workspace.sql`

- [ ] **Step 1: Create status enum**

Create `VectorPermissionSyncStatus.java`:

```java
package com.zjl.knowledge.domain;

/**
 * 向量权限 metadata 同步任务状态
 */
public enum VectorPermissionSyncStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    DEAD
}
```

- [ ] **Step 2: Create trigger type enum**

Create `VectorPermissionSyncTriggerType.java`:

```java
package com.zjl.knowledge.domain;

/**
 * 向量权限 metadata 同步任务触发类型
 */
public enum VectorPermissionSyncTriggerType {
    DOCUMENT_PERMISSION_CHANGED,
    DOCUMENT_ENABLED_CHANGED,
    CHUNK_ENABLED_CHANGED,
    MANUAL_RETRY,
    REBUILD
}
```

- [ ] **Step 3: Create entity**

Create `KbVectorPermissionSyncTask.java`:

```java
package com.zjl.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * chunk 级向量权限 metadata 同步任务
 */
@Data
@TableName("kb_vector_permission_sync_task")
public class KbVectorPermissionSyncTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long kbId;
    private Long documentId;
    private Long chunkId;
    private String collectionName;
    private String status;
    private Integer retryCount;
    private Integer maxRetry;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private String skipReason;
    private String triggerType;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    @TableLogic
    private Integer deleted;
}
```

- [ ] **Step 4: Create mapper**

Create `KbVectorPermissionSyncTaskMapper.java`:

```java
package com.zjl.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.knowledge.entity.KbVectorPermissionSyncTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 向量权限 metadata 同步任务 Mapper
 */
@Mapper
public interface KbVectorPermissionSyncTaskMapper extends BaseMapper<KbVectorPermissionSyncTask> {
}
```

Create XML:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zjl.knowledge.mapper.KbVectorPermissionSyncTaskMapper">
</mapper>
```

- [ ] **Step 5: Add table DDL**

Modify `resouces/enterprise_knowledge_workspace.sql` and add:

```sql
CREATE TABLE IF NOT EXISTS kb_vector_permission_sync_task (
    id BIGINT PRIMARY KEY,
    kb_id BIGINT NULL,
    document_id BIGINT NOT NULL,
    chunk_id BIGINT NOT NULL,
    collection_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry INT NOT NULL DEFAULT 4,
    next_retry_at DATETIME NULL,
    last_error TEXT NULL,
    skip_reason VARCHAR(255) NULL,
    trigger_type VARCHAR(64) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_vpst_status_retry (status, next_retry_at),
    INDEX idx_vpst_doc_status (document_id, status),
    INDEX idx_vpst_kb_status_updated (kb_id, status, updated_at),
    INDEX idx_vpst_chunk (chunk_id)
);
```

- [ ] **Step 6: Compile**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -DskipTests
```

Expected: compile succeeds.

- [ ] **Step 7: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/domain/VectorPermissionSyncStatus.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/domain/VectorPermissionSyncTriggerType.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/entity/KbVectorPermissionSyncTask.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/mapper/KbVectorPermissionSyncTaskMapper.java \
  enterprise-knowledge-ai-service/src/main/resources/mapper/KbVectorPermissionSyncTaskMapper.xml \
  resouces/enterprise_knowledge_workspace.sql
git commit -m "新增向量权限同步任务模型"
```

## Task 8: Sync Task Service State Machine

**Files:**

- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/VectorPermissionSyncTaskService.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorPermissionSyncTaskServiceImpl.java`
- Test: `enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/service/impl/VectorPermissionSyncTaskServiceImplTest.java`

- [ ] **Step 1: Write task service tests**

Create `VectorPermissionSyncTaskServiceImplTest.java` with Mockito for mapper and verify:

```java
package com.zjl.knowledge.service.impl;

import com.zjl.knowledge.domain.VectorPermissionSyncStatus;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.entity.KbVectorPermissionSyncTask;
import com.zjl.knowledge.mapper.KbVectorPermissionSyncTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VectorPermissionSyncTaskServiceImplTest {

    @Mock
    private KbVectorPermissionSyncTaskMapper taskMapper;

    @Test
    void markFailedMovesToDeadWhenRetryCountExceedsMaxRetry() {
        VectorPermissionSyncTaskServiceImpl service = new VectorPermissionSyncTaskServiceImpl(taskMapper);
        KbVectorPermissionSyncTask task = new KbVectorPermissionSyncTask();
        task.setId(1L);
        task.setRetryCount(3);
        task.setMaxRetry(4);

        service.markFailed(task, "milvus down", LocalDateTime.of(2026, 5, 29, 10, 0));

        ArgumentCaptor<KbVectorPermissionSyncTask> captor = ArgumentCaptor.forClass(KbVectorPermissionSyncTask.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(VectorPermissionSyncStatus.DEAD.name());
        assertThat(captor.getValue().getRetryCount()).isEqualTo(4);
        assertThat(captor.getValue().getLastError()).isEqualTo("milvus down");
    }
}
```

- [ ] **Step 2: Run and verify failure**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -Dtest=VectorPermissionSyncTaskServiceImplTest
```

Expected: compilation fails because service does not exist.

- [ ] **Step 3: Create service interface**

Create `VectorPermissionSyncTaskService.java`:

```java
package com.zjl.knowledge.service;

import com.zjl.knowledge.domain.VectorPermissionSyncTriggerType;

/**
 * 向量权限 metadata 同步任务服务
 */
public interface VectorPermissionSyncTaskService {

    void createTasksForDocument(Long documentId, Long operatorUserId,
                                VectorPermissionSyncTriggerType triggerType);

    void retryTask(Long taskId, Long operatorUserId);

    void retryDocument(Long documentId, Long operatorUserId);
}
```

- [ ] **Step 4: Implement minimal state method and constructor**

Create `VectorPermissionSyncTaskServiceImpl.java`:

```java
package com.zjl.knowledge.service.impl;

import com.zjl.knowledge.domain.VectorPermissionSyncStatus;
import com.zjl.knowledge.domain.VectorPermissionSyncTriggerType;
import com.zjl.knowledge.entity.KbVectorPermissionSyncTask;
import com.zjl.knowledge.mapper.KbVectorPermissionSyncTaskMapper;
import com.zjl.knowledge.service.VectorPermissionSyncTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 向量权限 metadata 同步任务服务实现
 */
@Service
@RequiredArgsConstructor
public class VectorPermissionSyncTaskServiceImpl implements VectorPermissionSyncTaskService {

    private static final List<Integer> RETRY_MINUTES = List.of(1, 5, 15, 60);

    private final KbVectorPermissionSyncTaskMapper taskMapper;

    @Override
    public void createTasksForDocument(Long documentId, Long operatorUserId,
                                       VectorPermissionSyncTriggerType triggerType) {
        KbVectorPermissionSyncTask task = new KbVectorPermissionSyncTask();
        task.setDocumentId(documentId);
        task.setStatus(VectorPermissionSyncStatus.PENDING.name());
        task.setRetryCount(0);
        task.setMaxRetry(4);
        task.setNextRetryAt(LocalDateTime.now());
        task.setTriggerType(triggerType.name());
        task.setCreatedBy(operatorUserId);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setDeleted(0);
        taskMapper.insert(task);
    }

    @Override
    public void retryTask(Long taskId, Long operatorUserId) {
        KbVectorPermissionSyncTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(VectorPermissionSyncStatus.PENDING.name());
        task.setRetryCount(0);
        task.setNextRetryAt(LocalDateTime.now());
        task.setLastError(null);
        task.setSkipReason(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Override
    public void retryDocument(Long documentId, Long operatorUserId) {
        List<KbVectorPermissionSyncTask> tasks = taskMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(KbVectorPermissionSyncTask.class)
                        .eq(KbVectorPermissionSyncTask::getDocumentId, documentId)
                        .in(KbVectorPermissionSyncTask::getStatus,
                                VectorPermissionSyncStatus.FAILED.name(),
                                VectorPermissionSyncStatus.DEAD.name())
        );
        for (KbVectorPermissionSyncTask task : tasks) {
            retryTask(task.getId(), operatorUserId);
        }
    }

    public void markFailed(KbVectorPermissionSyncTask task, String error, LocalDateTime now) {
        int nextRetryCount = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
        int maxRetry = task.getMaxRetry() == null ? 4 : task.getMaxRetry();
        task.setRetryCount(nextRetryCount);
        task.setLastError(error);
        task.setUpdatedAt(now);
        task.setFinishedAt(now);
        if (nextRetryCount >= maxRetry) {
            task.setStatus(VectorPermissionSyncStatus.DEAD.name());
            task.setNextRetryAt(null);
        } else {
            task.setStatus(VectorPermissionSyncStatus.FAILED.name());
            int delayIndex = Math.min(nextRetryCount - 1, RETRY_MINUTES.size() - 1);
            task.setNextRetryAt(now.plusMinutes(RETRY_MINUTES.get(delayIndex)));
        }
        taskMapper.updateById(task);
    }
}
```

- [ ] **Step 5: Run test and verify pass**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -Dtest=VectorPermissionSyncTaskServiceImplTest
```

Expected: 1 test passes.

- [ ] **Step 6: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/VectorPermissionSyncTaskService.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorPermissionSyncTaskServiceImpl.java \
  enterprise-knowledge-ai-service/src/test/java/com/zjl/knowledge/service/impl/VectorPermissionSyncTaskServiceImplTest.java
git commit -m "新增向量权限同步任务状态机"
```

## Task 9: Create Tasks After Permission Changes

**Files:**

- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/event/DocumentPermissionChangedEvent.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/event/DocumentPermissionChangedListener.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/KbDocumentServiceImpl.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorPermissionSyncTaskServiceImpl.java`

- [ ] **Step 1: Create event**

```java
package com.zjl.knowledge.event;

import com.zjl.knowledge.domain.VectorPermissionSyncTriggerType;

/**
 * 文档权限或可检索状态变更事件
 */
public record DocumentPermissionChangedEvent(
        Long documentId,
        Long operatorUserId,
        VectorPermissionSyncTriggerType triggerType
) {
}
```

- [ ] **Step 2: Create listener**

```java
package com.zjl.knowledge.event;

import com.zjl.knowledge.service.VectorPermissionSyncTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文档权限变更后创建向量权限 metadata 同步任务
 */
@Component
@RequiredArgsConstructor
public class DocumentPermissionChangedListener {

    private final VectorPermissionSyncTaskService taskService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPermissionChanged(DocumentPermissionChangedEvent event) {
        taskService.createTasksForDocument(event.documentId(), event.operatorUserId(), event.triggerType());
    }
}
```

- [ ] **Step 3: Publish event after permission-changing operations**

Inject `ApplicationEventPublisher` into `KbDocumentServiceImpl`:

```java
private final ApplicationEventPublisher eventPublisher;
```

After document permission or enabled state changes, publish:

```java
eventPublisher.publishEvent(new DocumentPermissionChangedEvent(
        documentId,
        user.getUserId(),
        VectorPermissionSyncTriggerType.DOCUMENT_ENABLED_CHANGED
));
```

For permission update endpoints, use `DOCUMENT_PERMISSION_CHANGED`.

- [ ] **Step 4: Implement createTasksForDocument**

In `VectorPermissionSyncTaskServiceImpl`, inject document, chunk, and routing dependencies:

```java
private final KbDocumentMapper kbDocumentMapper;
private final KbDocumentChunkMapper kbDocumentChunkMapper;
private final KbMilvusRoutingService kbMilvusRoutingService;
```

Implement by querying chunks and inserting or resetting one task per chunk:

```java
KbDocument document = kbDocumentMapper.selectById(documentId);
if (document == null) {
    return;
}
String collection = kbMilvusRoutingService.collectionForVectorWriteOrDefault(document);
List<KbDocumentChunk> chunks = kbDocumentChunkMapper.selectList(
        Wrappers.lambdaQuery(KbDocumentChunk.class).eq(KbDocumentChunk::getDocumentId, documentId)
);
for (KbDocumentChunk chunk : chunks) {
    KbVectorPermissionSyncTask task = new KbVectorPermissionSyncTask();
    task.setKbId(document.getKbId());
    task.setDocumentId(documentId);
    task.setChunkId(chunk.getId());
    task.setCollectionName(collection);
    task.setStatus(VectorPermissionSyncStatus.PENDING.name());
    task.setRetryCount(0);
    task.setMaxRetry(4);
    task.setNextRetryAt(LocalDateTime.now());
    task.setTriggerType(triggerType.name());
    task.setCreatedBy(operatorUserId);
    task.setCreatedAt(LocalDateTime.now());
    task.setUpdatedAt(LocalDateTime.now());
    task.setDeleted(0);
    taskMapper.insert(task);
}
```

Before inserting, query existing active tasks by `chunk_id` and status. If a task is `PENDING` or `RUNNING`, skip insertion. If a task is `FAILED` or `DEAD`, reset that existing row to `PENDING`, set `retry_count=0`, clear `last_error` and `skip_reason`, and set `next_retry_at=now`.

- [ ] **Step 5: Compile**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -DskipTests
```

Expected: compile succeeds.

- [ ] **Step 6: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/event/DocumentPermissionChangedEvent.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/event/DocumentPermissionChangedListener.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/KbDocumentServiceImpl.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorPermissionSyncTaskServiceImpl.java
git commit -m "权限变更后创建向量权限同步任务"
```

## Task 10: Worker Execution and Milvus Metadata Upsert

**Files:**

- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorPermissionSyncTaskServiceImpl.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/ChunkVectorStore.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/MilvusChunkVectorStore.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/MilvusVectorWriter.java`

- [ ] **Step 1: Add vector store metadata update method**

In `ChunkVectorStore.java` add:

```java
void updateChunkMetadata(String collectionName, Long documentId, VectorDocChunk chunk);
```

In `MilvusChunkVectorStore.java` implement by delegating to writer:

```java
@Override
public void updateChunkMetadata(String collectionName, Long documentId, VectorDocChunk chunk) {
    milvusVectorWriter.upsertChunk(collectionName, String.valueOf(documentId), chunk);
}
```

- [ ] **Step 2: Add worker method**

In `VectorPermissionSyncTaskServiceImpl` add:

```java
public int runDueTasks(int limit) {
    List<KbVectorPermissionSyncTask> tasks = taskMapper.selectList(
            Wrappers.lambdaQuery(KbVectorPermissionSyncTask.class)
                    .eq(KbVectorPermissionSyncTask::getStatus, VectorPermissionSyncStatus.PENDING.name())
                    .le(KbVectorPermissionSyncTask::getNextRetryAt, LocalDateTime.now())
                    .last("LIMIT " + Math.max(1, limit))
    );
    int count = 0;
    for (KbVectorPermissionSyncTask task : tasks) {
        processTask(task);
        count++;
    }
    return count;
}
```

- [ ] **Step 3: Implement processTask**

Use the existing metadata service and vector store:

```java
private void processTask(KbVectorPermissionSyncTask task) {
    LocalDateTime now = LocalDateTime.now();
    task.setStatus(VectorPermissionSyncStatus.RUNNING.name());
    task.setStartedAt(now);
    task.setUpdatedAt(now);
    taskMapper.updateById(task);
    try {
        KbDocument document = kbDocumentMapper.selectById(task.getDocumentId());
        KbDocumentChunk chunk = kbDocumentChunkMapper.selectById(task.getChunkId());
        if (document == null || chunk == null) {
            task.setStatus(VectorPermissionSyncStatus.SUCCESS.name());
            task.setSkipReason("DOCUMENT_OR_CHUNK_NOT_FOUND");
            task.setFinishedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            return;
        }
        List<KbDocumentPermission> permissions = permissionMapper.selectList(
                Wrappers.lambdaQuery(KbDocumentPermission.class)
                        .eq(KbDocumentPermission::getDocumentId, document.getId())
        );
        Map<String, Object> metadata = ragPermissionMetadataService.buildMetadata(document, chunk, permissions);
        VectorDocChunk vectorChunk = VectorDocChunk.builder()
                .chunkId(String.valueOf(chunk.getId()))
                .content(chunk.getChunkText())
                .index(chunk.getChunkIndex())
                .metadata(metadata)
                .embedding(toArray(vectorSyncService.embed(chunk.getChunkText(), document)))
                .build();
        chunkVectorStore.updateChunkMetadata(task.getCollectionName(), document.getId(), vectorChunk);
        task.setStatus(VectorPermissionSyncStatus.SUCCESS.name());
        task.setFinishedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    } catch (Exception ex) {
        markFailed(task, ex.getMessage(), LocalDateTime.now());
    }
}
```

Add the same `toArray(List<Float>)` helper used in `VectorSyncServiceImpl`.

- [ ] **Step 4: Compile**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -DskipTests
```

Expected: compile succeeds.

- [ ] **Step 5: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorPermissionSyncTaskServiceImpl.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/ChunkVectorStore.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/MilvusChunkVectorStore.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/milvus/MilvusVectorWriter.java
git commit -m "实现向量权限 metadata 同步 worker"
```

## Task 11: Admin DTOs and Controller

**Files:**

- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/vector/VectorPermissionSyncOverviewVO.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/vector/VectorPermissionSyncTaskVO.java`
- Create: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/web/VectorPermissionAdminController.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/VectorPermissionSyncTaskService.java`
- Modify: `enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorPermissionSyncTaskServiceImpl.java`

- [ ] **Step 1: Create overview DTO**

```java
package com.zjl.knowledge.dto.vector;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 向量权限 metadata 同步总览
 */
@Data
public class VectorPermissionSyncOverviewVO {
    private long pendingCount;
    private long runningCount;
    private long failedCount;
    private long deadCount;
    private double successRate;
    private LocalDateTime lastSyncedAt;
}
```

- [ ] **Step 2: Create task DTO**

```java
package com.zjl.knowledge.dto.vector;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 向量权限 metadata 同步任务视图
 */
@Data
public class VectorPermissionSyncTaskVO {
    private Long id;
    private Long kbId;
    private Long documentId;
    private Long chunkId;
    private String collectionName;
    private String status;
    private Integer retryCount;
    private Integer maxRetry;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private String skipReason;
    private String triggerType;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: Extend service interface**

Add:

```java
VectorPermissionSyncOverviewVO overview(UserContext user);

PageResult<VectorPermissionSyncTaskVO> pageTasks(UserContext user, Long kbId, Long documentId,
                                                 String status, String collectionName,
                                                 long current, long size);
```

Use `com.zjl.common.response.PageResult`.

- [ ] **Step 4: Create controller**

```java
package com.zjl.knowledge.web;

import com.zjl.common.response.PageResult;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.knowledge.dto.vector.VectorPermissionSyncOverviewVO;
import com.zjl.knowledge.dto.vector.VectorPermissionSyncTaskVO;
import com.zjl.knowledge.service.VectorPermissionSyncTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 向量权限 metadata 同步管理接口
 */
@RestController
@RequestMapping("/api/kb/admin/vector-permission-sync")
@RequiredArgsConstructor
public class VectorPermissionAdminController {

    private final VectorPermissionSyncTaskService taskService;

    @GetMapping("/overview")
    public Result<VectorPermissionSyncOverviewVO> overview() {
        return Results.success(taskService.overview(UserContextHolder.get()));
    }

    @GetMapping("/tasks")
    public Result<PageResult<VectorPermissionSyncTaskVO>> pageTasks(
            @RequestParam(required = false) Long kbId,
            @RequestParam(required = false) Long documentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String collectionName,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return Results.success(taskService.pageTasks(
                UserContextHolder.get(), kbId, documentId, status, collectionName, current, size));
    }

    @PostMapping("/tasks/{taskId}/retry")
    public Result<Void> retryTask(@PathVariable Long taskId) {
        taskService.retryTask(taskId, UserContextHolder.get().getUserId());
        return Results.success();
    }

    @PostMapping("/documents/{documentId}/retry")
    public Result<Void> retryDocument(@PathVariable Long documentId) {
        taskService.retryDocument(documentId, UserContextHolder.get().getUserId());
        return Results.success();
    }
}
```

Use the imports shown above: `com.zjl.common.response.PageResult`, `com.zjl.common.response.Result`, and `com.zjl.common.response.Results`.

- [ ] **Step 5: Compile**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -DskipTests
```

Expected: compile succeeds.

- [ ] **Step 6: Commit**

```bash
git add enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/vector/VectorPermissionSyncOverviewVO.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/dto/vector/VectorPermissionSyncTaskVO.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/web/VectorPermissionAdminController.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/VectorPermissionSyncTaskService.java \
  enterprise-knowledge-ai-service/src/main/java/com/zjl/knowledge/service/impl/VectorPermissionSyncTaskServiceImpl.java
git commit -m "新增向量权限同步管理接口"
```

## Task 12: Final Verification

**Files:**

- No new files expected.

- [ ] **Step 1: Run focused tests**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -Dtest=RagPermissionMetadataServiceImplTest,RagPermissionFilterBuilderTest,RagVectorSearchServiceImplTest,RagQaToolTest,VectorPermissionSyncTaskServiceImplTest
```

Expected: all focused tests pass.

- [ ] **Step 2: Run module compile**

Run:

```bash
/Users/zjl/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -pl enterprise-knowledge-ai-service -DskipTests
```

Expected: compile succeeds.

- [ ] **Step 3: Review git diff**

Run:

```bash
git diff --stat
git status --short
```

Expected: only intended files changed; unrelated existing dirty files remain unstaged.

- [ ] **Step 4: Final commit if needed**

For the plan document commit:

```bash
git add docs/superpowers/plans/2026-05-29-rag-permission-filter.md
git commit -m "补充 RAG 权限下推实施计划"
```

## Self-Review

Spec coverage:

1. Metadata fields are covered by Tasks 1 and 5.
2. Milvus filter is covered by Tasks 2, 3, and 4.
3. DB final permission check remains in `RagQaTool` and is covered by Task 6.
4. Sync task table is covered by Task 7.
5. State machine and retry are covered by Tasks 8 and 10.
6. Permission change task creation is covered by Task 9.
7. Admin APIs are covered by Task 11.
8. Tests are assigned across Tasks 1, 2, 4, 8, 11, and 12.

Known implementation cautions:

1. Use `com.zjl.common.response.Result`, `com.zjl.common.response.Results`, and `com.zjl.common.response.PageResult`.
2. Edit the schema file at `resouces/enterprise_knowledge_workspace.sql`.
3. Validate Milvus JSON array filter syntax with the project’s Milvus version before relying on `in metadata["project_ids"]` in production.
