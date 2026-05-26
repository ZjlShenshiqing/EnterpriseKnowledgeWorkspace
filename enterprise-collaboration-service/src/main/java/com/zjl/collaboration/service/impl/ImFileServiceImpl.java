package com.zjl.collaboration.service.impl;

import com.zjl.collaboration.config.ImOssProperties;
import com.zjl.collaboration.service.ImFileService;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class ImFileServiceImpl implements ImFileService {

    private final S3Client s3Client;
    private final String bucket;

    public ImFileServiceImpl(ImOssProperties props) {
        this.bucket = props.getBucket();
        if (StringUtils.hasText(props.getAccessKey())) {
            this.s3Client = S3Client.builder()
                    .endpointOverride(URI.create(props.getEndpoint()))
                    .region(Region.of(props.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                    .build();
            log.info("IM OSS 已初始化: endpoint={}, bucket={}", props.getEndpoint(), props.getBucket());
        } else {
            this.s3Client = null;
            log.info("IM OSS 未配置 (access-key 为空)，文件上传功能不可用");
        }
    }

    @Override
    public boolean isAvailable() {
        return s3Client != null;
    }

    @Override
    public Map<String, Object> upload(MultipartFile file) throws IOException {
        if (s3Client == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "OSS 未配置，文件上传暂不可用");
        }
        String originalName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "file");
        if (originalName.contains("..")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法文件名");
        }
        String key = "im/" + UUID.randomUUID() + "/" + originalName;

        byte[] bytes = file.getBytes();
        PutObjectRequest request = PutObjectRequest.builder().bucket(bucket).key(key).build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ossKey", key);
        result.put("fileName", originalName);
        result.put("fileSize", bytes.length);
        result.put("fileType",
                file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        log.info("IM 文件上传成功: key={}, size={}", key, bytes.length);
        return result;
    }

    @Override
    public InputStream read(String key) throws IOException {
        if (s3Client == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "OSS 未配置，文件下载暂不可用");
        }
        try {
            return s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在: " + key);
        }
    }
}
