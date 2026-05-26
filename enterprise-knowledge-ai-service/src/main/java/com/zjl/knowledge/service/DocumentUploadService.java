package com.zjl.knowledge.service;

import com.zjl.knowledge.domain.ChunkingMode;
import com.zjl.knowledge.web.UserContext;
import com.zjl.knowledge.dto.KbDocumentUploadRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档上传服务
 */
public interface DocumentUploadService {

    Long upload(UserContext user, KbDocumentUploadRequest meta, MultipartFile file);

    /** 校验分块配置 JSON 格式并标准化 */
    String normalizeChunkConfigJson(ChunkingMode mode, String chunkConfigJson);
}
