package com.zjl.knowledge.milvus;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.config.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Milvus 集合创建与管理工具
 *
 * <p>负责按 Schema 创建集合、建立 AUTOINDEX 索引并加载到内存。
 * Schema 包含四个字段：{@code id}(VarChar PK)、{@code content}(VarChar)、
 * {@code metadata}(JSON)、{@code embedding}(FloatVector + COSINE)</p>
 *
 * <p>若环境中已有旧版集合（仅 chunk_id / document_id / embedding），
 * 需手动 drop 后由本类重建，否则字段不匹配会导致写入失败</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusCollectionHelper {

    /**
     * content 字段最大字符数，与 Milvus VarChar 上限对齐
     */
    private static final int CONTENT_MAX_LEN = 65535;

    /**
     * Milvus gRPC 客户端
     */
    private final MilvusClientV2 milvusClient;

    /**
     * Milvus 配置属性
     */
    private final MilvusProperties milvusProperties;

    /**
     * 确保集合存在并已加载到内存
     *
     * <p>步骤：检查集合是否存在 → 不存在则创建（Schema + Index）→
     * 同步 load 到内存。创建时使用 COSINE 度量 + AUTOINDEX 索引类型</p>
     *
     * @param collectionName 集合名，不可为空
     * @throws IllegalArgumentException 集合名为空时抛出
     * @throws BizException             集合操作失败时抛出，错误码 {@code VECTOR_WRITE_FAILED}
     */
    public void ensureCollectionLoaded(String collectionName) {
        if (!StringUtils.hasText(collectionName)) {
            throw new IllegalArgumentException("collectionName must not be blank");
        }
        try {
            Boolean exists = milvusClient.hasCollection(
                    HasCollectionReq.builder().collectionName(collectionName).build());
            if (!Boolean.TRUE.equals(exists)) {
                CreateCollectionReq.CollectionSchema schema =
                        CreateCollectionReq.CollectionSchema.builder().build();
                schema.addField(AddFieldReq.builder()
                        .fieldName("id")
                        .dataType(DataType.VarChar)
                        .maxLength(128)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("content")
                        .dataType(DataType.VarChar)
                        .maxLength(CONTENT_MAX_LEN)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("metadata")
                        .dataType(DataType.JSON)
                        .build());
                schema.addField(AddFieldReq.builder()
                        .fieldName("embedding")
                        .dataType(DataType.FloatVector)
                        .dimension(milvusProperties.getVectorDimension())
                        .build());
                CreateCollectionReq req = CreateCollectionReq.builder()
                        .collectionName(collectionName)
                        .collectionSchema(schema)
                        .indexParams(List.of(IndexParam.builder()
                                .fieldName("embedding")
                                .indexType(IndexParam.IndexType.AUTOINDEX)
                                .metricType(IndexParam.MetricType.COSINE)
                                .build()))
                        .build();
                milvusClient.createCollection(req);
                log.info("Milvus collection created: {}", collectionName);
            }
            milvusClient.loadCollection(LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .async(false)
                    .build());
            log.info("Milvus collection loaded: {}", collectionName);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Milvus ensure collection failed, collection={}", collectionName, ex);
            throw new BizException(ErrorCode.VECTOR_WRITE_FAILED,
                    "Milvus 集合初始化失败: " + ex.getMessage());
        }
    }
}
