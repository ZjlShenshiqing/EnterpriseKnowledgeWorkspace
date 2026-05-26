package com.zjl.knowledge.service;

import com.zjl.knowledge.dto.pipeline.PipelineTaskVO;
import com.zjl.knowledge.dto.pipeline.PipelineVO;

import java.util.List;

/**
 * 流水线管理服务。
 */
public interface PipelineService {

    /**
     * 列出流水线定义，支持按知识库 ID 和状态筛选。
     */
    List<PipelineVO> listPipelines(Long knowledgeBaseId, String status);

    /**
     * 获取流水线详情。
     */
    PipelineVO getPipeline(Long id);

    /**
     * 为指定知识库创建流水线记录。
     */
    void createPipelineForKb(Long kbId);

    /**
     * 从知识库同步配置到流水线快照。
     */
    void syncPipelineFromKb(Long kbId);

    /**
     * 软删除知识库关联的流水线。
     */
    void deletePipelineByKbId(Long kbId);

    /**
     * 分页查询流水线任务（文档分块日志）。
     */
    List<PipelineTaskVO> listTasks(Long pipelineId, Long documentId, String status, int current, int size);

    /**
     * 查询流水线任务总数。
     */
    long countTasks(Long pipelineId, Long documentId, String status);
}
