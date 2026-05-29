package com.zjl.knowledge.agent.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.entity.KbDocumentPermission;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.mapper.KbDocumentPermissionMapper;
import com.zjl.knowledge.milvus.SearchResult;
import com.zjl.knowledge.service.DocumentVisibilityService;
import com.zjl.knowledge.service.VectorSyncService;
import com.zjl.knowledge.service.impl.DocumentVisibilityServiceImpl;
import com.zjl.knowledge.web.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RAG 问答 Tool 权限测试
 */
@ExtendWith(MockitoExtension.class)
class RagQaToolTest {

    @Mock
    private VectorSyncService vectorSyncService;

    @Mock
    private KbDocumentMapper kbDocumentMapper;

    @Mock
    private KbDocumentChunkMapper kbDocumentChunkMapper;

    @Mock
    private KbDocumentPermissionMapper kbDocumentPermissionMapper;

    private final DocumentVisibilityService documentVisibilityService = new DocumentVisibilityServiceImpl();

    private RagQaTool ragQaTool;

    @BeforeEach
    void setUp() {
        ragQaTool = new RagQaTool(
                vectorSyncService,
                kbDocumentMapper,
                kbDocumentChunkMapper,
                kbDocumentPermissionMapper,
                documentVisibilityService
        );
    }

    @Test
    void executeExcludesDocumentWhenUserPermissionTargetsSomeoneElse() {
        UserContext user = UserContext.builder()
                .userId(1001L)
                .departmentId(10L)
                .projectId(20L)
                .admin(false)
                .build();

        KbDocument doc = new KbDocument();
        doc.setId(2001L);
        doc.setOwnerId(9999L);
        doc.setPermissionType("USER");
        doc.setDeleted(0);
        doc.setEnabled(1);
        doc.setStatus(DocumentStatus.SUCCESS.name());
        doc.setTitle("restricted");

        KbDocumentPermission permission = new KbDocumentPermission();
        permission.setDocumentId(2001L);
        permission.setPermissionTargetType("USER");
        permission.setPermissionTargetId(3001L);

        when(vectorSyncService.searchSimilar("question", 15, new KbDocument()))
                .thenReturn(List.of(new SearchResult("9001", "2001", 0.91f, Map.of("doc_id", "2001"))));
        when(kbDocumentMapper.selectBatchIds(List.of(2001L))).thenReturn(List.of(doc));
        when(kbDocumentPermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(permission));

        ToolResult result = ragQaTool.execute(Map.of("question", "question"), user);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isInstanceOf(Map.class);
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertThat(data.get("documents")).isEqualTo(List.of());
    }

    @Test
    void executeExcludesDocumentWhenDocumentIsNotSearchable() {
        UserContext user = defaultUser();
        KbDocument doc = visibleDocument(2001L);
        doc.setStatus(DocumentStatus.RUNNING.name());
        doc.setEnabled(1);

        when(vectorSyncService.searchSimilar("question", 15, new KbDocument()))
                .thenReturn(List.of(new SearchResult("9001", "2001", 0.91f, Map.of("doc_id", "2001"))));
        when(kbDocumentMapper.selectBatchIds(List.of(2001L))).thenReturn(List.of(doc));

        ToolResult result = ragQaTool.execute(Map.of("question", "question"), user);

        assertThat(documents(result)).isEqualTo(List.of());
    }

    @Test
    void executeExcludesDisabledChunksFromMatchedChunks() {
        UserContext user = defaultUser();
        KbDocument doc = visibleDocument(2001L);
        KbDocumentChunk disabledChunk = chunk(9001L, 0);
        KbDocumentChunk enabledChunk = chunk(9002L, 1);

        when(vectorSyncService.searchSimilar("question", 15, new KbDocument()))
                .thenReturn(List.of(
                        new SearchResult("9001", "2001", 0.91f, Map.of("doc_id", "2001")),
                        new SearchResult("9002", "2001", 0.89f, Map.of("doc_id", "2001"))
                ));
        when(kbDocumentMapper.selectBatchIds(List.of(2001L))).thenReturn(List.of(doc));
        when(kbDocumentPermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(kbDocumentChunkMapper.selectBatchIds(List.of(9001L, 9002L)))
                .thenReturn(List.of(disabledChunk, enabledChunk));

        ToolResult result = ragQaTool.execute(Map.of("question", "question"), user);

        List<?> documents = documents(result);
        assertThat(documents).hasSize(1);
        Map<?, ?> document = (Map<?, ?>) documents.get(0);
        assertThat(document.get("matchedChunks")).isEqualTo(List.of(Map.of(
                "chunkIndex", 2,
                "text", "chunk-9002",
                "score", 0.89f,
                "metadata", Map.of("doc_id", "2001")
        )));
    }

    @Test
    void executePreservesDocumentOrderFromVectorSearchResults() {
        UserContext user = defaultUser();
        KbDocument firstDoc = visibleDocument(3001L);
        firstDoc.setTitle("first");
        KbDocument secondDoc = visibleDocument(2001L);
        secondDoc.setTitle("second");

        when(vectorSyncService.searchSimilar("question", 15, new KbDocument()))
                .thenReturn(List.of(
                        new SearchResult("9301", "3001", 0.96f, Map.of("doc_id", "3001")),
                        new SearchResult("9201", "2001", 0.82f, Map.of("doc_id", "2001"))
                ));
        when(kbDocumentMapper.selectBatchIds(List.of(3001L, 2001L))).thenReturn(List.of(secondDoc, firstDoc));
        when(kbDocumentPermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(kbDocumentChunkMapper.selectBatchIds(List.of(9301L))).thenReturn(List.of(chunk(9301L, 1, 1)));
        when(kbDocumentChunkMapper.selectBatchIds(List.of(9201L))).thenReturn(List.of(chunk(9201L, 1, 1)));

        ToolResult result = ragQaTool.execute(Map.of("question", "question"), user);

        List<?> documents = documents(result);
        assertThat(documents).hasSize(2);
        assertThat(((Map<?, ?>) documents.get(0)).get("documentId")).isEqualTo(3001L);
        assertThat(((Map<?, ?>) documents.get(1)).get("documentId")).isEqualTo(2001L);
    }

    @Test
    void executeSkipsInvalidVectorSearchIds() {
        UserContext user = defaultUser();
        KbDocument doc = visibleDocument(2001L);

        when(vectorSyncService.searchSimilar("question", 15, new KbDocument()))
                .thenReturn(List.of(
                        new SearchResult("bad-chunk", "bad-doc", 0.99f, Map.of()),
                        new SearchResult("9201", "2001", 0.82f, Map.of("doc_id", "2001"))
                ));
        when(kbDocumentMapper.selectBatchIds(List.of(2001L))).thenReturn(List.of(doc));
        when(kbDocumentPermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(kbDocumentChunkMapper.selectBatchIds(List.of(9201L))).thenReturn(List.of(chunk(9201L, 1, 1)));

        ToolResult result = ragQaTool.execute(Map.of("question", "question"), user);

        List<?> documents = documents(result);
        assertThat(documents).hasSize(1);
        assertThat(((Map<?, ?>) documents.get(0)).get("documentId")).isEqualTo(2001L);
    }

    private static UserContext defaultUser() {
        return UserContext.builder()
                .userId(1001L)
                .departmentId(10L)
                .projectId(20L)
                .admin(false)
                .build();
    }

    private static KbDocument visibleDocument(Long id) {
        KbDocument doc = new KbDocument();
        doc.setId(id);
        doc.setOwnerId(1001L);
        doc.setPermissionType("USER");
        doc.setDeleted(0);
        doc.setEnabled(1);
        doc.setStatus(DocumentStatus.SUCCESS.name());
        doc.setTitle("visible");
        return doc;
    }

    private static KbDocumentChunk chunk(Long id, int enabled) {
        return chunk(id, id.equals(9001L) ? 1 : 2, enabled);
    }

    private static KbDocumentChunk chunk(Long id, int chunkIndex, int enabled) {
        KbDocumentChunk chunk = new KbDocumentChunk();
        chunk.setId(id);
        chunk.setChunkIndex(chunkIndex);
        chunk.setChunkText("chunk-" + id);
        chunk.setEnabled(enabled);
        return chunk;
    }

    private static List<?> documents(ToolResult result) {
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isInstanceOf(Map.class);
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertThat(data.get("documents")).isInstanceOf(List.class);
        return (List<?>) data.get("documents");
    }
}
