package com.zjl.collaboration.dto;

import lombok.Data;

/**
 * 意图绑定知识库请求。
 */
@Data
public class IntentBindKbReq {
    private Long kbId;
    private Double weight;
}
