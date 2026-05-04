package com.zjl.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.knowledge.entity.KbDocumentChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档切片 Mapper
 *
 * <p>提供 {@link KbDocumentChunk} 实体的标准 CRUD 操作，
 * 切片的 {@code id} 与 Milvus 主键一一对应</p>
 */
@Mapper
public interface KbDocumentChunkMapper extends BaseMapper<KbDocumentChunk> {
}
