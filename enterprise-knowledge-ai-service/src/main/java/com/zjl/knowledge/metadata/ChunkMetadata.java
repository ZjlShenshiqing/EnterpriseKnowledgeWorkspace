package com.zjl.knowledge.metadata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 单个 chunk 的元数据，序列化后存入 {@code kb_document_chunk.metadata_json} 和 Milvus metadata JSON
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChunkMetadata {

    /** 文档 ID */
    private Long docId;

    /** 原始文件名 */
    private String fileName;

    /** 文件访问 URL */
    private String sourceUrl;

    /** 敏感级别：ALL / ADMIN_ONLY */
    private String sensitivityLevel;

    /** 允许访问的角色编码 */
    private List<String> accessRoles;

    /** 允许访问的部门 ID */
    private List<Long> accessDepartments;

    /** 块序号 */
    private int chunkIndex;

    /** 在原文中的起始字符位置 */
    private int startOffset;

    /** 在原文中的结束字符位置 */
    private int endOffset;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 序列化为 JSON 字符串
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * 从 JSON 字符串反序列化
     */
    public static ChunkMetadata fromJson(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return null;
        }
        try {
            return MAPPER.readValue(json, ChunkMetadata.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 转为 Map，供 VectorDocChunk.metadata() 使用
     */
    public Map<String, Object> toMap() {
        try {
            String json = MAPPER.writeValueAsString(this);
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }
}
