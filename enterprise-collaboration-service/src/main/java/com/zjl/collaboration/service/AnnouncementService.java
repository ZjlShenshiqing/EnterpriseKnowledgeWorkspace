package com.zjl.collaboration.service;

import com.zjl.collaboration.entity.SysAnnouncement;

import java.util.List;

/**
 * 公告业务服务。
 */
public interface AnnouncementService {

    /**
     * 查询公告。
     *
     * @return 公告列表
     */
    List<SysAnnouncement> list();

    /**
     * 发布公告。
     *
     * @param title   标题
     * @param content 内容
     * @param userId  发布人
     * @return 公告 ID
     */
    Long publish(String title, String content, Long userId);

    /**
     * 删除公告。
     *
     * @param id 公告 ID
     */
    void delete(Long id);
}
