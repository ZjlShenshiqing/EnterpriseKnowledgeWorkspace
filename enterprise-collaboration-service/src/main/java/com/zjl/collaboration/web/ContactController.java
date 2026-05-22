package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.SysDept;
import com.zjl.collaboration.entity.SysUser;
import com.zjl.collaboration.mapper.SysDeptMapper;
import com.zjl.collaboration.mapper.SysUserMapper;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;

    @GetMapping("/departments")
    public Result<List<SysDept>> listDepartments() {
        return Results.success(sysDeptMapper.selectList(Wrappers.emptyWrapper()));
    }

    @GetMapping("/users")
    @Cacheable(value = "contacts_users", key = "#deptId != null ? #deptId : 'all'", unless = "#result.data.isEmpty()")
    public Result<List<Map<String,Object>>> listUsers(@RequestParam(required=false) Long deptId) {
        var q = Wrappers.lambdaQuery(SysUser.class).eq(SysUser::getEnabled, 1);
        if (deptId != null) q.eq(SysUser::getDeptId, deptId);
        return Results.success(sysUserMapper.selectList(q).stream().map(u -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", u.getId()); m.put("username", u.getUsername()); m.put("realName", u.getRealName());
            m.put("deptId", u.getDeptId()); m.put("isAdmin", u.getIsAdmin()); return m;
        }).toList());
    }
}
