package com.zjl.knowledge.agent.mcp.tool;

import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.service.retrieval.RagRetrievalService;
import com.zjl.knowledge.service.retrieval.RetrievalResult;
import com.zjl.knowledge.web.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * RAG 问答 Tool 测试
 */
@ExtendWith(MockitoExtension.class)
class RagQaToolTest {

    @Mock
    private RagRetrievalService ragRetrievalService;

    private RagQaTool ragQaTool;

    @BeforeEach
    void setUp() {
        ragQaTool = new RagQaTool(ragRetrievalService);
    }

    @Test
    void executeExcludesDocumentWhenUserPermissionTargetsSomeoneElse() {
        UserContext user = UserContext.builder()
                .userId(1001L)
                .departmentId(10L)
                .projectId(20L)
                .admin(false)
                .build();

        when(ragRetrievalService.retrieve("question", 5, user))
                .thenReturn(new RetrievalResult(List.of()));

        ToolResult result = ragQaTool.execute(Map.of("question", "question"), user);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isInstanceOf(Map.class);
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertThat(data.get("documents")).isEqualTo(List.of());
    }

    @Test
    void executeReturnsDocumentsWhenFound() {
        UserContext user = UserContext.builder()
                .userId(1001L)
                .departmentId(10L)
                .projectId(20L)
                .admin(false)
                .build();

        RetrievalResult.DocumentResult doc = new RetrievalResult.DocumentResult(
                2001L, "Test Doc", "summary", "pdf", "test.pdf",
                1024L, null, Map.of(),
                List.of(new RetrievalResult.ChunkResult(0, "content", 0.9f, Map.of()))
        );
        when(ragRetrievalService.retrieve("question", 5, user))
                .thenReturn(new RetrievalResult(List.of(doc)));

        ToolResult result = ragQaTool.execute(Map.of("question", "question"), user);

        assertThat(result.isSuccess()).isTrue();
        Map<?, ?> data = (Map<?, ?>) result.getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> docs = (List<Map<String, Object>>) data.get("documents");
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).get("documentId")).isEqualTo(2001L);
        assertThat(docs.get(0).get("title")).isEqualTo("Test Doc");
    }
}
