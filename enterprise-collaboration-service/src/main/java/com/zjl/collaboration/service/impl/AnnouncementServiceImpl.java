package com.zjl.collaboration.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.SysAnnouncement;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.mapper.SysAnnouncementMapper;
import com.zjl.collaboration.service.AnnouncementService;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公告业务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    private final SysAnnouncementMapper announcementMapper;
    private final GatewayUserClient gatewayUserClient;

    @Override
    public List<SysAnnouncement> list() {
        return announcementMapper.selectList(Wrappers.lambdaQuery(SysAnnouncement.class)
                .orderByDesc(SysAnnouncement::getIsPinned)
                .orderByDesc(SysAnnouncement::getCreatedAt));
    }

    @Override
    public Long publish(String title, String content, Long userId) {
        UserInfo user = gatewayUserClient.getById(userId);
        SysAnnouncement announcement = new SysAnnouncement();
        announcement.setTitle(title);
        announcement.setContent(content);
        announcement.setPublisherId(userId);
        announcement.setPublisherName(user != null ? user.realName() : "管理员");
        announcement.setCreatedAt(LocalDateTime.now());
        announcementMapper.insert(announcement);
        log.info("公告发布: userId={}, announcementId={}", userId, announcement.getId());
        return announcement.getId();
    }

    @Override
    public void delete(Long id) {
        SysAnnouncement ann = announcementMapper.selectById(id);
        if (ann == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        announcementMapper.deleteById(id);
    }
}
