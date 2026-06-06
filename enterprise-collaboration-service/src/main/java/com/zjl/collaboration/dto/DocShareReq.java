package com.zjl.collaboration.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分享链接请求。
 */
@Data
public class DocShareReq {
    private String permission;
    private LocalDateTime expiredAt;
}
