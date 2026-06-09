package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.entity.KbDocumentChunkLog;
import com.zjl.knowledge.entity.KbDocumentPermission;
import com.zjl.knowledge.mapper.KbDocumentChunkLogMapper;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.mapper.KbDocumentPermissionMapper;
import com.zjl.knowledge.service.DocumentDeleteService;
import com.zjl.knowledge.service.FileStorageService;
import com.zjl.knowledge.service.VectorSyncService;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 文档删除服务：先删 DB 关联数据，再删 Milvus 向量和 S3 文件
 * 
 * <p>设计说明：为保证 MySQL、Milvus、S3 的数据一致性，采用"先删数据库，后删外部存储"策略：
 * 1. 在事务中删除数据库记录（chunk、log、permission、document）
 * 2. 事务提交成功后，异步删除 Milvus 向量
 * 3. 删除 S3 文件
 * 
 * <p>这样即使外部存储删除失败，数据库记录已删除，不会出现数据不一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentDeleteServiceImpl implements DocumentDeleteService {

    private final KbDocumentMapper kbDocumentMapper;
    private final KbDocumentChunkMapper kbDocumentChunkMapper;
    private final KbDocumentChunkLogMapper kbDocumentChunkLogMapper;
    private final KbDocumentPermissionMapper kbDocumentPermissionMapper;
    private final VectorSyncService vectorSyncService;
    private final FileStorageService fileStorageService;

    /**
     * 删除文档：校验权限与状态 → 删 DB 记录 → 删 Milvus 向量 → 删 S3 文件
     */
    @Override
    public void deleteVisible(Long id, UserContext user) {
        // 先查询文档信息（用于后续删除外部资源）
        KbDocument doc = kbDocumentMapper.selectById(id);
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!user.isAdmin() && !Objects.equals(doc.getOwnerId(), user.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (DocumentStatus.RUNNING.name().equals(doc.getStatus())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文档正在分块中，无法删除");
        }

        // 保存文件路径用于后续删除
        String fileUrl = doc.getFileUrl();
        
        // 第一步：在事务中删除数据库记录
        deleteFromDatabase(id);
        
        // 第二步：事务成功后删除 Milvus 向量（外部操作放在事务外）
        try {
            vectorSyncService.deleteDocumentVectors(doc);
            log.info("Milvus 向量已删除: docId={}", id);
        } catch (Exception ex) {
            // Milvus 删除失败不影响主流程，记录日志便于后续清理
            log.error("删除 Milvus 向量失败: docId={}, error={}", id, ex.getMessage());
        }
        
        // 第三步：删除 S3 文件
        if (fileUrl != null && !fileUrl.isBlank()) {
            try {
                fileStorageService.delete(fileUrl);
                log.info("S3 文件已删除: docId={}, path={}", id, fileUrl);
            } catch (Exception ex) {
                // S3 删除失败不影响主流程，记录日志便于后续清理
                log.error("删除 S3 文件失败: docId={}, path={}, error={}", id, fileUrl, ex.getMessage());
            }
        }
    }

    /**
     * 在事务中删除数据库记录
     */
    @Transactional(rollbackFor = Exception.class)
    protected void deleteFromDatabase(Long id) {
        kbDocumentChunkMapper.delete(new LambdaQueryWrapper<KbDocumentChunk>().eq(KbDocumentChunk::getDocumentId, id));
        kbDocumentChunkLogMapper.delete(new LambdaQueryWrapper<KbDocumentChunkLog>().eq(KbDocumentChunkLog::getDocumentId, id));
        kbDocumentPermissionMapper.delete(
                new LambdaQueryWrapper<KbDocumentPermission>().eq(KbDocumentPermission::getDocumentId, id));
        kbDocumentMapper.deleteById(id);
        log.info("数据库记录已删除: docId={}", id);
    }
}
