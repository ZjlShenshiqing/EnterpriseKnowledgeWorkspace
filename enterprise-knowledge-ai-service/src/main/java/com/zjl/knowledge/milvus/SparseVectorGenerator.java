package com.zjl.knowledge.milvus;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.knowledge.config.MilvusProperties;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.entity.KbTermStats;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbTermStatsMapper;
import com.zjl.knowledge.tokenization.ChineseTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 稀疏向量生成器 — BM25 算法
 *
 * <p>文档侧：仅 BM25 TF 分量（不含 IDF），存入 Milvus SparseFloatVector</p>
 * <p>查询侧：BM25 TF × IDF，与文档向量的内积近似 BM25 得分</p>
 *
 * <pre>
 *   BM25 公式（无长度归一化，b=0）：
 *     TF(t) = ((k1 + 1) * tf) / (k1 + tf)
 *     IDF(t) = log((N - df + 0.5) / (df + 0.5) + 1)
 *     score = Σ IDF(t) * TF_doc(t)
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SparseVectorGenerator {

    private static final float K1 = 1.2f;
    private static final int REBUILD_BATCH_SIZE = 500;
    private static final int INSERT_BATCH_SIZE = 1000;

    private final MilvusProperties milvusProperties;
    private final ChineseTokenizer chineseTokenizer;
    private final KbTermStatsMapper termStatsMapper;
    private final KbDocumentChunkMapper chunkMapper;

    /**
     * 查询侧稀疏向量：IDF × BM25 TF（查询 tf 通常为 1，因子抵消，等价于 IDF）
     *
     * @param text 查询文本
     * @return 稀疏向量：位置 → IDF 权重
     */
    public Map<Long, Float> generateQuery(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Sparse vector input is empty, returning empty map");
            return Map.of();
        }
        return generateQueryVector(chineseTokenizer.tokenizeQuery(text));
    }

    /**
     * 文档侧稀疏向量：BM25 TF（不含 IDF），存入 Milvus
     *
     * @param text 文档文本
     * @return 稀疏向量：位置 → BM25 TF 权重
     */
    public Map<Long, Float> generateDocument(String text) {
        if (text == null || text.isBlank()) {
            log.warn("Sparse vector input is empty, returning empty map");
            return Map.of();
        }
        return generateDocumentVector(chineseTokenizer.tokenizeDocument(text));
    }

    /**
     * 全量重建词项统计 — 扫描所有启用的 chunk，重新计算每个 term 的文档频率
     *
     * <p>应在应用启动时和数据迁移后调用。处理过程会先清空旧统计再逐批重建。</p>
     */
    public void rebuildTermStats() {
        log.info("Starting full term stats rebuild...");
        long startTime = System.currentTimeMillis();

        termStatsMapper.truncate();
        log.info("Truncated kb_term_stats table");

        long totalChunks = countActiveChunks();
        if (totalChunks == 0) {
            log.info("No active chunks found, term stats rebuild skipped");
            return;
        }

        Map<String, Integer> globalDf = new HashMap<>();
        long pages = (totalChunks + REBUILD_BATCH_SIZE - 1) / REBUILD_BATCH_SIZE;

        for (long pageNum = 1; pageNum <= pages; pageNum++) {
            Page<KbDocumentChunk> page = new Page<>(pageNum, REBUILD_BATCH_SIZE);
            LambdaQueryWrapper<KbDocumentChunk> wrapper = new LambdaQueryWrapper<KbDocumentChunk>()
                    .eq(KbDocumentChunk::getEnabled, 1)
                    .select(KbDocumentChunk::getChunkText);
            Page<KbDocumentChunk> result = chunkMapper.selectPage(page, wrapper);

            for (KbDocumentChunk chunk : result.getRecords()) {
                String text = chunk.getChunkText();
                if (text == null || text.isBlank()) {
                    continue;
                }
                Set<String> terms = new HashSet<>(chineseTokenizer.tokenizeDocument(text));
                for (String term : terms) {
                    globalDf.merge(term, 1, Integer::sum);
                }
            }

            if (pageNum % 10 == 0 || pageNum == pages) {
                log.info("Term stats rebuild progress: {}/{} pages, {} unique terms so far",
                        pageNum, pages, globalDf.size());
            }
        }

        batchPersistTermStats(globalDf);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Term stats rebuild complete: {} unique terms from {} chunks in {}ms",
                globalDf.size(), totalChunks, elapsed);
    }

    private long countActiveChunks() {
        LambdaQueryWrapper<KbDocumentChunk> wrapper = new LambdaQueryWrapper<KbDocumentChunk>()
                .eq(KbDocumentChunk::getEnabled, 1);
        Long count = chunkMapper.selectCount(wrapper);
        return count != null ? count : 0;
    }

    private void batchPersistTermStats(Map<String, Integer> dfMap) {
        List<KbTermStats> batch = new ArrayList<>(INSERT_BATCH_SIZE);
        for (Map.Entry<String, Integer> entry : dfMap.entrySet()) {
            KbTermStats stats = new KbTermStats();
            stats.setTerm(entry.getKey());
            stats.setDocCount(entry.getValue());
            batch.add(stats);
            if (batch.size() >= INSERT_BATCH_SIZE) {
                termStatsMapper.batchInsert(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            termStatsMapper.batchInsert(batch);
        }
    }

    private Map<Long, Float> generateDocumentVector(List<String> tokens) {
        if (tokens.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> freq = countFreq(tokens);
        return toSparseVector(freq, 1.0f);
    }

    private Map<Long, Float> generateQueryVector(List<String> tokens) {
        if (tokens.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> freq = countFreq(tokens);
        Set<String> distinctTerms = freq.keySet();
        Map<String, Double> idfMap = lookupIdf(distinctTerms);
        return toSparseVectorWithIdf(freq, idfMap);
    }

    private Map<Long, Float> toSparseVector(Map<String, Integer> freq, float idf) {
        int dim = milvusProperties.getSparseDimension();
        Map<Long, Float> vec = new HashMap<>();
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            float tf = bm25Tf(e.getValue());
            long pos = toPosition(e.getKey(), dim);
            vec.merge(pos, tf * idf, Float::sum);
        }
        return vec;
    }

    private Map<Long, Float> toSparseVectorWithIdf(Map<String, Integer> freq, Map<String, Double> idfMap) {
        int dim = milvusProperties.getSparseDimension();
        Map<Long, Float> vec = new HashMap<>();
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            double idf = idfMap.getOrDefault(e.getKey(), 0.0);
            if (idf <= 0) continue;
            float tf = bm25Tf(e.getValue());
            long pos = toPosition(e.getKey(), dim);
            vec.merge(pos, (float) (tf * idf), Float::sum);
        }
        return vec;
    }

    private Map<String, Double> lookupIdf(Set<String> terms) {
        Map<String, Double> idfMap = new HashMap<>();
        try {
            List<KbTermStats> statsList = termStatsMapper.selectByTerms(terms);
            long totalDocs = countActiveChunks();
            if (totalDocs <= 0) {
                for (String term : terms) {
                    idfMap.put(term, 1.0);
                }
                return idfMap;
            }
            for (KbTermStats stats : statsList) {
                double idf = smoothIdf(totalDocs, stats.getDocCount());
                idfMap.put(stats.getTerm(), idf);
            }
        } catch (Exception e) {
            log.warn("Failed to lookup term stats, falling back to uniform IDF: {}", e.getMessage());
            for (String term : terms) {
                idfMap.put(term, 1.0);
            }
        }
        return idfMap;
    }

    /**
     * BM25 TF 饱和公式（无长度归一化）
     *
     * @param tf term 在当前文档中的出现次数
     * @return BM25 TF 饱和值
     */
    static float bm25Tf(int tf) {
        return ((K1 + 1) * tf) / (K1 + tf);
    }

    /**
     * 平滑 IDF：log((N - df + 0.5) / (df + 0.5) + 1)
     */
    static double smoothIdf(long totalDocs, int docFreq) {
        if (docFreq <= 0) return 0;
        double numerator = totalDocs - docFreq + 0.5;
        double denominator = docFreq + 0.5;
        return Math.log(numerator / denominator + 1.0);
    }

    /**
     * 将 term 哈希到 [0, dim) 范围
     */
    static long toPosition(String term, int dim) {
        int h = term.hashCode();
        return ((long) h - Integer.MIN_VALUE) % dim;
    }

    private static Map<String, Integer> countFreq(List<String> tokens) {
        Map<String, Integer> freq = new HashMap<>();
        for (String token : tokens) {
            freq.merge(token, 1, Integer::sum);
        }
        return freq;
    }
}
