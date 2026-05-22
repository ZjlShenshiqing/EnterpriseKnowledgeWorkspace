package com.zjl.knowledge.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zjl.knowledge.agent.entity.KbAgentMessage;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

/**
 * Agent 消息 Mapper
 */
@Mapper
public interface KbAgentMessageMapper extends BaseMapper<KbAgentMessage> {

    /** 消息与会话统计（msgCount, sessionCount, totalTokens） */
    Map<String, Object> messageStats();
}
