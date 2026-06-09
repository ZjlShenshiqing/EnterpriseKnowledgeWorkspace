package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.zjl.knowledge.service.DocumentUploadService;

/**
 * 文档上传服务：校验 → 魔数检测 → INSERT kb_document (PENDING) → 落盘 → 权限行写入
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadServiceImpl implements DocumentUploadService {

    /**
     * 支持的 MIME 大类
     */
    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv",
            "text/markdown",
            "text/x-web-markdown",
            "text/html",
            "image/png",
            "image/jpeg"
    );

    private final KbDocumentMapper kbDocumentMapper;
    private final KbCategoryService kbCategoryService;
    private final KbDocumentPermissionMapper kbDocumentPermissionMapper;
    private final KbKnowledgeBaseMapper kbKnowledgeBaseMapper;
    private final TikaDocumentParser tikaDocumentParser;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /**
     * 上传文档：校验参数 → 检测文件类型 → 文件预存储 → 创建文档记录 → 更新存储路径 → 写入权限。
     * 
     * <p>设计说明：为保证 MySQL 和 S3 的数据一致性，采用"先写外部存储，后写数据库"策略：
     * 1. 先将文件存储到 S3（使用临时文件名）
     * 2. 在事务中插入数据库记录
     * 3. 更新文件路径关联
     * 4. 数据库失败时删除已存储的文件
     */
    @Override
    public Long upload(UserContext user, KbDocumentUploadRequest meta, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件不能为空");
        }

        // 当前仅支持本地文件上传，URL 来源暂不接入
        SourceType sourceType = SourceType.normalize(meta.getSourceType());
        if (sourceType == SourceType.URL) {
            throw new BizException(ErrorCode.PARAM_INVALID, "URL 来源上传尚未支持，请使用 FILE 上传");
        }
        validateSchedule(meta, sourceType);

        // 校验权限类型及对应授权参数
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

        // 校验文档归属的分类和知识库是否存在
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

        // 标准化分块配置，保证后续解析流程可直接使用。
        String chunkConfigJson = normalizeChunkConfigJson(chunkingMode, meta.getChunkConfig());

        // 读取文件字节，供 MIME 检测和后续落盘复用。
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "读取文件失败: " + e.getMessage());
        }

        // 使用文件头检测真实类型，避免只依赖前端传入的 Content-Type。
        String detectedType = tikaDocumentParser.detectMime(
                fileBytes.length > 512 ? java.util.Arrays.copyOf(fileBytes, 512) : fileBytes,
                file.getOriginalFilename());
        if (!StringUtils.hasText(detectedType)) {
            detectedType = file.getContentType();
        }
        if (!SUPPORTED_MIME_TYPES.contains(detectedType)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "不支持的文件类型: " + detectedType + "（支持 PDF、Word、Excel、PPT、文本、图片）");
        }

        // ========== 关键修复：先存储文件，再插入数据库 ==========
        String originalFilename = file.getOriginalFilename();
        String tempFileName = java.util.UUID.randomUUID().toString() + "_" + 
                (originalFilename != null ? originalFilename : "upload.bin");
        
        String storedPath = null;
        try (InputStream in = new ByteArrayInputStream(fileBytes)) {
            storedPath = fileStorageService.store(null, tempFileName, in);
            log.info("文件预存储成功: path={}", storedPath);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "文件存储失败: " + ex.getMessage());
        }

        // 文件存储成功后，使用 TransactionTemplate 在事务中插入数据库记录（避免 Spring 自调用事务失效）
        Long docId;
        try {
            docId = transactionTemplate.execute(status -> {
                KbDocument doc = new KbDocument();
                doc.setTitle(meta.getTitle().trim());
                doc.setCategoryId(meta.getCategoryId());
                doc.setKbId(meta.getKbId());
                doc.setOwnerId(user.getUserId());
                doc.setDepartmentId(user.getDepartmentId());
                doc.setFileName(file.getOriginalFilename());
                doc.setFileType(detectedType);
                doc.setFileSize((long) file.getSize());
                doc.setTags(meta.getTags());
                doc.setPermissionType(meta.getPermissionType());
                doc.setStatus(DocumentStatus.PENDING.name());
                doc.setCurrentVersion(1);
                doc.setChunkCount(0);
                doc.setEnabled(1);
                doc.setProcessMode(processMode.name());
                doc.setChunkStrategy(chunkingMode.name());
                doc.setChunkConfig(chunkConfigJson);
                doc.setPipelineId(meta.getPipelineId());
                doc.setSourceType(SourceType.FILE.name());
                doc.setSourceLocation(StringUtils.hasText(meta.getSourceLocation()) ? meta.getSourceLocation().trim() : null);
                doc.setScheduleEnabled(Boolean.TRUE.equals(meta.getScheduleEnabled()) ? 1 : 0);
                doc.setScheduleCron(StringUtils.hasText(meta.getScheduleCron()) ? meta.getScheduleCron().trim() : null);
                doc.setFilterTags(StringUtils.hasText(meta.getFilterTags()) ? meta.getFilterTags().trim() : null);
                doc.setFileUrl(storedPath);
                doc.setCreatedAt(LocalDateTime.now());
                doc.setUpdatedAt(LocalDateTime.now());

                kbDocumentMapper.insert(doc);
                savePermissionRows(doc.getId(), DocumentPermissionType.valueOf(meta.getPermissionType()), meta, user.getUserId());
                return doc.getId();
            });
            log.info("文档记录已创建: docId={}", docId);
            return docId;
        } catch (Exception ex) {
            // 数据库操作失败，清理已存储的文件
            log.warn("数据库操作失败，清理预存储文件: path={}, error={}", storedPath, ex.getMessage());
            try {
                fileStorageService.delete(storedPath);
            } catch (Exception cleanupEx) {
                log.error("清理预存储文件失败: path={}, error={}", storedPath, cleanupEx.getMessage());
            }
            throw ex;
        }
    }

    /**
     * 校验 JSON 格式并标准化
     */
    @Override
    public String normalizeChunkConfigJson(ChunkingMode mode, String chunkConfigJson) {
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
