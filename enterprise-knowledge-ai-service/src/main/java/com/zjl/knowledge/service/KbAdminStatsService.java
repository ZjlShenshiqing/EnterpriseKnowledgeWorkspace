package com.zjl.knowledge.service;

import com.zjl.knowledge.dto.kb.KbAdminStatsVO;
import com.zjl.knowledge.dto.kb.KbDocumentStatsVO;

/**
 * 管理后台 AI 统计服务
 */
public interface KbAdminStatsService {

    KbAdminStatsVO compute();

    KbDocumentStatsVO computeDocumentStats();
}
