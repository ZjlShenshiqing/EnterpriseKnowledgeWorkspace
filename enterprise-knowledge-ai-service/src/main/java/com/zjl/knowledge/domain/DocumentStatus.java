package com.zjl.knowledge.domain;

/**
 * 文档状态
 */
public enum DocumentStatus {

    /**
     * 草稿
     */
    DRAFT,

    /**
     * 已上传，等待分块任务
     */
    PENDING,

    /**
     * 解析中
     */
    PARSING,

    /**
     * 分块/向量任务处理中（与解析类似，禁止手工改 Chunk）
     */
    RUNNING,

    /**
     * 审核中
     */
    REVIEWING,

    /**
     * 已发布
     */
    PUBLISHED,

    /**
     * 分块与向量写入成功（与参考工程 SUCCESS 对齐）
     */
    SUCCESS,

    /**
     * 审核拒绝
     */
    REJECTED,

    /**
     * 已下架
     */
    OFFLINE,

    /**
     * 解析失败
     */
    FAILED
}
