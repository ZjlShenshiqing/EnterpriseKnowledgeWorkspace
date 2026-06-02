package com.zjl.platform.service;

import com.zjl.platform.dto.RoleDTO;
import com.zjl.platform.entity.SysRole;

import java.util.List;
import java.util.Set;

public interface RoleService {

    List<RoleDTO> listRoles();

    SysRole getRole(Long id);

    SysRole createRole(String code, String name, Set<String> permissionCodes);

    SysRole updateRole(Long id, String name, Set<String> permissionCodes);

    void deleteRole(Long id);
}
