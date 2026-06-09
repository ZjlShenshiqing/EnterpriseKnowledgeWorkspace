package com.zjl.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IntentMatchReq {
    @NotBlank(message = "查询文本不能为空")
    private String query;
}
