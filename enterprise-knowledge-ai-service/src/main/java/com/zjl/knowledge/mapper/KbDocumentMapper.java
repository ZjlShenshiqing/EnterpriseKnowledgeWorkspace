package com.zjl.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.knowledge.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 知识文档 Mapper
 *
 * <p>提供 {@link KbDocument} 实体的 CRUD 操作，
 * 包含权限感知的自定义分页查询 {@link #selectPageVisible}</p>
 */
@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    /**
     * 分页查询当前用户可见的文档
     *
     * <p>按权限类型（ALL / DEPARTMENT / PROJECT / USER / ADMIN）
     * 结合 {@code kb_document_permission} 表过滤，
     * SQL 定义在 {@code KbDocumentMapper.xml}</p>
     *
     * @param page      分页参数
     * @param userId    当前用户 ID
     * @param deptId    当前用户部门 ID（可为空）
     * @param projectId 当前用户项目 ID（可为空）
     * @param admin     是否管理员（1=是）
     * @return 过滤后的分页结果
     */
    IPage<KbDocument> selectPageVisible(
            Page<KbDocument> page,
            @Param("userId") Long userId,
            @Param("deptId") Long deptId,
            @Param("projectId") Long projectId,
            @Param("admin") int admin
    );
}
