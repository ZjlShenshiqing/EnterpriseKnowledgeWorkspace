package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("im_message_file")
public class ImMessageFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String ossKey;
    private String thumbOssKey;
    private LocalDateTime createdAt;
}
