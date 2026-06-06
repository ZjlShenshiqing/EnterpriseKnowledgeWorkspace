package com.zjl.collaboration.service;

import com.zjl.collaboration.entity.SysTodo;

import java.util.Date;
import java.util.List;

/**
 * 待办业务服务。
 */
public interface TodoService {

    /**
     * 查询用户待办。
     *
     * @param userId 用户 ID
     * @return 待办列表
     */
    List<SysTodo> list(Long userId);

    /**
     * 创建待办。
     *
     * @param title    标题
     * @param priority 优先级
     * @param dueDate  截止日期
     * @param userId   用户 ID
     * @return 待办 ID
     */
    Long create(String title, String priority, Date dueDate, Long userId);

    /**
     * 更新待办。
     *
     * @param id       待办 ID
     * @param title    标题
     * @param priority 优先级
     * @param dueDate  截止日期
     */
    void update(Long id, String title, String priority, Date dueDate);

    /**
     * 切换完成状态。
     *
     * @param id 待办 ID
     */
    void toggle(Long id);

    /**
     * 删除待办。
     *
     * @param id 待办 ID
     */
    void delete(Long id);
}
