package com.zjl.collaboration.dto;

import lombok.Data;

/**
 * 任务保存请求。
 */
@Data
public class TaskReq {
    private String title;
    private String description;
    private Long assigneeId;
    private String priority;
}
