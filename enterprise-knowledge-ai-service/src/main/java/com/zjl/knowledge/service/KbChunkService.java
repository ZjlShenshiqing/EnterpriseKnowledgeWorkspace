package com.zjl.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zjl.knowledge.dto.chunk.ChunkSensitivityUpdateRequest;
import com.zjl.knowledge.dto.chunk.ChunkSensitivityVO;
import com.zjl.knowledge.dto.chunk.KbChunkBatchRequest;
import com.zjl.knowledge.dto.chunk.KbChunkCreateRequest;
import com.zjl.knowledge.dto.chunk.KbChunkPageRequest;
import com.zjl.knowledge.dto.chunk.KbChunkUpdateRequest;
import com.zjl.knowledge.dto.chunk.KbChunkVO;
import com.zjl.knowledge.web.UserContext;

import java.util.List;

/**
 * 知识文档 Chunk 服务（对齐「分块 + 向量同步」能力）
 */
public interface KbChunkService {

    IPage<KbChunkVO> pageQuery(Long docId, KbChunkPageRequest request, UserContext user);

    KbChunkVO create(Long docId, KbChunkCreateRequest request, UserContext user);

    void batchCreate(Long docId, List<KbChunkCreateRequest> requests, UserContext user);

    void batchCreate(Long docId, List<KbChunkCreateRequest> requests, boolean writeVector, UserContext user);

    void update(Long docId, Long chunkId, KbChunkUpdateRequest request, UserContext user);

    void delete(Long docId, Long chunkId, UserContext user);

    void enableChunk(Long docId, Long chunkId, boolean enabled, UserContext user);

    void batchToggleEnabled(Long docId, KbChunkBatchRequest request, boolean enabled, UserContext user);

    void updateEnabledByDocId(Long docId, boolean enabled, Long updatedBy);

    List<KbChunkVO> listByDocId(Long docId, UserContext user);

    void deleteByDocId(Long docId);

    /**
     * 查询文档中被自动标记为敏感的 chunk 列表
     *
     * @param docId 文档 ID
     * @param user  当前用户
     * @return 敏感 chunk 视图列表
     */
    List<ChunkSensitivityVO> listSensitiveChunks(Long docId, UserContext user);

    /**
     * 批量更新 chunk 敏感级别
     *
     * @param docId   文档 ID
     * @param request 更新请求
     * @param user    当前用户
     */
    void updateChunkSensitivity(Long docId, ChunkSensitivityUpdateRequest request, UserContext user);
}
