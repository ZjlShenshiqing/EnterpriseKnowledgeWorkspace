package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.collaboration.entity.SysDoc;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.mapper.SysDocMapper;
import com.zjl.collaboration.service.DocOTService;
import com.zjl.common.response.PageResult;
import com.zjl.common.response.Result;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.common.response.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocController {

    private final SysDocMapper docMapper;
    private final GatewayUserClient gatewayUserClient;
    private final DocOTService docOTService;

    private static final String EMPTY_DELTA = "{\"ops\":[{\"insert\":\"\\n\"}]}";

    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        LambdaQueryWrapper<SysDoc> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SysDoc::getTitle, keyword);
        }
        wrapper.orderByDesc(SysDoc::getUpdatedAt);

        Page<SysDoc> pageResult = docMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = pageResult.getRecords().stream().map(doc -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", doc.getId());
            m.put("title", doc.getTitle());
            m.put("updatedBy", doc.getUpdatedBy());
            m.put("updatedByName", doc.getUpdatedByName());
            m.put("version", doc.getVersion());
            m.put("createdAt", doc.getCreatedAt());
            m.put("updatedAt", doc.getUpdatedAt());
            return m;
        }).toList();

        return Results.success(PageResult.of((int) pageResult.getCurrent(),
                (int) pageResult.getSize(), (int) pageResult.getTotal(), records));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> get(@PathVariable Long id) {
        DocOTService.DocSnapshot snapshot = docOTService.getDocument(id);
        if (snapshot == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        SysDoc doc = docMapper.selectById(id);
        Map<String, Object> m = new HashMap<>();
        m.put("id", doc.getId());
        m.put("title", doc.getTitle());
        m.put("content", snapshot.content());
        m.put("version", snapshot.version());
        m.put("updatedBy", doc.getUpdatedBy());
        m.put("updatedByName", doc.getUpdatedByName());
        m.put("createdAt", doc.getCreatedAt());
        m.put("updatedAt", doc.getUpdatedAt());
        return Results.success(m);
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody DocReq req,
                                               @RequestHeader("X-User-Id") Long userId) {
        SysDoc doc = new SysDoc();
        doc.setTitle(req.getTitle());
        doc.setContent(EMPTY_DELTA);
        doc.setVersion(0);
        doc.setSnapshotVersion(0);
        doc.setUpdatedBy(userId);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());

        UserInfo user = gatewayUserClient.getById(userId);
        if (user != null) {
            doc.setUpdatedByName(user.realName());
        }

        docMapper.insert(doc);
        log.info("文档创建: docId={}, title={}, userId={}", doc.getId(), req.getTitle(), userId);
        Map<String, Object> m = new HashMap<>();
        m.put("id", doc.getId());
        return Results.success(m);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                                @RequestBody DocUpdateReq req) {
        SysDoc doc = docMapper.selectById(id);
        if (doc == null) throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        if (req.getTitle() != null) doc.setTitle(req.getTitle());
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("文档删除: docId={}", id);
        docMapper.deleteById(id);
        return Results.success();
    }

    @Data
    static class DocReq {
        private String title;
    }

    @Data
    static class DocUpdateReq {
        private String title;
    }
}
