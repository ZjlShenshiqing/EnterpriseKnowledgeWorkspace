package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_doc_share_link")
public class SysDocShareLink {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private String token;
    private String permission;
    private LocalDateTime expiredAt;
    private Integer deleted;
    private LocalDateTime createdAt;
}
