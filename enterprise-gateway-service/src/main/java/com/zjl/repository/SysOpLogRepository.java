package com.zjl.repository;

import com.zjl.domain.SysOpLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 操作日志数据访问层
 */
public interface SysOpLogRepository extends JpaRepository<SysOpLog, Long>, JpaSpecificationExecutor<SysOpLog> {

    /**
     * 分页查询操作日志，支持关键词（模糊匹配用户名/详情）和操作类型筛选
     */
    @Query("SELECT l FROM SysOpLog l WHERE "
            + "(:keyword IS NULL OR :keyword = '' OR l.username LIKE %:keyword% OR l.detail LIKE %:keyword%) "
            + "AND (:action IS NULL OR :action = '' OR l.action = :action) "
            + "ORDER BY l.createdAt DESC")
    Page<SysOpLog> searchLogs(@Param("keyword") String keyword, @Param("action") String action, Pageable pageable);
}

