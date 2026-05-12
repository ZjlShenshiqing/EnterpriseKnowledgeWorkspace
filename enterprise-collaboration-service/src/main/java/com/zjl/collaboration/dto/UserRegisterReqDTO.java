package com.zjl.collaboration.dto;

import lombok.Data;

@Data
public class UserRegisterReqDTO {
    private String username;
    private String password;
    private String realName;
    private Long deptId;
}
