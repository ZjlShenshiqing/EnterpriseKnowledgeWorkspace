package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.dto.kb.KbKnowledgeBaseCreateRequest;
import com.zjl.knowledge.dto.kb.KbKnowledgeBasePageRequest;
import com.zjl.knowledge.dto.kb.KbKnowledgeBaseRenameRequest;
import com.zjl.knowledge.dto.kb.KbKnowledgeBaseUpdateRequest;
import com.zjl.knowledge.dto.kb.KbKnowledgeBaseVO;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbKnowledgeBase;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.mapper.KbKnowledgeBaseMapper;
import com.zjl.knowledge.milvus.MilvusCollectionHelper;
import com.zjl.knowledge.service.KbKnowledgeBaseService;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KbKnowledgeBaseServiceImpl implements KbKnowledgeBaseService {

    private static final Pattern COLLECTION_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,127}$");

    private final KbKnowledgeBaseMapper kbKnowledgeBaseMapper;
    private final KbDocumentMapper kbDocumentMapper;
    private final MilvusCollectionHelper milvusCollectionHelper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(KbKnowledgeBaseCreateRequest request, UserContext user) {
        if (!StringUtils.hasText(request.getName())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "知识库名称不能为空");
        }
        String displayName = request.getName().trim();
        String collection = request.getCollectionName() == null ? "" : request.getCollectionName().trim();
        if (!StringUtils.hasText(collection)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "集合名称不能为空");
        }
        if (!COLLECTION_NAME.matcher(collection).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "集合名称仅允许字母、数字、下划线，且不能以数字开头，长度 1~128");
        }

        Long dupName = kbKnowledgeBaseMapper.selectCount(
                Wrappers.lambdaQuery(KbKnowledgeBase.class)
                        .eq(KbKnowledgeBase::getName, displayName)
        );
        if (dupName != null && dupName > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "知识库名称已存在：" + displayName);
        }
        Long dupCol = kbKnowledgeBaseMapper.selectCount(
                Wrappers.lambdaQuery(KbKnowledgeBase.class)
                        .eq(KbKnowledgeBase::getCollectionName, collection)
        );
        if (dupCol != null && dupCol > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "Milvus 集合名已被占用：" + collection);
        }

        KbKnowledgeBase row = new KbKnowledgeBase();
        row.setName(displayName);
        row.setCollectionName(collection);
        row.setEmbeddingModel(StringUtils.hasText(request.getEmbeddingModel())
                ? request.getEmbeddingModel().trim() : null);
        row.setOwnerId(user.getUserId());
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        row.setDeleted(0);
        kbKnowledgeBaseMapper.insert(row);

        milvusCollectionHelper.ensureCollectionLoaded(collection);
        log.info("创建知识库成功 id={}, collection={}", row.getId(), collection);
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, KbKnowledgeBaseUpdateRequest request, UserContext user) {
        KbKnowledgeBase kb = kbKnowledgeBaseMapper.selectById(id);
        assertKbWritable(kb, user);

        if (StringUtils.hasText(request.getEmbeddingModel())
                && !request.getEmbeddingModel().trim().equals(
                kb.getEmbeddingModel() == null ? "" : kb.getEmbeddingModel())) {

            Long docCount = kbDocumentMapper.selectCount(
                    Wrappers.lambdaQuery(KbDocument.class)
                            .eq(KbDocument::getKbId, id)
                            .gt(KbDocument::getChunkCount, 0)
            );
            if (docCount != null && docCount > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "知识库下已有向量化文档，不允许修改嵌入模型");
            }
            kb.setEmbeddingModel(request.getEmbeddingModel().trim());
        }

        if (StringUtils.hasText(request.getName())) {
            String nm = request.getName().trim();
            Long exists = kbKnowledgeBaseMapper.selectCount(
                    Wrappers.lambdaQuery(KbKnowledgeBase.class)
                            .eq(KbKnowledgeBase::getName, nm)
                            .ne(KbKnowledgeBase::getId, id)
            );
            if (exists != null && exists > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "知识库名称已存在：" + nm);
            }
            kb.setName(nm);
        }

        kb.setUpdatedAt(LocalDateTime.now());
        kbKnowledgeBaseMapper.updateById(kb);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(Long id, KbKnowledgeBaseRenameRequest request, UserContext user) {
        KbKnowledgeBase kb = kbKnowledgeBaseMapper.selectById(id);
        assertKbWritable(kb, user);
        if (!StringUtils.hasText(request.getName())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "知识库名称不能为空");
        }
        String nm = request.getName().trim();
        Long count = kbKnowledgeBaseMapper.selectCount(
                Wrappers.lambdaQuery(KbKnowledgeBase.class)
                        .eq(KbKnowledgeBase::getName, nm)
                        .ne(KbKnowledgeBase::getId, id)
        );
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "知识库名称已存在：" + nm);
        }
        kb.setName(nm);
        kb.setUpdatedAt(LocalDateTime.now());
        kbKnowledgeBaseMapper.updateById(kb);
        log.info("重命名知识库 kbId={}, newName={}", id, nm);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, UserContext user) {
        KbKnowledgeBase kb = kbKnowledgeBaseMapper.selectById(id);
        assertKbWritable(kb, user);

        Long docCount = kbDocumentMapper.selectCount(
                Wrappers.lambdaQuery(KbDocument.class)
                        .eq(KbDocument::getKbId, id)
        );
        if (docCount != null && docCount > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "当前知识库下还有文档，请先删除或迁移文档");
        }

        kbKnowledgeBaseMapper.deleteById(id);
    }

    @Override
    public KbKnowledgeBaseVO getById(Long id, UserContext user) {
        KbKnowledgeBase kb = kbKnowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        if (!user.isAdmin() && !Objects.equals(kb.getOwnerId(), user.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        KbKnowledgeBaseVO vo = toVo(kb);
        Long c = kbDocumentMapper.selectCount(
                Wrappers.lambdaQuery(KbDocument.class).eq(KbDocument::getKbId, id)
        );
        vo.setDocumentCount(c != null ? c : 0L);
        return vo;
    }

    @Override
    public IPage<KbKnowledgeBaseVO> pageQuery(KbKnowledgeBasePageRequest request, UserContext user) {
        LambdaQueryWrapper<KbKnowledgeBase> q = Wrappers.lambdaQuery(KbKnowledgeBase.class)
                .like(StringUtils.hasText(request.getName()), KbKnowledgeBase::getName, request.getName().trim())
                .orderByDesc(KbKnowledgeBase::getUpdatedAt);
        if (!user.isAdmin()) {
            q.eq(KbKnowledgeBase::getOwnerId, user.getUserId());
        }

        Page<KbKnowledgeBase> page = new Page<>(request.getCurrent(), request.getSize());
        IPage<KbKnowledgeBase> raw = kbKnowledgeBaseMapper.selectPage(page, q);

        Map<Long, Long> docCountMap = new HashMap<>();
        List<KbKnowledgeBase> records = raw.getRecords();
        if (records != null && !records.isEmpty()) {
            List<Long> kbIds = records.stream().map(KbKnowledgeBase::getId).filter(Objects::nonNull).collect(Collectors.toList());
            if (!kbIds.isEmpty()) {
                List<Map<String, Object>> rows = kbDocumentMapper.selectMaps(
                        Wrappers.query(KbDocument.class)
                                .select("kb_id AS kbId", "COUNT(1) AS docCount")
                                .in("kb_id", kbIds)
                                .groupBy("kb_id")
                );
                for (Map<String, Object> row : rows) {
                    Object kbIdVal = row.get("kbId");
                    Object cntVal = row.get("docCount");
                    if (kbIdVal == null) {
                        continue;
                    }
                    long kbId = kbIdVal instanceof Number ? ((Number) kbIdVal).longValue() : Long.parseLong(kbIdVal.toString());
                    long cnt = cntVal instanceof Number ? ((Number) cntVal).longValue()
                            : cntVal != null ? Long.parseLong(cntVal.toString()) : 0L;
                    docCountMap.put(kbId, cnt);
                }
            }
        }

        return raw.convert(each -> {
            KbKnowledgeBaseVO vo = toVo(each);
            Long dc = docCountMap.get(each.getId());
            vo.setDocumentCount(dc != null ? dc : 0L);
            return vo;
        });
    }

    private static KbKnowledgeBaseVO toVo(KbKnowledgeBase e) {
        KbKnowledgeBaseVO vo = new KbKnowledgeBaseVO();
        vo.setId(e.getId());
        vo.setName(e.getName());
        vo.setEmbeddingModel(e.getEmbeddingModel());
        vo.setCollectionName(e.getCollectionName());
        vo.setOwnerId(e.getOwnerId());
        vo.setCreatedAt(e.getCreatedAt());
        vo.setUpdatedAt(e.getUpdatedAt());
        return vo;
    }

    private void assertKbWritable(KbKnowledgeBase kb, UserContext user) {
        if (kb == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        if (!user.isAdmin() && !Objects.equals(kb.getOwnerId(), user.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
