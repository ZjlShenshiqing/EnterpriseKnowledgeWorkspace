package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.collaboration.entity.SysDocComment;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.mapper.SysDocCommentMapper;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocCommentController {

    private final SysDocCommentMapper commentMapper;
    private final GatewayUserClient gatewayUserClient;

    @GetMapping("/{docId}/comments")
    public Result<List<Map<String, Object>>> list(@PathVariable Long docId) {
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
            Map<String, Object> m = commentToMap(comment);

            List<SysDocComment> replies = commentMapper.selectList(
                    new LambdaQueryWrapper<SysDocComment>()
                            .eq(SysDocComment::getDocId, docId)
                            .eq(SysDocComment::getParentId, comment.getId())
                            .eq(SysDocComment::getDeleted, 0)
                            .orderByAsc(SysDocComment::getCreatedAt));
            List<Map<String, Object>> replyList = new ArrayList<>();
            for (SysDocComment reply : replies) {
                userIds.add(reply.getUserId());
                replyList.add(commentToMap(reply));
            }
            m.put("replies", replyList);
            result.add(m);
        }

        if (!userIds.isEmpty()) {
            var users = gatewayUserClient.batchQuery(new ArrayList<>(userIds));
            Map<Long, String> nameMap = new HashMap<>();
            users.forEach((id, u) -> nameMap.put(id, u.realName()));
            for (Map<String, Object> m : result) {
                m.put("userName", nameMap.getOrDefault(m.get("userId"), ""));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> replies = (List<Map<String, Object>>) m.get("replies");
                if (replies != null) {
                    for (Map<String, Object> r : replies) {
                        r.put("userName", nameMap.getOrDefault(r.get("userId"), ""));
                    }
                }
            }
        }

        return Results.success(result);
    }

    @PostMapping("/{docId}/comments")
    public Result<Map<String, Object>> create(@PathVariable Long docId,
                                               @RequestBody CommentReq req,
                                               @RequestHeader("X-User-Id") Long userId) {
        SysDocComment comment = new SysDocComment();
        comment.setDocId(docId);
        comment.setUserId(userId);
        comment.setContent(req.getContent());
        comment.setAnchorIndex(req.getAnchorIndex());
        comment.setAnchorLength(req.getAnchorLength());
        comment.setParentId(req.getParentId());
        comment.setResolved(0);
        comment.setDeleted(0);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        log.info("评论添加: docId={}, userId={}", docId, userId);

        Map<String, Object> m = commentToMap(comment);
        return Results.success(m);
    }

    @PutMapping("/comments/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CommentUpdateReq req) {
        SysDocComment comment = commentMapper.selectById(id);
        if (comment == null) return Results.failure("404", "评论不存在");
        if (req.getContent() != null) comment.setContent(req.getContent());
        if (req.getResolved() != null) comment.setResolved(req.getResolved());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.updateById(comment);
        return Results.success();
    }

    @DeleteMapping("/comments/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("评论删除: commentId={}", id);
        SysDocComment comment = commentMapper.selectById(id);
        if (comment != null) {
            comment.setDeleted(1);
            commentMapper.updateById(comment);
        }
        return Results.success();
    }

    private Map<String, Object> commentToMap(SysDocComment c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("docId", c.getDocId());
        m.put("userId", c.getUserId());
        m.put("content", c.getContent());
        m.put("anchorIndex", c.getAnchorIndex());
        m.put("anchorLength", c.getAnchorLength());
        m.put("parentId", c.getParentId());
        m.put("resolved", c.getResolved());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }

    @Data
    static class CommentReq {
        private String content;
        private Integer anchorIndex;
        private Integer anchorLength;
        private Long parentId;
    }

    @Data
    static class CommentUpdateReq {
        private String content;
        private Integer resolved;
    }
}
