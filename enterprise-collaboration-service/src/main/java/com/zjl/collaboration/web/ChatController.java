package com.zjl.collaboration.web;

import jakarta.validation.Valid;
import com.zjl.collaboration.dto.ChatCreateConversationReq;
import com.zjl.collaboration.dto.ChatReadReq;
import com.zjl.collaboration.entity.ImMessage;
import com.zjl.collaboration.service.ChatService;
import com.zjl.collaboration.service.ImFileService;
import com.zjl.collaboration.service.ImReadService;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.framework.starter.log.annotation.ILog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 即时通讯接口。
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@ILog
public class ChatController {

    private final ChatService chatService;
    private final ImReadService readService;
    private final ImFileService fileService;

    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> conversations(@RequestHeader("X-User-Id") Long userId) {
        return Results.success(chatService.conversations(userId));
    }

    @GetMapping("/unread-count")
    public Result<Integer> unreadCount(@RequestHeader("X-User-Id") Long userId) {
        return Results.success(chatService.unreadCount(userId));
    }

    @GetMapping("/messages/{convId}")
    public Result<List<ImMessage>> messages(@PathVariable Long convId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return Results.success(chatService.messages(convId, page, size));
    }

    @PostMapping("/conversations")
    public Result<Long> createConv(@Valid @RequestBody ChatCreateConversationReq req,
                                   @RequestHeader("X-User-Id") Long userId) {
        return Results.success(chatService.createConversation(req.getName(), req.getType(), req.getMemberIds(), userId));
    }

    @PostMapping("/conversations/{id}/read")
    public Result<Void> markRead(@PathVariable Long id,
                                 @RequestHeader("X-User-Id") Long userId,
                                 @Valid @RequestBody ChatReadReq req) {
        readService.markRead(userId, id, req.getLastReadMsgId());
        return Results.success();
    }

    @PostMapping("/files/upload")
    public Result<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file)
            throws IOException {
        return Results.success(fileService.upload(file));
    }

    @GetMapping("/members/{convId}")
    public Result<List<Map<String, Object>>> members(@PathVariable Long convId) {
        return Results.success(chatService.members(convId));
    }

}
