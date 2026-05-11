package com.zjl.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zjl.knowledge.dto.KbDocumentChunkLogVO;
import com.zjl.knowledge.dto.KbDocumentUpdateRequest;
import com.zjl.knowledge.dto.KbDocumentUploadRequest;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.web.UserContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识文档服务
 */
public interface KbDocumentService extends IService<KbDocument> {

    IPage<KbDocument> pageVisible(Page<KbDocument> page, UserContext user);

    KbDocument getVisible(Long id, UserContext user);

    /**
     * 上传文件并落库为 {@link com.zjl.knowledge.domain.DocumentStatus#PENDING}，需再调用 {@link #startChunk} 触发异步分块
     */
    Long upload(UserContext user, KbDocumentUploadRequest meta, MultipartFile file);

    void deleteVisible(Long id, UserContext user);

    /**
     * 提交分块任务：将状态置为 RUNNING 并在事务提交后异步执行 {@link #executeChunk}
     */
    void startChunk(Long documentId, UserContext user);

    /**
     * 执行分块（消费者 / 监听器调用；也可用于运维补偿）
     */
    void executeChunk(Long documentId, Long operatorUserId);

    /**
     * 带写权限校验的手动触发分块（HTTP 入口）
     */
    void executeChunkAsUser(Long documentId, UserContext user);

    void updateDocument(Long documentId, KbDocumentUpdateRequest request, UserContext user);

    void enableDocument(Long documentId, boolean enabled, UserContext user);

    IPage<KbDocumentChunkLogVO> pageChunkLogs(Long documentId, long current, long size, UserContext user);

    List<KbDocument> searchDocuments(UserContext user, String keyword, int limit);

    /**
     * 刷新文档的 chunk_count（从 kb_document_chunk 表查询计数）
     */
    void refreshChunkCount(Long documentId);
}
