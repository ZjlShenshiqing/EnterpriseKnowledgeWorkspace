package com.zjl.knowledge.dto.kb;

import lombok.Builder;
import lombok.Data;

/**
 * 管理后台 AI 统计指标。
 */
@Data
@Builder
public class KbAdminStatsVO {

    /** 文档处理成功率 (0-100) */
    private double successRate;

    /** 平均处理耗时（毫秒） */
    private long avgDurationMs;

    /** P95 处理耗时（毫秒） */
    private long p95DurationMs;

    /** 会话总数 */
    private long sessionCount;

    /** 消息总数 */
    private long messageCount;

    /** 平均每条会话消息数 */
    private double avgMessagesPerSession;

    /** 累计 token 消耗 */
    private long totalTokens;

    /** 文档总数 */
    private long totalDocs;

    /** 处理成功文档数 */
    private long successDocs;

    /** 待处理文档数 */
    private long pendingDocs;

    /** 处理中文档数 */
    private long runningDocs;

    /** 处理失败文档数 */
    private long failedDocs;

    /** 文档维度成功率 (0-100) */
    private double docSuccessRate;

    /** 全库切片总数 */
    private long totalChunks;
}
