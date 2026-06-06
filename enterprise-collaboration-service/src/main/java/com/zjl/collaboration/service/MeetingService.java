package com.zjl.collaboration.service;

import com.zjl.collaboration.entity.SysMeeting;

import java.util.List;

/**
 * 会议业务服务。
 */
public interface MeetingService {

    /**
     * 查询全部会议。
     *
     * @return 会议列表
     */
    List<SysMeeting> list();

    /**
     * 查询当前用户相关会议。
     *
     * @param userId   用户 ID
     * @param userName 用户名称
     * @return 会议列表
     */
    List<SysMeeting> listMine(Long userId, String userName);

    /**
     * 创建会议。
     *
     * @param title       标题
     * @param room        会议室
     * @param date        日期
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @param attendees   参会人
     * @param description 描述
     * @param userId      创建人
     * @return 会议 ID
     */
    Long create(String title, String room, String date, String startTime, String endTime,
                String attendees, String description, Long userId);

    /**
     * 更新会议。
     *
     * @param id          会议 ID
     * @param title       标题
     * @param room        会议室
     * @param date        日期
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @param attendees   参会人
     * @param description 描述
     */
    void update(Long id, String title, String room, String date, String startTime, String endTime,
                String attendees, String description);

    /**
     * 删除会议。
     *
     * @param id 会议 ID
     */
    void delete(Long id);

    /**
     * 检查会议冲突。
     *
     * @param date      日期
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param room      会议室
     * @param excludeId 排除会议 ID
     * @return 冲突检查结果
     */
    ConflictCheckResult checkConflict(String date, String startTime, String endTime, String room, Long excludeId);

    /**
     * 会议冲突检查结果。
     *
     * @param conflict  是否冲突
     * @param conflicts 冲突会议
     * @param message   提示信息
     */
    record ConflictCheckResult(boolean conflict, List<SysMeeting> conflicts, String message) {
    }
}
