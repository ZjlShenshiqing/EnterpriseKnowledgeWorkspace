package com.zjl.workbench.web;

import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final RestTemplate rt = new RestTemplate();

    @Value("${collab.service.url:http://localhost:8090}")
    private String collabUrl;

    @Value("${knowledge.service.url:http://localhost:8083}")
    private String knowledgeUrl;

    private static final String UA = "X-User-Id";
    private static final String AD = "X-Is-Admin";

    @GetMapping("/overview")
    public Result<Map<String,Object>> overview(@RequestHeader(UA) Long userId, @RequestHeader(value=AD,defaultValue="false") String isAdmin) {
        Map<String,Object> data = new LinkedHashMap<>();
        var headers = Map.of(UA, String.valueOf(userId), AD, isAdmin);

        // 从 collaboration 聚合
        try { data.put("todos", callList(collabUrl + "/api/todos", headers)); } catch (Exception e) { data.put("todos", List.of()); }
        try { data.put("meetings", callList(collabUrl + "/api/meetings", headers)); } catch (Exception e) { data.put("meetings", List.of()); }

        // 从 knowledge-ai 聚合
        try {
            var kbResp = rt.getForObject(knowledgeUrl + "/api/kb/documents?current=1&size=5", Map.class);
            if (kbResp != null && kbResp.get("data") instanceof Map kbData) {
                data.put("recentDocs", kbData.getOrDefault("records", List.of()));
                data.put("docCount", kbData.getOrDefault("total", 0));
            }
        } catch (Exception e) { data.put("recentDocs", List.of()); data.put("docCount", 0); }

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

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> callList(String url, Map<String,String> headers) {
        var resp = rt.getForObject(url, Map.class);
        if (resp != null && resp.get("data") instanceof List) return (List<Map<String,Object>>) resp.get("data");
        return List.of();
    }
}
