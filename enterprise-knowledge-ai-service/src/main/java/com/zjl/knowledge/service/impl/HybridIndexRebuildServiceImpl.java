package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.dto.kb.HybridIndexRebuildResult;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.service.HybridIndexRebuildService;
import com.zjl.knowledge.service.VectorSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Hybrid 索引重建服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridIndexRebuildServiceImpl implements HybridIndexRebuildService {

    private static final int DEFAULT_LIMIT = 100;

    private static final int MAX_LIMIT = 1000;

    private final KbDocumentMapper kbDocumentMapper;

    private final KbDocumentChunkMapper kbDocumentChunkMapper;

    private final VectorSyncService vectorSyncService;

    @Override
    public HybridIndexRebuildResult rebuildDocument(Long documentId) {
        KbDocument document = kbDocumentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        ResultAccumulator acc = new ResultAccumulator();
        rebuildOne(document, acc);
        return acc.toResult();
    }

    @Override
    public HybridIndexRebuildResult rebuildSuccessDocuments(int limit) {
        int safeLimit = normalizeLimit(limit);
        List<KbDocument> documents = kbDocumentMapper.selectList(
                Wrappers.lambdaQuery(KbDocument.class)
                        .eq(KbDocument::getStatus, DocumentStatus.SUCCESS.name())
                        .orderByAsc(KbDocument::getId)
                        .last("LIMIT " + safeLimit));
        ResultAccumulator acc = new ResultAccumulator();
        for (KbDocument document : documents) {
            rebuildOne(document, acc);
        }
        return acc.toResult();
    }

    private void rebuildOne(KbDocument document, ResultAccumulator acc) {
        acc.total++;
        if (!DocumentStatus.SUCCESS.name().equals(document.getStatus())) {
            acc.skipped++;
            acc.skippedDocumentIds.add(document.getId());
            return;
        }
        if (!vectorSyncService.shouldEmbed(document)) {
            acc.skipped++;
            acc.skippedDocumentIds.add(document.getId());
            return;
        }
        List<KbDocumentChunk> chunks = kbDocumentChunkMapper.selectList(
                Wrappers.lambdaQuery(KbDocumentChunk.class)
                        .eq(KbDocumentChunk::getDocumentId, document.getId())
                        .orderByAsc(KbDocumentChunk::getChunkIndex));
        List<KbDocumentChunk> indexableChunks = chunks.stream()
                .filter(chunk -> StringUtils.hasText(chunk.getChunkText()))
                .toList();
        if (indexableChunks.isEmpty()) {
            acc.skipped++;
            acc.skippedDocumentIds.add(document.getId());
            return;
        }
        try {
            vectorSyncService.rebuildHybridChunks(document, indexableChunks);
            acc.success++;
            acc.chunkCount += indexableChunks.size();
            log.info("Hybrid 索引重建成功, docId={}, chunkCount={}", document.getId(), indexableChunks.size());
        } catch (Exception ex) {
            acc.failed++;
            acc.failedDocumentIds.add(document.getId());
            log.warn("Hybrid 索引重建失败, docId={}, error={}", document.getId(), ex.getMessage());
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static class ResultAccumulator {

        private int total;

        private int success;

        private int skipped;

        private int failed;

        private int chunkCount;

        private final List<Long> failedDocumentIds = new ArrayList<>();

        private final List<Long> skippedDocumentIds = new ArrayList<>();

        private HybridIndexRebuildResult toResult() {
            return HybridIndexRebuildResult.builder()
                    .total(total)
                    .success(success)
                    .skipped(skipped)
                    .failed(failed)
                    .chunkCount(chunkCount)
                    .failedDocumentIds(failedDocumentIds)
                    .skippedDocumentIds(skippedDocumentIds)
                    .build();
        }
    }
}
