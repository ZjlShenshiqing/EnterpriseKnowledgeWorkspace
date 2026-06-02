package com.zjl.collaboration.workflow.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ApprovalCreateRequest {
    private String type;
    private String title;
    private Map<String, Object> formData;
}
