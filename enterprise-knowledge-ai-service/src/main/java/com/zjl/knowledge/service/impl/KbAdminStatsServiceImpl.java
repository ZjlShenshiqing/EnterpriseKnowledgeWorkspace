package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.knowledge.agent.mapper.KbAgentMessageMapper;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.dto.kb.KbAdminStatsVO;
import com.zjl.knowledge.dto.kb.KbDocumentStatsVO;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.mapper.KbDocumentChunkLogMapper;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.service.KbAdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KbAdminStatsServiceImpl implements KbAdminStatsService {

    private final KbDocumentChunkLogMapper chunkLogMapper;
    private final KbAgentMessageMapper messageMapper;
    private final KbDocumentMapper documentMapper;
    private final KbDocumentChunkMapper documentChunkMapper;

    @Override
    public KbAdminStatsVO compute() {
        List<Map<String, Object>> statusRows = chunkLogMapper.countByStatus();
        long total = 0, success = 0;
        for (Map<String, Object> row : statusRows) {
            long cnt = ((Number) row.get("cnt")).longValue();
            total += cnt;
            if ("SUCCESS".equals(row.get("status"))) {
                success = cnt;
            }
        }

        double successRate = total > 0 ? (double) success / total * 100 : 100;

        Long avgMs = chunkLogMapper.avgTotalDuration();
        long avgDuration = avgMs != null ? avgMs : 0;

        List<Long> all = chunkLogMapper.selectAllDurations();
        long p95 = 0;
        if (!all.isEmpty()) {
            int idx = (int) Math.ceil(all.size() * 0.95) - 1;
            if (idx < 0) idx = 0;
            if (idx >= all.size()) idx = all.size() - 1;
            p95 = all.get(idx);
        }

        Map<String, Object> msgStats = messageMapper.messageStats();
        long msgCount = ((Number) msgStats.get("msgCount")).longValue();
        long sessionCount = ((Number) msgStats.get("sessionCount")).longValue();
        long totalTokens = ((Number) msgStats.get("totalTokens")).longValue();
        double avgMsgPerSession = sessionCount > 0 ? (double) msgCount / sessionCount : 0;

        KbDocumentStatsVO docStats = computeDocumentStats();

        return KbAdminStatsVO.builder()
                .successRate(Math.round(successRate * 10) / 10.0)
                .avgDurationMs(avgDuration)
                .p95DurationMs(p95)
                .sessionCount(sessionCount)
                .messageCount(msgCount)
                .avgMessagesPerSession(Math.round(avgMsgPerSession * 10) / 10.0)
                .totalTokens(totalTokens)
                .totalDocs(docStats.getTotalDocs())
                .successDocs(docStats.getSuccessDocs())
                .pendingDocs(docStats.getPendingDocs())
                .runningDocs(docStats.getRunningDocs())
                .failedDocs(docStats.getFailedDocs())
                .docSuccessRate(docStats.getDocSuccessRate())
                .totalChunks(docStats.getTotalChunks())
                .build();
    }

    @Override
    public KbDocumentStatsVO computeDocumentStats() {
        long totalDocs = countDocs(null);
        long successDocs = countDocs(DocumentStatus.SUCCESS.name());
        long pendingDocs = countDocs(DocumentStatus.PENDING.name());
        long runningDocs = countDocs(DocumentStatus.RUNNING.name());
        long failedDocs = countDocs(DocumentStatus.FAILED.name());
        long processingDocs = pendingDocs + runningDocs;
        double docSuccessRate = totalDocs > 0
                ? Math.round((double) successDocs / totalDocs * 1000) / 10.0
                : 100;
        return KbDocumentStatsVO.builder()
                .totalDocs(totalDocs)
                .successDocs(successDocs)
                .pendingDocs(pendingDocs)
                .runningDocs(runningDocs)
                .failedDocs(failedDocs)
                .processingDocs(processingDocs)
                .docSuccessRate(docSuccessRate)
                .totalChunks(countChunks())
                .build();
    }

    private long countDocs(String status) {
        var q = Wrappers.lambdaQuery(KbDocument.class);
        if (status != null) {
            q.eq(KbDocument::getStatus, status);
        }
        Long count = documentMapper.selectCount(q);
        return count != null ? count : 0;
    }

    private long countChunks() {
        Long count = documentChunkMapper.selectCount(Wrappers.lambdaQuery(KbDocumentChunk.class));
        return count != null ? count : 0;
    }
}
