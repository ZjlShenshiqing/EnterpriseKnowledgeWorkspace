package com.zjl.knowledge.service;

import com.zjl.knowledge.agent.mapper.KbAgentMessageMapper;
import com.zjl.knowledge.dto.kb.KbAdminStatsVO;
import com.zjl.knowledge.mapper.KbDocumentChunkLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 AI 统计服务。
 */
@Service
@RequiredArgsConstructor
public class KbAdminStatsService {

    private final KbDocumentChunkLogMapper chunkLogMapper;
    private final KbAgentMessageMapper messageMapper;

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

        return KbAdminStatsVO.builder()
                .successRate(Math.round(successRate * 10) / 10.0)
                .avgDurationMs(avgDuration)
                .p95DurationMs(p95)
                .sessionCount(sessionCount)
                .messageCount(msgCount)
                .avgMessagesPerSession(Math.round(avgMsgPerSession * 10) / 10.0)
                .totalTokens(totalTokens)
                .build();
    }
}
