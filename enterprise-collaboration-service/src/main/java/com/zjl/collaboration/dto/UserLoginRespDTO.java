package com.zjl.collaboration.dto;

import lombok.Data;

@Data
public class UserLoginRespDTO {
    private Long userId;
    private String username;
    private String realName;
    private Boolean isAdmin;
    private Long deptId;
    private String accessToken;
}
