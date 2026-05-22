package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_doc_collaborator")
public class SysDocCollaborator {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private String targetType;
    private Long targetId;
    private String permission;
    private Integer deleted;
    private LocalDateTime createdAt;
}
