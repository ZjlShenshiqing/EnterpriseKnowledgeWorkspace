package com.zjl.knowledge.service.retrieval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.knowledge.config.RagRetrievalProperties;
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
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RAG 检索服务实现
 *
 * <p>负责：检索模式选择 → 执行检索 → DB 查询 → 权限终检 → 结果组装</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final VectorSyncService vectorSyncService;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbDocumentChunkMapper kbDocumentChunkMapper;
    private final KbDocumentPermissionMapper kbDocumentPermissionMapper;
    private final DocumentVisibilityService documentVisibilityService;
    private final RagRetrievalProperties retrievalProperties;

    @Override
    public RetrievalResult retrieve(String question, int topK, UserContext user) {
        KbDocument contextDoc = new KbDocument();
        List<SearchResult> searchResults = vectorSyncService.searchSimilar(question, topK * 3, contextDoc);

        if (searchResults.isEmpty()) {
            return new RetrievalResult(List.of());
        }

        List<SearchResult> validResults = searchResults.stream()
                .filter(r -> parseLong(r.docId()).isPresent())
                .filter(r -> parseLong(r.chunkId()).isPresent())
                .collect(Collectors.toList());
        if (validResults.isEmpty()) {
            return new RetrievalResult(List.of());
        }

        List<Long> docIds = validResults.stream()
                .map(r -> parseLong(r.docId()).orElse(null))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, KbDocument> docMap = kbDocumentMapper.selectBatchIds(docIds).stream()
                .filter(this::isNotDeleted)
                .filter(this::isSearchable)
                .filter(d -> isVisible(d, user))
                .collect(Collectors.toMap(KbDocument::getId, d -> d, (l, r) -> l, LinkedHashMap::new));

        Map<Long, List<SearchResult>> resultsByDoc = validResults.stream()
                .filter(r -> docMap.containsKey(parseLong(r.docId()).orElse(null)))
                .collect(Collectors.groupingBy(r -> parseLong(r.docId()).orElseThrow()));

        List<RetrievalResult.DocumentResult> documents = new ArrayList<>();
        int count = 0;
        for (Long docId : docIds) {
            if (count >= topK) break;
            KbDocument doc = docMap.get(docId);
            if (doc == null) continue;
            List<SearchResult> docResults = resultsByDoc.get(doc.getId());
            if (docResults == null || docResults.isEmpty()) continue;

            List<RetrievalResult.ChunkResult> matchedChunks = buildMatchedChunks(doc.getId(), docResults);
            if (matchedChunks.isEmpty()) continue;

            documents.add(new RetrievalResult.DocumentResult(
                    doc.getId(),
                    doc.getTitle(),
                    doc.getSummary(),
                    doc.getFileType(),
                    doc.getFileName(),
                    doc.getFileSize(),
                    doc.getCreatedAt(),
                    parseJson(doc.getMetadata()),
                    matchedChunks
            ));
            count++;
        }
        return new RetrievalResult(documents);
    }

    private List<RetrievalResult.ChunkResult> buildMatchedChunks(Long docId, List<SearchResult> results) {
        List<Long> chunkIds = results.stream()
                .map(r -> parseLong(r.chunkId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<Long, KbDocumentChunk> chunkMap = kbDocumentChunkMapper.selectBatchIds(chunkIds).stream()
                .collect(Collectors.toMap(KbDocumentChunk::getId, c -> c, (l, r) -> l));

        Map<Long, Float> scoreMap = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> metaMap = new LinkedHashMap<>();
        for (SearchResult r : results) {
            Optional<Long> chunkId = parseLong(r.chunkId());
            if (chunkId.isPresent()) {
                scoreMap.put(chunkId.get(), r.score());
                metaMap.put(chunkId.get(), r.metadata());
            }
        }

        return chunkIds.stream()
                .map(chunkMap::get)
                .filter(Objects::nonNull)
                .filter(c -> c.getEnabled() == null || c.getEnabled() == 1)
                .map(c -> new RetrievalResult.ChunkResult(
                        c.getChunkIndex(),
                        c.getChunkText(),
                        scoreMap.getOrDefault(c.getId(), 0f),
                        metaMap.getOrDefault(c.getId(), Map.of())
                ))
                .collect(Collectors.toList());
    }

    private boolean isNotDeleted(KbDocument doc) {
        return doc.getDeleted() == null || doc.getDeleted() == 0;
    }

    private boolean isSearchable(KbDocument doc) {
        boolean enabled = doc.getEnabled() == null || doc.getEnabled() == 1;
        return enabled && DocumentStatus.SUCCESS.name().equals(doc.getStatus());
    }

    private boolean isVisible(KbDocument doc, UserContext user) {
        List<KbDocumentPermission> permissions = kbDocumentPermissionMapper.selectList(
                new LambdaQueryWrapper<KbDocumentPermission>()
                        .eq(KbDocumentPermission::getDocumentId, doc.getId())
        );
        return documentVisibilityService.canView(doc, user, permissions);
    }

    private Optional<Long> parseLong(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
