package com.zjl.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.knowledge.entity.KbDocumentPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档权限明细 Mapper
 *
 * <p>提供 {@link KbDocumentPermission} 实体的标准 CRUD 操作，
 * 用于存储 USER 或 PROJECT 级别的细粒度授权记录</p>
 */
@Mapper
public interface KbDocumentPermissionMapper extends BaseMapper<KbDocumentPermission> {
}
