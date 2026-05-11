package com.zjl.knowledge.web;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.knowledge.dto.KbCategoryRequest;
import com.zjl.knowledge.entity.KbCategory;
import com.zjl.knowledge.service.KbCategoryService;
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

import java.util.List;

/**
 * 知识分类接口
 */
@RestController
@RequestMapping("/api/kb/categories")
@RequiredArgsConstructor
public class KbCategoryController {

    /**
     * 分类服务
     */
    private final KbCategoryService kbCategoryService;

    /**
     * 查询全部分类
     *
     * @return 分类列表
     */
    @GetMapping
    public Result<List<KbCategory>> list() {
        List<KbCategory> list = kbCategoryService.list();
        return Results.success(list);
    }

    /**
     * 新增分类
     *
     * @param req 请求体
     * @return 分类 ID
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody KbCategoryRequest req) {
        KbCategory c = new KbCategory();
        c.setCategoryName(req.getCategoryName());
        c.setParentId(req.getParentId());
        c.setCategoryType(req.getCategoryType());
        c.setDepartmentId(req.getDepartmentId());
        c.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        c.setStatus("ACTIVE");
        kbCategoryService.save(c);
        return Results.success(c.getId());
    }

    /**
     * 修改分类
     *
     * @param id 分类 ID
     * @param req 请求体
     * @return 空数据
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id, @Valid @RequestBody KbCategoryRequest req) {
        KbCategory c = kbCategoryService.getById(id);
        if (c == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        c.setCategoryName(req.getCategoryName());
        c.setParentId(req.getParentId());
        c.setCategoryType(req.getCategoryType());
        c.setDepartmentId(req.getDepartmentId());
        if (req.getSortOrder() != null) {
            c.setSortOrder(req.getSortOrder());
        }
        kbCategoryService.updateById(c);
        return Results.success();
    }

    /**
     * 删除分类
     *
     * @param id 分类 ID
     * @return 空数据
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        if (!kbCategoryService.removeById(id)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return Results.success();
    }
}
