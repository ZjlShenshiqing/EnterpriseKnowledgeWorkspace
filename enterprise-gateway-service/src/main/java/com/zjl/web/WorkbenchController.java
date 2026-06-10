package com.zjl.web;

import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final WebClient webClient;

    private static final String UA = "X-User-Id";
    private static final String AD = "X-Is-Admin";

    public WorkbenchController(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @GetMapping("/overview")
    public Mono<Result<Map<String, Object>>> overview() {
        Long userId = UserContext.userId();
        String isAdmin = String.valueOf(Boolean.TRUE.equals(UserContext.isAdmin()));

        Map<String, Object> data = new LinkedHashMap<>();

        return fetchTodos(userId, isAdmin)
                .doOnNext(todos -> data.put("todos", todos))
                .then(fetchMeetings(userId, isAdmin)
                        .doOnNext(meetings -> {
                            data.put("meetings", meetings);
                            String today = LocalDate.now().toString();
                            List<Map> todayList = new ArrayList<>();
                            for (Object m : meetings) {
                                if (m instanceof Map<?, ?> map && today.equals(String.valueOf(map.get("date")))) {
                                    todayList.add((Map) m);
                                }
                            }
                            data.put("todayMeetings", todayList);
                            data.put("meetingCount", todayList.size());
                        }))
                .then(fetchRecentDocs(userId, isAdmin)
                        .doOnNext(docData -> {
                            Object records = docData.get("records");
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
                            } else {
                                data.put("recentDocs", List.of());
                            }
                            data.put("documentCount", docData.getOrDefault("total", 0));
                        }))
                .then(fetchTodos(userId, isAdmin)
                        .doOnNext(todos -> {
                            long count = todos.stream().filter(t -> {
                                Object d = ((Map<?, ?>) t).get("done");
                                return d == null || "0".equals(d.toString()) || Boolean.FALSE.equals(d);
                            }).count();
                            data.put("todoCount", count);
                        }))
                .then(fetchTasks(userId, isAdmin)
                        .doOnNext(tasks -> {
                            long count = tasks.stream().filter(t -> {
                                Object s = ((Map<?, ?>) t).get("status");
                                return "todo".equals(s) || "in_progress".equals(s);
                            }).count();
                            data.put("inProgressTaskCount", count);
                        }))
                .then(fetchApprovals(userId, isAdmin)
                        .doOnNext(approvals -> {
                            long count = approvals.stream().filter(a -> {
                                Object s = ((Map<?, ?>) a).get("status");
                                return s == null || (!"approved".equals(s.toString()) && !"rejected".equals(s.toString()));
                            }).count();
                            data.put("pendingApprovalCount", count);
                        }))
                .then(fetchUnreadCount(userId, isAdmin)
                        .doOnNext(count -> data.put("unreadMessageCount", count)))
                .then(fetchBaseCount(userId, isAdmin)
                        .doOnNext(count -> data.put("baseCount", count)))
                .then(fetchIntentCount(userId, isAdmin)
                        .doOnNext(count -> data.put("intentCount", count)))
                .then(fetchSessionCount(userId, isAdmin)
                        .doOnNext(count -> data.put("todaySessionCount", count)))
                .thenReturn(Results.success(data));
    }

    @GetMapping("/stats")
    public Mono<Result<Map<String, Object>>> stats() {
        Long userId = UserContext.userId();
        String isAdmin = String.valueOf(Boolean.TRUE.equals(UserContext.isAdmin()));

        Map<String, Object> data = new LinkedHashMap<>();

        return fetchTasks(userId, isAdmin)
                .doOnNext(tasks -> {
                    long todo = 0, inProgress = 0, review = 0, done = 0;
                    for (Object t : tasks) {
                        String s = String.valueOf(((Map<?, ?>) t).get("status"));
                        switch (s) {
                            case "todo" -> todo++;
                            case "in_progress" -> inProgress++;
                            case "review" -> review++;
                            case "done" -> done++;
                        }
                    }
                    data.put("taskStats", Map.of("todo", todo, "inProgress", inProgress,
                            "review", review, "done", done, "total", tasks.size()));
                })
                .then(fetchApprovals(userId, isAdmin)
                        .doOnNext(approvals -> {
                            long pending = 0, approved = 0, rejected = 0;
                            for (Object a : approvals) {
                                String s = String.valueOf(((Map<?, ?>) a).get("status"));
                                if ("approved".equals(s)) approved++;
                                else if ("rejected".equals(s)) rejected++;
                                else pending++;
                            }
                            data.put("approvalStats", Map.of("pending", pending, "approved", approved,
                                    "rejected", rejected, "total", approvals.size()));
                        }))
                .then(fetchMeetings(userId, isAdmin)
                        .doOnNext(meetings -> {
                            String today = LocalDate.now().toString();
                            long todayCount = meetings.stream()
                                    .filter(m -> today.equals(String.valueOf(((Map<?, ?>) m).get("date"))))
                                    .count();
                            data.put("meetingStats", Map.of("today", todayCount, "total", meetings.size()));
                        }))
                .then(fetchDocCount(userId, isAdmin)
                        .doOnNext(count -> data.put("docCount", count)))
                .thenReturn(Results.success(data));
    }

    @SuppressWarnings("unchecked")
    private Mono<List<Map>> fetchTodos(Long userId, String isAdmin) {
        return callList("lb://enterprise-collaboration-service/api/todos", userId, isAdmin);
    }

    @SuppressWarnings("unchecked")
    private Mono<List<Map>> fetchMeetings(Long userId, String isAdmin) {
        return callList("lb://enterprise-collaboration-service/api/meetings/my?userName=", userId, isAdmin);
    }

    @SuppressWarnings("unchecked")
    private Mono<List<Map>> fetchTasks(Long userId, String isAdmin) {
        return callList("lb://enterprise-collaboration-service/api/tasks", userId, isAdmin);
    }

    @SuppressWarnings("unchecked")
    private Mono<List<Map>> fetchApprovals(Long userId, String isAdmin) {
        return callList("lb://enterprise-collaboration-service/api/approvals", userId, isAdmin);
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> fetchRecentDocs(Long userId, String isAdmin) {
        return callMap("lb://enterprise-knowledge-ai-service/api/kb/documents?current=1&size=5",
                userId, isAdmin);
    }

    private Mono<Long> fetchUnreadCount(Long userId, String isAdmin) {
        return webClient.get()
                .uri("lb://enterprise-collaboration-service/api/chat/unread-count")
                .header(UA, String.valueOf(userId))
                .header(AD, isAdmin)
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> {
                    Object d = resp.get("data");
                    if (d instanceof Number n) return n.longValue();
                    return 0L;
                })
                .onErrorReturn(0L);
    }

    @SuppressWarnings("unchecked")
    private Mono<Long> fetchBaseCount(Long userId, String isAdmin) {
        return callMap("lb://enterprise-knowledge-ai-service/api/kb/bases?current=1&size=1",
                userId, isAdmin)
                .map(m -> {
                    Object total = m.get("total");
                    if (total instanceof Number n) return n.longValue();
                    return 0L;
                })
                .onErrorReturn(0L);
    }

    @SuppressWarnings("unchecked")
    private Mono<Long> fetchIntentCount(Long userId, String isAdmin) {
        return callCount("lb://enterprise-collaboration-service/api/intents/nodes", userId, isAdmin);
    }

    @SuppressWarnings("unchecked")
    private Mono<Long> fetchSessionCount(Long userId, String isAdmin) {
        return callCount("lb://enterprise-knowledge-ai-service/api/kb/agent/sessions", userId, isAdmin);
    }

    @SuppressWarnings("unchecked")
    private Mono<Long> fetchDocCount(Long userId, String isAdmin) {
        return callCount("lb://enterprise-knowledge-ai-service/api/kb/documents?current=1&size=1",
                userId, isAdmin);
    }

    private Mono<Long> callCount(String uri, Long userId, String isAdmin) {
        return webClient.get()
                .uri(uri)
                .header(UA, String.valueOf(userId))
                .header(AD, isAdmin)
                .retrieve()
                .bodyToMono(Map.class)
                .map(WorkbenchController::countDataItems)
                .onErrorReturn(0L);
    }

    @SuppressWarnings("unchecked")
    private Mono<List<Map>> callList(String uri, Long userId, String isAdmin) {
        return webClient.get()
                .uri(uri)
                .header(UA, String.valueOf(userId))
                .header(AD, isAdmin)
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> {
                    Object data = resp.get("data");
                    if (data instanceof List list) return (List<Map>) list;
                    return List.<Map>of();
                })
                .onErrorReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> callMap(String uri, Long userId, String isAdmin) {
        return webClient.get()
                .uri(uri)
                .header(UA, String.valueOf(userId))
                .header(AD, isAdmin)
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> {
                    Object data = resp.get("data");
                    if (data instanceof Map map) return (Map<String, Object>) map;
                    return Map.<String, Object>of();
                })
                .onErrorReturn(Map.of());
    }

    static long countDataItems(Map<String, Object> response) {
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            Object total = map.get("total");
            if (total instanceof Number n) {
                return n.longValue();
            }
            Object records = map.get("records");
            if (records instanceof List<?> list) {
                return list.size();
            }
            return 0L;
        }
        if (data instanceof List<?> list) {
            return list.size();
        }
        if (data instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }
}
