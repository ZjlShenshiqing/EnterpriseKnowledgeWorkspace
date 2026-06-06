package com.zjl.collaboration.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建聊天会话请求。
 */
@Data
public class ChatCreateConversationReq {
    private String name;
    private String type;
    private List<Long> memberIds;
}
