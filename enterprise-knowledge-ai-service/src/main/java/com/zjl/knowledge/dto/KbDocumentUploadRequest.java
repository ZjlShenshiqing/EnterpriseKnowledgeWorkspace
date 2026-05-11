package com.zjl.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 文档上传元数据（multipart 中的 JSON 部分）
 */
@Data
public class KbDocumentUploadRequest {

    /**
     * 文档标题
     */
    @NotBlank
    private String title;

    /**
     * 分类 ID
     */
    @NotNull
    private Long categoryId;

    /**
     * 可选；指定后向量写入该知识库对应的 Milvus 集合
     */
    private Long kbId;

    /**
     * 权限类型：ALL / DEPARTMENT / PROJECT / USER / ADMIN
     */
    @NotBlank
    private String permissionType;

    /**
     * USER 权限时授权的用户 ID 列表
     */
    private List<Long> grantUserIds;

    /**
     * PROJECT 权限时授权的项目 ID
     */
    private Long grantProjectId;

    /**
     * 标签，逗号分隔
     */
    private String tags;

    /**
     * 处理模式：CHUNK（默认）/ PIPELINE（未实现会报错）
     */
    private String processMode;

    /**
     * 分块策略：FIXED_SIZE（默认）/ PARAGRAPH
     */
    private String chunkStrategy;

    /**
     * 分块参数 JSON，如 {"maxChars":2000,"overlapChars":0}
     */
    private String chunkConfig;

    /**
     * PIPELINE 模式下的管线 ID
     */
    private String pipelineId;

    /**
     * 来源类型：FILE（默认）/ URL（当前未支持上传 URL）
     */
    private String sourceType;

    /**
     * URL 来源地址（预留）
     */
    private String sourceLocation;

    /**
     * 是否启用定时拉取（仅 URL 场景有意义，当前仅占位）
     */
    private Boolean scheduleEnabled;

    /**
     * 定时 Cron
     */
    private String scheduleCron;

    /**
     * 管理员填写的过滤标签，JSON 格式如 {"department":"技术部"}
     */
    private String filterTags;
}
