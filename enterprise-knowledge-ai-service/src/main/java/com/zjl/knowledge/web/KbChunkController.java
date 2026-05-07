package com.zjl.knowledge.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zjl.knowledge.dto.chunk.KbChunkBatchRequest;
import com.zjl.knowledge.dto.chunk.KbChunkCreateRequest;
import com.zjl.knowledge.dto.chunk.KbChunkPageRequest;
import com.zjl.knowledge.dto.chunk.KbChunkUpdateRequest;
import com.zjl.knowledge.dto.chunk.KbChunkVO;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.knowledge.service.KbChunkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档 Chunk API。
 */
@RestController
@RequestMapping("/api/kb/documents/{docId}/chunks")
@RequiredArgsConstructor
public class KbChunkController {

    private final KbChunkService kbChunkService;

    @GetMapping
    public Result<IPage<KbChunkVO>> page(
            @PathVariable("docId") Long docId,
            KbChunkPageRequest request
    ) {
        return Results.success(kbChunkService.pageQuery(docId, request, UserContextHolder.get()));
    }

    @GetMapping("/list")
    public Result<List<KbChunkVO>> list(@PathVariable("docId") Long docId) {
        return Results.success(kbChunkService.listByDocId(docId, UserContextHolder.get()));
    }

    @PostMapping
    public Result<KbChunkVO> create(
            @PathVariable("docId") Long docId,
            @Valid @RequestBody KbChunkCreateRequest request
    ) {
        return Results.success(kbChunkService.create(docId, request, UserContextHolder.get()));
    }

    @PostMapping("/batch")
    public Result<Void> batchCreate(
            @PathVariable("docId") Long docId,
            @RequestParam(defaultValue = "false") boolean writeVector,
            @Valid @RequestBody List<KbChunkCreateRequest> requests
    ) {
        kbChunkService.batchCreate(docId, requests, writeVector, UserContextHolder.get());
        return Results.success();
    }

    @PutMapping("/{chunkId}")
    public Result<Void> update(
            @PathVariable("docId") Long docId,
            @PathVariable("chunkId") Long chunkId,
            @Valid @RequestBody KbChunkUpdateRequest request
    ) {
        kbChunkService.update(docId, chunkId, request, UserContextHolder.get());
        return Results.success();
    }

    @DeleteMapping("/{chunkId}")
    public Result<Void> delete(
            @PathVariable("docId") Long docId,
            @PathVariable("chunkId") Long chunkId
    ) {
        kbChunkService.delete(docId, chunkId, UserContextHolder.get());
        return Results.success();
    }

    @PatchMapping("/{chunkId}/enabled")
    public Result<Void> enableChunk(
            @PathVariable("docId") Long docId,
            @PathVariable("chunkId") Long chunkId,
            @RequestParam("on") boolean enabled
    ) {
        kbChunkService.enableChunk(docId, chunkId, enabled, UserContextHolder.get());
        return Results.success();
    }

    @PostMapping("/batch-enabled")
    public Result<Void> batchToggleEnabled(
            @PathVariable("docId") Long docId,
            @RequestParam("on") boolean enabled,
            @Valid @RequestBody KbChunkBatchRequest request
    ) {
        kbChunkService.batchToggleEnabled(docId, request, enabled, UserContextHolder.get());
        return Results.success();
    }
}
