package com.zjl.collaboration.web;

import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final GatewayUserClient gatewayUserClient;

    @GetMapping("/departments")
    public Result<List<Map<String, Object>>> listDepartments() {
        return Results.success(Collections.emptyList());
    }

    @GetMapping("/users")
    public Result<List<Map<String, Object>>> listUsers(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "200") int limit) {
        List<UserInfo> users;
        if (keyword != null && !keyword.isBlank()) {
            users = gatewayUserClient.search(keyword, limit);
        } else {
            users = gatewayUserClient.search("", limit);
        }
        List<Map<String, Object>> result = users.stream()
                .filter(u -> deptId == null || deptId.equals(u.deptId()))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.userId());
                    m.put("username", u.username());
                    m.put("realName", u.realName());
                    m.put("deptId", u.deptId());
                    return m;
                })
                .toList();
        return Results.success(result);
    }
}
