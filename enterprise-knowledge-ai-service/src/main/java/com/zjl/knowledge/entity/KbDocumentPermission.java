package com.zjl.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档权限明细实体
 */
@Data
@TableName("kb_document_permission")
public class KbDocumentPermission {

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 文档 ID
     */
    private Long documentId;

    /**
     * 授权对象类型：USER / PROJECT
     */
    private String permissionTargetType;

    /**
     * 授权对象 ID
     */
    private Long permissionTargetId;

    /**
     * 权限级别
     */
    private String permissionLevel;

    /**
     * 创建人
     */
    private Long createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
