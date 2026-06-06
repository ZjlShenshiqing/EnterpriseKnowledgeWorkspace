package com.zjl.collaboration.dto;

import lombok.Data;

/**
 * 公告发布请求。
 */
@Data
public class AnnouncementReq {
    private String title;
    private String content;
}
