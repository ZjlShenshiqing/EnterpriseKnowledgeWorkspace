package com.zjl.collaboration.dto;

import lombok.Data;

/**
 * 意图节点排序请求。
 */
@Data
public class IntentSortReq {
    private Long parentId;
    private Integer sortOrder;
}
