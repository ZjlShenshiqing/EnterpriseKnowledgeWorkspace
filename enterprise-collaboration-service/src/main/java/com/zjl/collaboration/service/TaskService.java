package com.zjl.collaboration.service;

import com.zjl.collaboration.entity.SysTaskComment;

import java.util.List;
import java.util.Map;

/**
 * 任务业务服务。
 */
public interface TaskService {

    /**
     * 查询任务列表。
     *
     * @param status 状态
     * @return 任务视图列表
     */
    List<Map<String, Object>> list(String status);

    /**
     * 创建任务。
     *
     * @param title       标题
     * @param description 描述
     * @param assigneeId  负责人
     * @param priority    优先级
     * @param userId      创建人
     * @return 任务 ID
     */
    Long create(String title, String description, Long assigneeId, String priority, Long userId);

    /**
     * 更新任务。
     *
     * @param id          任务 ID
     * @param title       标题
     * @param description 描述
     * @param assigneeId  负责人
     * @param priority    优先级
     */
    void update(Long id, String title, String description, Long assigneeId, String priority);

    /**
     * 更新任务状态。
     *
     * @param id     任务 ID
     * @param status 状态
     */
    void updateStatus(Long id, String status);

    /**
     * 删除任务。
     *
     * @param id 任务 ID
     */
    void delete(Long id);

    /**
     * 查询评论。
     *
     * @param taskId 任务 ID
     * @return 评论列表
     */
    List<SysTaskComment> comments(Long taskId);

    /**
     * 添加评论。
     *
     * @param taskId  任务 ID
     * @param content 内容
     * @param userId  用户 ID
     */
    void addComment(Long taskId, String content, Long userId);
}
