package com.zjl.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.knowledge.entity.KbTermStats;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * 词项统计 Mapper
 */
@Mapper
public interface KbTermStatsMapper extends BaseMapper<KbTermStats> {

    /**
     * 批量查询词项的文档频率
     */
    @Select("<script>"
            + "SELECT term, doc_count FROM kb_term_stats WHERE term IN "
            + "<foreach collection='terms' item='t' open='(' separator=',' close=')'>#{t}</foreach>"
            + "</script>")
    List<KbTermStats> selectByTerms(@Param("terms") Collection<String> terms);

    /**
     * 清空词项统计表
     */
    @Update("TRUNCATE TABLE kb_term_stats")
    void truncate();

    /**
     * 批量插入词项统计（用于全量重建）
     */
    @Insert("<script>"
            + "INSERT INTO kb_term_stats (term, doc_count) VALUES "
            + "<foreach collection='list' item='item' separator=','>"
            + "(#{item.term}, #{item.docCount})"
            + "</foreach>"
            + "</script>")
    void batchInsert(@Param("list") List<KbTermStats> list);
}
