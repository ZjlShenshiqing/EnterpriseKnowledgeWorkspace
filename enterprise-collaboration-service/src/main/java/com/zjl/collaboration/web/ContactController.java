package com.zjl.collaboration.web;

import com.zjl.collaboration.service.ContactService;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 联系人接口。
 */
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @GetMapping("/departments")
    public Result<List<Map<String, Object>>> listDepartments() {
        return Results.success(contactService.listDepartments());
    }

    @GetMapping("/users")
    public Result<List<Map<String, Object>>> listUsers(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "200") int limit) {
        return Results.success(contactService.listUsers(deptId, keyword, limit));
    }
}
