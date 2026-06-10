package com.zjl.collaboration.dto;

import lombok.Data;

/**
 * 聊天已读请求
 *
 * @author zjl
 * @date 2026-06-10
 */
@Data
public class ChatReadReq {

    /**
     * 上一次已读信息id
     */
    private Long lastReadMsgId;
}
