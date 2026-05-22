package com.zjl.collaboration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.entity.SysDoc;
import com.zjl.collaboration.entity.SysDocOperation;
import com.zjl.collaboration.mapper.SysDocMapper;
import com.zjl.collaboration.mapper.SysDocOperationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocOTService {

    private static final int SNAPSHOT_INTERVAL = 50;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SysDocMapper docMapper;
    private final SysDocOperationMapper docOperationMapper;
    private final ConcurrentHashMap<Long, ReentrantLock> docLocks = new ConcurrentHashMap<>();

    /**
     * 提交操作：做 OT 变换后持久化并返回变换后的 op
     */
    public JsonNode submitOperation(Long docId, Long userId, JsonNode ops, int baseVersion) {
        ReentrantLock lock = docLocks.computeIfAbsent(docId, k -> new ReentrantLock());
        lock.lock();
        try {
            SysDoc doc = docMapper.selectById(docId);
            if (doc == null) {
                throw new IllegalArgumentException("文档不存在: " + docId);
            }

            int currentVersion = doc.getVersion() != null ? doc.getVersion() : 0;

            if (baseVersion > currentVersion) {
                throw new IllegalStateException(
                    "版本冲突: baseVersion=" + baseVersion + " > currentVersion=" + currentVersion);
            }

            List<Map<String, Object>> opList = opsToMapList(ops);

            if (baseVersion < currentVersion) {
                List<Map<String, Object>> concurrentOps = loadOpsSinceVersion(docId, baseVersion);
                for (Map<String, Object> concurrentOp : concurrentOps) {
                    opList = transform(opList, List.of(concurrentOp), true);
                }
            }

            JsonNode transformedOps = mapListToOps(opList);

            int newVersion = currentVersion + 1;
            SysDocOperation record = new SysDocOperation();
            record.setDocId(docId);
            record.setUserId(userId);
            record.setVersion(newVersion);
            record.setOperation(transformedOps.toString());
            docOperationMapper.insert(record);

            doc.setVersion(newVersion);
            if (newVersion - (doc.getSnapshotVersion() != null ? doc.getSnapshotVersion() : 0) >= SNAPSHOT_INTERVAL) {
                String newContent = applyOpsToContent(doc.getContent(), opList);
                doc.setContent(newContent);
                doc.setSnapshotVersion(newVersion);
            }
            docMapper.updateById(doc);

            return transformedOps;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取文档加载信息：快照内容 + version
     */
    public DocSnapshot getDocument(Long docId) {
        SysDoc doc = docMapper.selectById(docId);
        if (doc == null) return null;
        return new DocSnapshot(doc.getContent(), doc.getVersion() != null ? doc.getVersion() : 0);
    }

    /**
     * 获取指定版本之后的增量 ops
     */
    public List<Map<String, Object>> getOpsSinceVersion(Long docId, int sinceVersion) {
        return loadOpsSinceVersion(docId, sinceVersion);
    }

    /**
     * Quill Delta OT 变换算法
     * 将 opA 变换为可以应用在 opB 之后的形式
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> transform(
            List<Map<String, Object>> opA,
            List<Map<String, Object>> opB,
            boolean isLeft) {
        List<Map<String, Object>> result = new ArrayList<>();

        int indexA = 0;
        int offsetA = 0;
        int indexB = 0;
        int offsetB = 0;

        while (indexA < opA.size() && indexB < opB.size()) {
            Map<String, Object> curOpA = new java.util.LinkedHashMap<>(opA.get(indexA));
            Map<String, Object> curOpB = new java.util.LinkedHashMap<>(opB.get(indexB));

            if (curOpA.containsKey("insert")) {
                int len = getOpLength(curOpA);
                result.add(curOpA);
                offsetA += len;
                if (offsetA >= getOpLength(opA.get(indexA))) {
                    indexA++;
                    offsetA = 0;
                }
                continue;
            }
            if (curOpB.containsKey("insert")) {
                int lenB = getOpLength(curOpB);
                Map<String, Object> retainOp = new java.util.LinkedHashMap<>();
                retainOp.put("retain", lenB);
                result.add(retainOp);
                offsetB += lenB;
                if (offsetB >= getOpLength(opB.get(indexB))) {
                    indexB++;
                    offsetB = 0;
                }
                continue;
            }

            int lenA = getOpLength(curOpA) - offsetA;
            int lenB = getOpLength(curOpB) - offsetB;
            int minLen = Math.min(lenA, lenB);

            if (curOpA.containsKey("retain") && curOpB.containsKey("retain")) {
                Map<String, Object> retainOp = new java.util.LinkedHashMap<>();
                retainOp.put("retain", minLen);
                result.add(retainOp);
                offsetA += minLen;
                offsetB += minLen;
            } else if (curOpA.containsKey("delete") && curOpB.containsKey("delete")) {
                offsetA += minLen;
                offsetB += minLen;
            } else if (curOpA.containsKey("delete") && curOpB.containsKey("retain")) {
                Map<String, Object> deleteOp = new java.util.LinkedHashMap<>();
                deleteOp.put("delete", minLen);
                result.add(deleteOp);
                offsetA += minLen;
                offsetB += minLen;
            } else if (curOpA.containsKey("retain") && curOpB.containsKey("delete")) {
                offsetA += minLen;
                offsetB += minLen;
            }

            if (offsetA >= getOpLength(opA.get(indexA))) {
                indexA++;
                offsetA = 0;
            }
            if (offsetB >= getOpLength(opB.get(indexB))) {
                indexB++;
                offsetB = 0;
            }
        }

        while (indexA < opA.size()) {
            Map<String, Object> curOpA = opA.get(indexA);
            if (curOpA.containsKey("insert")) {
                result.add(new java.util.LinkedHashMap<>(curOpA));
            } else if (curOpA.containsKey("delete")) {
                result.add(new java.util.LinkedHashMap<>(curOpA));
            } else if (curOpA.containsKey("retain")) {
                int retainVal = ((Number) curOpA.get("retain")).intValue();
                int remaining = retainVal - offsetA;
                if (remaining > 0) {
                    Map<String, Object> retainOp = new java.util.LinkedHashMap<>();
                    retainOp.put("retain", remaining);
                    result.add(retainOp);
                }
            }
            indexA++;
            offsetA = 0;
        }

        return result;
    }

    private int getOpLength(Map<String, Object> op) {
        if (op.containsKey("retain")) {
            Object val = op.get("retain");
            return val instanceof Number ? ((Number) val).intValue() : 0;
        }
        if (op.containsKey("delete")) {
            Object val = op.get("delete");
            return val instanceof Number ? ((Number) val).intValue() : 0;
        }
        if (op.containsKey("insert")) {
            Object val = op.get("insert");
            if (val instanceof String) return ((String) val).length();
            if (val instanceof Map) return 1;
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> opsToMapList(JsonNode ops) {
        try {
            return OBJECT_MAPPER.convertValue(ops, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("解析 ops 失败", e);
        }
    }

    private JsonNode mapListToOps(List<Map<String, Object>> ops) {
        return OBJECT_MAPPER.valueToTree(ops);
    }

    private List<Map<String, Object>> loadOpsSinceVersion(Long docId, int sinceVersion) {
        List<SysDocOperation> records = docOperationMapper.selectList(
            new LambdaQueryWrapper<SysDocOperation>()
                .eq(SysDocOperation::getDocId, docId)
                .gt(SysDocOperation::getVersion, sinceVersion)
                .orderByAsc(SysDocOperation::getVersion));
        List<Map<String, Object>> ops = new ArrayList<>();
        for (SysDocOperation record : records) {
            try {
                List<Map<String, Object>> op = OBJECT_MAPPER.readValue(
                    record.getOperation(),
                    new TypeReference<List<Map<String, Object>>>() {});
                ops.addAll(op);
            } catch (JsonProcessingException e) {
                log.error("解析操作日志失败: {}", record.getId(), e);
            }
        }
        return ops;
    }

    @SuppressWarnings("unchecked")
    private String applyOpsToContent(String content, List<Map<String, Object>> ops) {
        try {
            JsonNode deltaNode = OBJECT_MAPPER.readTree(content);
            List<Map<String, Object>> delta = OBJECT_MAPPER.convertValue(
                deltaNode.path("ops"),
                new TypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> composed = composeDeltas(delta, ops);
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("ops", composed);
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            log.error("应用操作到内容失败", e);
            return content;
        }
    }

    private List<Map<String, Object>> composeDeltas(
            List<Map<String, Object>> base,
            List<Map<String, Object>> delta) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> op : base) {
            result.add(new java.util.LinkedHashMap<>(op));
        }
        for (Map<String, Object> op : delta) {
            result.add(new java.util.LinkedHashMap<>(op));
        }
        return simplifyOps(result);
    }

    private List<Map<String, Object>> simplifyOps(List<Map<String, Object>> ops) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> op : ops) {
            if (result.isEmpty()) {
                result.add(op);
                continue;
            }
            Map<String, Object> last = result.get(result.size() - 1);
            if (last.containsKey("retain") && op.containsKey("retain")) {
                int sum = ((Number) last.get("retain")).intValue() + ((Number) op.get("retain")).intValue();
                last.put("retain", sum);
            } else if (last.containsKey("delete") && op.containsKey("delete")) {
                int sum = ((Number) last.get("delete")).intValue() + ((Number) op.get("delete")).intValue();
                last.put("delete", sum);
            } else if (last.containsKey("insert") && op.containsKey("insert")) {
                Object lastInsert = last.get("insert");
                Object thisInsert = op.get("insert");
                if (lastInsert instanceof String && thisInsert instanceof String) {
                    last.put("insert", (String) lastInsert + (String) thisInsert);
                } else {
                    result.add(op);
                }
            } else {
                result.add(op);
            }
        }
        return result;
    }

    public record DocSnapshot(String content, int version) {}
}
