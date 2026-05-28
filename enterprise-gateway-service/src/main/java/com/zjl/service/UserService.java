package com.zjl.service;

import com.zjl.domain.SysUser;
import com.zjl.common.response.PageResult;
import com.zjl.dto.UserInfoDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户管理业务逻辑
 *
 * <p>封装 JPA 阻塞调用，供 Controller 通过 Mono.fromCallable 在弹性线程池中调用。</p>
 */
public interface UserService {

    /**
     * 分页用户列表
     *
     * @param keyword 搜索关键词（username 或 realName），null 或空则全量
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<SysUser> listUsers(String keyword, int page, int size);

    /**
     * 通讯录用户列表：仅返回启用用户，供 IM/通讯录等模块使用。
     *
     * @param deptId 部门 ID，为空则返回全部启用用户
     * @return 用户摘要列表
     */
    List<Map<String, Object>> listDirectoryUsers(Long deptId);

    /**
     * 按 ID 查询用户
     *
     * @param id 用户 ID
     * @return 用户实体
     * @throws com.zjl.common.exception.BizException(40400) 用户不存在
     */
    SysUser getUser(Long id);

    /**
     * 批量查询用户简要信息。
     *
     * @param userIds 用户 ID 列表
     * @return userId → UserInfoDTO
     */
    Map<Long, UserInfoDTO> batchGetUsers(List<Long> userIds);

    /**
     * 按关键词搜索用户（用户名或姓名，模糊匹配）。
     *
     * @param keyword 搜索关键词
     * @param limit   最大返回数
     * @return 用户简要信息列表
     */
    List<UserInfoDTO> searchUsers(String keyword, int limit);

    /**
     * 用户汇总统计
     *
     * @return UserStats(total, enabled, admin, disabled)
     */
    UserStats getUserStats();

    /**
     * 创建用户
     *
     * @param username 用户名
     * @param password 明文密码
     * @param realName 真实姓名（可选）
     * @param deptId 部门 ID（可选）
     * @param roleCodes 角色编码集合（可选）
     * @return 创建后的用户
     * @throws com.zjl.common.exception.BizException(40000) 用户名已存在
     */
    SysUser createUser(String username, String password, String realName,
                       Long deptId, Set<String> roleCodes);

    /**
     * 更新用户
     *
     * @param id 用户 ID
     * @param realName 真实姓名（null 则不更新）
     * @param deptId 部门 ID（null 则不更新）
     * @param enabled 是否启用（null 则不更新）
     * @param roleCodes 角色编码集合（null 则不更新）
     * @return 更新后的用户
     */
    SysUser updateUser(Long id, String realName, Long deptId,
                       Boolean enabled, Set<String> roleCodes);

    /**
     * 删除用户
     *
     * @param id 用户 ID
     */
    void deleteUser(Long id);

    /**
     * 更新用户角色
     *
     * @param id 用户 ID
     * @param roleCodes 角色编码集合
     * @return 更新后的用户
     */
    SysUser updateUserRoles(Long id, Set<String> roleCodes);

    /**
     * 用户汇总统计数据
     */
    record UserStats(long total, long enabled, long admin, long disabled) {}
}
