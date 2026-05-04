package com.zjl.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.knowledge.entity.KbDocumentChunkLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分块任务日志 Mapper
 *
 * <p>提供 {@link KbDocumentChunkLog} 实体的标准 CRUD 操作，
 * 记录每次分块任务的各阶段耗时与结果</p>
 */
@Mapper
public interface KbDocumentChunkLogMapper extends BaseMapper<KbDocumentChunkLog> {
}
