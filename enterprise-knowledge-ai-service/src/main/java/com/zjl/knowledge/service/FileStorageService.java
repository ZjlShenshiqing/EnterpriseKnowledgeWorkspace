package com.zjl.knowledge.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件存储抽象，支持本地磁盘、MinIO 等多实现。
 */
public interface FileStorageService {

    /**
     * 存储文件。
     *
     * @param docId       文档 ID
     * @param originalName 原始文件名
     * @param content     文件流
     * @return 存储后的访问路径
     * @throws IOException IO 异常
     */
    String store(Long docId, String originalName, InputStream content) throws IOException;

    /**
     * 读取文件。
     *
     * @param docId 文档 ID
     * @return 文件流
     * @throws IOException IO 异常
     */
    InputStream read(Long docId) throws IOException;

    /**
     * 删除文档目录下所有文件。
     *
     * @param docId 文档 ID
     * @throws IOException IO 异常
     */
    void delete(Long docId) throws IOException;
}
