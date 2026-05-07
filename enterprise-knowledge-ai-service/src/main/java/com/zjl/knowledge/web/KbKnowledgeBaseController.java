package com.zjl.knowledge.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zjl.common.response.PageResult;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.knowledge.dto.kb.KbKnowledgeBaseCreateRequest;
import com.zjl.knowledge.dto.kb.KbKnowledgeBasePageRequest;
import com.zjl.knowledge.dto.kb.KbKnowledgeBaseRenameRequest;
import com.zjl.knowledge.dto.kb.KbKnowledgeBaseUpdateRequest;
import com.zjl.knowledge.dto.kb.KbKnowledgeBaseVO;
import com.zjl.knowledge.service.KbKnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库（多 Milvus 集合）管理接口。
 */
@RestController
@RequestMapping("/api/kb/bases")
@RequiredArgsConstructor
public class KbKnowledgeBaseController {

    private final KbKnowledgeBaseService kbKnowledgeBaseService;

    @PostMapping
    public Result<Long> create(@Valid @RequestBody KbKnowledgeBaseCreateRequest request) {
        return Results.success(kbKnowledgeBaseService.create(request, UserContextHolder.get()));
    }

    @PutMapping("/{id}")
    public Result<Void> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody KbKnowledgeBaseUpdateRequest request
    ) {
        kbKnowledgeBaseService.update(id, request, UserContextHolder.get());
        return Results.success();
    }

    @PutMapping("/{id}/rename")
    public Result<Void> rename(
            @PathVariable("id") Long id,
            @Valid @RequestBody KbKnowledgeBaseRenameRequest request
    ) {
        kbKnowledgeBaseService.rename(id, request, UserContextHolder.get());
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        kbKnowledgeBaseService.delete(id, UserContextHolder.get());
        return Results.success();
    }

    @GetMapping("/{id}")
    public Result<KbKnowledgeBaseVO> detail(@PathVariable("id") Long id) {
        return Results.success(kbKnowledgeBaseService.getById(id, UserContextHolder.get()));
    }

    @GetMapping
    public Result<PageResult<KbKnowledgeBaseVO>> page(@Valid KbKnowledgeBasePageRequest request) {
        IPage<KbKnowledgeBaseVO> pageResult = kbKnowledgeBaseService.pageQuery(request, UserContextHolder.get());
        return Results.success(PageResult.of(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal(), pageResult.getRecords()));
    }
}
