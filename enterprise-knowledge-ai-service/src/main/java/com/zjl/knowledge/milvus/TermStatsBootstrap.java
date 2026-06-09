package com.zjl.knowledge.milvus;

import com.zjl.knowledge.mapper.KbTermStatsMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 启动时自动重建 BM25 词项统计
 *
 * <p>仅在词项统计表为空时触发全量重建，避免使用过期或错误的 DF 数据。
 * 若表非空则跳过（由定时任务或手动触发后续重建）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TermStatsBootstrap {

    private final SparseVectorGenerator sparseVectorGenerator;
    private final KbTermStatsMapper termStatsMapper;

    @PostConstruct
    public void init() {
        try {
            Long count = termStatsMapper.selectCount(null);
            if (count != null && count > 0) {
                log.info("Term stats table already contains {} entries, skipping rebuild", count);
                return;
            }
            log.info("Term stats table is empty, triggering initial rebuild...");
            sparseVectorGenerator.rebuildTermStats();
        } catch (Exception e) {
            log.error("Term stats rebuild failed, BM25 will use uniform IDF fallback", e);
        }
    }
}
