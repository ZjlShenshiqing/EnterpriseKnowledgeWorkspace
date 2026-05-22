package com.zjl.knowledge.service.impl;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.config.KbStorageProperties;
import com.zjl.knowledge.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * 本地磁盘文件存储实现
 */
@Component
@ConditionalOnProperty(name = "app.kb.storage.type", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private final KbStorageProperties kbStorageProperties;

    @Override
    public String store(Long docId, String originalName, InputStream content) throws IOException {
        String safeName = StringUtils.cleanPath(Objects.requireNonNullElse(originalName, "upload.bin"));
        if (safeName.contains("..")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法文件名");
        }
        Path baseDir = Paths.get(kbStorageProperties.getUploadDir()).toAbsolutePath().normalize();
        Path dir = baseDir.resolve(docId.toString());
        Files.createDirectories(dir);
        Path target = dir.resolve(safeName);
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return docId + "/" + safeName;
    }

    private Path resolvePath(String relativePath) {
        return Paths.get(kbStorageProperties.getUploadDir()).toAbsolutePath().normalize().resolve(relativePath);
    }

    @Override
    public InputStream read(String key) throws IOException {
        Path target = resolvePath(key);
        if (!Files.exists(target)) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return Files.newInputStream(target);
    }

    @Override
    public void delete(String key) throws IOException {
        Path target = resolvePath(key);
        Files.deleteIfExists(target);
        /** 尝试清理父目录（docId 目录），忽略失败 */
        Path parent = target.getParent();
        if (parent != null) {
            try {
                Files.deleteIfExists(parent);
            } catch (IOException ignored) {
            }
        }
    }
}
