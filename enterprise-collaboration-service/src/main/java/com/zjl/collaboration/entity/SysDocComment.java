package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_doc_comment")
public class SysDocComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Long userId;
    private String content;
    private Integer anchorIndex;
    private Integer anchorLength;
    private Long parentId;
    private Integer resolved;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
