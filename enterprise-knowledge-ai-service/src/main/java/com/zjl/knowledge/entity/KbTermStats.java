package com.zjl.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * BM25 词项统计实体 — 记录每个 term 在多少个文档（chunk）中出现
 */
@Data
@TableName("kb_term_stats")
public class KbTermStats {

    /** 归一化后的词项 */
    private String term;

    /** 包含该词项的文档（chunk）数量 */
    private Integer docCount;
}
