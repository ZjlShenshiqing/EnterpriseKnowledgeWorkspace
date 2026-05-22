package com.zjl.knowledge.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件存储抽象，支持本地磁盘、MinIO 等多实现
 */
public interface FileStorageService {

    /**
     * 存储文件
     *
     * @param docId       文档 ID
     * @param originalName 原始文件名
     * @param content     文件流
     * @return 存储后的访问路径
     * @throws IOException IO 异常
     */
    String store(Long docId, String originalName, InputStream content) throws IOException;

    /**
     * 读取文件
     *
     * @param key 存储路径（由 store 返回）
     * @return 文件流
     * @throws IOException IO 异常
     */
    InputStream read(String key) throws IOException;

    /**
     * 删除文件
     *
     * @param key 存储路径（由 store 返回）
     * @throws IOException IO 异常
     */
    void delete(String key) throws IOException;
}
