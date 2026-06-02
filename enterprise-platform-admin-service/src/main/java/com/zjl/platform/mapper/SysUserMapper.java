package com.zjl.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.platform.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT u.* FROM sys_user u WHERE u.deleted = 0 AND (u.username LIKE #{keyword} OR u.real_name LIKE #{keyword}) ORDER BY u.id DESC")
    Page<SysUser> searchUsers(Page<SysUser> page, @Param("keyword") String keyword);

    @Select("SELECT u.* FROM sys_user u WHERE u.deleted = 0 AND u.enabled = true ORDER BY u.id")
    List<SysUser> findEnabledUsers();

    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0")
    long countAll();

    @Select("SELECT COUNT(*) FROM sys_user u INNER JOIN sys_user_role ur ON u.id = ur.user_id INNER JOIN sys_role r ON ur.role_id = r.id WHERE u.deleted = 0 AND r.code = 'admin'")
    long countAdmin();

    @Select("SELECT COUNT(*) FROM sys_user_role WHERE role_id = #{roleId}")
    long countByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    @Update("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    void deleteUserRoles(@Param("userId") Long userId);

    @org.apache.ibatis.annotations.Insert("<script>" +
            "INSERT INTO sys_user_role (user_id, role_id) VALUES " +
            "<foreach collection='roleIds' item='roleId' separator=','>" +
            "(#{userId}, #{roleId})" +
            "</foreach>" +
            "</script>")
    void insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    @Select("SELECT u.* FROM sys_user u WHERE u.deleted = 0 AND (u.username LIKE #{pattern} OR u.real_name LIKE #{pattern}) LIMIT #{limit}")
    List<SysUser> findByUsernameLikeOrRealNameLike(@Param("pattern") String pattern, @Param("limit") int limit);
}
