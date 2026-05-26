package com.zjl.collaboration.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * IM 文件存储服务
 */
public interface ImFileService {

    boolean isAvailable();

    Map<String, Object> upload(MultipartFile file) throws IOException;

    InputStream read(String key) throws IOException;
}
