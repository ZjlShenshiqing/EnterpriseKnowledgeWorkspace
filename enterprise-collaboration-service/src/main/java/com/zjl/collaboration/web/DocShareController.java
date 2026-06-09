package com.zjl.collaboration.web;

import jakarta.validation.Valid;
import com.zjl.collaboration.dto.DocCollaboratorReq;
import com.zjl.collaboration.dto.DocShareReq;
import com.zjl.collaboration.service.DocShareService;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 文档分享接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocShareController {

    private final DocShareService docShareService;

    @GetMapping("/docs/{docId}/collaborators")
    public Result<List<Map<String, Object>>> listCollaborators(@PathVariable Long docId) {
        return Results.success(docShareService.listCollaborators(docId));
    }

    @PostMapping("/docs/{docId}/collaborators")
    public Result<Map<String, Object>> addCollaborator(@PathVariable Long docId,
                                                       @Valid @RequestBody DocCollaboratorReq req) {
        return Results.success(docShareService.addCollaborator(
                docId, req.getTargetType(), req.getTargetId(), req.getPermission()));
    }

    @PutMapping("/collaborators/{id}")
    public Result<Void> updateCollaborator(@PathVariable Long id,
                                           @Valid @RequestBody DocCollaboratorReq req) {
        docShareService.updateCollaborator(id, req.getPermission());
        return Results.success();
    }

    @DeleteMapping("/collaborators/{id}")
    public Result<Void> removeCollaborator(@PathVariable Long id) {
        docShareService.removeCollaborator(id);
        return Results.success();
    }

    @GetMapping("/docs/{docId}/shares")
    public Result<List<Map<String, Object>>> listShares(@PathVariable Long docId) {
        return Results.success(docShareService.listShares(docId));
    }

    @PostMapping("/docs/{docId}/shares")
    public Result<Map<String, Object>> createShare(@PathVariable Long docId,
                                                   @Valid @RequestBody DocShareReq req) {
        return Results.success(docShareService.createShare(docId, req.getPermission(), req.getExpiredAt()));
    }

    @DeleteMapping("/shares/{id}")
    public Result<Void> deleteShare(@PathVariable Long id) {
        docShareService.deleteShare(id);
        return Results.success();
    }

    @GetMapping("/docs/shared/{token}")
    public Result<Map<String, Object>> openByToken(@PathVariable String token) {
        return Results.success(docShareService.openByToken(token));
    }

}
