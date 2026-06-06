package com.zjl.collaboration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.collaboration.entity.SysDoc;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.mapper.SysDocMapper;
import com.zjl.collaboration.service.DocOTService;
import com.zjl.collaboration.service.DocService;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.common.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 在线文档业务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocServiceImpl implements DocService {

    private static final String EMPTY_DELTA = "{\"ops\":[{\"insert\":\"\\n\"}]}";

    private final SysDocMapper docMapper;
    private final GatewayUserClient gatewayUserClient;
    private final DocOTService docOTService;

    @Override
    public PageResult<Map<String, Object>> list(String keyword, int page, int size) {
        LambdaQueryWrapper<SysDoc> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(SysDoc::getTitle, keyword);
        }
        wrapper.orderByDesc(SysDoc::getUpdatedAt);
        Page<SysDoc> pageResult = docMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of((int) pageResult.getCurrent(), (int) pageResult.getSize(), pageResult.getTotal(),
                pageResult.getRecords().stream().map(this::toListItem).toList());
    }

    @Override
    public Map<String, Object> get(Long id) {
        DocOTService.DocSnapshot snapshot = docOTService.getDocument(id);
        if (snapshot == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        SysDoc doc = docMapper.selectById(id);
        Map<String, Object> item = toListItem(doc);
        item.put("content", snapshot.content());
        item.put("version", snapshot.version());
        return item;
    }

    @Override
    public Map<String, Object> create(String title, Long userId) {
        SysDoc doc = new SysDoc();
        doc.setTitle(title);
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
        log.info("文档创建: docId={}, title={}, userId={}", doc.getId(), title, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("id", doc.getId());
        return result;
    }

    @Override
    public void update(Long id, String title) {
        SysDoc doc = docMapper.selectById(id);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        if (title != null) {
            doc.setTitle(title);
        }
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);
    }

    @Override
    public void delete(Long id) {
        log.info("文档删除: docId={}", id);
        docMapper.deleteById(id);
    }

    private Map<String, Object> toListItem(SysDoc doc) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", doc.getId());
        item.put("title", doc.getTitle());
        item.put("updatedBy", doc.getUpdatedBy());
        item.put("updatedByName", doc.getUpdatedByName());
        item.put("version", doc.getVersion());
        item.put("createdAt", doc.getCreatedAt());
        item.put("updatedAt", doc.getUpdatedAt());
        return item;
    }
}
