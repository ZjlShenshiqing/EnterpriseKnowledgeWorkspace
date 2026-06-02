package com.zjl.platform.service;

import com.zjl.common.response.PageResult;
import com.zjl.platform.entity.SysOpLog;

public interface OpLogService {

    void log(Long userId, String username, String action, String method, String path, String detail);

    PageResult<SysOpLog> searchLogs(String keyword, String action, int page, int size);
}
