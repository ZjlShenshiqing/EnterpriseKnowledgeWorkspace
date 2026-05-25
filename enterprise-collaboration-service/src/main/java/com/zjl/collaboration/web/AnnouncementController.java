package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.collaboration.entity.SysAnnouncement;
import com.zjl.collaboration.entity.SysUser;
import com.zjl.collaboration.mapper.SysAnnouncementMapper;
import com.zjl.collaboration.mapper.SysUserMapper;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final SysAnnouncementMapper announcementMapper;
    private final SysUserMapper userMapper;

    @GetMapping
    @Cacheable(value = "announcements", key = "'list'", unless = "#result.data.isEmpty()")
    public Result<List<SysAnnouncement>> list() {
        return Results.success(announcementMapper.selectList(
            Wrappers.lambdaQuery(SysAnnouncement.class).orderByDesc(SysAnnouncement::getIsPinned).orderByDesc(SysAnnouncement::getCreatedAt)));
    }

    @PostMapping
    public Result<Long> publish(@RequestBody AnnounceReq req, @RequestHeader("X-User-Id") Long userId) {
        SysUser user = userMapper.selectById(userId);
        SysAnnouncement a = new SysAnnouncement();
        a.setTitle(req.getTitle()); a.setContent(req.getContent());
        a.setPublisherId(userId); a.setPublisherName(user != null ? user.getRealName() : "管理员");
        a.setCreatedAt(LocalDateTime.now());
        announcementMapper.insert(a);
        log.info("公告发布: userId={}, announcementId={}", userId, a.getId());
        return Results.success(a.getId());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) { announcementMapper.deleteById(id); return Results.success(); }

    @Data public static class AnnounceReq { private String title; private String content; }
}
