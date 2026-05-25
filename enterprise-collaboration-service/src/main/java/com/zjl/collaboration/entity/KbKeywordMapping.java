package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_keyword_mapping")
public class KbKeywordMapping {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String keyword;
    private String kbName;
    private Integer priority;
    private String strategy;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
