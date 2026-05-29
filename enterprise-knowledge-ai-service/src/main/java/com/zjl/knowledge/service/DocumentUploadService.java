package com.zjl.knowledge.service;

import com.zjl.knowledge.domain.ChunkingMode;
import com.zjl.knowledge.web.UserContext;
import com.zjl.knowledge.dto.KbDocumentUploadRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档上传服务
 *
 * <p>负责知识库文档上传、元数据保存以及分块配置校验等操作。</p>
 *
 * @author zjl
 * @date 2026-05-22
 */
public interface DocumentUploadService {

    /**
     * 上传知识库文档
     *
     * @param user 当前登录用户上下文
     * @param meta 文档上传元数据
     * @param file 上传的文件
     * @return 文档 ID
     */
    Long upload(UserContext user, KbDocumentUploadRequest meta, MultipartFile file);

    /**
     * 校验分块配置 JSON 格式，并按分块模式标准化配置内容
     *
     * @param mode 分块模式
     * @param chunkConfigJson 分块配置 JSON 字符串
     * @return 标准化后的分块配置 JSON 字符串
     */
    String normalizeChunkConfigJson(ChunkingMode mode, String chunkConfigJson);
}
