package com.zjl.collaboration.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ChatCreateConversationReq {
    private String name;
    @NotBlank(message = "会话类型不能为空")
    private String type;
    @NotEmpty(message = "会话成员不能为空")
    private List<Long> memberIds;
}
