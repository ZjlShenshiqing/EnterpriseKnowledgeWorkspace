package com.zjl.knowledge.milvus;

import com.zjl.knowledge.config.MilvusProperties;
import com.zjl.knowledge.config.RagRetrievalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Milvus 集合启动初始化测试。
 */
@ExtendWith(MockitoExtension.class)
class MilvusCollectionBootstrapTest {

    @Mock
    private MilvusCollectionHelper milvusCollectionHelper;

    private MilvusProperties milvusProperties;

    private RagRetrievalProperties retrievalProperties;

    @BeforeEach
    void setUp() {
        milvusProperties = new MilvusProperties();
        milvusProperties.setCollection("kb_chunk_embedding");
        milvusProperties.setHybridCollection("kb_chunk_hybrid_v1");
        retrievalProperties = new RagRetrievalProperties();
        retrievalProperties.setMode(RagRetrievalProperties.RetrievalMode.VECTOR_ONLY);
    }

    @Test
    void initLoadsOnlyDenseCollectionInVectorOnlyMode() {
        MilvusCollectionBootstrap bootstrap =
                new MilvusCollectionBootstrap(milvusCollectionHelper, milvusProperties, retrievalProperties);

        bootstrap.init();

        verify(milvusCollectionHelper).ensureCollectionLoaded("kb_chunk_embedding");
        verify(milvusCollectionHelper, never()).ensureHybridCollectionLoaded("kb_chunk_hybrid_v1");
    }

    @Test
    void initLoadsHybridCollectionInHybridMode() {
        retrievalProperties.setMode(RagRetrievalProperties.RetrievalMode.HYBRID_MILVUS);
        MilvusCollectionBootstrap bootstrap =
                new MilvusCollectionBootstrap(milvusCollectionHelper, milvusProperties, retrievalProperties);

        bootstrap.init();

        verify(milvusCollectionHelper).ensureCollectionLoaded("kb_chunk_embedding");
        verify(milvusCollectionHelper).ensureHybridCollectionLoaded("kb_chunk_hybrid_v1");
    }
}
