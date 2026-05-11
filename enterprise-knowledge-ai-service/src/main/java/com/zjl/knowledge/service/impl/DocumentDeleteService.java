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
import com.zjl.knowledge.service.VectorSyncService;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 文档删除服务：先删 Milvus 向量，再清 DB 关联数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentDeleteService {

    private final KbDocumentMapper kbDocumentMapper;
    private final KbDocumentChunkMapper kbDocumentChunkMapper;
    private final KbDocumentChunkLogMapper kbDocumentChunkLogMapper;
    private final KbDocumentPermissionMapper kbDocumentPermissionMapper;
    private final VectorSyncService vectorSyncService;

    /**
     * 删除文档：校验权限与状态 → 删 Milvus 向量 → 删 chunk/log/permission → 逻辑删文档
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteVisible(Long id, UserContext user) {
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
        try {
            vectorSyncService.deleteDocumentVectors(doc);
        } catch (BizException ex) {
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "删除向量失败，文档未删除: " + ex.getMessage());
        }
        kbDocumentChunkMapper.delete(new LambdaQueryWrapper<KbDocumentChunk>().eq(KbDocumentChunk::getDocumentId, id));
        kbDocumentChunkLogMapper.delete(new LambdaQueryWrapper<KbDocumentChunkLog>().eq(KbDocumentChunkLog::getDocumentId, id));
        kbDocumentPermissionMapper.delete(
                new LambdaQueryWrapper<KbDocumentPermission>().eq(KbDocumentPermission::getDocumentId, id));
        kbDocumentMapper.deleteById(id);
    }
}
