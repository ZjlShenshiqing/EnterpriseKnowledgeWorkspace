package com.zjl.web;

import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.domain.SysDept;
import com.zjl.domain.SysUser;
import com.zjl.repository.SysDeptRepository;
import com.zjl.repository.SysUserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
public class ContactDirectoryController {

    private final SysUserRepository userRepository;
    private final SysDeptRepository deptRepository;

    public ContactDirectoryController(SysUserRepository userRepository, SysDeptRepository deptRepository) {
        this.userRepository = userRepository;
        this.deptRepository = deptRepository;
    }

    @GetMapping("/departments")
    public Mono<Result<List<SysDept>>> listDepartments() {
        return Mono.fromCallable(() -> Results.success(
                        deptRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/users")
    public Mono<Result<List<Map<String, Object>>>> listUsers(
            @RequestParam(value = "deptId", required = false) Long deptId) {
        return Mono.fromCallable(() -> Results.success(userRepository.findDirectoryUsers(deptId)
                        .stream().map(u -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("id", u.getId());
                            row.put("username", u.getUsername());
                            row.put("realName", u.getRealName());
                            row.put("deptId", u.getDept() != null ? u.getDept().getId() : null);
                            row.put("isAdmin", u.getRoles().stream()
                                    .anyMatch(r -> "admin".equalsIgnoreCase(r.getCode())));
                            return (Map<String, Object>) row;
                        }).toList()))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
