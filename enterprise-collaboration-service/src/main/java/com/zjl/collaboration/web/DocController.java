package com.zjl.collaboration.web;

import jakarta.validation.Valid;
import com.zjl.collaboration.dto.DocCreateReq;
import com.zjl.collaboration.dto.DocUpdateReq;
import com.zjl.collaboration.service.DocService;
import com.zjl.common.response.PageResult;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 在线文档接口。
 */
@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Results.success(docService.list(keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        return Results.success(docService.get(id));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody DocCreateReq req,
                                              @RequestHeader("X-User-Id") Long userId) {
        return Results.success(docService.create(req.getTitle(), userId));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DocUpdateReq req) {
        docService.update(id, req.getTitle());
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        docService.delete(id);
        return Results.success();
    }

}
