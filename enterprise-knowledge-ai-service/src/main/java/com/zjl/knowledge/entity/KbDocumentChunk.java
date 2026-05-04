package com.zjl.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档切片实体
 */
@Data
@TableName("kb_document_chunk")
public class KbDocumentChunk {

    /**
     * 主键（与 Milvus chunk_id 对齐）
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 文档 ID
     */
    private Long documentId;

    /**
     * 切片序号
     */
    private Integer chunkIndex;

    /**
     * 切片文本
     */
    private String chunkText;

    /**
     * 正文 SHA-256（十六进制）
     */
    private String contentHash;

    /**
     * 字符数
     */
    private Integer charCount;

    /**
     * Token 数
     */
    private Integer tokenCount;

    /**
     * 向量侧主键（与 Milvus chunk_id 字符串一致）
     */
    private String vectorId;

    /**
     * 是否启用（0/1）
     */
    private Integer enabled;

    /**
     * 元数据 JSON
     */
    private String metadataJson;

    /**
     * 创建人
     */
    private Long createdBy;

    /**
     * 更新人
     */
    private Long updatedBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
