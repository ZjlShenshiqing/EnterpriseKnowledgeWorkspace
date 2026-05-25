package com.zjl.workbench.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.workbench.entity.WbFavorite;
import com.zjl.workbench.feign.CollaborationFeignClient;
import com.zjl.workbench.feign.KnowledgeFeignClient;
import com.zjl.workbench.mapper.WbFavoriteMapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final CollaborationFeignClient collabClient;
    private final KnowledgeFeignClient knowledgeClient;
    private final WbFavoriteMapper favoriteMapper;

    private static final String UA = "X-User-Id";
    private static final String AD = "X-Is-Admin";

    public WorkbenchController(CollaborationFeignClient collabClient,
                               KnowledgeFeignClient knowledgeClient,
                               WbFavoriteMapper favoriteMapper) {
        this.collabClient = collabClient;
        this.knowledgeClient = knowledgeClient;
        this.favoriteMapper = favoriteMapper;
    }

    @GetMapping("/overview")
    @Cacheable(value = "wb_overview", key = "#userId", unless = "#result.data.isEmpty()")
    public Result<Map<String, Object>> overview(
            @RequestHeader(UA) Long userId,
            @RequestHeader(value = AD, defaultValue = "false") String isAdmin) {

        Map<String, Object> data = new LinkedHashMap<>();

        // 待办列表
        try {
            Result<List<Map<String, Object>>> resp = collabClient.getTodos(userId, isAdmin);
            List<Map<String, Object>> todos = resp != null ? resp.getData() : List.of();
            data.put("todos", todos != null ? todos : List.of());
        } catch (FeignException e) {
            log.warn("获取待办失败: {}", e.getMessage());
            data.put("todos", List.of());
        }

        // 会议（使用 /api/meetings/my 与会议页保持一致）
        try {
            Result<List<Map<String, Object>>> resp = collabClient.getMyMeetings(userId, isAdmin, "");
            List<Map<String, Object>> meetings = resp != null ? resp.getData() : List.of();
            if (meetings == null) meetings = List.of();
            var today = java.time.LocalDate.now().toString();
            var todayMeetings = meetings.stream()
                    .filter(m -> today.equals(String.valueOf(m.get("date"))))
                    .toList();
            data.put("meetings", meetings);
            data.put("todayMeetings", todayMeetings);
            data.put("meetingCount", todayMeetings.size());
        } catch (FeignException e) {
            log.warn("获取会议失败: {}", e.getMessage());
            data.put("meetings", List.of());
            data.put("todayMeetings", List.of());
            data.put("meetingCount", 0);
        }

        // 最近文档（知识库）
        try {
            Result<Map<String, Object>> resp = knowledgeClient.getDocuments(userId, isAdmin, 1, 5);
            if (resp != null && resp.getData() != null) {
                Map<String, Object> kbData = resp.getData();
                Object records = kbData.get("records");
                if (records instanceof List list) {
                    List<Map<String, Object>> docs = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map m) {
                            Map<String, Object> doc = new LinkedHashMap<>(m);
                            doc.put("docType", "knowledge");
                            docs.add(doc);
                        }
                    }
                    data.put("recentDocs", docs);
                    data.put("documentCount", kbData.getOrDefault("total", 0));
                } else {
                    data.put("recentDocs", List.of());
                    data.put("documentCount", 0);
                }
            } else {
                data.put("recentDocs", List.of());
                data.put("documentCount", 0);
            }
        } catch (FeignException e) {
            log.warn("获取文档失败: {}", e.getMessage());
            data.put("recentDocs", List.of());
            data.put("documentCount", 0);
        }

        // 待办数量
        try {
            Result<List<Map<String, Object>>> resp = collabClient.getTodos(userId, isAdmin);
            List<Map<String, Object>> todoList = resp != null ? resp.getData() : List.of();
            if (todoList == null) todoList = List.of();
            data.put("todoCount", todoList.stream().filter(t -> {
                Object d = t.get("done");
                return d == null || "0".equals(d.toString()) || Boolean.FALSE.equals(d);
            }).count());
        } catch (FeignException e) {
            log.warn("获取待办统计失败: {}", e.getMessage());
            data.put("todoCount", 0);
        }

        // 任务进行中数量
        try {
            Result<List<Map<String, Object>>> resp = collabClient.getTasks(userId, isAdmin);
            List<Map<String, Object>> tasks = resp != null ? resp.getData() : List.of();
            if (tasks == null) tasks = List.of();
            data.put("inProgressTaskCount", tasks.stream().filter(t -> {
                Object s = t.get("status");
                return "todo".equals(s) || "in_progress".equals(s);
            }).count());
        } catch (FeignException e) {
            log.warn("获取任务统计失败: {}", e.getMessage());
            data.put("inProgressTaskCount", 0);
        }

        // 审批统计
        try {
            Result<List<Map<String, Object>>> resp = collabClient.getApprovals(userId, isAdmin);
            List<Map<String, Object>> approvals = resp != null ? resp.getData() : List.of();
            if (approvals == null) approvals = List.of();
            data.put("pendingApprovalCount", approvals.stream().filter(a -> {
                Object s = a.get("status");
                return s == null || (!"approved".equals(s.toString()) && !"rejected".equals(s.toString()));
            }).count());
        } catch (FeignException e) {
            log.warn("获取审批统计失败: {}", e.getMessage());
            data.put("pendingApprovalCount", 0);
        }

        // 未读消息数
        try {
            Result<Integer> resp = collabClient.getUnreadCount(userId, isAdmin);
            data.put("unreadMessageCount", resp != null && resp.getData() != null ? resp.getData() : 0);
        } catch (FeignException e) {
            log.warn("获取未读消息数失败: {}", e.getMessage());
            data.put("unreadMessageCount", 0);
        }

        // 知识库数量
        try {
            Result<Map<String, Object>> resp = knowledgeClient.getBases(userId, isAdmin, 1, 1);
            if (resp != null && resp.getData() instanceof Map) {
                Map basesData = (Map) resp.getData();
                data.put("baseCount", basesData.getOrDefault("total", 0));
            } else {
                data.put("baseCount", 0);
            }
        } catch (FeignException e) {
            log.warn("获取知识库统计失败: {}", e.getMessage());
            data.put("baseCount", 0);
        }

        // 意图配置数量
        try {
            Result<Map<String, Object>> resp = collabClient.getIntents(userId, isAdmin, 1, 1);
            if (resp != null && resp.getData() instanceof Map) {
                Map intentsData = (Map) resp.getData();
                data.put("intentCount", intentsData.getOrDefault("total", 0));
            } else {
                data.put("intentCount", 0);
            }
        } catch (FeignException e) {
            log.warn("获取意图统计失败: {}", e.getMessage());
            data.put("intentCount", 0);
        }

        // 今日会话数量
        try {
            Result<Map<String, Object>> resp = knowledgeClient.getAgentSessions(userId, isAdmin, 1, 1);
            if (resp != null && resp.getData() instanceof Map) {
                Map sessionsData = (Map) resp.getData();
                data.put("todaySessionCount", sessionsData.getOrDefault("total", 0));
            } else {
                data.put("todaySessionCount", 0);
            }
        } catch (FeignException e) {
            log.warn("获取会话统计失败: {}", e.getMessage());
            data.put("todaySessionCount", 0);
        }

        return Results.success(data);
    }

    @GetMapping("/stats")
    @Cacheable(value = "wb_stats", key = "'global'", unless = "#result.data.isEmpty()")
    public Result<Map<String, Object>> stats(
            @RequestHeader(UA) Long userId,
            @RequestHeader(value = AD, defaultValue = "false") String isAdmin) {

        Map<String, Object> data = new LinkedHashMap<>();

        try {
            Result<List<Map<String, Object>>> resp = collabClient.getTasks(userId, isAdmin);
            List<Map<String, Object>> tasks = resp != null ? resp.getData() : List.of();
            if (tasks == null) tasks = List.of();
            long todo = 0, inProgress = 0, review = 0, done = 0;
            for (var t : tasks) {
                String s = String.valueOf(t.get("status"));
                switch (s) {
                    case "todo" -> todo++;
                    case "in_progress" -> inProgress++;
                    case "review" -> review++;
                    case "done" -> done++;
                }
            }
            data.put("taskStats", Map.of("todo", todo, "inProgress", inProgress,
                    "review", review, "done", done, "total", tasks.size()));
        } catch (FeignException e) {
            log.warn("获取任务统计失败: {}", e.getMessage());
            data.put("taskStats", Map.of());
        }

        try {
            Result<List<Map<String, Object>>> resp = collabClient.getApprovals(userId, isAdmin);
            List<Map<String, Object>> approvals = resp != null ? resp.getData() : List.of();
            if (approvals == null) approvals = List.of();
            long pending = 0, approved = 0, rejected = 0;
            for (var a : approvals) {
                String s = String.valueOf(a.get("status"));
                if ("approved".equals(s)) approved++;
                else if ("rejected".equals(s)) rejected++;
                else pending++;
            }
            data.put("approvalStats", Map.of("pending", pending, "approved", approved,
                    "rejected", rejected, "total", approvals.size()));
        } catch (FeignException e) {
            log.warn("获取审批统计失败: {}", e.getMessage());
            data.put("approvalStats", Map.of());
        }

        try {
            Result<List<Map<String, Object>>> resp = collabClient.getMyMeetings(userId, isAdmin, "");
            List<Map<String, Object>> meetings = resp != null ? resp.getData() : List.of();
            if (meetings == null) meetings = List.of();
            var today = java.time.LocalDate.now().toString();
            long todayMeetings = meetings.stream()
                    .filter(m -> today.equals(String.valueOf(m.get("date"))))
                    .count();
            data.put("meetingStats", Map.of("today", todayMeetings, "total", meetings.size()));
        } catch (FeignException e) {
            log.warn("获取会议统计失败: {}", e.getMessage());
            data.put("meetingStats", Map.of());
        }

        try {
            Result<Map<String, Object>> resp = knowledgeClient.getDocuments(userId, isAdmin, 1, 1);
            if (resp != null && resp.getData() instanceof Map) {
                Map kbData = (Map) resp.getData();
                data.put("docCount", kbData.getOrDefault("total", 0));
            } else {
                data.put("docCount", 0);
            }
        } catch (FeignException e) {
            log.warn("获取文档统计失败: {}", e.getMessage());
            data.put("docCount", 0);
        }

        return Results.success(data);
    }

    @GetMapping("/favorites")
    public Result<List<WbFavorite>> listFavorites(@RequestHeader(UA) Long userId) {
        List<WbFavorite> list = favoriteMapper.selectList(
                new LambdaQueryWrapper<WbFavorite>().eq(WbFavorite::getUserId, userId)
                        .orderByDesc(WbFavorite::getCreatedAt));
        return Results.success(list);
    }

    @PostMapping("/favorites")
    public Result<WbFavorite> addFavorite(@RequestHeader(UA) Long userId,
                                          @RequestBody Map<String, Object> body) {
        WbFavorite f = new WbFavorite();
        f.setUserId(userId);
        f.setItemType((String) body.get("itemType"));
        f.setItemId(Long.valueOf(body.get("itemId").toString()));
        f.setTitle((String) body.get("title"));
        favoriteMapper.insert(f);
        return Results.success(f);
    }

    @DeleteMapping("/favorites/{id}")
    public Result<Void> removeFavorite(@RequestHeader(UA) Long userId,
                                       @PathVariable Long id) {
        favoriteMapper.delete(
                new LambdaQueryWrapper<WbFavorite>()
                        .eq(WbFavorite::getId, id)
                        .eq(WbFavorite::getUserId, userId));
        return Results.success();
    }

    @DeleteMapping("/cache")
    @CacheEvict(value = {"wb_overview", "wb_stats"}, allEntries = true)
    public Result<Void> clearCache() {
        return Results.success();
    }

    @DeleteMapping("/cache/user/{userId}")
    @CacheEvict(value = "wb_overview", key = "#userId")
    public Result<Void> clearUserCache(@PathVariable Long userId) {
        return Results.success();
    }
}
