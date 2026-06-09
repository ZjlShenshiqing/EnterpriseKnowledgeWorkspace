package com.zjl.collaboration.web;

import jakarta.validation.Valid;
import com.zjl.collaboration.dto.DocCommentReq;
import com.zjl.collaboration.dto.DocCommentUpdateReq;
import com.zjl.collaboration.service.DocCommentService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 文档评论接口。
 */
@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocCommentController {

    private final DocCommentService docCommentService;

    @GetMapping("/{docId}/comments")
    public Result<List<Map<String, Object>>> list(@PathVariable Long docId) {
        return Results.success(docCommentService.list(docId));
    }

    @PostMapping("/{docId}/comments")
    public Result<Map<String, Object>> create(@PathVariable Long docId,
                                              @Valid @RequestBody DocCommentReq req,
                                              @RequestHeader("X-User-Id") Long userId) {
        return Results.success(docCommentService.create(docId, req.getContent(), req.getAnchorIndex(),
                req.getAnchorLength(), req.getParentId(), userId));
    }

    @PutMapping("/comments/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DocCommentUpdateReq req) {
        if (!docCommentService.update(id, req.getContent(), req.getResolved())) {
            return Results.failure("404", "评论不存在");
        }
        return Results.success();
    }

    @DeleteMapping("/comments/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        docCommentService.delete(id);
        return Results.success();
    }

}
