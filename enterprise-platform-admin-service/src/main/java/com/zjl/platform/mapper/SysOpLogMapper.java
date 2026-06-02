package com.zjl.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.platform.entity.SysOpLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysOpLogMapper extends BaseMapper<SysOpLog> {

    @Select("<script>" +
            "SELECT * FROM sys_op_log WHERE 1=1" +
            "<if test='keyword != null and keyword != \"\"'> AND (username LIKE #{keyword} OR action LIKE #{keyword} OR detail LIKE #{keyword})</if>" +
            "<if test='action != null and action != \"\"'> AND action = #{action}</if>" +
            " ORDER BY created_at DESC" +
            "</script>")
    Page<SysOpLog> searchLogs(Page<SysOpLog> page, @Param("keyword") String keyword, @Param("action") String action);
}
