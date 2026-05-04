package com.zjl.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.knowledge.entity.KbCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识分类 Mapper
 *
 * <p>提供 {@link KbCategory} 实体的标准 CRUD 操作</p>
 */
@Mapper
public interface KbCategoryMapper extends BaseMapper<KbCategory> {
}
