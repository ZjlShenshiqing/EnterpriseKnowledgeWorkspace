package com.zjl.knowledge.dto.kb;

import lombok.Builder;
import lombok.Data;

/**
 * 文档处理状态统计。
 */
@Data
@Builder
public class KbDocumentStatsVO {

    private long totalDocs;

    private long successDocs;

    private long pendingDocs;

    private long runningDocs;

    private long failedDocs;

    private long processingDocs;

    private double docSuccessRate;
}
