package com.zjl.workbench.web;

import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${collab.service.url:http://enterprise-collaboration-service}")
    private String collabUrl;

    @Value("${knowledge.service.url:http://enterprise-knowledge-ai-service}")
    private String knowledgeUrl;

    public WorkbenchController(RestTemplate restTemplate) {
        this.rt = restTemplate;
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
        try { data.put("meetings", callList(collabUrl + "/api/meetings", headers)); } catch (Exception e) { data.put("meetings", List.of()); }

        // 从 knowledge-ai 聚合
        try {
            var kbResp = callForObject(knowledgeUrl + "/api/kb/documents?current=1&size=5", headers, Map.class);
            if (kbResp != null) {
                Object dataObj = kbResp.get("data");
                if (dataObj instanceof Map kbData) {
                    data.put("recentDocs", kbData.getOrDefault("records", List.of()));
                    data.put("docCount", kbData.getOrDefault("total", 0));
                }
            }
        } catch (Exception e) {
            data.put("recentDocs", List.of()); data.put("docCount", 0);
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
            var meetings = callList(collabUrl + "/api/meetings", headers);
            data.put("meetingCount", meetings.size());
        } catch (Exception e) { data.put("meetingCount", 0); }

        try {
            var tasks = callList(collabUrl + "/api/tasks", headers);
            data.put("inProgressTaskCount", tasks.stream().filter(t -> {
                Object s = ((Map<?,?>)t).get("status");
                return "todo".equals(s) || "in_progress".equals(s);
            }).count());
        } catch (Exception e) { data.put("inProgressTaskCount", 0); }

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

    private HttpHeaders toHttpHeaders(Map<String,String> headers) {
        HttpHeaders h = new HttpHeaders();
        if (headers != null) headers.forEach(h::set);
        return h;
    }
}
