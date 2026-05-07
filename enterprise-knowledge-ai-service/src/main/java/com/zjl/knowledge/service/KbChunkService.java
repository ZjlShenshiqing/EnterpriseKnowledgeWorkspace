package com.zjl.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
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
}
