package com.zjl.knowledge.agent.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.entity.KbDocument;
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
        doc.setStatus("SUCCESS");
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
}
