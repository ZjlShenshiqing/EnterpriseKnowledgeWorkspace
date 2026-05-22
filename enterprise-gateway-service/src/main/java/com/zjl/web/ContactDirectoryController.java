package com.zjl.web;

import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.domain.SysDept;
import com.zjl.repository.SysDeptRepository;
import com.zjl.service.UserService;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * 通讯录接口：面向全体登录用户，数据来源于网关用户/部门主数据。
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactDirectoryController {

    private final UserService userService;
    private final SysDeptRepository deptRepository;

    public ContactDirectoryController(UserService userService, SysDeptRepository deptRepository) {
        this.userService = userService;
        this.deptRepository = deptRepository;
    }

    /**
     * 部门列表（组织架构）
     */
    @GetMapping("/departments")
    public Mono<Result<List<SysDept>>> listDepartments() {
        return Mono.fromCallable(() -> Results.success(
                        deptRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 启用中的用户列表，可按部门筛选。
     */
    @GetMapping("/users")
    public Mono<Result<List<Map<String, Object>>>> listUsers(
            @RequestParam(value = "deptId", required = false) Long deptId) {
        return Mono.fromCallable(() -> Results.success(userService.listDirectoryUsers(deptId)))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
