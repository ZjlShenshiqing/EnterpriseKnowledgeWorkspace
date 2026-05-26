package com.zjl.knowledge.service;

import com.zjl.common.context.UserContext;
import com.zjl.knowledge.dto.KbDocumentUploadRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档上传服务
 */
public interface DocumentUploadService {

    Long upload(UserContext user, KbDocumentUploadRequest meta, MultipartFile file);
}
