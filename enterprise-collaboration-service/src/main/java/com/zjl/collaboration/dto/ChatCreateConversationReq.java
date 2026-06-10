package com.zjl.collaboration.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 创建会话请求
 *
 * @author zjl
 * @date 2026-06-10
 */
@Data
public class ChatCreateConversationReq {

    /**
     * 会话名称
     */
    private String name;

    /**
     * 会话类型
     */
    @NotBlank(message = "会话类型不能为空")
    private String type;

    /**
     * 会话成员
     */
    @NotEmpty(message = "会话成员不能为空")
    private List<Long> memberIds;
}
