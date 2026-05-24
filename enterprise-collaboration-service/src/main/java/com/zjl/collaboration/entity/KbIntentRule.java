package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_intent_rule")
public class KbIntentRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long nodeId;
    private String ruleType;
    private String expression;
    private Double weight;
    private Integer enabled;
    private LocalDateTime createdAt;
}
