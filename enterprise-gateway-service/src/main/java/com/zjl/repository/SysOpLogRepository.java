package com.zjl.repository;

import com.zjl.domain.SysOpLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 操作日志数据访问层
 */
public interface SysOpLogRepository extends JpaRepository<SysOpLog, Long> {
}

