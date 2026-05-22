package com.zjl.knowledge.web;

import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.knowledge.dto.kb.KbAdminStatsVO;
import com.zjl.knowledge.service.KbAdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台知识服务统计接口。
 */
@RestController
@RequestMapping("/api/kb/admin")
@RequiredArgsConstructor
public class KbAdminController {

    private final KbAdminStatsService adminStatsService;

    @GetMapping("/stats")
    public Result<KbAdminStatsVO> stats() {
        return Results.success(adminStatsService.compute());
    }
}
