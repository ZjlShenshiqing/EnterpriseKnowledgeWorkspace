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
import com.zjl.knowledge.service.rerank.RagRerankService;
import com.zjl.knowledge.service.rerank.RerankRequest;
import com.zjl.knowledge.service.rerank.RerankedCandidate;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 检索服务实现
 *
 * <p>负责：检索模式选择 → 执行检索 → DB 查询 → 权限终检 → rerank → 结果组装</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_RECALL_ROUNDS = 3;

    private final VectorSyncService vectorSyncService;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbDocumentChunkMapper kbDocumentChunkMapper;
    private final KbDocumentPermissionMapper kbDocumentPermissionMapper;
    private final DocumentVisibilityService documentVisibilityService;
    private final RagRetrievalProperties retrievalProperties;
    private final RagRerankService ragRerankService;

    @Override
    public RetrievalResult retrieve(String question, int topK, UserContext user, Long kbId) {
        KbDocument contextDoc = constructKbDocument(kbId);

        Set<Long> seenChunkIds = new HashSet<>();
        List<SearchResult> accumulated = new ArrayList<>();
        Map<Long, KbDocument> docMap = new LinkedHashMap<>();
        int multiplier = retrievalProperties.getTopNMultiplier();

        for (int round = 0; round < MAX_RECALL_ROUNDS; round++) {
            List<SearchResult> roundResults = vectorSyncService.searchSimilar(
                    question, topK * multiplier, contextDoc);
            if (roundResults.isEmpty()) break;

            // 去重：跳过已在之前轮次中见过的 chunk
            for (SearchResult r : roundResults) {
                Optional<Long> chunkId = parseLong(r.chunkId());
                if (chunkId.isEmpty()) continue;
                if (!seenChunkIds.add(chunkId.get())) continue;
                if (parseLong(r.docId()).isEmpty()) continue;
                accumulated.add(r);
            }

            // 将本轮新出现的 docId 查 DB 做权限过滤
            Set<Long> allDocIds = accumulated.stream()
                    .map(r -> parseLong(r.docId()).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<Long> newDocIds = allDocIds.stream()
                    .filter(id -> !docMap.containsKey(id))
                    .collect(Collectors.toList());
            if (!newDocIds.isEmpty()) {
                Map<Long, KbDocument> newDocMap = kbDocumentMapper.selectBatchIds(newDocIds).stream()
                        .filter(this::isNotDeleted)
                        .filter(this::isSearchable)
                        .filter(d -> isVisible(d, user))
                        .collect(Collectors.toMap(KbDocument::getId, d -> d, (l, r) -> l, LinkedHashMap::new));
                docMap.putAll(newDocMap);
            }

            if (docMap.size() >= topK) break;
            multiplier *= 2;
        }

        // 仅保留通过权限终检的 accumulated 结果
        List<SearchResult> validResults = accumulated.stream()
                .filter(r -> docMap.containsKey(parseLong(r.docId()).orElse(null)))
                .collect(Collectors.toList());
        if (validResults.isEmpty()) {
            return new RetrievalResult(List.of());
        }

        List<RerankedCandidate> candidates = buildCandidates(validResults, docMap);
        if (candidates.isEmpty()) {
            return new RetrievalResult(List.of());
        }

        List<RerankedCandidate> reranked = ragRerankService.rerank(new RerankRequest(question, candidates));

        Map<Long, KbDocument> docById = new LinkedHashMap<>();
        List<RetrievalResult.DocumentResult> documents = new ArrayList<>();
        for (RerankedCandidate c : reranked) {
            if (documents.size() >= topK) break;
            if (docById.containsKey(c.documentId())) continue;

            KbDocument doc = docMap.get(c.documentId());
            if (doc == null) continue;

            List<RerankedCandidate> docCandidates = reranked.stream()
                    .filter(rc -> rc.documentId().equals(c.documentId()))
                    .collect(Collectors.toList());

            List<RetrievalResult.ChunkResult> matchedChunks = docCandidates.stream()
                    .map(this::toChunkResult)
                    .collect(Collectors.toList());

            docById.put(c.documentId(), doc);
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
        }
        return new RetrievalResult(documents);
    }

    /**
     * 构建 rerank 候选列表：在 DB 权限终检后执行，仅包含通过权限和可见性过滤的 chunk
     */
    private List<RerankedCandidate> buildCandidates(List<SearchResult> validResults,
                                                     Map<Long, KbDocument> docMap) {
        List<Long> allChunkIds = validResults.stream()
                .map(r -> parseLong(r.chunkId()).orElse(null))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, KbDocumentChunk> chunkMap = kbDocumentChunkMapper.selectBatchIds(allChunkIds).stream()
                .filter(c -> c.getEnabled() == null || c.getEnabled() == 1)
                .collect(Collectors.toMap(KbDocumentChunk::getId, c -> c, (l, r) -> l));

        List<RerankedCandidate> candidates = new ArrayList<>();
        int rank = 0;
        for (SearchResult r : validResults) {
            Long docId = parseLong(r.docId()).orElse(null);
            if (docId == null || !docMap.containsKey(docId)) continue;
            Long chunkId = parseLong(r.chunkId()).orElse(null);
            if (chunkId == null) continue;
            KbDocumentChunk chunk = chunkMap.get(chunkId);
            if (chunk == null) continue;
            if (!Objects.equals(chunk.getDocumentId(), docId)) {
                log.warn("RAG chunk ownership mismatch: chunkId={}, searchDocId={}, databaseDocId={}",
                        chunkId, docId, chunk.getDocumentId());
                continue;
            }
            rank++;
            candidates.add(new RerankedCandidate(
                    docId,
                    chunkId,
                    chunk.getChunkIndex(),
                    chunk.getChunkText(),
                    r.score(),
                    rank,
                    inferRetrievalSource(retrievalProperties.getMode()),
                    r.metadata(),
                    0f,
                    "",
                    ""
            ));
        }
        return candidates;
    }

    private RetrievalResult.ChunkResult toChunkResult(RerankedCandidate c) {
        return new RetrievalResult.ChunkResult(
                c.chunkIndex(),
                c.text(),
                c.originalScore(),
                c.metadata(),
                c.rerankScore(),
                c.rerankStrategy(),
                c.rerankReason()
        );
    }

    private String inferRetrievalSource(RagRetrievalProperties.RetrievalMode mode) {
        return switch (mode) {
            case HYBRID_MILVUS -> "HYBRID_MILVUS";
            case KEYWORD_DB -> "KEYWORD_DB";
            default -> "VECTOR_ONLY";
        };
    }

    private KbDocument constructKbDocument(Long kbId) {
        KbDocument doc = new KbDocument();
        doc.setKbId(kbId);
        return doc;
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
