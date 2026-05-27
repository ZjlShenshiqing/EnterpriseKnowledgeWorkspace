package com.zjl.repository;

import com.zjl.domain.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问层
 */
public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    /**
     * 按用户名查询用户
     *
     * @param username 用户名
     * @return 用户（可能为空）
     */
    Optional<SysUser> findByUsername(String username);

    /**
     * 分页模糊搜索用户（按 username 或 realName）
     *
     * @param keyword 搜索关键词
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query("SELECT u FROM SysUser u WHERE u.username LIKE %:keyword% OR u.realName LIKE %:keyword%")
    Page<SysUser> searchUsers(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 统计拥有指定角色的用户数
     *
     * @param roleId 角色 ID
     * @return 用户数
     */
    @Query("SELECT COUNT(u) FROM SysUser u JOIN u.roles r WHERE r.id = :roleId")
    long countByRoleId(@Param("roleId") Long roleId);

    /**
     * 统计管理员数量（角色 code = 'admin'）
     *
     * @return 管理员数
     */
    @Query("SELECT COUNT(u) FROM SysUser u JOIN u.roles r WHERE r.code = 'admin'")
    long countAdmin();

    /**
     * 查询启用中的用户（通讯录），可按部门筛选。
     */
    @Query("""
            SELECT u FROM SysUser u LEFT JOIN FETCH u.dept LEFT JOIN FETCH u.roles
            WHERE u.enabled = true AND (:deptId IS NULL OR u.dept.id = :deptId)
            ORDER BY u.id ASC
            """)
    java.util.List<SysUser> findDirectoryUsers(@Param("deptId") Long deptId);

    /**
     * 按用户名或姓名模糊搜索用户。
     */
    @Query("SELECT DISTINCT u FROM SysUser u LEFT JOIN FETCH u.dept WHERE u.enabled = true AND (u.username LIKE ?1 OR u.realName LIKE ?2)")
    List<SysUser> findByUsernameLikeOrRealNameLike(String usernamePattern, String realNamePattern, Pageable pageable);
}
