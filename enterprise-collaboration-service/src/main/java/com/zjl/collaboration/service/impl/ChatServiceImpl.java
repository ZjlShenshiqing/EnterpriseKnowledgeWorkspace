package com.zjl.collaboration.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.collaboration.entity.ImConversation;
import com.zjl.collaboration.entity.ImConversationMember;
import com.zjl.collaboration.entity.ImMessage;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.mapper.ImConversationMapper;
import com.zjl.collaboration.mapper.ImConversationMemberMapper;
import com.zjl.collaboration.mapper.ImMessageMapper;
import com.zjl.collaboration.service.ChatService;
import com.zjl.collaboration.service.ImReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 即时通讯业务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ImConversationMapper convMapper;
    private final ImConversationMemberMapper memberMapper;
    private final ImMessageMapper msgMapper;
    private final GatewayUserClient gatewayUserClient;
    private final ImReadService readService;

    @Override
    public List<Map<String, Object>> conversations(Long userId) {
        log.debug("会话列表查询: userId={}", userId);
        List<Long> convIds = conversationIds(userId);
        if (convIds.isEmpty()) {
            return List.of();
        }
        return convMapper.selectBatchIds(convIds).stream()
                .sorted(this::compareByLastMessageTime)
                .map(conversation -> toConversationMap(conversation, userId))
                .toList();
    }

    @Override
    public int unreadCount(Long userId) {
        int total = 0;
        for (Long convId : conversationIds(userId)) {
            total += readService.unreadCount(userId, convId);
        }
        return total;
    }

    @Override
    public List<ImMessage> messages(Long convId, int page, int size) {
        Page<ImMessage> pageResult = msgMapper.selectPage(
                new Page<>(page, size),
                Wrappers.lambdaQuery(ImMessage.class)
                        .eq(ImMessage::getConversationId, convId)
                        .orderByDesc(ImMessage::getCreatedAt));
        List<ImMessage> list = new ArrayList<>(pageResult.getRecords());
        Collections.reverse(list);
        return list;
    }

    @Override
    public Long createConversation(String name, String type, List<Long> memberIds, Long userId) {
        Long existingPrivateConvId = findExistingPrivateConversation(type, memberIds, userId);
        if (existingPrivateConvId != null) {
            return existingPrivateConvId;
        }
        ImConversation conversation = new ImConversation();
        conversation.setName(name);
        conversation.setType(type);
        conversation.setCreatedBy(userId);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        convMapper.insert(conversation);
        insertMember(conversation.getId(), userId);
        if (memberIds != null) {
            for (Long uid : memberIds) {
                insertMember(conversation.getId(), uid);
            }
        }
        return conversation.getId();
    }

    @Override
    public List<Map<String, Object>> members(Long convId) {
        List<Long> userIds = memberMapper.selectList(
                        Wrappers.lambdaQuery(ImConversationMember.class)
                                .eq(ImConversationMember::getConversationId, convId))
                .stream()
                .map(ImConversationMember::getUserId)
                .toList();
        Map<Long, UserInfo> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : gatewayUserClient.batchQuery(userIds);
        return userIds.stream().map(uid -> toMemberMap(uid, userMap.get(uid))).toList();
    }

    private List<Long> conversationIds(Long userId) {
        return memberMapper.selectList(
                        Wrappers.lambdaQuery(ImConversationMember.class)
                                .eq(ImConversationMember::getUserId, userId))
                .stream()
                .map(ImConversationMember::getConversationId)
                .toList();
    }

    private Long findExistingPrivateConversation(String type, List<Long> memberIds, Long userId) {
        if (!"private".equals(type) || memberIds == null || memberIds.size() != 1) {
            return null;
        }
        Long targetUserId = memberIds.get(0);
        for (ImConversationMember myMembership : memberMapper.selectList(
                Wrappers.lambdaQuery(ImConversationMember.class)
                        .eq(ImConversationMember::getUserId, userId))) {
            ImConversation conversation = convMapper.selectById(myMembership.getConversationId());
            if (conversation != null && "private".equals(conversation.getType())) {
                List<ImConversationMember> otherMembers = memberMapper.selectList(
                        Wrappers.lambdaQuery(ImConversationMember.class)
                                .eq(ImConversationMember::getConversationId, conversation.getId())
                                .eq(ImConversationMember::getUserId, targetUserId));
                if (!otherMembers.isEmpty()) {
                    return conversation.getId();
                }
            }
        }
        return null;
    }

    private void insertMember(Long conversationId, Long userId) {
        ImConversationMember member = new ImConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        memberMapper.insert(member);
    }

    private int compareByLastMessageTime(ImConversation left, ImConversation right) {
        LocalDateTime leftTime = left.getLastMsgAt() != null ? left.getLastMsgAt() : left.getUpdatedAt();
        LocalDateTime rightTime = right.getLastMsgAt() != null ? right.getLastMsgAt() : right.getUpdatedAt();
        if (leftTime == null && rightTime == null) {
            return 0;
        }
        if (leftTime == null) {
            return 1;
        }
        if (rightTime == null) {
            return -1;
        }
        return rightTime.compareTo(leftTime);
    }

    private Map<String, Object> toConversationMap(ImConversation conversation, Long userId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", conversation.getId());
        item.put("name", conversation.getName());
        item.put("type", conversation.getType());
        item.put("last_msg", conversation.getLastMsgContent());
        item.put("last_msg_time", conversation.getLastMsgAt());
        item.put("last_msg_sender", conversation.getLastMsgSender());
        item.put("unread", readService.unreadCount(userId, conversation.getId()));
        item.put("updatedAt", conversation.getUpdatedAt());
        return item;
    }

    private Map<String, Object> toMemberMap(Long userId, UserInfo user) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", userId);
        if (user != null) {
            item.put("username", user.username());
            item.put("realName", user.realName());
        } else {
            item.put("username", "user" + userId);
            item.put("realName", "用户" + userId);
        }
        return item;
    }
}
