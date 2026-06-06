package com.zjl.collaboration.dto;

import lombok.Data;

/**
 * 聊天已读请求。
 */
@Data
public class ChatReadReq {
    private Long lastReadMsgId;
}
