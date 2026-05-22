package com.zjl.collaboration.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("im_message")
public class ImMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderName;
    private String content;
    private String msgType;
    private String status;
    private String mqMsgId;
    private LocalDateTime createdAt;
}
