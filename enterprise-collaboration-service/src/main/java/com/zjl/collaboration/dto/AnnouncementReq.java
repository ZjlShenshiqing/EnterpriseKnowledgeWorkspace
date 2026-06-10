package com.zjl.collaboration.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 公告发布请求
 *
 * @author zjl
 * @date 2026-06-10
 */
@Data
public class AnnouncementReq {

    /**
     * 公告标题
     */
    @NotBlank(message = "公告标题不能为空")
    private String title;

    /**
     * 公告内容
     */
    @NotBlank(message = "公告内容不能为空")
    private String content;
}
