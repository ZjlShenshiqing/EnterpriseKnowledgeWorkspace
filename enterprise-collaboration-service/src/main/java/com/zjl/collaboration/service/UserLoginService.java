package com.zjl.collaboration.service;

import com.zjl.collaboration.dto.*;

public interface UserLoginService {

    UserLoginRespDTO login(UserLoginReqDTO requestParam);

    UserLoginRespDTO checkLogin(String accessToken);

    void logout(String accessToken);

    Boolean hasUserName(String username);

    UserRegisterRespDTO register(UserRegisterReqDTO requestParam);

    void deletion(UserDeletionReqDTO requestParam);
}
