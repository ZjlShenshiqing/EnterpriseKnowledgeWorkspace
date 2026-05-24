package com.zjl.collaboration.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("kb_intent_kb_rel")
public class KbIntentKbRel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long nodeId;
    private Long kbId;
    private Double weight;
    private LocalDateTime createdAt;
}
