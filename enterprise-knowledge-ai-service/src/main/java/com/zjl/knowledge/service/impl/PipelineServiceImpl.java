package com.zjl.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.knowledge.dto.pipeline.PipelineTaskVO;
import com.zjl.knowledge.dto.pipeline.PipelineVO;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunkLog;
import com.zjl.knowledge.entity.KbKnowledgeBase;
import com.zjl.knowledge.entity.KbPipeline;
import com.zjl.knowledge.mapper.KbDocumentChunkLogMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.mapper.KbKnowledgeBaseMapper;
import com.zjl.knowledge.mapper.KbPipelineMapper;
import com.zjl.knowledge.service.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineServiceImpl implements PipelineService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KbPipelineMapper pipelineMapper;
    private final KbKnowledgeBaseMapper kbMapper;
    private final KbDocumentChunkLogMapper chunkLogMapper;
    private final KbDocumentMapper documentMapper;

    @Override
    public List<PipelineVO> listPipelines(Long knowledgeBaseId, String status) {
        backfillMissingPipelines();
        var q = Wrappers.lambdaQuery(KbPipeline.class);
        if (knowledgeBaseId != null) {
            q.eq(KbPipeline::getKnowledgeBaseId, knowledgeBaseId);
        }
        if (StringUtils.hasText(status)) {
            q.eq(KbPipeline::getStatus, status);
        }
        q.orderByDesc(KbPipeline::getUpdatedAt);
        return pipelineMapper.selectList(q).stream()
                .peek(this::ensurePipelineDefaults)
                .map(this::toVo)
                .collect(Collectors.toList());
    }

    @Override
    public PipelineVO getPipeline(Long id) {
        KbPipeline p = pipelineMapper.selectById(id);
        return p == null ? null : toVo(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPipelineForKb(Long kbId) {
        KbKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            return;
        }

        Long exists = pipelineMapper.selectCount(
                Wrappers.lambdaQuery(KbPipeline.class)
                        .eq(KbPipeline::getKnowledgeBaseId, kbId)
        );
        if (exists != null && exists > 0) {
            return;
        }

        KbPipeline p = buildPipeline(kb);
        pipelineMapper.insert(p);
        log.info("自动创建流水线: kbId={}, pipelineId={}", kbId, p.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncPipelineFromKb(Long kbId) {
        KbKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            return;
        }

        KbPipeline p = pipelineMapper.selectOne(
                Wrappers.lambdaQuery(KbPipeline.class)
                        .eq(KbPipeline::getKnowledgeBaseId, kbId)
        );
        if (p == null) {
            return;
        }

        p.setName(kb.getName() + " · 文档入库链路");
        p.setEmbeddingModel(kb.getEmbeddingModel() == null ? "" : kb.getEmbeddingModel());
        p.setVectorEnabled(kb.getEmbeddingModel() != null && !kb.getEmbeddingModel().isEmpty() ? 1 : 0);
        p.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(p);
        log.info("同步流水线配置: kbId={}, pipelineId={}", kbId, p.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePipelineByKbId(Long kbId) {
        var q = Wrappers.lambdaUpdate(KbPipeline.class)
                .set(KbPipeline::getStatus, "DELETED")
                .eq(KbPipeline::getKnowledgeBaseId, kbId)
                .eq(KbPipeline::getDeleted, 0);
        int rows = pipelineMapper.delete(q);
        log.info("删除流水线: kbId={}, affectedRows={}", kbId, rows);
    }

    @Override
    public List<PipelineTaskVO> listTasks(Long pipelineId, Long documentId, String status, int current, int size) {
        var q = Wrappers.lambdaQuery(KbDocumentChunkLog.class);
        if (pipelineId != null) {
            q.eq(KbDocumentChunkLog::getPipelineId, String.valueOf(pipelineId));
        }
        if (StringUtils.hasText(status)) {
            q.eq(KbDocumentChunkLog::getStatus, status);
        }
        q.orderByDesc(KbDocumentChunkLog::getStartedAt);

        Page<KbDocumentChunkLog> page = new Page<>(current, size);
        var result = chunkLogMapper.selectPage(page, q);

        if (result.getRecords().isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> docIds = result.getRecords().stream()
                .map(KbDocumentChunkLog::getDocumentId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> docNameMap = documentMapper.selectBatchIds(docIds).stream()
                .collect(Collectors.toMap(KbDocument::getId, KbDocument::getTitle));

        List<Long> pIds = result.getRecords().stream()
                .map(KbDocumentChunkLog::getPipelineId)
                .filter(pid -> pid != null && !pid.isEmpty())
                .map(Long::parseLong)
                .distinct()
                .collect(Collectors.toList());
        final Map<Long, String> pipelineNameMap;
        if (!pIds.isEmpty()) {
            pipelineNameMap = pipelineMapper.selectBatchIds(pIds).stream()
                    .collect(Collectors.toMap(KbPipeline::getId, KbPipeline::getName));
        } else {
            pipelineNameMap = Collections.emptyMap();
        }

        return result.getRecords().stream().map(log -> toTaskVo(log, docNameMap, pipelineNameMap))
                .collect(Collectors.toList());
    }

    @Override
    public long countTasks(Long pipelineId, Long documentId, String status) {
        var q = Wrappers.lambdaQuery(KbDocumentChunkLog.class);
        if (pipelineId != null) {
            q.eq(KbDocumentChunkLog::getPipelineId, String.valueOf(pipelineId));
        }
        if (StringUtils.hasText(status)) {
            q.eq(KbDocumentChunkLog::getStatus, status);
        }
        return chunkLogMapper.selectCount(q);
    }

    private void backfillMissingPipelines() {
        List<KbKnowledgeBase> kbs = kbMapper.selectList(
                Wrappers.lambdaQuery(KbKnowledgeBase.class).eq(KbKnowledgeBase::getDeleted, 0));
        for (KbKnowledgeBase kb : kbs) {
            createPipelineForKb(kb.getId());
        }
    }

    private void ensurePipelineDefaults(KbPipeline p) {
        if (p == null) {
            return;
        }
        boolean dirty = false;
        KbKnowledgeBase kb = p.getKnowledgeBaseId() != null ? kbMapper.selectById(p.getKnowledgeBaseId()) : null;

        if (!StringUtils.hasText(p.getName()) && kb != null) {
            p.setName(kb.getName() + " · 文档入库链路");
            dirty = true;
        }
        if (!StringUtils.hasText(p.getDescription())) {
            p.setDescription("覆盖上传、解析、分块、向量写入和主表回写");
            dirty = true;
        }
        if (!StringUtils.hasText(p.getStages())) {
            p.setStages("[\"上传\", \"解析\", \"分块\", \"向量写入\", \"回写\"]");
            dirty = true;
        }
        if (!StringUtils.hasText(p.getChunkStrategy())) {
            p.setChunkStrategy("PARAGRAPH");
            dirty = true;
        }
        if (!StringUtils.hasText(p.getStatus())) {
            p.setStatus("ACTIVE");
            dirty = true;
        }
        if (kb != null) {
            String embeddingModel = kb.getEmbeddingModel() == null ? "" : kb.getEmbeddingModel();
            int vectorEnabled = StringUtils.hasText(embeddingModel) ? 1 : 0;
            if (!embeddingModel.equals(p.getEmbeddingModel() == null ? "" : p.getEmbeddingModel())) {
                p.setEmbeddingModel(embeddingModel);
                dirty = true;
            }
            if (p.getVectorEnabled() == null || p.getVectorEnabled() != vectorEnabled) {
                p.setVectorEnabled(vectorEnabled);
                dirty = true;
            }
        }
        if (dirty) {
            p.setUpdatedAt(LocalDateTime.now());
            pipelineMapper.updateById(p);
        }
    }

    private KbPipeline buildPipeline(KbKnowledgeBase kb) {
        KbPipeline p = new KbPipeline();
        p.setKnowledgeBaseId(kb.getId());
        p.setName(kb.getName() + " · 文档入库链路");
        p.setDescription("覆盖上传、解析、分块、向量写入和主表回写");
        p.setStages("[\"上传\", \"解析\", \"分块\", \"向量写入\", \"回写\"]");
        p.setChunkStrategy("PARAGRAPH");
        p.setVectorEnabled(kb.getEmbeddingModel() != null && !kb.getEmbeddingModel().isEmpty() ? 1 : 0);
        p.setEmbeddingModel(kb.getEmbeddingModel() == null ? "" : kb.getEmbeddingModel());
        p.setStatus("ACTIVE");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        p.setDeleted(0);
        return p;
    }

    private PipelineVO toVo(KbPipeline p) {
        PipelineVO vo = new PipelineVO();
        vo.setId(p.getId());
        vo.setKnowledgeBaseId(p.getKnowledgeBaseId());
        vo.setName(p.getName());
        vo.setDescription(p.getDescription());
        vo.setStages(parseStages(p.getStages()));
        vo.setChunkStrategy(p.getChunkStrategy());
        vo.setVectorEnabled(p.getVectorEnabled() != null && p.getVectorEnabled() == 1);
        vo.setEmbeddingModel(p.getEmbeddingModel());
        vo.setStatus(p.getStatus());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }

    private PipelineTaskVO toTaskVo(KbDocumentChunkLog log, Map<Long, String> docNameMap,
                                     Map<Long, String> pipelineNameMap) {
        PipelineTaskVO vo = new PipelineTaskVO();
        vo.setTaskId(String.valueOf(log.getId()));
        vo.setType("文档分块");
        vo.setDocumentName(docNameMap.getOrDefault(log.getDocumentId(), "—"));
        String pid = log.getPipelineId();
        if (pid != null && !pid.isEmpty()) {
            try {
                vo.setPipelineId(Long.parseLong(pid));
            } catch (NumberFormatException ignored) {
                vo.setPipelineId(null);
            }
            vo.setPipelineName(pipelineNameMap.getOrDefault(vo.getPipelineId(), "—"));
        }
        vo.setProgress(resolveProgress(log.getStatus()));
        vo.setStatus(log.getStatus());
        vo.setErrorMessage(log.getErrorMessage());
        vo.setCreatedAt(log.getStartedAt());
        vo.setUpdatedAt(log.getEndedAt());
        return vo;
    }

    private String resolveProgress(String status) {
        if ("SUCCESS".equals(status)) {
            return "100%";
        }
        if ("RUNNING".equals(status)) {
            return "处理中";
        }
        return "—";
    }

    private List<String> parseStages(String stagesJson) {
        if (!StringUtils.hasText(stagesJson)) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(stagesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析 stages JSON 失败: {}", stagesJson, e);
            return Collections.emptyList();
        }
    }
}
