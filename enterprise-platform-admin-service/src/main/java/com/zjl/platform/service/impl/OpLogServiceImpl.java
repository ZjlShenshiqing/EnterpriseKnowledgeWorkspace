package com.zjl.platform.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.common.response.PageResult;
import com.zjl.platform.entity.SysOpLog;
import com.zjl.platform.mapper.SysOpLogMapper;
import com.zjl.platform.service.OpLogService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OpLogServiceImpl implements OpLogService {

    private final SysOpLogMapper opLogMapper;

    public OpLogServiceImpl(SysOpLogMapper opLogMapper) {
        this.opLogMapper = opLogMapper;
    }

    @Override
    @Async
    public void log(Long userId, String username, String action, String method, String path, String detail) {
        SysOpLog log = new SysOpLog();
        log.setUserId(userId);
        log.setUsername(username != null ? username : "");
        log.setAction(action);
        log.setMethod(method != null ? method : "");
        log.setPath(path != null ? path : "");
        log.setDetail(detail);
        opLogMapper.insert(log);
    }

    @Override
    public PageResult<SysOpLog> searchLogs(String keyword, String action, int page, int size) {
        Page<SysOpLog> p = new Page<>(page, size);
        Page<SysOpLog> result = opLogMapper.searchLogs(p,
                keyword != null && !keyword.isBlank() ? "%" + keyword.trim() + "%" : null,
                action != null && !action.isBlank() ? action : null);
        return PageResult.of(page, size, result.getTotal(), result.getRecords());
    }
}
