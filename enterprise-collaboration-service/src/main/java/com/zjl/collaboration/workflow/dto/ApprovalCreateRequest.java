package com.zjl.collaboration.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ApprovalCreateRequest {
    @NotBlank(message = "审批类型不能为空")
    private String type;
    @NotBlank(message = "审批标题不能为空")
    private String title;
    private Map<String, Object> formData;
}
