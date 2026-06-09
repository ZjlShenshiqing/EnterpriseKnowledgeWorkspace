package com.zjl.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskReq {
    @NotBlank(message = "任务标题不能为空")
    private String title;
    private String description;
    private Long assigneeId;
    private String priority;
}
