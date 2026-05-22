package com.zjl.knowledge.service.impl;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.config.KbStorageProperties;
import com.zjl.knowledge.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;

/**
 * S3 兼容对象存储实现 — 支持 Supabase Storage、MinIO、AWS S3 等
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kb.storage.type", havingValue = "s3")
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final String bucket;

    public S3FileStorageService(KbStorageProperties properties) {
        KbStorageProperties.S3 s3 = properties.getS3();
        if (!StringUtils.hasText(s3.getEndpoint())) {
            throw new IllegalStateException("S3 存储模式下 app.kb.storage.s3.endpoint 不能为空");
        }
        if (!StringUtils.hasText(s3.getBucket())) {
            throw new IllegalStateException("S3 存储模式下 app.kb.storage.s3.bucket 不能为空");
        }
        this.bucket = s3.getBucket();
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(s3.getEndpoint()))
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())))
                .build();
        log.info("S3 存储已初始化: endpoint={}, bucket={}", s3.getEndpoint(), s3.getBucket());
    }

    @Override
    public String store(Long docId, String originalName, InputStream content) throws IOException {
        String safeName = StringUtils.cleanPath(Objects.requireNonNullElse(originalName, "upload.bin"));
        if (safeName.contains("..")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法文件名");
        }
        String key = docId + "/" + safeName;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        byte[] bytes = content.readAllBytes();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        log.info("S3 上传成功: key={}, size={}", key, bytes.length);
        return key;
    }

    @Override
    public InputStream read(String key) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        try {
            return s3Client.getObject(request);
        } catch (NoSuchKeyException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在: " + key);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3Client.deleteObject(request);
        log.info("S3 删除成功: key={}", key);
    }
}
