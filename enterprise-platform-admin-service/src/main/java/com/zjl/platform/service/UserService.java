package com.zjl.platform.service;

import com.zjl.common.response.PageResult;
import com.zjl.platform.dto.UserInfoDTO;
import com.zjl.platform.entity.SysUser;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface UserService {

    PageResult<SysUser> listUsers(String keyword, int page, int size);

    Map<Long, UserInfoDTO> batchGetUsers(List<Long> userIds);

    List<UserInfoDTO> searchUsers(String keyword, int limit);

    UserStats getUserStats();

    SysUser getUser(Long id);

    SysUser createUser(String username, String password, String realName,
                       Long deptId, Set<String> roleCodes);

    SysUser updateUser(Long id, String realName, Long deptId,
                       Boolean enabled, Set<String> roleCodes);

    SysUser updateUserRoles(Long id, Set<String> roleCodes);

    void deleteUser(Long id);

    record UserStats(long total, long enabled, long admin, long disabled) {}
}
