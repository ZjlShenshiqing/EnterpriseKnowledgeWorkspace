package com.zjl.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文档实体
 */
@Data
@TableName("kb_document")
public class KbDocument {

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 分类 ID
     */
    private Long categoryId;

    /**
     * 所属知识库 ID（为空时使用默认 Milvus 集合）
     */
    private Long kbId;

    /**
     * 上传人
     */
    private Long ownerId;

    /**
     * 所属部门
     */
    private Long departmentId;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件存储路径
     */
    private String fileUrl;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 解析正文
     */
    private String contentText;

    /**
     * 标签
     */
    private String tags;

    /**
     * 权限类型
     */
    private String permissionType;

    /**
     * 文档状态
     */
    private String status;

    /**
     * 当前版本号
     */
    private Integer currentVersion;

    /**
     * 切片数量
     */
    private Integer chunkCount;

    /**
     * 是否启用（禁用时不应再向量化检索）
     */
    private Integer enabled;

    /**
     * 处理模式：CHUNK / PIPELINE
     */
    private String processMode;

    /**
     * 分块策略：FIXED_SIZE / PARAGRAPH
     */
    private String chunkStrategy;

    /**
     * 分块参数 JSON
     */
    private String chunkConfig;

    /**
     * Pipeline 定义 ID（PIPELINE 模式）
     */
    private String pipelineId;

    /**
     * 来源：FILE / URL
     */
    private String sourceType;

    /**
     * URL 来源地址等
     */
    private String sourceLocation;

    /**
     * 是否启用定时拉取
     */
    private Integer scheduleEnabled;

    /**
     * 定时 Cron 表达式
     */
    private String scheduleCron;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;
}
