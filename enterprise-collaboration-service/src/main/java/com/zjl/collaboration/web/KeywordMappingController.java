package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.collaboration.entity.KbKeywordMapping;
import com.zjl.collaboration.mapper.KbKeywordMappingMapper;
import com.zjl.common.response.PageResult;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/keyword-mappings")
@RequiredArgsConstructor
public class KeywordMappingController {

    private final KbKeywordMappingMapper mapper;

    @GetMapping
    public Result<PageResult<KbKeywordMapping>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        var wrapper = new LambdaQueryWrapper<KbKeywordMapping>()
                .orderByDesc(KbKeywordMapping::getPriority);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(KbKeywordMapping::getKeyword, keyword);
        }
        Page<KbKeywordMapping> page = mapper.selectPage(new Page<>(current, size), wrapper);
        return Results.success(PageResult.of(current, size, page.getTotal(), page.getRecords()));
    }

    @PostMapping
    public Result<KbKeywordMapping> create(@RequestBody KbKeywordMapping mapping) {
        mapping.setEnabled(mapping.getEnabled() != null ? mapping.getEnabled() : 1);
        mapper.insert(mapping);
        return Results.success(mapping);
    }

    @PutMapping("/{id}")
    public Result<KbKeywordMapping> update(@PathVariable Long id, @RequestBody KbKeywordMapping mapping) {
        mapping.setId(id);
        mapper.updateById(mapping);
        return Results.success(mapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return Results.success();
    }

    @PostMapping("/match")
    public Result<Map<String, Object>> match(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        if (query.isBlank()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("query", query);
            result.put("hits", List.of());
            return Results.success(result);
        }
        List<KbKeywordMapping> all = mapper.selectList(
                new LambdaQueryWrapper<KbKeywordMapping>()
                        .eq(KbKeywordMapping::getEnabled, 1)
                        .orderByDesc(KbKeywordMapping::getPriority));
        List<KbKeywordMapping> hits = all.stream()
                .filter(m -> query.contains(m.getKeyword()))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("hits", hits);
        return Results.success(result);
    }
}
