package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.collaboration.entity.*;
import com.zjl.collaboration.mapper.*;
import com.zjl.collaboration.service.ImFileService;
import com.zjl.collaboration.service.ImReadService;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ImConversationMapper convMapper;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageMapper msgMapper;
    private final SysUserMapper userMapper;
    private final ImReadService readService;
    private final ImFileService fileService;

    public ChatController(ImConversationMapper convMapper,
                           ImConversationMemberMapper memberMapper,
                           ImMessageMapper msgMapper,
                           SysUserMapper userMapper,
                           ImReadService readService,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) ImFileService fileService) {
        this.convMapper = convMapper;
        this.memberMapper = memberMapper;
        this.msgMapper = msgMapper;
        this.userMapper = userMapper;
        this.readService = readService;
        this.fileService = fileService;
    }

    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> conversations(@RequestHeader("X-User-Id") Long userId) {
        List<Long> convIds = memberMapper.selectList(
                Wrappers.lambdaQuery(ImConversationMember.class).eq(ImConversationMember::getUserId, userId))
                .stream().map(ImConversationMember::getConversationId).toList();
        if (convIds.isEmpty()) return Results.success(List.of());

        List<ImConversation> convs = convMapper.selectBatchIds(convIds).stream()
                .sorted((a, b) -> {
                    LocalDateTime ta = a.getLastMsgAt() != null ? a.getLastMsgAt() : a.getUpdatedAt();
                    LocalDateTime tb = b.getLastMsgAt() != null ? b.getLastMsgAt() : b.getUpdatedAt();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta);
                }).toList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (ImConversation c : convs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("type", c.getType());
            m.put("last_msg", c.getLastMsgContent());
            m.put("last_msg_time", c.getLastMsgAt());
            m.put("last_msg_sender", c.getLastMsgSender());
            m.put("unread", readService.unreadCount(userId, c.getId()));
            m.put("updatedAt", c.getUpdatedAt());
            result.add(m);
        }
        return Results.success(result);
    }

    @GetMapping("/messages/{convId}")
    public Result<List<ImMessage>> messages(@PathVariable Long convId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        Page<ImMessage> pageResult = msgMapper.selectPage(
                new Page<>(page, size),
                Wrappers.lambdaQuery(ImMessage.class)
                        .eq(ImMessage::getConversationId, convId)
                        .orderByDesc(ImMessage::getCreatedAt));
        List<ImMessage> list = new ArrayList<>(pageResult.getRecords());
        Collections.reverse(list);
        return Results.success(list);
    }

    @PostMapping("/conversations")
    public Result<Long> createConv(@RequestBody CreateConvReq req,
                                    @RequestHeader("X-User-Id") Long userId) {
        if ("private".equals(req.getType()) && req.getMemberIds() != null
                && req.getMemberIds().size() == 1) {
            Long targetUserId = req.getMemberIds().get(0);
            List<ImConversationMember> myMemberships = memberMapper.selectList(
                    Wrappers.lambdaQuery(ImConversationMember.class)
                            .eq(ImConversationMember::getUserId, userId));
            for (ImConversationMember myMem : myMemberships) {
                ImConversation conv = convMapper.selectById(myMem.getConversationId());
                if (conv != null && "private".equals(conv.getType())) {
                    List<ImConversationMember> otherMembers = memberMapper.selectList(
                            Wrappers.lambdaQuery(ImConversationMember.class)
                                    .eq(ImConversationMember::getConversationId, conv.getId())
                                    .eq(ImConversationMember::getUserId, targetUserId));
                    if (!otherMembers.isEmpty()) {
                        return Results.success(conv.getId());
                    }
                }
            }
        }
        ImConversation c = new ImConversation();
        c.setName(req.getName());
        c.setType(req.getType());
        c.setCreatedBy(userId);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        convMapper.insert(c);
        ImConversationMember self = new ImConversationMember();
        self.setConversationId(c.getId());
        self.setUserId(userId);
        memberMapper.insert(self);
        if (req.getMemberIds() != null) {
            for (Long uid : req.getMemberIds()) {
                ImConversationMember m = new ImConversationMember();
                m.setConversationId(c.getId());
                m.setUserId(uid);
                memberMapper.insert(m);
            }
        }
        return Results.success(c.getId());
    }

    @PostMapping("/conversations/{id}/read")
    public Result<Void> markRead(@PathVariable Long id,
                                  @RequestHeader("X-User-Id") Long userId,
                                  @RequestBody ReadReq req) {
        readService.markRead(userId, id, req.getLastReadMsgId());
        return Results.success();
    }

    @PostMapping("/files/upload")
    public Result<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file)
            throws IOException {
        if (fileService == null) {
            throw new com.zjl.common.exception.BizException(
                    com.zjl.common.enums.ErrorCode.SYSTEM_ERROR, "OSS 未配置，文件上传不可用");
        }
        return Results.success(fileService.upload(file));
    }

    @GetMapping("/members/{convId}")
    public Result<List<Map<String, Object>>> members(@PathVariable Long convId) {
        List<Long> userIds = memberMapper.selectList(
                        Wrappers.lambdaQuery(ImConversationMember.class)
                                .eq(ImConversationMember::getConversationId, convId))
                .stream().map(ImConversationMember::getUserId).toList();
        List<SysUser> users = userIds.isEmpty() ? List.of()
                : userMapper.selectBatchIds(userIds);
        Map<Long, SysUser> userMap = users.stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));
        return Results.success(userIds.stream().map(uid -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", uid);
            SysUser u = userMap.get(uid);
            if (u != null) {
                m.put("username", u.getUsername());
                m.put("realName", u.getRealName());
            } else {
                m.put("username", "user" + uid);
                m.put("realName", "用户" + uid);
            }
            return m;
        }).collect(Collectors.toList()));
    }

    @Data
    public static class CreateConvReq {
        private String name;
        private String type;
        private List<Long> memberIds;
    }

    @Data
    public static class ReadReq {
        private Long lastReadMsgId;
    }
}
