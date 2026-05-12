package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.SysDoc;
import com.zjl.collaboration.entity.SysUser;
import com.zjl.collaboration.mapper.SysDocMapper;
import com.zjl.collaboration.mapper.SysUserMapper;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocController {

    private final SysDocMapper docMapper;
    private final SysUserMapper userMapper;

    @GetMapping
    public Result<List<SysDoc>> list(@RequestParam(required=false) String keyword) {
        var q = Wrappers.lambdaQuery(SysDoc.class).orderByDesc(SysDoc::getUpdatedAt);
        if (keyword != null && !keyword.isEmpty()) q.like(SysDoc::getTitle, keyword);
        return Results.success(docMapper.selectList(q));
    }

    @GetMapping("/{id}")
    public Result<SysDoc> detail(@PathVariable Long id) {
        return Results.success(docMapper.selectById(id));
    }

    @PostMapping
    public Result<Long> create(@RequestBody DocReq req, @RequestHeader("X-User-Id") Long userId) {
        SysUser u = userMapper.selectById(userId);
        SysDoc d = new SysDoc();
        d.setTitle(req.getTitle()); d.setContent(req.getContent());
        d.setUpdatedBy(userId); d.setUpdatedByName(u != null ? u.getRealName() : null);
        d.setCreatedAt(LocalDateTime.now()); d.setUpdatedAt(LocalDateTime.now());
        docMapper.insert(d);
        return Results.success(d.getId());
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DocReq req) {
        SysDoc d = docMapper.selectById(id);
        if (d == null) return Results.success();
        d.setTitle(req.getTitle()); d.setContent(req.getContent()); d.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(d);
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { docMapper.deleteById(id); return Results.success(); }

    @Data public static class DocReq { private String title; private String content; }
}
