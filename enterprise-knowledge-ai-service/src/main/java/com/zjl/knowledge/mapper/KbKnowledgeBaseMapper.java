package com.zjl.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.knowledge.entity.KbKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 逻辑知识库 Mapper
 *
 * <p>提供 {@link KbKnowledgeBase} 实体的标准 CRUD 操作，
 * 知识库与 Milvus 集合一一绑定</p>
 */
@Mapper
public interface KbKnowledgeBaseMapper extends BaseMapper<KbKnowledgeBase> {
}
