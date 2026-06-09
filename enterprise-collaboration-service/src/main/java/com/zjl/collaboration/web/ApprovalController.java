package com.zjl.collaboration.web;

import jakarta.validation.Valid;
import com.zjl.collaboration.workflow.dto.ApprovalCreateRequest;
import com.zjl.collaboration.workflow.service.ApprovalApplicationService;
import com.zjl.collaboration.workflow.vo.ApprovalCreateVO;
import com.zjl.collaboration.workflow.vo.ApprovalDetailVO;
import com.zjl.collaboration.workflow.vo.ApprovalListVO;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {
    private final ApprovalApplicationService approvalApplicationService;

    @PostMapping
    public Result<ApprovalCreateVO> create(@Valid @RequestBody ApprovalCreateRequest request,
                                           @RequestHeader("X-User-Id") Long userId) {
        return Results.success(approvalApplicationService.create(request, userId));
    }

    @GetMapping("/my")
    public Result<List<ApprovalListVO>> listMine(@RequestHeader("X-User-Id") Long userId) {
        return Results.success(approvalApplicationService.listMine(userId));
    }

    @GetMapping
    public Result<List<ApprovalListVO>> list(@RequestHeader("X-User-Id") Long userId,
                                             @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin) {
        if ("true".equalsIgnoreCase(isAdmin)) {
            return Results.success(approvalApplicationService.listAll());
        }
        return Results.success(approvalApplicationService.listMine(userId));
    }

    @GetMapping("/{id}")
    public Result<ApprovalDetailVO> detail(@PathVariable Long id,
                                           @RequestHeader("X-User-Id") Long userId,
                                           @RequestHeader(value = "X-Is-Admin", defaultValue = "false") String isAdmin) {
        return Results.success(approvalApplicationService.detail(id, userId, "true".equalsIgnoreCase(isAdmin)));
    }
}
