package com.zjl.workbench.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.workbench.entity.WbFavorite;
import com.zjl.workbench.mapper.WbFavoriteMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final RestTemplate rt;
    private final WbFavoriteMapper favoriteMapper;
    private final CacheManager cacheManager;

    @Value("${collab.service.url:http://enterprise-collaboration-service}")
    private String collabUrl;

    @Value("${knowledge.service.url:http://enterprise-knowledge-ai-service}")
    private String knowledgeUrl;

    public WorkbenchController(RestTemplate restTemplate, WbFavoriteMapper favoriteMapper, CacheManager cacheManager) {
        this.rt = restTemplate;
        this.favoriteMapper = favoriteMapper;
        this.cacheManager = cacheManager;
    }

    private static final String UA = "X-User-Id";
    private static final String AD = "X-Is-Admin";

    @GetMapping("/overview")
    @Cacheable(value = "wb_overview", key = "#userId", unless = "#result.data.isEmpty()")
    public Result<Map<String,Object>> overview(@RequestHeader(UA) Long userId, @RequestHeader(value=AD,defaultValue="false") String isAdmin) {
        Map<String,Object> data = new LinkedHashMap<>();
        var headers = Map.of(UA, String.valueOf(userId), AD, isAdmin);

        // 从 collaboration 聚合
        try { data.put("todos", callList(collabUrl + "/api/todos", headers)); } catch (Exception e) { data.put("todos", List.of()); }
        try { data.put("meetings", callList(collabUrl + "/api/meetings/my", headers)); } catch (Exception e) { data.put("meetings", List.of()); }

        // 从 knowledge-ai 聚合
        try {
            var kbResp = callForObject(knowledgeUrl + "/api/kb/documents?current=1&size=5", headers, Map.class);
            if (kbResp != null) {
                Object dataObj = kbResp.get("data");
                if (dataObj instanceof Map kbData) {
                    Object records = kbData.get("records");
                    if (records instanceof List list) {
                        List<Map<String,Object>> docs = new ArrayList<>();
                        for (Object item : list) {
                            if (item instanceof Map m) {
                                Map<String,Object> doc = new LinkedHashMap<>(m);
                                doc.put("docType", "knowledge");
                                docs.add(doc);
                            }
                        }
                        data.put("recentDocs", docs);
                        data.put("documentCount", kbData.getOrDefault("total", 0));
                    }
                }
            }
        } catch (Exception e) {
            data.put("recentDocs", List.of()); data.put("documentCount", 0);
        }

        // 从 collaboration 统计
        try {
            var todoList = callList(collabUrl + "/api/todos", headers);
            data.put("todoCount", todoList.stream().filter(t -> {
                Object d = ((Map<?,?>)t).get("done");
                return d == null || "0".equals(d.toString()) || Boolean.FALSE.equals(d);
            }).count());
        } catch (Exception e) { data.put("todoCount", 0); }

        try {
            var meetings = callList(collabUrl + "/api/meetings/my", headers);
            data.put("meetingCount", meetings.size());
        } catch (Exception e) { data.put("meetingCount", 0); }

        try {
            var tasks = callList(collabUrl + "/api/tasks", headers);
            data.put("inProgressTaskCount", tasks.stream().filter(t -> {
                Object s = ((Map<?,?>)t).get("status");
                return "todo".equals(s) || "in_progress".equals(s);
            }).count());
        } catch (Exception e) { data.put("inProgressTaskCount", 0); }

        // 审批统计
        try {
            var approvals = callList(collabUrl + "/api/approvals", headers);
            long pendingApprovals = approvals.stream().filter(a -> {
                Object s = ((Map<?,?>)a).get("status");
                return s == null || (!"approved".equals(s.toString()) && !"rejected".equals(s.toString()));
            }).count();
            data.put("pendingApprovalCount", pendingApprovals);
        } catch (Exception e) { data.put("pendingApprovalCount", 0); }

        // 未读消息数
        try {
            var unreadResp = callForObject(collabUrl + "/api/im/unread-count?userId=" + userId, Map.of(UA, String.valueOf(userId)), Map.class);
            if (unreadResp != null && unreadResp.get("data") instanceof Number n) {
                data.put("unreadMessageCount", n.intValue());
            } else {
                data.put("unreadMessageCount", 0);
            }
        } catch (Exception e) { data.put("unreadMessageCount", 0); }

        // 知识库数量
        try {
            var basesResp = callForObject(knowledgeUrl + "/api/kb/bases?current=1&size=1", headers, Map.class);
            if (basesResp != null && basesResp.get("data") instanceof Map basesData) {
                data.put("baseCount", basesData.getOrDefault("total", 0));
            }
        } catch (Exception e) { data.put("baseCount", 0); }

        // 意图配置数量
        try {
            var intentsResp = callForObject(collabUrl + "/api/intents?current=1&size=1", headers, Map.class);
            if (intentsResp != null && intentsResp.get("data") instanceof Map intentsData) {
                data.put("intentCount", intentsData.getOrDefault("total", 0));
            }
        } catch (Exception e) { data.put("intentCount", 0); }

        // 今日会话数量
        try {
            var sessionsResp = callForObject(knowledgeUrl + "/api/kb/agent/sessions?current=1&size=1", headers, Map.class);
            if (sessionsResp != null && sessionsResp.get("data") instanceof Map sessionsData) {
                data.put("todaySessionCount", sessionsData.getOrDefault("total", 0));
            }
        } catch (Exception e) { data.put("todaySessionCount", 0); }

        return Results.success(data);
    }

    @GetMapping("/stats")
    @Cacheable(value = "wb_stats", key = "'global'", unless = "#result.data.isEmpty()")
    public Result<Map<String,Object>> stats(@RequestHeader(UA) Long userId, @RequestHeader(value=AD,defaultValue="false") String isAdmin) {
        Map<String,Object> data = new LinkedHashMap<>();
        var headers = Map.of(UA, String.valueOf(userId), AD, isAdmin);

        try {
            var tasks = callList(collabUrl + "/api/tasks", headers);
            long todo=0, inProgress=0, review=0, done=0;
            for (var t : tasks) {
                String s = String.valueOf(((Map<?,?>)t).get("status"));
                switch(s) { case "todo": todo++; break; case "in_progress": inProgress++; break; case "review": review++; break; case "done": done++; break; }
            }
            data.put("taskStats", Map.of("todo",todo,"inProgress",inProgress,"review",review,"done",done,"total",tasks.size()));
        } catch(Exception e) { data.put("taskStats", Map.of()); }

        try {
            var approvals = callList(collabUrl + "/api/approvals", headers);
            long pending=0, approved=0, rejected=0;
            for (var a : approvals) {
                String s = String.valueOf(((Map<?,?>)a).get("status"));
                if ("approved".equals(s)) approved++; else if ("rejected".equals(s)) rejected++; else pending++;
            }
            data.put("approvalStats", Map.of("pending",pending,"approved",approved,"rejected",rejected,"total",approvals.size()));
        } catch(Exception e) { data.put("approvalStats", Map.of()); }

        try {
            var meetings = callList(collabUrl + "/api/meetings", headers);
            var today = java.time.LocalDate.now().toString();
            long todayMeetings = meetings.stream().filter(m -> today.equals(String.valueOf(((Map<?,?>)m).get("date")))).count();
            data.put("meetingStats", Map.of("today",todayMeetings,"total",meetings.size()));
        } catch(Exception e) { data.put("meetingStats", Map.of()); }

        try {
            var kbResp = callForObject(knowledgeUrl + "/api/kb/documents?current=1&size=1", headers, Map.class);
            if (kbResp != null && kbResp.get("data") instanceof Map kbData) {
                data.put("docCount", kbData.getOrDefault("total", 0));
            }
        } catch(Exception e) { data.put("docCount", 0); }

        return Results.success(data);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> callList(String url, Map<String,String> headers) {
        var entity = new HttpEntity<>(toHttpHeaders(headers));
        var resp = rt.exchange(url, HttpMethod.GET, entity, Map.class).getBody();
        if (resp != null && resp.get("data") instanceof List) return (List<Map<String,Object>>) resp.get("data");
        return List.of();
    }

    private <T> T callForObject(String url, Map<String,String> headers, Class<T> type) {
        var entity = new HttpEntity<>(toHttpHeaders(headers));
        return rt.exchange(url, HttpMethod.GET, entity, type).getBody();
    }

    @DeleteMapping("/cache/overview")
    public Result<Void> clearOverviewCache(@RequestHeader(UA) Long userId) {
        var cache = cacheManager.getCache("wb_overview");
        if (cache != null) {
            cache.evict(userId);
        }
        return Results.success();
    }

    @GetMapping("/favorites")
    public Result<List<WbFavorite>> listFavorites(@RequestHeader(UA) Long userId) {
        List<WbFavorite> list = favoriteMapper.selectList(
                new LambdaQueryWrapper<WbFavorite>().eq(WbFavorite::getUserId, userId)
                        .orderByDesc(WbFavorite::getCreatedAt));
        return Results.success(list);
    }

    @PostMapping("/favorites")
    public Result<WbFavorite> addFavorite(@RequestHeader(UA) Long userId, @RequestBody Map<String,Object> body) {
        WbFavorite f = new WbFavorite();
        f.setUserId(userId);
        f.setItemType((String) body.get("itemType"));
        f.setItemId(Long.valueOf(body.get("itemId").toString()));
        f.setTitle((String) body.get("title"));
        favoriteMapper.insert(f);
        return Results.success(f);
    }

    @DeleteMapping("/favorites/{id}")
    public Result<Void> removeFavorite(@RequestHeader(UA) Long userId, @PathVariable Long id) {
        favoriteMapper.delete(
                new LambdaQueryWrapper<WbFavorite>()
                        .eq(WbFavorite::getId, id)
                        .eq(WbFavorite::getUserId, userId));
        return Results.success();
    }

    private HttpHeaders toHttpHeaders(Map<String,String> headers) {
        HttpHeaders h = new HttpHeaders();
        if (headers != null) headers.forEach(h::set);
        return h;
    }
}
