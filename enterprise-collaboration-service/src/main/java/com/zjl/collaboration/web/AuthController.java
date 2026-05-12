package com.zjl.collaboration.web;

import com.zjl.collaboration.dto.*;
import com.zjl.collaboration.service.UserLoginService;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserLoginService userLoginService;

    @PostMapping("/login")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO request) {
        return Results.success(userLoginService.login(request));
    }

    @GetMapping("/check-login")
    public Result<UserLoginRespDTO> checkLogin(@RequestParam String accessToken) {
        return Results.success(userLoginService.checkLogin(accessToken));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestParam String accessToken) {
        userLoginService.logout(accessToken);
        return Results.success();
    }

    @GetMapping("/has-username")
    public Result<Boolean> hasUserName(@RequestParam String username) {
        return Results.success(userLoginService.hasUserName(username));
    }

    @PostMapping("/register")
    public Result<UserRegisterRespDTO> register(@RequestBody UserRegisterReqDTO request) {
        return Results.success(userLoginService.register(request));
    }

    @PostMapping("/deletion")
    public Result<Void> deletion(@RequestBody UserDeletionReqDTO request) {
        userLoginService.deletion(request);
        return Results.success();
    }
}
