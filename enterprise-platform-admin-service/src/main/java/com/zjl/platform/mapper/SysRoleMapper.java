package com.zjl.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.platform.entity.SysRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    @Update("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    void deleteRolePermissions(@Param("roleId") Long roleId);

    @org.apache.ibatis.annotations.Insert("<script>" +
            "INSERT INTO sys_role_permission (role_id, permission_id) VALUES " +
            "<foreach collection='permIds' item='permId' separator=','>" +
            "(#{roleId}, #{permId})" +
            "</foreach>" +
            "</script>")
    void insertRolePermissions(@Param("roleId") Long roleId, @Param("permIds") List<Long> permIds);
}
