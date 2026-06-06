package com.zjl.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.collaboration.entity.SysDocComment;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.mapper.SysDocCommentMapper;
import com.zjl.collaboration.service.DocCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档评论业务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocCommentServiceImpl implements DocCommentService {

    private final SysDocCommentMapper commentMapper;
    private final GatewayUserClient gatewayUserClient;

    @Override
    public List<Map<String, Object>> list(Long docId) {
        List<SysDocComment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<SysDocComment>()
                        .eq(SysDocComment::getDocId, docId)
                        .eq(SysDocComment::getDeleted, 0)
                        .isNull(SysDocComment::getParentId)
                        .orderByDesc(SysDocComment::getCreatedAt));
        Set<Long> userIds = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysDocComment comment : comments) {
            userIds.add(comment.getUserId());
            Map<String, Object> item = commentToMap(comment);
            List<Map<String, Object>> replies = listReplies(docId, comment.getId(), userIds);
            item.put("replies", replies);
            result.add(item);
        }
        fillUserNames(result, userIds);
        return result;
    }

    @Override
    public Map<String, Object> create(Long docId, String content, Integer anchorIndex, Integer anchorLength,
                                      Long parentId, Long userId) {
        SysDocComment comment = new SysDocComment();
        comment.setDocId(docId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setAnchorIndex(anchorIndex);
        comment.setAnchorLength(anchorLength);
        comment.setParentId(parentId);
        comment.setResolved(0);
        comment.setDeleted(0);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        log.info("评论添加: docId={}, userId={}", docId, userId);
        return commentToMap(comment);
    }

    @Override
    public boolean update(Long id, String content, Integer resolved) {
        SysDocComment comment = commentMapper.selectById(id);
        if (comment == null) {
            return false;
        }
        if (content != null) {
            comment.setContent(content);
        }
        if (resolved != null) {
            comment.setResolved(resolved);
        }
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.updateById(comment);
        return true;
    }

    @Override
    public void delete(Long id) {
        log.info("评论删除: commentId={}", id);
        SysDocComment comment = commentMapper.selectById(id);
        if (comment != null) {
            comment.setDeleted(1);
            commentMapper.updateById(comment);
        }
    }

    private List<Map<String, Object>> listReplies(Long docId, Long parentId, Set<Long> userIds) {
        List<SysDocComment> replies = commentMapper.selectList(
                new LambdaQueryWrapper<SysDocComment>()
                        .eq(SysDocComment::getDocId, docId)
                        .eq(SysDocComment::getParentId, parentId)
                        .eq(SysDocComment::getDeleted, 0)
                        .orderByAsc(SysDocComment::getCreatedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysDocComment reply : replies) {
            userIds.add(reply.getUserId());
            result.add(commentToMap(reply));
        }
        return result;
    }

    private void fillUserNames(List<Map<String, Object>> comments, Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = new HashMap<>();
        gatewayUserClient.batchQuery(new ArrayList<>(userIds))
                .forEach((id, user) -> nameMap.put(id, user.realName()));
        for (Map<String, Object> comment : comments) {
            comment.put("userName", nameMap.getOrDefault(comment.get("userId"), ""));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> replies = (List<Map<String, Object>>) comment.get("replies");
            if (replies != null) {
                for (Map<String, Object> reply : replies) {
                    reply.put("userName", nameMap.getOrDefault(reply.get("userId"), ""));
                }
            }
        }
    }

    private Map<String, Object> commentToMap(SysDocComment comment) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", comment.getId());
        item.put("docId", comment.getDocId());
        item.put("userId", comment.getUserId());
        item.put("content", comment.getContent());
        item.put("anchorIndex", comment.getAnchorIndex());
        item.put("anchorLength", comment.getAnchorLength());
        item.put("parentId", comment.getParentId());
        item.put("resolved", comment.getResolved());
        item.put("createdAt", comment.getCreatedAt());
        return item;
    }
}
