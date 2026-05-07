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
import org.springframework.util.StringUtils;

/**
 * 按集合名创建并加载 Milvus 集合。
 *
 * <p>Schema 对齐参考 {@code MilvusVectorStoreService}：主键 {@code id}（chunkId）、{@code content}、
 * {@code metadata}（JSON，含 doc_id / chunk_index / collection_name）、{@code embedding}。</p>
 *
 * <p><b>注意</b>：若环境中已有旧版集合（仅 chunk_id / document_id / embedding），需删除旧集后由本类重建，
 * 否则字段不匹配会导致写入失败。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusCollectionHelper {

    private static final int CONTENT_MAX_LEN = 65535;

    private final MilvusClientV2 milvusClient;
    private final MilvusProperties milvusProperties;

    /**
     * 若集合不存在则创建并建立索引，然后加载到内存。
     *
     * @param collectionName 集合名，不可为空
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
                        .indexParam(IndexParam.builder()
                                .fieldName("embedding")
                                .indexType(IndexParam.IndexType.AUTOINDEX)
                                .metricType(IndexParam.MetricType.COSINE)
                                .build())
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
