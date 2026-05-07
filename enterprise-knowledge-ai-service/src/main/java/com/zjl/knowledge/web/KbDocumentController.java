package com.zjl.knowledge.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.common.response.PageResult;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zjl.knowledge.dto.KbDocumentChunkLogVO;
import com.zjl.knowledge.dto.KbDocumentUpdateRequest;
import com.zjl.knowledge.dto.KbDocumentUploadRequest;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.service.KbDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识文档接口。
 */
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KbDocumentController {

    /**
     * 文档服务
     */
    private final KbDocumentService kbDocumentService;

    /**
     * 分页查询文档列表。
     *
     * @param current 页码
     * @param size 每页条数
     * @return 分页结果
     */
    @GetMapping("/documents")
    public Result<PageResult<KbDocument>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size
    ) {
        UserContext user = UserContextHolder.get();
        Page<KbDocument> p = new Page<>(current, size);
        IPage<KbDocument> pageResult = kbDocumentService.pageVisible(p, user);
        return Results.success(PageResult.of(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal(), pageResult.getRecords()));
    }

    /**
     * 文档详情。
     *
     * @param id 文档 ID
     * @return 文档
     */
    @GetMapping("/documents/{id}")
    public Result<KbDocument> detail(@PathVariable("id") Long id) {
        UserContext user = UserContextHolder.get();
        return Results.success(kbDocumentService.getVisible(id, user));
    }

    /**
     * 上传文档：落库为 PENDING，需再调用 {@code POST .../documents/{id}/start-chunk} 触发异步分块。
     */
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Long> upload(
            @Valid @RequestPart("meta") KbDocumentUploadRequest meta,
            @RequestPart("file") MultipartFile file
    ) {
        UserContext user = UserContextHolder.get();
        Long id = kbDocumentService.upload(user, meta, file);
        return Results.success(id);
    }

    /**
     * 提交异步分块任务（事务提交后由监听器执行解析、分块、向量写入）。
     */
    @PostMapping("/documents/{id}/start-chunk")
    public Result<Void> startChunk(@PathVariable("id") Long id) {
        kbDocumentService.startChunk(id, UserContextHolder.get());
        return Results.success();
    }

    /**
     * 立即执行分块（运维补偿；需对文档有写权限）。
     */
    @PostMapping("/documents/{id}/execute-chunk")
    public Result<Void> executeChunk(@PathVariable("id") Long id) {
        kbDocumentService.executeChunkAsUser(id, UserContextHolder.get());
        return Results.success();
    }

    @PutMapping("/documents/{id}")
    public Result<Void> updateDocument(
            @PathVariable("id") Long id,
            @Valid @RequestBody KbDocumentUpdateRequest request
    ) {
        kbDocumentService.updateDocument(id, request, UserContextHolder.get());
        return Results.success();
    }

    @PatchMapping("/documents/{id}/enabled")
    public Result<Void> enableDocument(
            @PathVariable("id") Long id,
            @RequestParam("on") boolean enabled
    ) {
        kbDocumentService.enableDocument(id, enabled, UserContextHolder.get());
        return Results.success();
    }

    @GetMapping("/documents/{id}/chunk-logs")
    public Result<IPage<KbDocumentChunkLogVO>> chunkLogs(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size
    ) {
        return Results.success(kbDocumentService.pageChunkLogs(id, current, size, UserContextHolder.get()));
    }

    @GetMapping("/documents/search")
    public Result<List<KbDocument>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return Results.success(kbDocumentService.searchDocuments(UserContextHolder.get(), keyword, limit));
    }

    /**
     * 删除文档。
     *
     * @param id 文档 ID
     * @return 空数据
     */
    @DeleteMapping("/documents/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        UserContext user = UserContextHolder.get();
        kbDocumentService.deleteVisible(id, user);
        return Results.success();
    }
}
