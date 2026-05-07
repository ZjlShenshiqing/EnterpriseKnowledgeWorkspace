package com.zjl.knowledge.milvus;

import com.zjl.knowledge.config.MilvusProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 启动时自动创建并加载 Milvus 默认集合
 *
 * <p>通过 {@code @PostConstruct} 在 Bean 初始化阶段执行，
 * 由 {@code app.milvus.fail-on-init} 控制初始化失败时是否中止启动</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusCollectionBootstrap {

    /**
     * Milvus 集合创建工具
     */
    private final MilvusCollectionHelper milvusCollectionHelper;

    /**
     * Milvus 配置属性
     */
    private final MilvusProperties milvusProperties;

    /**
     * 初始化默认集合并加载到内存
     *
     * <p>若 {@code fail-on-init=true} 且初始化失败，抛出
     * {@link IllegalStateException} 中止应用启动</p>
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
