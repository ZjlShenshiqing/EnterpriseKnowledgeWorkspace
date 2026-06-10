package com.zjl.collaboration.dto;

import lombok.Data;

/**
 * 文档协作者请求
 *
 * @author zjl
 * @date 2026-06-10
 */
@Data
public class DocCollaboratorReq {
    private String targetType;
    private Long targetId;
    private String permission;
}
