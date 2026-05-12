package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.SysTodo;
import com.zjl.collaboration.mapper.SysTodoMapper;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final SysTodoMapper todoMapper;

    @GetMapping
    public Result<List<SysTodo>> list(@RequestHeader("X-User-Id") Long userId) {
        return Results.success(todoMapper.selectList(Wrappers.lambdaQuery(SysTodo.class).eq(SysTodo::getUserId, userId).orderByAsc(SysTodo::getDone).orderByAsc(SysTodo::getDueDate)));
    }

    @PostMapping
    public Result<Long> create(@RequestBody TodoReq req, @RequestHeader("X-User-Id") Long userId) {
        SysTodo t = new SysTodo();
        t.setTitle(req.getTitle()); t.setUserId(userId); t.setPriority(req.getPriority()); t.setDueDate(req.getDueDate()); t.setDone(0);
        todoMapper.insert(t);
        return Results.success(t.getId());
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody TodoReq req) {
        SysTodo t = todoMapper.selectById(id);
        if (t == null) return Results.success();
        t.setTitle(req.getTitle()); t.setPriority(req.getPriority()); t.setDueDate(req.getDueDate());
        todoMapper.updateById(t);
        return Results.success();
    }

    @PutMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id) {
        SysTodo t = todoMapper.selectById(id);
        if (t == null) return Results.success();
        t.setDone(t.getDone() == 1 ? 0 : 1);
        todoMapper.updateById(t);
        return Results.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { todoMapper.deleteById(id); return Results.success(); }

    @Data public static class TodoReq { private String title; private String priority; private java.util.Date dueDate; }
}
