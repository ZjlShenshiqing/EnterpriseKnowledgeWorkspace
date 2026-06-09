package com.zjl.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocCommentReq {
    @NotBlank(message = "评论内容不能为空")
    private String content;
    private Integer anchorIndex;
    private Integer anchorLength;
    private Long parentId;
}
