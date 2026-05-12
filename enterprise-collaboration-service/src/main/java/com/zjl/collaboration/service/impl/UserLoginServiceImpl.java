package com.zjl.collaboration.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.dto.*;
import com.zjl.collaboration.entity.SysUser;
import com.zjl.collaboration.mapper.SysUserMapper;
import com.zjl.collaboration.service.UserLoginService;
import com.zjl.collaboration.util.JwtUtil;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class UserLoginServiceImpl implements UserLoginService {

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Set<String> tokenBlacklist = ConcurrentHashMap.newKeySet();

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO req) {
        if (!StringUtils.hasText(req.getUsername()) || !StringUtils.hasText(req.getPassword())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名和密码不能为空");
        }
        SysUser user = sysUserMapper.selectOne(
                Wrappers.lambdaQuery(SysUser.class).eq(SysUser::getUsername, req.getUsername()));
        if (user == null || user.getEnabled() == 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("isAdmin", user.getIsAdmin() != null && user.getIsAdmin() == 1);
        String token = jwtUtil.generate(claims);

        UserLoginRespDTO resp = new UserLoginRespDTO();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRealName(user.getRealName());
        resp.setIsAdmin(user.getIsAdmin() != null && user.getIsAdmin() == 1);
        resp.setDeptId(user.getDeptId());
        resp.setAccessToken(token);
        return resp;
    }

    @Override
    public UserLoginRespDTO checkLogin(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Token 不能为空");
        }
        if (tokenBlacklist.contains(accessToken)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Token 已失效");
        }
        try {
            Claims claims = jwtUtil.parse(accessToken);
            Long userId = claims.get("userId", Long.class);
            SysUser user = sysUserMapper.selectById(userId);
            if (user == null || user.getEnabled() == 0) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在或已禁用");
            }
            UserLoginRespDTO resp = new UserLoginRespDTO();
            resp.setUserId(user.getId());
            resp.setUsername(user.getUsername());
            resp.setRealName(user.getRealName());
            resp.setIsAdmin(user.getIsAdmin() != null && user.getIsAdmin() == 1);
            resp.setDeptId(user.getDeptId());
            return resp;
        } catch (BizException e) { throw e;
        } catch (Exception e) { throw new BizException(ErrorCode.UNAUTHORIZED, "Token 无效或已过期"); }
    }

    @Override
    public void logout(String accessToken) {
        if (StringUtils.hasText(accessToken)) tokenBlacklist.add(accessToken);
    }

    @Override
    public Boolean hasUserName(String username) {
        if (!StringUtils.hasText(username)) return false;
        Long count = sysUserMapper.selectCount(
                Wrappers.lambdaQuery(SysUser.class).eq(SysUser::getUsername, username));
        return count != null && count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserRegisterRespDTO register(UserRegisterReqDTO req) {
        if (!StringUtils.hasText(req.getUsername()) || !StringUtils.hasText(req.getPassword())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名和密码不能为空");
        }
        if (hasUserName(req.getUsername())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRealName(req.getRealName());
        user.setDeptId(req.getDeptId());
        user.setIsAdmin(0);
        user.setEnabled(1);
        sysUserMapper.insert(user);

        UserRegisterRespDTO resp = new UserRegisterRespDTO();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletion(UserDeletionReqDTO req) {
        if (!StringUtils.hasText(req.getUsername()) || !StringUtils.hasText(req.getPassword())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户名和密码不能为空");
        }
        SysUser user = sysUserMapper.selectOne(
                Wrappers.lambdaQuery(SysUser.class).eq(SysUser::getUsername, req.getUsername()));
        if (user == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码错误");
        }
        sysUserMapper.deleteById(user.getId());
    }
}
