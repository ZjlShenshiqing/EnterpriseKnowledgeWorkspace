package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.config.KbStorageProperties;
import com.zjl.knowledge.domain.ChunkingMode;
import com.zjl.knowledge.domain.DocumentPermissionType;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.domain.ProcessMode;
import com.zjl.knowledge.domain.SourceType;
import com.zjl.knowledge.dto.KbDocumentUploadRequest;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentPermission;
import com.zjl.knowledge.entity.KbKnowledgeBase;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.mapper.KbDocumentPermissionMapper;
import com.zjl.knowledge.mapper.KbKnowledgeBaseMapper;
import com.zjl.knowledge.service.FileStorageService;
import com.zjl.knowledge.service.KbCategoryService;
import com.zjl.knowledge.service.TikaDocumentParser;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * 文档上传服务：元数据校验 → 落盘 → 权限行写入
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadService {

    private final KbDocumentMapper kbDocumentMapper;
    private final KbCategoryService kbCategoryService;
    private final KbDocumentPermissionMapper kbDocumentPermissionMapper;
    private final KbKnowledgeBaseMapper kbKnowledgeBaseMapper;
    private final TikaDocumentParser tikaDocumentParser;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    /**
     * 上传文档：校验 → INSERT kb_document (PENDING) → 落盘 → Tika 探测 MIME → 权限行
     */
    @Transactional(rollbackFor = Exception.class)
    public Long upload(UserContext user, KbDocumentUploadRequest meta, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件不能为空");
        }
        SourceType sourceType = SourceType.normalize(meta.getSourceType());
        if (sourceType == SourceType.URL) {
            throw new BizException(ErrorCode.PARAM_INVALID, "URL 来源上传尚未支持，请使用 FILE 上传");
        }
        validateSchedule(meta, sourceType);

        DocumentPermissionType permType;
        try {
            permType = DocumentPermissionType.valueOf(meta.getPermissionType());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "permissionType 非法");
        }
        if (permType == DocumentPermissionType.USER
                && (meta.getGrantUserIds() == null || meta.getGrantUserIds().isEmpty())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "USER 权限必须指定 grantUserIds");
        }
        if (permType == DocumentPermissionType.PROJECT && meta.getGrantProjectId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "PROJECT 权限必须指定 grantProjectId");
        }
        if (kbCategoryService.getById(meta.getCategoryId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        if (meta.getKbId() != null) {
            KbKnowledgeBase kb = kbKnowledgeBaseMapper.selectById(meta.getKbId());
            if (kb == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
            }
        }

        ProcessMode processMode = ProcessMode.normalize(meta.getProcessMode());
        ChunkingMode chunkingMode = ChunkingMode.fromValue(meta.getChunkStrategy());
        String chunkConfigJson = normalizeChunkConfigJson(chunkingMode, meta.getChunkConfig());

        KbDocument doc = new KbDocument();
        doc.setTitle(meta.getTitle().trim());
        doc.setCategoryId(meta.getCategoryId());
        doc.setKbId(meta.getKbId());
        doc.setOwnerId(user.getUserId());
        doc.setDepartmentId(user.getDepartmentId());
        doc.setFileName(file.getOriginalFilename());
        doc.setFileType(file.getContentType());
        doc.setFileSize(file.getSize());
        doc.setTags(meta.getTags());
        doc.setPermissionType(permType.name());
        doc.setStatus(DocumentStatus.PENDING.name());
        doc.setCurrentVersion(1);
        doc.setChunkCount(0);
        doc.setEnabled(1);
        doc.setProcessMode(processMode.name());
        doc.setChunkStrategy(chunkingMode.name());
        doc.setChunkConfig(chunkConfigJson);
        doc.setPipelineId(meta.getPipelineId());
        doc.setSourceType(sourceType.name());
        doc.setSourceLocation(StringUtils.hasText(meta.getSourceLocation()) ? meta.getSourceLocation().trim() : null);
        doc.setScheduleEnabled(Boolean.TRUE.equals(meta.getScheduleEnabled()) ? 1 : 0);
        doc.setScheduleCron(StringUtils.hasText(meta.getScheduleCron()) ? meta.getScheduleCron().trim() : null);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        kbDocumentMapper.insert(doc);

        try (InputStream in = file.getInputStream()) {
            String storedPath = fileStorageService.store(doc.getId(),
                    Objects.requireNonNullElse(file.getOriginalFilename(), "upload.bin"), in);
            doc.setFileUrl(storedPath);

            try (InputStream probeIn = fileStorageService.read(doc.getId())) {
                byte[] probe = new byte[8192];
                int probeLen = probeIn.read(probe);
                if (probeLen > 0) {
                    String detected = tikaDocumentParser.detectMime(
                            java.util.Arrays.copyOf(probe, probeLen), file.getOriginalFilename());
                    if (StringUtils.hasText(detected)) {
                        doc.setFileType(detected);
                    }
                }
            }
            doc.setUpdatedAt(LocalDateTime.now());
            kbDocumentMapper.updateById(doc);

            savePermissionRows(doc.getId(), permType, meta, user.getUserId());
            return doc.getId();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            markUploadFailed(doc.getId(), ex.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件保存失败: " + ex.getMessage());
        }
    }

    /**
     * 校验 JSON 格式并标准化
     */
    String normalizeChunkConfigJson(ChunkingMode mode, String chunkConfigJson) {
        if (!StringUtils.hasText(chunkConfigJson)) {
            return null;
        }
        try {
            objectMapper.readValue(chunkConfigJson.trim(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "分块参数 JSON 格式不合法");
        }
        return chunkConfigJson.trim();
    }

    private void markUploadFailed(Long docId, String reason) {
        kbDocumentMapper.update(null, Wrappers.lambdaUpdate(KbDocument.class)
                .eq(KbDocument::getId, docId)
                .set(KbDocument::getStatus, DocumentStatus.FAILED.name())
                .set(KbDocument::getSummary, reason)
                .set(KbDocument::getUpdatedAt, LocalDateTime.now()));
    }

    private void validateSchedule(KbDocumentUploadRequest meta, SourceType sourceType) {
        if (!Boolean.TRUE.equals(meta.getScheduleEnabled())) {
            return;
        }
        if (sourceType != SourceType.URL) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仅 URL 来源支持定时拉取配置");
        }
        if (!StringUtils.hasText(meta.getScheduleCron())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "启用定时拉取时必须填写 scheduleCron");
        }
    }

    private void savePermissionRows(Long documentId, DocumentPermissionType type, KbDocumentUploadRequest meta, Long createdBy) {
        if (type == DocumentPermissionType.USER && meta.getGrantUserIds() != null) {
            for (Long uid : meta.getGrantUserIds()) {
                KbDocumentPermission row = new KbDocumentPermission();
                row.setDocumentId(documentId);
                row.setPermissionTargetType("USER");
                row.setPermissionTargetId(uid);
                row.setPermissionLevel("READ");
                row.setCreatedBy(createdBy);
                row.setCreatedAt(LocalDateTime.now());
                kbDocumentPermissionMapper.insert(row);
            }
        }
        if (type == DocumentPermissionType.PROJECT && meta.getGrantProjectId() != null) {
            KbDocumentPermission row = new KbDocumentPermission();
            row.setDocumentId(documentId);
            row.setPermissionTargetType("PROJECT");
            row.setPermissionTargetId(meta.getGrantProjectId());
            row.setPermissionLevel("READ");
            row.setCreatedBy(createdBy);
            row.setCreatedAt(LocalDateTime.now());
            kbDocumentPermissionMapper.insert(row);
        }
    }
}
