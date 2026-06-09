package com.zjl.collaboration.web;

import jakarta.validation.Valid;
import com.zjl.collaboration.dto.AnnouncementReq;
import com.zjl.collaboration.entity.SysAnnouncement;
import com.zjl.collaboration.service.AnnouncementService;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公告接口。
 */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    @Cacheable(value = "announcements", key = "'list'", unless = "#result.data.isEmpty()")
    public Result<List<SysAnnouncement>> list() {
        return Results.success(announcementService.list());
    }

    @PostMapping
    public Result<Long> publish(@Valid @RequestBody AnnouncementReq req, @RequestHeader("X-User-Id") Long userId) {
        return Results.success(announcementService.publish(req.getTitle(), req.getContent(), userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return Results.success();
    }

}
