package com.zjl.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Date;

/**
 *
 *
 */
@Data
public class TodoReq {
    @NotBlank(message = "待办标题不能为空")
    private String title;
    private String priority;
    private Date dueDate;
}
