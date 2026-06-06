package com.zjl.collaboration.dto;

import lombok.Data;

/**
 * 文档评论创建请求。
 */
@Data
public class DocCommentReq {
    private String content;
    private Integer anchorIndex;
    private Integer anchorLength;
    private Long parentId;
}
