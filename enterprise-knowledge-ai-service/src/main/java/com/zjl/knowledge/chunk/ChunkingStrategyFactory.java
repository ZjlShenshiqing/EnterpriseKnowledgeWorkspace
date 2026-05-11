package com.zjl.knowledge.chunk;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.domain.ChunkingMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 分块策略工厂
 *
 * <p>Spring 容器中所有 {@link ChunkingStrategy} 实现会被自动注入，
 * 按 {@link ChunkingMode} 建立索引，通过 {@link #requireStrategy} 按模式获取。</p>
 *
 * <p>新增分块策略只需实现 {@link ChunkingStrategy} 接口并注册为 Spring Bean，
 * 无需修改本类</p>
 */
@Component
public class ChunkingStrategyFactory {

    /**
     * 策略注册表，key 为分块模式枚举，value 为对应策略实现
     */
    private final Map<ChunkingMode, ChunkingStrategy> strategies = new EnumMap<>(ChunkingMode.class);

    /**
     * 通过构造器注入自动收集所有 {@link ChunkingStrategy} 实现
     *
     * @param list Spring 容器中所有分块策略 Bean
     */
    public ChunkingStrategyFactory(List<ChunkingStrategy> list) {
        for (ChunkingStrategy s : list) {
            strategies.put(s.mode(), s);
        }
    }

    /**
     * 根据分块模式获取对应的策略实现，不存在则抛出 {@link BizException}
     *
     * @param mode 分块模式
     * @return 对应的策略实现
     * @throws BizException 当策略未注册时抛出，错误码 {@code PARAM_INVALID}
     */
    public ChunkingStrategy requireStrategy(ChunkingMode mode) {
        ChunkingStrategy s = strategies.get(mode);
        if (s == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的分块策略: " + mode);
        }
        return s;
    }
}
