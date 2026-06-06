package com.zjl.collaboration.service.impl;

import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.service.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 联系人业务服务实现。
 */
@Service
@RequiredArgsConstructor
public class ContactServiceImpl implements ContactService {

    private final GatewayUserClient gatewayUserClient;

    @Override
    public List<Map<String, Object>> listDepartments() {
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> listUsers(Long deptId, String keyword, int limit) {
        List<UserInfo> users = keyword != null && !keyword.isBlank()
                ? gatewayUserClient.search(keyword, limit)
                : gatewayUserClient.search("", limit);
        return users.stream()
                .filter(user -> deptId == null || deptId.equals(user.deptId()))
                .map(user -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", user.userId());
                    item.put("username", user.username());
                    item.put("realName", user.realName());
                    item.put("deptId", user.deptId());
                    return item;
                })
                .toList();
    }
}
