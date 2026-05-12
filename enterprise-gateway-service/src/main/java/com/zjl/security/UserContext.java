package com.zjl.security;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserContext {
    private final Long userId;
    private final Long departmentId;
    private final Long projectId;
    private final boolean admin;

    public static UserContext build(Long userId, boolean isAdmin) {
        return UserContext.builder().userId(userId).admin(isAdmin).build();
    }
}
