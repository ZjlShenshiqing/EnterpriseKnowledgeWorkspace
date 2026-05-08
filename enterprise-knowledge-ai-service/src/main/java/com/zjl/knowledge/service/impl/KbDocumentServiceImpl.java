package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.domain.ChunkingMode;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.domain.ProcessMode;
import com.zjl.knowledge.dto.KbDocumentChunkLogVO;
import com.zjl.knowledge.dto.KbDocumentUpdateRequest;
import com.zjl.knowledge.dto.KbDocumentUploadRequest;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.entity.KbDocumentChunkLog;
import com.zjl.knowledge.entity.KbDocumentPermission;
import com.zjl.knowledge.mapper.KbDocumentChunkLogMapper;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.mapper.KbDocumentPermissionMapper;
import com.zjl.knowledge.milvus.VectorDocChunk;
import com.zjl.knowledge.service.DocumentVisibilityService;
import com.zjl.knowledge.service.KbChunkService;
import com.zjl.knowledge.service.KbDocumentService;
import com.zjl.knowledge.service.VectorSyncService;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识文档服务：轻量查询/更新 + 委托给独立职责服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbDocumentServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument> implements KbDocumentService {

    private final DocumentUploadService documentUploadService;
    private final DocumentChunkingService documentChunkingService;
    private final DocumentDeleteService documentDeleteService;
    private final KbDocumentPermissionMapper kbDocumentPermissionMapper;
    private final KbDocumentChunkMapper kbDocumentChunkMapper;
    private final KbDocumentChunkLogMapper kbDocumentChunkLogMapper;
    private final DocumentVisibilityService documentVisibilityService;
    private final VectorSyncService vectorSyncService;
    private final KbChunkService kbChunkService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public IPage<KbDocument> pageVisible(Page<KbDocument> page, UserContext user) {
        Long deptId = user.getDepartmentId();
        Long projectId = user.getProjectId();
        int admin = user.isAdmin() ? 1 : 0;
        return baseMapper.selectPageVisible(page, user.getUserId(), deptId, projectId, admin);
    }

    @Override
    public KbDocument getVisible(Long id, UserContext user) {
        KbDocument doc = getById(id);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        List<KbDocumentPermission> perms = kbDocumentPermissionMapper.selectList(
                new LambdaQueryWrapper<KbDocumentPermission>().eq(KbDocumentPermission::getDocumentId, id)
        );
        if (!documentVisibilityService.canView(doc, user, perms)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return doc;
    }

    @Override
    public Long upload(UserContext user, KbDocumentUploadRequest meta, MultipartFile file) {
        return documentUploadService.upload(user, meta, file);
    }

    @Override
    public void startChunk(Long documentId, UserContext user) {
        assertWritable(documentId, user);
        documentChunkingService.startChunk(documentId, user);
    }

    @Override
    public void executeChunk(Long documentId, Long operatorUserId) {
        documentChunkingService.executeChunk(documentId, operatorUserId);
    }

    @Override
    public void executeChunkAsUser(Long documentId, UserContext user) {
        documentChunkingService.executeChunkAsUser(documentId, user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDocument(Long documentId, KbDocumentUpdateRequest request, UserContext user) {
        assertWritable(documentId, user);
        KbDocument doc = getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        if (DocumentStatus.RUNNING.name().equals(doc.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档正在分块中，无法修改");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档标题不能为空");
        }
        LambdaUpdateWrapper<KbDocument> uw = Wrappers.lambdaUpdate(KbDocument.class)
                .eq(KbDocument::getId, documentId)
                .set(KbDocument::getTitle, request.getTitle().trim())
                .set(KbDocument::getUpdatedAt, LocalDateTime.now());

        if (StringUtils.hasText(request.getProcessMode())) {
            ProcessMode pm = ProcessMode.normalize(request.getProcessMode());
            uw.set(KbDocument::getProcessMode, pm.name());
            if (pm == ProcessMode.CHUNK) {
                ChunkingMode cm = ChunkingMode.fromValue(request.getChunkStrategy());
                String cfg = documentUploadService.normalizeChunkConfigJson(cm, request.getChunkConfig());
                uw.set(KbDocument::getChunkStrategy, cm.name());
                uw.set(KbDocument::getChunkConfig, cfg);
                uw.set(KbDocument::getPipelineId, null);
            } else {
                if (!StringUtils.hasText(request.getPipelineId())) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "使用 PIPELINE 模式时必须指定 pipelineId");
                }
                uw.set(KbDocument::getPipelineId, request.getPipelineId().trim());
                uw.set(KbDocument::getChunkStrategy, null);
                uw.set(KbDocument::getChunkConfig, null);
            }
        }
        if (request.getScheduleEnabled() != null) {
            uw.set(KbDocument::getScheduleEnabled, request.getScheduleEnabled());
        }
        if (StringUtils.hasText(request.getScheduleCron())) {
            uw.set(KbDocument::getScheduleCron, request.getScheduleCron().trim());
        }
        if (StringUtils.hasText(request.getSourceLocation())) {
            uw.set(KbDocument::getSourceLocation, request.getSourceLocation().trim());
        }
        baseMapper.update(null, uw);
    }

    @Override
    public void enableDocument(Long documentId, boolean enabled, UserContext user) {
        getVisible(documentId, user);
        assertWritable(documentId, user);
        KbDocument doc = getById(documentId);
        if (DocumentStatus.RUNNING.name().equals(doc.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档正在分块中，无法修改");
        }
        int target = enabled ? 1 : 0;
        if (doc.getEnabled() != null && doc.getEnabled() == target) {
            return;
        }

        boolean shouldEmbed = vectorSyncService.shouldEmbed(doc);
        List<KbDocumentChunk> chunks = kbDocumentChunkMapper.selectList(
                Wrappers.lambdaQuery(KbDocumentChunk.class)
                        .eq(KbDocumentChunk::getDocumentId, documentId)
                        .orderByAsc(KbDocumentChunk::getChunkIndex)
        );
        List<VectorDocChunk> vectorChunks = null;
        if (enabled && shouldEmbed) {
            if (chunks.isEmpty()) {
                log.warn("启用文档时未找到任何 Chunk，跳过向量重建, documentId={}", documentId);
            } else {
                vectorChunks = chunks.stream()
                        .map(c -> VectorDocChunk.builder()
                                .chunkId(String.valueOf(c.getId()))
                                .content(c.getChunkText())
                                .index(c.getChunkIndex())
                                .build())
                        .collect(Collectors.toList());
                List<String> texts = vectorChunks.stream().map(VectorDocChunk::getContent).collect(Collectors.toList());
                List<List<Float>> vecs = vectorSyncService.embedBatch(texts, doc);
                for (int i = 0; i < vectorChunks.size(); i++) {
                    vectorChunks.get(i).setEmbedding(VectorSyncService.toArray(vecs.get(i)));
                }
            }
        }

        final List<VectorDocChunk> finalChunks = vectorChunks;
        transactionTemplate.executeWithoutResult(status -> {
            doc.setEnabled(target);
            doc.setUpdatedAt(LocalDateTime.now());
            updateById(doc);
            kbChunkService.updateEnabledByDocId(documentId, enabled, user.getUserId());
            if (shouldEmbed) {
                if (!enabled) {
                    vectorSyncService.deleteDocumentVectors(doc);
                } else if (finalChunks != null && !finalChunks.isEmpty()) {
                    vectorSyncService.indexDocumentChunks(doc, finalChunks);
                }
            }
        });
    }

    @Override
    public IPage<KbDocumentChunkLogVO> pageChunkLogs(Long documentId, long current, long size, UserContext user) {
        getVisible(documentId, user);
        Page<KbDocumentChunkLog> p = new Page<>(current, size);
        IPage<KbDocumentChunkLog> raw = kbDocumentChunkLogMapper.selectPage(p,
                Wrappers.lambdaQuery(KbDocumentChunkLog.class)
                        .eq(KbDocumentChunkLog::getDocumentId, documentId)
                        .orderByDesc(KbDocumentChunkLog::getStartedAt));
        return raw.convert(this::toChunkLogVo);
    }

    @Override
    public List<KbDocument> searchDocuments(UserContext user, String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        int size = Math.min(Math.max(limit, 1), 50);
        LambdaQueryWrapper<KbDocument> q = Wrappers.lambdaQuery(KbDocument.class)
                .like(KbDocument::getTitle, keyword.trim())
                .orderByDesc(KbDocument::getUpdatedAt)
                .last("LIMIT " + size);
        if (!user.isAdmin()) {
            q.eq(KbDocument::getOwnerId, user.getUserId());
        }
        return list(q);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVisible(Long id, UserContext user) {
        documentDeleteService.deleteVisible(id, user);
    }

    private KbDocumentChunkLogVO toChunkLogVo(KbDocumentChunkLog e) {
        KbDocumentChunkLogVO vo = new KbDocumentChunkLogVO();
        vo.setId(e.getId());
        vo.setDocumentId(e.getDocumentId());
        vo.setStatus(e.getStatus());
        vo.setProcessMode(e.getProcessMode());
        vo.setChunkStrategy(e.getChunkStrategy());
        vo.setPipelineId(e.getPipelineId());
        vo.setChunkCount(e.getChunkCount());
        vo.setExtractDurationMs(e.getExtractDurationMs());
        vo.setChunkDurationMs(e.getChunkDurationMs());
        vo.setEmbedDurationMs(e.getEmbedDurationMs());
        vo.setPersistDurationMs(e.getPersistDurationMs());
        vo.setTotalDurationMs(e.getTotalDurationMs());
        vo.setErrorMessage(e.getErrorMessage());
        vo.setStartedAt(e.getStartedAt());
        vo.setEndedAt(e.getEndedAt());
        return vo;
    }

    private void assertWritable(Long documentId, UserContext user) {
        KbDocument doc = getById(documentId);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        if (!user.isAdmin() && !Objects.equals(doc.getOwnerId(), user.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
