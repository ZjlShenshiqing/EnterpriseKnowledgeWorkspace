package com.zjl.knowledge.chunk;

import java.util.Collections;
import java.util.Map;

/**
 * 分块策略参数
 *
 * <p>支持从 {@code kb_document.chunk_config} 的 JSON Map 反序列化，
 * 也可通过 {@link #defaults()} 获取默认配置（最大 2000 字符，无重叠）</p>
 *
 * @param maxChars     单个 chunk 的最大字符数，下限 256
 * @param overlapChars 相邻 chunk 之间的重叠字符数，非负数
 */
public record ChunkingOptions(
        int maxChars,
        int overlapChars
) {

    /**
     * 返回默认分块参数：最大 2000 字符，无重叠。
     *
     * @return 默认配置实例
     */
    public static ChunkingOptions defaults() {
        return new ChunkingOptions(2000, 0);
    }

    /**
     * 从键值对 Map 中解析分块参数，缺失的键使用默认值
     *
     * <p>支持的键：{@code maxChars}、{@code overlapChars}。
     * 数值越界会被修正：maxChars 下限 256，overlapChars 下限 0。</p>
     *
     * @param map 配置 Map，可为空
     * @return 解析后的分块参数
     */
    public static ChunkingOptions fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return defaults();
        }
        int max = parseInt(map.get("maxChars"), 2000);
        int overlap = parseInt(map.get("overlapChars"), 0);
        return new ChunkingOptions(Math.max(256, max), Math.max(0, overlap));
    }

    /**
     * 安全解析对象为 int，解析失败返回默认值。
     *
     * @param v   待解析的值（可为 Number 或数字字符串）
     * @param def 解析失败时的默认值
     * @return 解析后的整数
     */
    private static int parseInt(Object v, int def) {
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * 将当前参数序列化为不可变 Map，便于 JSON 序列化写入 {@code chunk_config}。
     *
     * @return 包含 maxChars 和 overlapChars 的 Map
     */
    public Map<String, Object> asMap() {
        return Map.of(
                "maxChars", maxChars,
                "overlapChars", overlapChars
        );
    }

    /**
     * 返回空 Map 常量
     *
     * @return 不可变空 Map
     */
    public static Map<String, Object> emptyMap() {
        return Collections.emptyMap();
    }
}
