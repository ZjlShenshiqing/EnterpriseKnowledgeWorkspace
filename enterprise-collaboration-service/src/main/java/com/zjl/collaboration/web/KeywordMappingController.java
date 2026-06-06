package com.zjl.collaboration.web;

import com.zjl.collaboration.entity.KbKeywordMapping;
import com.zjl.collaboration.service.KeywordMappingService;
import com.zjl.common.response.PageResult;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 关键词映射接口。
 */
@RestController
@RequestMapping("/api/keyword-mappings")
@RequiredArgsConstructor
public class KeywordMappingController {

    private final KeywordMappingService keywordMappingService;

    @GetMapping
    public Result<PageResult<KbKeywordMapping>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Results.success(keywordMappingService.list(current, size, keyword));
    }

    @PostMapping
    public Result<KbKeywordMapping> create(@RequestBody KbKeywordMapping mapping) {
        return Results.success(keywordMappingService.create(mapping));
    }

    @PutMapping("/{id}")
    public Result<KbKeywordMapping> update(@PathVariable Long id, @RequestBody KbKeywordMapping mapping) {
        return Results.success(keywordMappingService.update(id, mapping));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        keywordMappingService.delete(id);
        return Results.success();
    }

    @PostMapping("/match")
    public Result<Map<String, Object>> match(@RequestBody Map<String, String> body) {
        return Results.success(keywordMappingService.match(body.getOrDefault("query", "")));
    }
}
