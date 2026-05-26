package com.zjl.collaboration.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * 文档 OT 协同编辑服务
 */
public interface DocOTService {

    JsonNode submitOperation(Long docId, Long userId, JsonNode ops, int baseVersion);

    DocSnapshot getDocument(Long docId);

    List<Map<String, Object>> getOpsSinceVersion(Long docId, int sinceVersion);

    List<Map<String, Object>> transform(
            List<Map<String, Object>> opA,
            List<Map<String, Object>> opB,
            boolean isLeft);

    record DocSnapshot(String content, int version) {}
}
