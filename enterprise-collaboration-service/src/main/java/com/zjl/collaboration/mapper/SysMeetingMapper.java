package com.zjl.collaboration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.collaboration.entity.SysMeeting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SysMeetingMapper extends BaseMapper<SysMeeting> {

    /**
     * 查询指定日期和会议室的会议，带悲观锁（FOR UPDATE）
     * 
     * <p>用于会议冲突检测的并发场景，确保在事务中锁定相关记录，
     * 防止多个请求同时创建冲突的会议。</p>
     *
     * @param date 会议日期
     * @param room 会议室名称
     * @param excludeId 排除的会议ID（更新时使用），为null时不排除
     * @return 匹配的会议列表
     */
    @Select("SELECT * FROM sys_meeting WHERE date = #{date} AND room = #{room} " +
            "AND (#{excludeId} IS NULL OR id != #{excludeId}) FOR UPDATE")
    List<SysMeeting> selectForUpdateByDateAndRoom(
            @Param("date") LocalDate date, 
            @Param("room") String room, 
            @Param("excludeId") Long excludeId);
}
