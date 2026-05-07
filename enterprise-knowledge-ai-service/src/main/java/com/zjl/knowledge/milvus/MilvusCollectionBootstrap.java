package com.zjl.knowledge.milvus;

import com.zjl.knowledge.config.MilvusProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 启动时创建并加载 Milvus 集合；失败时根据配置中止启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusCollectionBootstrap {

    private final MilvusCollectionHelper milvusCollectionHelper;

    /**
     * Milvus 配置
     */
    private final MilvusProperties milvusProperties;

    /**
     * 初始化集合与索引。
     */
    @PostConstruct
    public void init() {
        String collection = milvusProperties.getCollection();
        try {
            milvusCollectionHelper.ensureCollectionLoaded(collection);
        } catch (Exception ex) {
            log.error("Milvus init failed, collection={}", collection, ex);
            if (milvusProperties.isFailOnInit()) {
                throw new IllegalStateException(
                        "Milvus 初始化失败：必须可用并完成集合加载后才能启动知识库服务。请检查 app.milvus.uri 与 Milvus 进程。",
                        ex
                );
            }
        }
    }
}
