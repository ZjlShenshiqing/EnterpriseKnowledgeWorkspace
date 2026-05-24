package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_intent_node")
public class KbIntentNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String name;
    private Integer level;
    private Integer sortOrder;
    private String description;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private java.util.List<KbIntentNode> children;
    @TableField(exist = false)
    private java.util.List<KbIntentRule> rules;
    @TableField(exist = false)
    private java.util.List<KbIntentKbRel> kbRels;
}
