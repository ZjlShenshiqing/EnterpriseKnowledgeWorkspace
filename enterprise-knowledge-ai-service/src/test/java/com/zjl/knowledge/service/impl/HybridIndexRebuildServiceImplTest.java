package com.zjl.knowledge.service.impl;

import com.zjl.common.exception.BizException;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.dto.kb.HybridIndexRebuildResult;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.service.VectorSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hybrid 索引重建服务测试。
 */
@ExtendWith(MockitoExtension.class)
class HybridIndexRebuildServiceImplTest {

    @Mock
    private KbDocumentMapper kbDocumentMapper;

    @Mock
    private KbDocumentChunkMapper kbDocumentChunkMapper;

    @Mock
    private VectorSyncService vectorSyncService;

    private HybridIndexRebuildServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HybridIndexRebuildServiceImpl(kbDocumentMapper, kbDocumentChunkMapper, vectorSyncService);
    }

    @Test
    void rebuildDocumentThrowsWhenDocumentNotFound() {
        when(kbDocumentMapper.selectById(1001L)).thenReturn(null);

        assertThatThrownBy(() -> service.rebuildDocument(1001L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("文档不存在");
    }

    @Test
    void rebuildDocumentSkipsNonSuccessDocument() {
        KbDocument document = document(1001L, DocumentStatus.FAILED.name());
        when(kbDocumentMapper.selectById(1001L)).thenReturn(document);

        HybridIndexRebuildResult result = service.rebuildDocument(1001L);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getSkippedDocumentIds()).containsExactly(1001L);
        verify(vectorSyncService, never()).rebuildHybridChunks(any(), anyList());
    }

    @Test
    void rebuildSuccessDocumentsContinuesAfterFailure() {
        KbDocument failedDocument = document(1001L, DocumentStatus.SUCCESS.name());
        KbDocument successDocument = document(1002L, DocumentStatus.SUCCESS.name());
        KbDocument skippedDocument = document(1003L, DocumentStatus.SUCCESS.name());
        when(kbDocumentMapper.selectList(any())).thenReturn(List.of(failedDocument, successDocument, skippedDocument));
        when(vectorSyncService.shouldEmbed(failedDocument)).thenReturn(true);
        when(vectorSyncService.shouldEmbed(successDocument)).thenReturn(true);
        when(vectorSyncService.shouldEmbed(skippedDocument)).thenReturn(false);
        when(kbDocumentChunkMapper.selectList(any()))
                .thenReturn(List.of(chunk(2001L, "制度编号 OA-001")))
                .thenReturn(List.of(chunk(2002L, "差旅报销材料")))
                .thenReturn(List.of(chunk(2003L, "不会被写入")));
        doThrow(new RuntimeException("milvus write failed"))
                .doNothing()
                .when(vectorSyncService).rebuildHybridChunks(any(), anyList());

        HybridIndexRebuildResult result = service.rebuildSuccessDocuments(10);

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getChunkCount()).isEqualTo(1);
        assertThat(result.getFailedDocumentIds()).containsExactly(1001L);
        assertThat(result.getSkippedDocumentIds()).containsExactly(1003L);
    }

    private KbDocument document(Long id, String status) {
        KbDocument document = new KbDocument();
        document.setId(id);
        document.setStatus(status);
        return document;
    }

    private KbDocumentChunk chunk(Long id, String text) {
        KbDocumentChunk chunk = new KbDocumentChunk();
        chunk.setId(id);
        chunk.setDocumentId(1001L);
        chunk.setChunkIndex(0);
        chunk.setChunkText(text);
        return chunk;
    }
}
