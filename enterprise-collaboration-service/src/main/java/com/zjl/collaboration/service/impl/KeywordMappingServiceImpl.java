package com.zjl.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.collaboration.entity.KbKeywordMapping;
import com.zjl.collaboration.mapper.KbKeywordMappingMapper;
import com.zjl.collaboration.service.KeywordMappingService;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.common.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 关键词映射业务服务实现。
 */
@Service
@RequiredArgsConstructor
public class KeywordMappingServiceImpl implements KeywordMappingService {

    private final KbKeywordMappingMapper mapper;

    @Override
    public PageResult<KbKeywordMapping> list(int current, int size, String keyword) {
        var wrapper = new LambdaQueryWrapper<KbKeywordMapping>()
                .orderByDesc(KbKeywordMapping::getPriority);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(KbKeywordMapping::getKeyword, keyword);
        }
        Page<KbKeywordMapping> page = mapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(current, size, page.getTotal(), page.getRecords());
    }

    @Override
    public KbKeywordMapping create(KbKeywordMapping mapping) {
        mapping.setEnabled(mapping.getEnabled() != null ? mapping.getEnabled() : 1);
        mapper.insert(mapping);
        return mapping;
    }

    @Override
    public KbKeywordMapping update(Long id, KbKeywordMapping mapping) {
        KbKeywordMapping existing = mapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "关键词映射不存在");
        }
        mapping.setId(id);
        mapper.updateById(mapping);
        return mapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        KbKeywordMapping existing = mapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "关键词映射不存在");
        }
        mapper.deleteById(id);
    }

    @Override
    public Map<String, Object> match(String query) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        if (query == null || query.isBlank()) {
            result.put("hits", List.of());
            return result;
        }
        List<KbKeywordMapping> hits = mapper.selectList(
                        new LambdaQueryWrapper<KbKeywordMapping>()
                                .eq(KbKeywordMapping::getEnabled, 1)
                                .orderByDesc(KbKeywordMapping::getPriority))
                .stream()
                .filter(mapping -> query.contains(mapping.getKeyword()))
                .toList();
        result.put("hits", hits);
        return result;
    }
}
