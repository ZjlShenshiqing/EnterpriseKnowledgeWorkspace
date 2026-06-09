package com.zjl.collaboration.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IntentBindKbReq {
    @NotNull(message = "知识库ID不能为空")
    private Long kbId;
    private Double weight;
}
