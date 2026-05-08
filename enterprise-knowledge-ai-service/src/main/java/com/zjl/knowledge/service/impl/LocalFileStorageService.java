package com.zjl.knowledge.service.impl;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.config.KbStorageProperties;
import com.zjl.knowledge.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 本地磁盘文件存储实现。
 */
@Component
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private final KbStorageProperties kbStorageProperties;

    @Override
    public String store(Long docId, String originalName, InputStream content) throws IOException {
        Path baseDir = Paths.get(kbStorageProperties.getUploadDir()).toAbsolutePath().normalize();
        Path dir = baseDir.resolve(docId.toString());
        Files.createDirectories(dir);

        String safeName = StringUtils.cleanPath(Objects.requireNonNullElse(originalName, "upload.bin"));
        if (safeName.contains("..")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法文件名");
        }
        Path target = dir.resolve(safeName);
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
    }

    @Override
    public InputStream read(Long docId) throws IOException {
        Path baseDir = Paths.get(kbStorageProperties.getUploadDir()).toAbsolutePath().normalize();
        Path dir = baseDir.resolve(docId.toString());
        if (!Files.exists(dir)) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        try (Stream<Path> files = Files.list(dir)) {
            Path first = files.filter(Files::isRegularFile).findFirst().orElse(null);
            if (first == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
            }
            return Files.newInputStream(first);
        }
    }

    @Override
    public void delete(Long docId) throws IOException {
        Path baseDir = Paths.get(kbStorageProperties.getUploadDir()).toAbsolutePath().normalize();
        Path dir = baseDir.resolve(docId.toString());
        if (Files.exists(dir)) {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }
}
