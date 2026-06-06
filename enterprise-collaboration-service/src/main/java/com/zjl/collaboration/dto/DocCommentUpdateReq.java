package com.zjl.collaboration.dto;

import lombok.Data;

/**
 * 文档评论更新请求。
 */
@Data
public class DocCommentUpdateReq {
    private String content;
    private Integer resolved;
}
