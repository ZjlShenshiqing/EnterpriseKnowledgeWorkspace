package com.zjl.collaboration.dto;

import lombok.Data;

import java.util.Date;

/**
 * 待办保存请求。
 */
@Data
public class TodoReq {
    private String title;
    private String priority;
    private Date dueDate;
}
