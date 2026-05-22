package com.zjl.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.knowledge.entity.KbDocumentChunkLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 分块任务日志 Mapper
 */
@Mapper
public interface KbDocumentChunkLogMapper extends BaseMapper<KbDocumentChunkLog> {

    /** 按状态统计数量 */
    List<Map<String, Object>> countByStatus();

    /** 平均总耗时（毫秒） */
    Long avgTotalDuration();

    /** 全部耗时列表（用于计算 P95） */
    List<Long> selectAllDurations();
}
